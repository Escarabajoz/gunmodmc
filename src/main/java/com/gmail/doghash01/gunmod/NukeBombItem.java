package com.gmail.doghash01.gunmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Tsar-class bomb. Right-click to arm it at the block you are looking at (up to 8 blocks away,
 * or in front of you if aiming at air). A beeping fuse counts down, then {@link NukeManager}
 * runs the blast: one-time entity damage with falloff, an expanding terrain shockwave, and a
 * mushroom cloud. The item is consumed on arming (except in creative mode).
 */
public class NukeBombItem extends Item {

    private final BombType type;

    public NukeBombItem(Item.Properties properties, BombType type) {
        super(properties);
        this.type = type;
    }

    public BombType getBombType() {
        return type;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel server) {
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getViewVector(1.0F).scale(8.0));
            BlockHitResult hit = server.clip(
                    new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 target = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : end;

            if (!NukeManager.start(server, target, type)) {
                player.sendOverlayMessage(Component.translatable("message.gunmod.too_many_nukes"));
                return InteractionResult.FAIL;
            }

            server.playSound(null, target.x, target.y, target.z,
                    ModSounds.NUKE_ARM.get(), SoundSource.BLOCKS, 4.0F, 1.0F);

            player.getCooldowns().addCooldown(stack, 20);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        } else {
            player.getCooldowns().addCooldown(stack, 20);
        }

        return InteractionResult.SUCCESS;
    }
}
