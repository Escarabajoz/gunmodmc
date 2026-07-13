package com.gmail.doghash01.gunmod;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events for the mod. The actual audio is defined in {@code assets/gunmod/sounds.json}, which
 * layers existing vanilla sound events so the mod ships no binary audio files of its own.
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GunMod.MODID);

    public static final RegistryObject<SoundEvent> PISTOL_FIRE = register("pistol_fire");
    public static final RegistryObject<SoundEvent> SMG_FIRE = register("smg_fire");
    public static final RegistryObject<SoundEvent> RIFLE_FIRE = register("rifle_fire");
    public static final RegistryObject<SoundEvent> SHOTGUN_FIRE = register("shotgun_fire");
    public static final RegistryObject<SoundEvent> SNIPER_FIRE = register("sniper_fire");
    public static final RegistryObject<SoundEvent> DRY_FIRE = register("dry_fire");
    public static final RegistryObject<SoundEvent> NUKE_ARM = register("nuke_arm");
    public static final RegistryObject<SoundEvent> NUKE_BEEP = register("nuke_beep");
    public static final RegistryObject<SoundEvent> NUKE_BLAST = register("nuke_blast");
    public static final RegistryObject<SoundEvent> NUKE_RUMBLE = register("nuke_rumble");
    public static final RegistryObject<SoundEvent> ROCKET_FIRE = register("rocket_fire");
    public static final RegistryObject<SoundEvent> HEADSHOT_DING = register("headshot_ding");
    public static final RegistryObject<SoundEvent> BLACK_HOLE = register("black_hole");

    private static RegistryObject<SoundEvent> register(String name) {
        // The SoundEvent's own id doubles as the key into sounds.json.
        // Note: ResourceKey#location was renamed to #identifier in this Minecraft generation.
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SOUNDS.key(name).identifier()));
    }

    private ModSounds() {
    }
}
