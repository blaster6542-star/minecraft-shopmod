package com.shopmod.shop;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalDouble;

/**
 * 데이터팩 JSON 하나에 대응하는 단일 거래 항목.
 * 가격은 double (sg_economy 소수점 지원).
 * buyPrice / sellPrice 중 하나만 있어도 유효.
 */
public record ShopEntry(
    ResourceLocation id,
    ItemStack item,
    OptionalDouble buyPrice,    // empty = 구매 불가
    OptionalDouble sellPrice,   // empty = 판매 불가
    String category,
    int sortOrder
) {
    public boolean canBuy()  { return buyPrice.isPresent(); }
    public boolean canSell() { return sellPrice.isPresent(); }

    public boolean isValid() {
        if (item == null || item.isEmpty()) return false;
        if (!canBuy() && !canSell()) return false;
        if (canBuy()  && buyPrice.getAsDouble()  <= 0) return false;
        if (canSell() && sellPrice.getAsDouble() <= 0) return false;
        return true;
    }
}
