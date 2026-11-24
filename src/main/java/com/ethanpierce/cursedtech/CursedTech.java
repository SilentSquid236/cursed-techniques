package com.ethanpierce.cursedtech;

import com.ethanpierce.cursedtech.core.ModRegistries;
import com.ethanpierce.cursedtech.core.ServerConfig;
import com.ethanpierce.cursedtech.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CursedTech.MODID)
public class CursedTech {
    public static final String MODID = "cursedtech";

    public CursedTech(IEventBus modBus, ModContainer container) {
        ModRegistries.register(modBus);
        modBus.addListener(ModNetwork::register);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        // Game-bus listeners live in core.ServerEvents (@EventBusSubscriber).
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
