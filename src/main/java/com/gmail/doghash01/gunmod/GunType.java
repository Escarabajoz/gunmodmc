package com.gmail.doghash01.gunmod;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Immutable stat block that defines how a single firearm behaves.
 *
 * @param damage             base damage per pellet, in half-hearts
 * @param range              maximum hitscan distance in blocks
 * @param pellets            rays fired per trigger pull (1 for most guns, many for the shotgun)
 * @param spread             maximum bullet spread in degrees (0 = perfectly accurate)
 * @param fireDelayTicks     cooldown between shots, in ticks (20 ticks = 1 second)
 * @param headshotMultiplier damage multiplier applied when a living target is hit near eye level
 * @param knockback          horizontal knockback strength applied to the target
 * @param volume             fire sound volume
 * @param ammo               the item consumed per trigger pull
 * @param fireSound          the sound played when the gun fires
 */
public record GunType(
        float damage,
        double range,
        int pellets,
        float spread,
        int fireDelayTicks,
        float headshotMultiplier,
        double knockback,
        float volume,
        Supplier<Item> ammo,
        Supplier<SoundEvent> fireSound) {
}
