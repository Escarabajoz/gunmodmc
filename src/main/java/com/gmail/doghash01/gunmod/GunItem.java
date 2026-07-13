package com.gmail.doghash01.gunmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * A hitscan firearm. On use it raycasts from the player's eyes along their look vector (with a random
 * spread cone), damages the first entity hit, applies knockback and a headshot bonus, and paints the
 * shot with muzzle, tracer and impact particles. Ammo is consumed from the player's inventory; the
 * gun's fire rate is enforced with the item cooldown system.
 *
 * <p>All world-affecting work runs on the logical server ({@link ServerLevel}); the method is still
 * invoked client-side, where it only reproduces the cooldown and hand-swing for responsiveness.</p>
 */
public class GunItem extends Item {

    private final GunType type;

    public GunItem(Item.Properties properties, GunType type) {
        super(properties);
        this.type = type;
    }

    public GunType getGunType() {
        return type;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        ItemCooldowns cooldowns = player.getCooldowns();
        if (cooldowns.isOnCooldown(gun)) {
            return InteractionResult.PASS;
        }

        boolean creative = player.getAbilities().instabuild;
        Item ammoItem = type.ammo().get();
        ItemStack ammo = creative ? ItemStack.EMPTY : findAmmo(player, ammoItem);

        // Out of ammo: dry-fire click and a short cooldown.
        if (!creative && ammo.isEmpty()) {
            if (!level.isClientSide) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                        ModSounds.DRY_FIRE.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
            }
            cooldowns.addCooldown(gun, 8);
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel server) {
            if (!creative) {
                ammo.shrink(1);
                // Live ammo counter on the action bar.
                player.sendOverlayMessage(Component.translatable(
                        "message.gunmod.ammo_left", countAmmo(player, ammoItem)));
            }

            RandomSource random = player.getRandom();
            Vec3 eye = player.getEyePosition();
            Vec3 forward = player.getViewVector(1.0F).normalize();
            // Orthonormal basis around the look vector for spread and muzzle offsets.
            Vec3 reference = Math.abs(forward.y) < 0.99 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
            Vec3 right = forward.cross(reference).normalize();
            Vec3 up = right.cross(forward).normalize();

            spawnMuzzleFlash(server, eye, forward, right, up);
            for (int i = 0; i < type.pellets(); i++) {
                fireRay(server, player, eye, forward, right, up, random);
            }

            float pitch = 0.9F + random.nextFloat() * 0.2F;
            server.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    type.fireSound().get(), SoundSource.PLAYERS, type.volume(), pitch);
        }

        cooldowns.addCooldown(gun, type.fireDelayTicks());
        return InteractionResult.SUCCESS;
    }

    /** Fires a single ray with random spread and resolves block/entity impact. */
    private void fireRay(ServerLevel level, Player player, Vec3 eye, Vec3 forward, Vec3 right, Vec3 up,
                         RandomSource random) {
        double spreadRad = Math.toRadians(type.spread());
        double yaw = random.nextGaussian() * spreadRad * 0.5;
        double pitch = random.nextGaussian() * spreadRad * 0.5;
        Vec3 dir = forward.add(right.scale(Math.tan(yaw))).add(up.scale(Math.tan(pitch))).normalize();
        Vec3 end = eye.add(dir.scale(type.range()));

        // Stop the ray at the first solid block.
        BlockHitResult blockHit = level.clip(
                new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 blockPos = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : end;

        EntityHit entityHit = raycastEntities(level, player, eye, blockPos);
        Vec3 impact;
        if (entityHit != null) {
            Entity target = entityHit.entity();
            boolean headshot = isHeadshot(target, entityHit.pos());
            float damage = headshot ? type.damage() * type.headshotMultiplier() : type.damage();

            DamageSource source = level.damageSources().playerAttack(player);
            target.hurtServer(level, source, damage);
            if (headshot) {
                // Satisfying confirmation ding for the shooter.
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                        ModSounds.HEADSHOT_DING.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
            }

            // Kick the target away from the shooter.
            Vec3 knock = dir.scale(type.knockback());
            target.push(knock.x, 0.08, knock.z);
            target.hurtMarked = true;

            impact = entityHit.pos();
            level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z,
                    headshot ? 10 : 6, 0.1, 0.1, 0.1, headshot ? 0.25 : 0.08);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, impact.x, impact.y, impact.z,
                    3, 0.1, 0.1, 0.1, 0.05);
        } else {
            impact = blockPos;
            if (blockHit.getType() != HitResult.Type.MISS) {
                level.sendParticles(ParticleTypes.SMOKE, impact.x, impact.y, impact.z, 5, 0.03, 0.03, 0.03, 0.01);
                level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z, 3, 0.02, 0.02, 0.02, 0.02);
            }
        }

        spawnTracer(level, eye, impact);
    }

    /** Returns the nearest hittable entity intersected by the segment, or {@code null}. */
    private static EntityHit raycastEntities(ServerLevel level, Player shooter, Vec3 start, Vec3 end) {
        AABB search = new AABB(start, end).inflate(1.0);
        Entity best = null;
        Vec3 bestPos = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (Entity entity : level.getEntities(shooter, search,
                candidate -> candidate != shooter && candidate.isPickable()
                        && candidate.isAlive() && !candidate.isSpectator())) {
            AABB box = entity.getBoundingBox().inflate(0.15);
            Vec3 pos = null;
            if (box.contains(start)) {
                pos = start;
            } else {
                Optional<Vec3> clip = box.clip(start, end);
                if (clip.isPresent()) {
                    pos = clip.get();
                }
            }
            if (pos != null) {
                double distSqr = start.distanceToSqr(pos);
                if (distSqr < bestDistSqr) {
                    bestDistSqr = distSqr;
                    best = entity;
                    bestPos = pos;
                }
            }
        }
        return best == null ? null : new EntityHit(best, bestPos);
    }

    /** A living target struck near eye level counts as a headshot. */
    private static boolean isHeadshot(Entity target, Vec3 hitPos) {
        if (!(target instanceof LivingEntity)) {
            return false;
        }
        return hitPos.y >= target.getEyeY() - 0.2;
    }

    private ItemStack findAmmo(Player player, Item ammoItem) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(ammoItem)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static int countAmmo(Player player, Item ammoItem) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(ammoItem)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void spawnMuzzleFlash(ServerLevel level, Vec3 eye, Vec3 forward, Vec3 right, Vec3 up) {
        Vec3 muzzle = eye.add(forward.scale(0.8)).add(right.scale(0.12)).subtract(0.0, 0.12, 0.0);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.01, 0.01, 0.01, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.02, 0.02, 0.02, 0.004);
    }

    private static void spawnTracer(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double distance = delta.length();
        int steps = Math.min(48, (int) (distance / 0.6));
        for (int i = 1; i <= steps; i++) {
            Vec3 point = start.add(delta.scale((double) i / (steps + 1)));
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private record EntityHit(Entity entity, Vec3 pos) {
    }
}
