package com.shopmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.shopmod.config.ShopConfig;
import com.shopmod.menu.ShopMenuProvider;
import com.shopmod.network.ModPackets;
import com.shopmod.shop.ShopRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ShopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("shop")
                .requires(src -> src.hasPermission(ShopConfig.SHOP_COMMAND_PERMISSION.get()))
                .executes(ctx -> openShop(ctx.getSource()))
        );
    }

    private static int openShop(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();

            if (ShopRegistry.getInstance().isEmpty()) {
                player.displayClientMessage(
                    Component.translatable("shop.shopmod.empty"), false);
                return 0;
            }

            String defaultCat = ShopRegistry.getInstance().getCategories()
                .stream().findFirst().orElse("");

            player.openMenu(
                new ShopMenuProvider(defaultCat),
                buf -> buf.writeUtf(defaultCat));

            // 상점 열릴 때 잔액 동기화
            ModPackets.sendBalanceSync(player);
            return 1;

        } catch (Exception e) {
            return 0;
        }
    }
}
