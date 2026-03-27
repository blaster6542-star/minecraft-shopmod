package com.shopmod.shop;

import com.shopmod.ShopMod;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 모든 ShopEntry를 보관하는 레지스트리.
 * - reload(): 데이터팩 리로드 시 전체 교체
 * - upsert(): Edit Mode에서 단일 항목 즉시 추가/수정
 * - removeById(): Edit Mode에서 단일 항목 즉시 삭제
 * 스레드 안전성: ConcurrentHashMap 사용 (서버/클라이언트 양쪽 접근 가능)
 */
public class ShopRegistry {

    private static final ShopRegistry INSTANCE = new ShopRegistry();

    // id → entry (순서 보장을 위해 LinkedHashMap)
    private final Map<ResourceLocation, ShopEntry> entryMap = new ConcurrentHashMap<>();

    private ShopRegistry() {}

    public static ShopRegistry getInstance() { return INSTANCE; }

    // ── 전체 리로드 (데이터팩 reload 시) ────────────────────────────

    public synchronized void reload(List<ShopEntry> entries) {
        entryMap.clear();
        entries.forEach(e -> entryMap.put(e.id(), e));
        ShopMod.LOGGER.info("[ShopMod] 상점 데이터 로드: {}개 항목, {}개 카테고리",
            entryMap.size(), getCategories().size());
    }

    // ── 단일 항목 즉시 갱신 (Edit Mode) ─────────────────────────────

    public synchronized void upsert(ShopEntry entry) {
        entryMap.put(entry.id(), entry);
        ShopMod.LOGGER.debug("[ShopMod] 항목 upsert: {}", entry.id());
    }

    public synchronized void removeById(ResourceLocation id) {
        ShopEntry removed = entryMap.remove(id);
        if (removed != null) {
            ShopMod.LOGGER.debug("[ShopMod] 항목 제거: {}", id);
        }
    }

    // ── 조회 ─────────────────────────────────────────────────────────

    public List<ShopEntry> getAll() {
        return List.copyOf(entryMap.values());
    }

    public List<String> getCategories() {
        return entryMap.values().stream()
            .map(ShopEntry::category)
            .distinct()
            .sorted()
            .toList();
    }

    public List<ShopEntry> getByCategory(String category) {
        return entryMap.values().stream()
            .filter(e -> e.category().equals(category))
            .sorted(Comparator.comparingInt(ShopEntry::sortOrder))
            .toList();
    }

    public boolean isEmpty() { return entryMap.isEmpty(); }
}
