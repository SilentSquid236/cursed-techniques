package com.ethanpierce.cursedtech.client;

import com.ethanpierce.cursedtech.CursedTech;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CursedTech.MODID, dist = Dist.CLIENT)
public class CursedTechClient {
    public CursedTechClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(ClientEvents::registerKeys);
        modBus.addListener(Hud::register);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }
}
