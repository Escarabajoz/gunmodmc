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
    public static final RegistryObject<Item> ROCKET = ammo("rocket");

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

    public static final RegistryObject<Item> MINIGUN = gun("minigun", new GunType(
            2.5F, 32.0, 1, 4.5F, 1, 1.3F, 0.10, 0.7F, BULLET::get, ModSounds.SMG_FIRE::get));

    public static final RegistryObject<Item> ROCKET_LAUNCHER = ITEMS.register("rocket_launcher",
            () -> new RocketLauncherItem(
                    new Item.Properties().setId(ITEMS.key("rocket_launcher")).stacksTo(1)));

    // ---- Tsar-class bombs ----
    // BombType(blastRadius, fuseTicks, maxDamage at ground zero, shockwave ringsPerTick, heat 0..1)
    // Yields are narrative tiers; radii are hand-tuned so the game stays playable. Heat drives
    // the melt system: lava/magma crater, sand->glass, ice->water, tree charring and fires.
    public static final RegistryObject<Item> TSAR_BOMB = bomb("tsar_bomb",
            new BombType(35, 100, 150.0F, 1, 0.40F));
    public static final RegistryObject<Item> TSAR_BOMB_100K = bomb("tsar_bomb_100k",
            new BombType(55, 120, 300.0F, 1, 0.55F));
    public static final RegistryObject<Item> TSAR_BOMB_1M = bomb("tsar_bomb_1m",
            new BombType(75, 140, 600.0F, 2, 0.70F));
    public static final RegistryObject<Item> TSAR_BOMB_100M = bomb("tsar_bomb_100m",
            new BombType(100, 160, 1200.0F, 2, 0.85F));
    public static final RegistryObject<Item> TSAR_BOMB_1G = bomb("tsar_bomb_1g",
            new BombType(130, 200, 2500.0F, 2, 0.95F));
    public static final RegistryObject<Item> TSAR_BOMB_1T = bomb("tsar_bomb_1t",
            new BombType(170, 240, 5000.0F, 3, 1.00F));

    /** Display order for the creative tab. */
    public static final List<RegistryObject<Item>> CREATIVE_ORDER = List.of(
            PISTOL, SMG, RIFLE, SHOTGUN, SNIPER, MINIGUN, ROCKET_LAUNCHER,
            BULLET, SHELL, HEAVY_ROUND, ROCKET,
            TSAR_BOMB, TSAR_BOMB_100K, TSAR_BOMB_1M, TSAR_BOMB_100M, TSAR_BOMB_1G, TSAR_BOMB_1T);

    /** Counts for the startup log line. */
    public static final int GUN_COUNT = 7;
    public static final int BOMB_COUNT = 6;

    private static RegistryObject<Item> ammo(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().setId(ITEMS.key(name))));
    }

    private static RegistryObject<Item> gun(String name, GunType type) {
        return ITEMS.register(name, () ->
                new GunItem(new Item.Properties().setId(ITEMS.key(name)).stacksTo(1), type));
    }

    private static RegistryObject<Item> bomb(String name, BombType type) {
        return ITEMS.register(name, () ->
                new NukeBombItem(new Item.Properties().setId(ITEMS.key(name)).stacksTo(16), type));
    }

    private ModItems() {
    }
}
