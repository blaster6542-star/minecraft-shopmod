package com.shopmod.client;

import com.shopmod.client.screen.ShopScreen;
import com.shopmod.menu.ShopMenuType;
import com.shopmod.network.ModPackets;
import com.shopmod.shop.ShopRegistry;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "shopmod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ShopMenuType.SHOP_MENU.get(), ShopScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_SHOP);
        event.register(KeyBindings.OPEN_EDIT_MODE);
    }
}

@EventBusSubscriber(modid = "shopmod", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
class ClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        while (KeyBindings.OPEN_SHOP.consumeClick()) {
            if (!ShopRegistry.getInstance().isEmpty()) {
                String cat = ShopRegistry.getInstance().getCategories()
                    .stream().findFirst().orElse("");
                PacketDistributor.sendToServer(new ModPackets.OpenShopPayload(cat));
            }
        }

        while (KeyBindings.OPEN_EDIT_MODE.consumeClick()) {
            PacketDistributor.sendToServer(new ModPackets.OpenEditModePayload());
        }
    }
}
