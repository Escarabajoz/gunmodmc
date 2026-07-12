package com.gmail.doghash01.gunmod;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Every item the mod registers: three ammo types followed by five firearms.
 *
 * <p>Ammo is declared before the guns so the gun definitions can reference the ammo
 * {@link RegistryObject}s during static initialisation.</p>
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GunMod.MODID);

    // ---- Ammo ----
    public static final RegistryObject<Item> BULLET = ammo("bullet");
    public static final RegistryObject<Item> SHELL = ammo("shell");
    public static final RegistryObject<Item> HEAVY_ROUND = ammo("heavy_round");

    // ---- Firearms ----
    // GunType(damage, range, pellets, spread°, fireDelayTicks, headshotMult, knockback, volume, ammo, sound)
    public static final RegistryObject<Item> PISTOL = gun("pistol", new GunType(
            5.0F, 32.0, 1, 1.6F, 7, 1.5F, 0.25, 0.9F, BULLET::get, ModSounds.PISTOL_FIRE::get));

    public static final RegistryObject<Item> SMG = gun("smg", new GunType(
            3.5F, 28.0, 1, 3.2F, 3, 1.4F, 0.15, 0.8F, BULLET::get, ModSounds.SMG_FIRE::get));

    public static final RegistryObject<Item> RIFLE = gun("rifle", new GunType(
            6.5F, 48.0, 1, 1.1F, 5, 1.6F, 0.30, 1.0F, BULLET::get, ModSounds.RIFLE_FIRE::get));

    public static final RegistryObject<Item> SHOTGUN = gun("shotgun", new GunType(
            3.0F, 16.0, 8, 6.0F, 16, 1.3F, 0.60, 1.0F, SHELL::get, ModSounds.SHOTGUN_FIRE::get));

    public static final RegistryObject<Item> SNIPER = gun("sniper", new GunType(
            14.0F, 96.0, 1, 0.25F, 28, 2.0F, 0.50, 1.0F, HEAVY_ROUND::get, ModSounds.SNIPER_FIRE::get));

    /** Display order for the creative tab. */
    public static final List<RegistryObject<Item>> CREATIVE_ORDER = List.of(
            PISTOL, SMG, RIFLE, SHOTGUN, SNIPER, BULLET, SHELL, HEAVY_ROUND);

    /** Number of firearms, for logging. */
    public static final int GUN_COUNT = 5;

    private static RegistryObject<Item> ammo(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().setId(ITEMS.key(name))));
    }

    private static RegistryObject<Item> gun(String name, GunType type) {
        return ITEMS.register(name, () ->
                new GunItem(new Item.Properties().setId(ITEMS.key(name)).stacksTo(1), type));
    }

    private ModItems() {
    }
}
