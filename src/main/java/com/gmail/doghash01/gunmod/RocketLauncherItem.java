package com.gmail.doghash01.gunmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fires a rocket that detonates where you aim (up to 64 blocks): a small, hot, instant
 * {@link NukeManager} blast — crater, fire, knockback — plus a smoke trail along the flight line.
 * Consumes one {@link ModItems#ROCKET} per shot.
 */
public class RocketLauncherItem extends Item {

    /** Small blast: radius 5, near-instant fuse, hot enough to start fires. */
    private static final BombType ROCKET_BLAST = BombType.nuke(5, 2, 40.0F, 6, 0.25F);
    private static final int FIRE_DELAY_TICKS = 40;
    private static final double RANGE = 64.0;

    public RocketLauncherItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack launcher = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(launcher)) {
            return InteractionResult.PASS;
        }

        boolean creative = player.getAbilities().instabuild;
        ItemStack rockets = creative ? ItemStack.EMPTY : findRockets(player);
        if (!creative && rockets.isEmpty()) {
            if (!level.isClientSide()) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                        ModSounds.DRY_FIRE.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
            }
            player.getCooldowns().addCooldown(launcher, 8);
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel server) {
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));
            BlockHitResult hit = server.clip(
                    new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 impact = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : end;

            if (!NukeManager.start(server, impact, ROCKET_BLAST)) {
                // Too many detonations in flight; don't waste the rocket.
                return InteractionResult.FAIL;
            }
            if (!creative) {
                rockets.shrink(1);
            }

            // Exhaust trail along the flight line.
            Vec3 delta = impact.subtract(eye);
            int steps = Math.min(40, (int) (delta.length() / 1.5));
            for (int i = 1; i <= steps; i++) {
                Vec3 point = eye.add(delta.scale((double) i / (steps + 1)));
                server.sendParticles(ParticleTypes.SMOKE, point.x, point.y, point.z, 2, 0.05, 0.05, 0.05, 0.005);
            }
            server.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    ModSounds.ROCKET_FIRE.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        }

        player.getCooldowns().addCooldown(launcher, FIRE_DELAY_TICKS);
        return InteractionResult.SUCCESS;
    }

    private static ItemStack findRockets(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(ModItems.ROCKET.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
