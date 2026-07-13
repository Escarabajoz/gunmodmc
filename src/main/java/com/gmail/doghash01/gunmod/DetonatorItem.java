package com.gmail.doghash01.gunmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The remote detonator. Right-click to trigger every bomb you previously planted with
 * sneak + right-click, no matter how far away you are. Bombs beyond the concurrent-detonation
 * cap stay planted for the next press.
 */
public class DetonatorItem extends Item {

    public DetonatorItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel server) {
            int fired = NukeManager.detonatePlanted(player.getUUID());
            if (fired == 0) {
                server.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                        ModSounds.DRY_FIRE.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
                player.sendOverlayMessage(Component.translatable("message.gunmod.no_planted"));
                return InteractionResult.FAIL;
            }
            server.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    ModSounds.NUKE_BEEP.get(), SoundSource.PLAYERS, 2.0F, 1.8F);
            player.sendOverlayMessage(Component.translatable("message.gunmod.detonated", fired));
        }

        player.getCooldowns().addCooldown(stack, 20);
        return InteractionResult.SUCCESS;
    }
}
