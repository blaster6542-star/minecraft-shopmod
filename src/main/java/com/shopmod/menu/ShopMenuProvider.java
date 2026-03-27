package com.shopmod.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.IContainerFactory;

public class ShopMenuProvider implements MenuProvider {

    private final String initialCategory;

    public ShopMenuProvider(String initialCategory) {
        this.initialCategory = initialCategory;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.shopmod.shop");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ShopMenu(containerId, inventory, initialCategory);
    }

    // 클라이언트로 초기 카테고리 전달
    public void writeExtraData(FriendlyByteBuf buf) {
        buf.writeUtf(initialCategory);
    }
}
