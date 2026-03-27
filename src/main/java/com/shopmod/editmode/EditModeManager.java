package com.shopmod.editmode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.shopmod.ShopMod;
import com.shopmod.network.ModPackets;
import com.shopmod.shop.ShopEntry;
import com.shopmod.shop.ShopRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.*;
import java.nio.file.Path;
import java.util.OptionalDouble;

/**
 * OP 편집 모드 서버 처리.
 * JSON 파일 저장 + ShopRegistry 직접 갱신(즉각 반영).
 * 저장 위치: world/datapacks/shopmod_edits/data/shopmod/shop/entries/
 */
public class EditModeManager {

    private static final Gson   GSON           = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATAPACK_NAME  = "shopmod_edits";

    /**
     * 편집/삭제 적용.
     * @return 성공 여부
     */
    public static boolean applyEdit(MinecraftServer server, ModPackets.EditEntryPayload p) {
        try {
            // 1. 저장 디렉터리 확보
            File entriesDir = getEntriesDir(server);
            entriesDir.mkdirs();
            ensureMcMeta(entriesDir.getParentFile().getParentFile().getParentFile());

            // 2. 파일명 결정 — itemId의 path 부분 사용
            //    예) "minecraft:diamond" → "diamond.json"
            //        "minecraft:iron_ingot" → "iron_ingot.json"
            String fileName = safeFileName(p.itemId()) + ".json";
            File   file     = new File(entriesDir, fileName);

            // 3. 삭제 처리
            if (p.delete()) {
                boolean deleted = !file.exists() || file.delete();
                if (deleted) {
                    // ShopRegistry에서 즉시 제거
                    ShopRegistry.getInstance().removeById(
                        ResourceLocation.fromNamespaceAndPath("shopmod_edits",
                            safeFileName(p.itemId())));
                    ShopMod.LOGGER.info("[ShopMod] 항목 삭제: {}", fileName);
                }
                return deleted;
            }

            // 4. 아이템 검증
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(p.itemId()));
            if (item == null || item == Items.AIR) {
                ShopMod.LOGGER.error("[ShopMod] 알 수 없는 아이템: {}", p.itemId());
                return false;
            }

            // 5. JSON 생성
            JsonObject json = new JsonObject();
            json.addProperty("item", p.itemId());
            if (p.count() > 1) json.addProperty("count", p.count());
            if (p.buyPrice()  > 0) json.addProperty("buy_price",  p.buyPrice());
            if (p.sellPrice() > 0) json.addProperty("sell_price", p.sellPrice());
            json.addProperty("category",   p.category().isEmpty() ? "general" : p.category());
            if (p.sortOrder() != 0) json.addProperty("sort_order", p.sortOrder());

            // 6. 파일 저장
            try (FileWriter w = new FileWriter(file)) {
                GSON.toJson(json, w);
            }

            // 7. ShopRegistry 직접 갱신 (즉각 반영 — /reload 불필요)
            ResourceLocation entryId = ResourceLocation.fromNamespaceAndPath(
                "shopmod_edits", safeFileName(p.itemId()));
            ItemStack stack = new ItemStack(item, Math.max(1, p.count()));

            ShopEntry newEntry = new ShopEntry(
                entryId,
                stack,
                p.buyPrice()  > 0 ? OptionalDouble.of(p.buyPrice())  : OptionalDouble.empty(),
                p.sellPrice() > 0 ? OptionalDouble.of(p.sellPrice()) : OptionalDouble.empty(),
                p.category().isEmpty() ? "general" : p.category(),
                p.sortOrder()
            );
            ShopRegistry.getInstance().upsert(newEntry);

            ShopMod.LOGGER.info("[ShopMod] 항목 저장+즉시반영: {}", file.getAbsolutePath());
            return true;

        } catch (Exception e) {
            ShopMod.LOGGER.error("[ShopMod] 편집 저장 실패: {}", e.getMessage(), e);
            return false;
        }
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────

    private static File getEntriesDir(MinecraftServer server) {
        Path world = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        return world.resolve("datapacks")
                    .resolve(DATAPACK_NAME)
                    .resolve("data/shopmod/shop/entries")
                    .toFile();
    }

    private static void ensureMcMeta(File datapackRoot) throws IOException {
        File mcmeta = new File(datapackRoot, "pack.mcmeta");
        if (!mcmeta.exists()) {
            datapackRoot.mkdirs();
            try (FileWriter w = new FileWriter(mcmeta)) {
                w.write("""
                    {
                      "pack": {
                        "pack_format": 48,
                        "description": "ShopMod Edit Mode"
                      }
                    }
                    """);
            }
        }
    }

    /**
     * 아이템 ID("minecraft:iron_ingot")에서 파일명용 문자열 추출.
     * "minecraft:iron_ingot" → "iron_ingot"
     * "mymod:custom/item"    → "custom_item"  (슬래시를 언더스코어로)
     */
    private static String safeFileName(String itemId) {
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        return path.replace("/", "_").replace(" ", "_");
    }
}
