package com.gmail.doghash01.gunmod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Drives Tsar-bomb detonations across server ticks.
 *
 * <p>The vanilla explosion engine computes ray-based blast resistance and cannot survive radii in
 * the hundreds, so detonations here are custom: after a beeping fuse, entities are damaged once
 * with distance falloff, and the terrain is erased by an expanding ring shockwave — a few rings
 * per tick — so even a 130-block-radius crater is spread over seconds instead of freezing the
 * server. A particle mushroom cloud lingers after the wave completes.</p>
 *
 * <p>All state is touched only from the server thread via {@code ServerTickEvent.Post}.</p>
 */
public final class NukeManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Concurrent-detonation cap; protects the server from bomb spam. */
    private static final int MAX_ACTIVE = 4;
    private static final List<Detonation> ACTIVE = new ArrayList<>();
    private static boolean initialized;

    private NukeManager() {
    }

    /** Called once from the mod constructor to hook the server tick. */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        TickEvent.ServerTickEvent.Post.BUS.addListener(NukeManager::onServerTick);
    }

    /** Arms a bomb. Returns false if too many detonations are already in flight. */
    static boolean start(ServerLevel level, Vec3 center, BombType type) {
        if (ACTIVE.size() >= MAX_ACTIVE) {
            return false;
        }
        ACTIVE.add(new Detonation(level, center, type));
        return true;
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Detonation> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Detonation detonation = iterator.next();
            boolean finished;
            try {
                finished = detonation.tick();
            } catch (Exception e) {
                LOGGER.error("Tsar bomb detonation failed; aborting it", e);
                finished = true;
            }
            if (finished) {
                iterator.remove();
            }
        }
    }

    /** One armed bomb: fuse countdown -> initial blast -> expanding ring shockwave -> lingering cloud. */
    private static final class Detonation {

        private static final BlockState AIR = Blocks.AIR.defaultBlockState();

        private final ServerLevel level;
        private final Vec3 center;
        private final BombType type;

        private int fuseLeft;
        private int ring;
        private int cloudTicks = 120;

        Detonation(ServerLevel level, Vec3 center, BombType type) {
            this.level = level;
            this.center = center;
            this.type = type;
            this.fuseLeft = type.fuseTicks();
        }

        /** Advances one tick; returns true when the detonation is completely finished. */
        boolean tick() {
            if (fuseLeft > 0) {
                fuseEffects();
                fuseLeft--;
                if (fuseLeft == 0) {
                    detonate();
                }
                return false;
            }
            if (ring <= type.radius()) {
                for (int i = 0; i < type.ringsPerTick() && ring <= type.radius(); i++) {
                    destroyRing(ring);
                    ring++;
                }
                waveEffects(Math.min(ring, type.radius()));
                return false;
            }
            if (cloudTicks-- > 0) {
                mushroomCloud();
                return false;
            }
            return true;
        }

        private void fuseEffects() {
            level.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.4, center.z, 3, 0.15, 0.3, 0.15, 0.01);
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.3, center.z, 1, 0.1, 0.1, 0.1, 0.004);
            if (fuseLeft % 20 == 0) {
                // Beeps rise in pitch as the countdown runs out.
                float progress = 1.0F - (float) fuseLeft / type.fuseTicks();
                level.playSound(null, center.x, center.y, center.z,
                        ModSounds.NUKE_BEEP.get(), SoundSource.BLOCKS, 4.0F, 0.7F + progress * 1.2F);
            }
        }

        private void detonate() {
            level.playSound(null, center.x, center.y, center.z,
                    ModSounds.NUKE_BLAST.get(), SoundSource.BLOCKS, 16.0F, 0.7F);
            level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 3, 1.0, 1.0, 1.0, 0.0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z,
                    12, 3.0, 2.0, 3.0, 0.0);
            damageEntities();
        }

        /** Hits every entity in 1.5x the blast radius once, with linear distance falloff. */
        private void damageEntities() {
            double reach = type.radius() * 1.5;
            AABB area = new AABB(center, center).inflate(reach);
            for (Entity entity : level.getEntities((Entity) null, area,
                    e -> e.isAlive() && !e.isSpectator())) {
                double distance = entity.position().distanceTo(center);
                if (distance > reach) {
                    continue;
                }
                float falloff = (float) (1.0 - distance / reach);
                float damage = Math.max(4.0F, type.maxDamage() * falloff);
                entity.hurtServer(level, level.damageSources().explosion(null, null), damage);

                Vec3 away = entity.position().subtract(center);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
                double knock = 4.0 * falloff;
                entity.push(away.x * knock, Math.min(2.0, 0.4 + knock * 0.25), away.z * knock);
                entity.hurtMarked = true;
            }
        }

        /**
         * Erases the vertical spherical slice whose horizontal distance from ground zero lies in
         * {@code [r, r+1)}. Scanning column-by-column keeps per-ring work proportional to the ring
         * circumference, and each column clears its full sphere chord in one pass.
         */
        private void destroyRing(int r) {
            int radius = type.radius();
            int cx = (int) Math.floor(center.x);
            int cy = (int) Math.floor(center.y);
            int cz = (int) Math.floor(center.z);
            long innerSq = (long) r * r;
            long outerSq = (long) (r + 1) * (r + 1);
            int minY = level.getMinY() + 1;
            int maxY = level.getMaxY() - 1;

            for (int dx = -(r + 1); dx <= r + 1; dx++) {
                for (int dz = -(r + 1); dz <= r + 1; dz++) {
                    long horizSq = (long) dx * dx + (long) dz * dz;
                    if (horizSq < innerSq || horizSq >= outerSq) {
                        continue;
                    }
                    double chordSq = (double) radius * radius - horizSq;
                    if (chordSq <= 0) {
                        continue;
                    }
                    int chord = (int) Math.floor(Math.sqrt(chordSq));
                    int x = cx + dx;
                    int z = cz + dz;
                    int yLo = Math.max(minY, cy - chord);
                    int yHi = Math.min(maxY, cy + chord);
                    for (int y = yLo; y <= yHi; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || isProtected(state)) {
                            continue;
                        }
                        // Flag 2: sync to clients; flag 16: skip neighbour shape updates. No drops.
                        level.setBlock(pos, AIR, 2 | 16);
                    }
                }
            }
        }

        private static boolean isProtected(BlockState state) {
            return state.is(Blocks.BEDROCK)
                    || state.is(Blocks.BARRIER)
                    || state.is(Blocks.END_PORTAL)
                    || state.is(Blocks.END_PORTAL_FRAME)
                    || state.is(Blocks.END_GATEWAY)
                    || state.is(Blocks.COMMAND_BLOCK);
        }

        private void waveEffects(int r) {
            RandomSource random = level.getRandom();
            for (int i = 0; i < 12; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                level.sendParticles(ParticleTypes.CLOUD,
                        center.x + Math.cos(angle) * r, center.y + 1.5, center.z + Math.sin(angle) * r,
                        2, 1.0, 1.0, 1.0, 0.02);
            }
            if ((r & 7) == 0) {
                double angle = random.nextDouble() * Math.PI * 2;
                level.playSound(null,
                        center.x + Math.cos(angle) * r, center.y, center.z + Math.sin(angle) * r,
                        ModSounds.NUKE_RUMBLE.get(), SoundSource.BLOCKS, 6.0F,
                        0.6F + random.nextFloat() * 0.3F);
            }
            mushroomCloud();
        }

        private void mushroomCloud() {
            RandomSource random = level.getRandom();
            int height = type.radius();
            // Stem.
            for (int i = 0; i < 6; i++) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        center.x + (random.nextDouble() - 0.5) * 6.0,
                        center.y + random.nextDouble() * height,
                        center.z + (random.nextDouble() - 0.5) * 6.0,
                        2, 1.2, 1.2, 1.2, 0.01);
            }
            // Cap.
            double capRadius = height * 0.45;
            for (int i = 0; i < 8; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(random.nextDouble()) * capRadius;
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        center.x + Math.cos(angle) * rr,
                        center.y + height + (random.nextDouble() - 0.5) * 4.0,
                        center.z + Math.sin(angle) * rr,
                        3, 2.0, 1.0, 2.0, 0.01);
            }
            // Burning ground zero.
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 2.0, center.z,
                    4, 3.0, 1.5, 3.0, 0.02);
        }
    }
}
