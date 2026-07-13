package com.gmail.doghash01.gunmod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
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
 * Drives Tsar-bomb (and rocket) detonations across server ticks.
 *
 * <p>The vanilla explosion engine computes ray-based blast resistance and cannot survive radii in
 * the hundreds, so detonations here are custom: after a beeping fuse, entities are damaged and set
 * ablaze once with distance falloff, and the terrain is erased by an expanding ring shockwave — a
 * few rings per tick — so even a 170-block-radius crater is spread over seconds instead of
 * freezing the server. The bomb's {@link BombType#heat() heat} then melts the world: the crater
 * floor turns to magma and lava near ground zero, and a scorch ring past the crater bakes sand to
 * glass, clay to terracotta, melts ice and snow, chars trees and starts fires. A particle mushroom
 * cloud lingers after the wave completes.</p>
 *
 * <p>All state is touched only from the server thread via {@code ServerTickEvent.Post}.</p>
 */
public final class NukeManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Concurrent-detonation cap; protects the server from bomb spam. Rockets share this pool. */
    private static final int MAX_ACTIVE = 8;
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

    /**
     * One armed bomb: fuse countdown -> initial blast -> expanding ring shockwave (with molten
     * floor) -> thermal scorch ring -> lingering cloud.
     */
    private static final class Detonation {

        private static final BlockState AIR = Blocks.AIR.defaultBlockState();

        private final ServerLevel level;
        private final Vec3 center;
        private final BombType type;

        private int fuseLeft;
        private int ring;
        private int cloudTicks;
        private int vortexTick;

        Detonation(ServerLevel level, Vec3 center, BombType type) {
            this.level = level;
            this.center = center;
            this.type = type;
            this.fuseLeft = type.fuseTicks();
            // Nukes: bigger bombs leave a longer-lasting mushroom cloud.
            // Black holes: a short collapse phase after everything is absorbed.
            this.cloudTicks = type.style() == BombType.Style.BLACK_HOLE ? 60 : 100 + type.radius();
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
            if (type.style() == BombType.Style.BLACK_HOLE) {
                return tickBlackHole();
            }
            int scorchEnd = type.radius() + type.scorchWidth();
            if (ring <= scorchEnd) {
                for (int i = 0; i < type.ringsPerTick() && ring <= scorchEnd; i++) {
                    if (ring <= type.radius()) {
                        destroyRing(ring);
                    } else {
                        scorchRing(ring);
                    }
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

        /**
         * Black hole phase: every tick the singularity drags entities inward (crushing whatever
         * reaches it), while the event horizon grows one ring every other tick, silently
         * swallowing the terrain sphere. Afterwards it collapses in on itself.
         */
        private boolean tickBlackHole() {
            vortexTick++;
            pullEntities();
            if (ring <= type.radius()) {
                if ((vortexTick & 1) == 0) {
                    destroyRing(ring);
                    ring++;
                }
                vortexEffects();
                return false;
            }
            if (cloudTicks-- > 0) {
                vortexEffects();
                return false;
            }
            // Final collapse.
            level.playSound(null, center.x, center.y, center.z,
                    ModSounds.NUKE_RUMBLE.get(), SoundSource.BLOCKS, 8.0F, 0.5F);
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1),
                    center.x, center.y, center.z, 2, 0.5, 0.5, 0.5, 0.0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z,
                    4, 1.0, 1.0, 1.0, 0.0);
            return true;
        }

        /** Drags everything toward the singularity; whatever reaches the core is crushed. */
        private void pullEntities() {
            double pull = type.radius() * 2.5;
            AABB area = new AABB(center, center).inflate(pull);
            for (Entity entity : level.getEntities((Entity) null, area,
                    e -> e.isAlive() && !e.isSpectator())) {
                Vec3 toCenter = center.subtract(entity.position());
                double distance = toCenter.length();
                if (distance > pull || distance < 1.0E-3) {
                    continue;
                }
                Vec3 dir = toCenter.scale(1.0 / distance);
                double strength = 0.05 + 0.55 * (1.0 - distance / pull);
                entity.push(dir.x * strength, dir.y * strength + 0.02, dir.z * strength);
                entity.hurtMarked = true;
                if (distance < 3.0) {
                    entity.hurtServer(level, level.damageSources().explosion(null, null),
                            type.maxDamage() / 20.0F);
                }
            }
        }

        /** Swirling portal vortex plus an ink-dark event horizon. */
        private void vortexEffects() {
            RandomSource random = level.getRandom();
            double horizon = Math.min(ring, type.radius());
            for (int i = 0; i < 10; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = horizon * (0.4 + random.nextDouble() * 1.2);
                level.sendParticles(ParticleTypes.PORTAL,
                        center.x + Math.cos(angle) * dist,
                        center.y + (random.nextDouble() - 0.5) * horizon,
                        center.z + Math.sin(angle) * dist,
                        3, 0.3, 0.3, 0.3, 0.05);
            }
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z,
                    8, 1.5, 1.5, 1.5, 0.02);
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z,
                    4, horizon * 0.3, horizon * 0.3, horizon * 0.3, 0.01);
            if (vortexTick % 40 == 1) {
                level.playSound(null, center.x, center.y, center.z,
                        ModSounds.BLACK_HOLE.get(), SoundSource.BLOCKS, 6.0F,
                        0.5F + random.nextFloat() * 0.2F);
            }
        }

        private void fuseEffects() {
            if (type.style() == BombType.Style.BLACK_HOLE) {
                level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 0.5, center.z,
                        6, 0.3, 0.3, 0.3, 0.05);
            } else {
                level.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.4, center.z, 3, 0.15, 0.3, 0.15, 0.01);
                level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.3, center.z, 1, 0.1, 0.1, 0.1, 0.004);
            }
            if (fuseLeft % 20 == 0) {
                // Beeps rise in pitch as the countdown runs out.
                float progress = 1.0F - (float) fuseLeft / type.fuseTicks();
                level.playSound(null, center.x, center.y, center.z,
                        ModSounds.NUKE_BEEP.get(), SoundSource.BLOCKS, 4.0F, 0.7F + progress * 1.2F);
            }
        }

        private void detonate() {
            if (type.style() == BombType.Style.BLACK_HOLE) {
                // Collapse inward: dark burst, no blast wave, no fire.
                level.playSound(null, center.x, center.y, center.z,
                        ModSounds.BLACK_HOLE.get(), SoundSource.BLOCKS, 10.0F, 0.4F);
                level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y + 1, center.z,
                        40, 2.0, 2.0, 2.0, 0.1);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 1, center.z,
                        60, 3.0, 3.0, 3.0, 0.2);
                return;
            }
            level.playSound(null, center.x, center.y, center.z,
                    ModSounds.NUKE_BLAST.get(), SoundSource.BLOCKS, 16.0F, 0.7F);
            // FLASH takes a ColorParticleOption in this Minecraft generation; -1 = opaque white.
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, -1),
                    center.x, center.y + 1, center.z, 3, 1.0, 1.0, 1.0, 0.0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z,
                    12, 3.0, 2.0, 3.0, 0.0);
            damageEntities();
        }

        /**
         * Hits every entity in 1.5x the blast radius once, with linear distance falloff, and sets
         * them on fire out to 2x the radius — the thermal pulse reaches further than the blast.
         */
        private void damageEntities() {
            double blastReach = type.radius() * 1.5;
            double heatReach = type.radius() * 2.0;
            AABB area = new AABB(center, center).inflate(heatReach);
            for (Entity entity : level.getEntities((Entity) null, area,
                    e -> e.isAlive() && !e.isSpectator())) {
                double distance = entity.position().distanceTo(center);

                if (distance <= heatReach && type.heat() > 0.0F) {
                    float heatFalloff = (float) (1.0 - distance / heatReach);
                    entity.igniteForSeconds(2.0F + 14.0F * type.heat() * heatFalloff);
                }

                if (distance > blastReach) {
                    continue;
                }
                float falloff = (float) (1.0 - distance / blastReach);
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
         * {@code [r, r+1)}, then heat-treats the newly exposed crater floor. Scanning
         * column-by-column keeps per-ring work proportional to the ring circumference, and each
         * column clears its full sphere chord in one pass.
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
            RandomSource random = level.getRandom();

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
                    meltCraterFloor(x, yLo - 1, z, Math.sqrt((double) horizSq), random);
                }
            }
        }

        /** Heat-treats the crater floor block under a cleared column: lava core, magma further out. */
        private void meltCraterFloor(int x, int floorY, int z, double horizDist, RandomSource random) {
            float heat = type.heat();
            if (heat <= 0.0F || floorY <= level.getMinY()) {
                return;
            }
            BlockPos pos = new BlockPos(x, floorY, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || isProtected(state)) {
                return;
            }
            if (horizDist <= type.moltenRadius()) {
                // Molten core: mostly lava with magma edges.
                if (random.nextFloat() < 0.45F * heat) {
                    level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 2 | 16);
                } else if (random.nextFloat() < 0.6F) {
                    level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2 | 16);
                }
            } else if (random.nextFloat() < 0.22F * heat) {
                level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2 | 16);
            } else {
                meltInPlace(pos, state, random, heat);
            }
        }

        /**
         * Thermal ring past the crater edge: nothing is destroyed, but the exposed surface melts —
         * sand vitrifies, clay bakes, ice and snow melt, water flashes to steam, trees char, and
         * fires break out.
         */
        private void scorchRing(int r) {
            int cx = (int) Math.floor(center.x);
            int cy = (int) Math.floor(center.y);
            int cz = (int) Math.floor(center.z);
            long innerSq = (long) r * r;
            long outerSq = (long) (r + 1) * (r + 1);
            int radius = type.radius();
            int minY = Math.max(level.getMinY() + 1, cy - radius);
            int maxY = Math.min(level.getMaxY() - 1, cy + radius);
            RandomSource random = level.getRandom();
            float heat = type.heat();
            // Heat fades across the scorch ring.
            float fade = 1.0F - (float) (r - radius) / Math.max(1, type.scorchWidth());

            for (int dx = -(r + 1); dx <= r + 1; dx++) {
                for (int dz = -(r + 1); dz <= r + 1; dz++) {
                    long horizSq = (long) dx * dx + (long) dz * dz;
                    if (horizSq < innerSq || horizSq >= outerSq) {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    // Find the exposed surface in this column.
                    for (int y = maxY; y >= minY; y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir()) {
                            continue;
                        }
                        if (!isProtected(state) && random.nextFloat() < heat * fade) {
                            meltInPlace(pos, state, random, heat * fade);
                        }
                        break;
                    }
                }
            }
        }

        /** Applies a heat transformation to a single exposed block. */
        private void meltInPlace(BlockPos pos, BlockState state, RandomSource random, float intensity) {
            BlockState replacement = null;
            boolean tryFire = false;

            if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) {
                replacement = Blocks.GLASS.defaultBlockState();
            } else if (state.is(Blocks.CLAY)) {
                replacement = Blocks.TERRACOTTA.defaultBlockState();
            } else if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                    || state.is(Blocks.FROSTED_ICE)) {
                replacement = Blocks.WATER.defaultBlockState();
            } else if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
                replacement = AIR;
            } else if (state.is(Blocks.WATER)) {
                // Steam flash: shallow surface water evaporates.
                if (random.nextFloat() < 0.5F * intensity) {
                    replacement = AIR;
                }
            } else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
                replacement = Blocks.COARSE_DIRT.defaultBlockState();
                tryFire = true;
            } else if (state.is(BlockTags.LEAVES)) {
                replacement = AIR;
            } else if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
                replacement = random.nextFloat() < 0.6F ? Blocks.COAL_BLOCK.defaultBlockState() : AIR;
                tryFire = true;
            } else if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.ANDESITE)
                    || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE) || state.is(Blocks.DEEPSLATE)) {
                if (random.nextFloat() < 0.25F * intensity) {
                    replacement = Blocks.MAGMA_BLOCK.defaultBlockState();
                }
                tryFire = true;
            } else {
                tryFire = true;
            }

            if (replacement != null) {
                level.setBlock(pos, replacement, 2 | 16);
            }
            // Fires break out on top of heated solid ground.
            if (tryFire && random.nextFloat() < 0.12F * intensity) {
                BlockPos above = pos.above();
                if (above.getY() < level.getMaxY() && level.getBlockState(above).isAir()) {
                    level.setBlock(above, Blocks.FIRE.defaultBlockState(), 2 | 16);
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
