package com.shopmod;

import com.shopmod.command.ShopCommand;
import com.shopmod.config.ShopConfig;
import com.shopmod.menu.ShopMenuType;
import com.shopmod.network.ModPackets;
import com.shopmod.shop.ShopReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ShopMod.MOD_ID)
public class ShopMod {

    public static final String MOD_ID = "shopmod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public ShopMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, ShopConfig.SERVER_SPEC);

        ShopMenuType.MENU_TYPES.register(modEventBus);
        ModPackets.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ShopReloadListener());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ShopCommand.register(event.getDispatcher());
    }
}
