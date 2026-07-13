package com.gmail.doghash01.gunmod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Gun Mod — main entry point.
 *
 * <p>Targets Minecraft 26.2 / Forge 65.x (ForgeGradle 7, EventBus 7, Java 25). Registration follows
 * the current Forge pattern: {@link DeferredRegister}s are attached to the mod bus group obtained
 * from the {@link FMLJavaModLoadingContext} passed to the constructor.</p>
 */
@Mod(GunMod.MODID)
public final class GunMod {

    /** Mod id — must match {@code [[mods]] modId} in mods.toml and {@code mod_id} in gradle.properties. */
    public static final String MODID = "gunmod";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Registry for this mod's creative tab(s). */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** A single creative tab holding every gun and ammo type, placed just before the vanilla Combat tab. */
    public static final RegistryObject<CreativeModeTab> GUN_TAB = CREATIVE_TABS.register("guns",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".guns"))
                    .icon(() -> ModItems.RIFLE.get().getDefaultInstance())
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .displayItems((parameters, output) ->
                            ModItems.CREATIVE_ORDER.forEach(item -> output.accept(item.get())))
                    .build());

    public GunMod(FMLJavaModLoadingContext context) {
        // The mod bus group is the EventBus-7 handle used to attach deferred registers and listeners.
        var modBus = context.getModBusGroup();

        // Touching these fields forces the classes to initialise, collecting every registry entry,
        // before the registers are attached to the mod bus.
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        CREATIVE_TABS.register(modBus);

        // Hook the server tick that drives Tsar-bomb detonations.
        NukeManager.init();

        LOGGER.info("Gun Mod loaded — {} firearms and {} Tsar bombs ready.",
                ModItems.GUN_COUNT, ModItems.BOMB_COUNT);
    }
}
