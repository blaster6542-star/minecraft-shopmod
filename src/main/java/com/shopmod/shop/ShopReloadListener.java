package com.shopmod.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.shopmod.ShopMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * data/<namespace>/shop/entries/*.json 파싱.
 * 가격 필드 buy_price / sell_price 는 double 지원.
 */
public class ShopReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().create();

    public ShopReloadListener() {
        super(GSON, "shop/entries");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects,
                         ResourceManager manager,
                         ProfilerFiller profiler) {
        List<ShopEntry> loaded = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> e : objects.entrySet()) {
            ResourceLocation id = e.getKey();
            try {
                ShopEntry entry = parse(id, e.getValue().getAsJsonObject());
                if (entry.isValid()) {
                    loaded.add(entry);
                } else {
                    ShopMod.LOGGER.warn("[ShopMod] 유효하지 않은 항목 무시: {}", id);
                }
            } catch (Exception ex) {
                ShopMod.LOGGER.error("[ShopMod] 파싱 실패: {} - {}", id, ex.getMessage());
            }
        }

        ShopRegistry.getInstance().reload(loaded);
    }

    private ShopEntry parse(ResourceLocation id, JsonObject json) {
        // 아이템
        String itemId = json.get("item").getAsString();
        int count = json.has("count") ? json.get("count").getAsInt() : 1;

        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == null || item == Items.AIR)
            throw new IllegalArgumentException("존재하지 않는 아이템: " + itemId);

        ItemStack stack = new ItemStack(item, count);

        // 가격 (double 지원)
        OptionalDouble buyPrice = json.has("buy_price")
            ? OptionalDouble.of(json.get("buy_price").getAsDouble())
            : OptionalDouble.empty();

        OptionalDouble sellPrice = json.has("sell_price")
            ? OptionalDouble.of(json.get("sell_price").getAsDouble())
            : OptionalDouble.empty();

        String category = json.has("category")
            ? json.get("category").getAsString() : "general";

        int sortOrder = json.has("sort_order")
            ? json.get("sort_order").getAsInt() : 0;

        return new ShopEntry(id, stack, buyPrice, sellPrice, category, sortOrder);
    }
}
