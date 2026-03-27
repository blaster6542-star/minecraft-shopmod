package com.shopmod.menu;

import com.shopmod.ShopMod;
import com.shopmod.config.ShopConfig;
import com.shopmod.currency.CurrencyManager;
import com.shopmod.shop.ShopEntry;
import com.shopmod.shop.ShopRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class ShopMenu extends AbstractContainerMenu {

    private double  cachedBalance = 0.0;
    private boolean decimalSystem = false;
    private final Player player;
    private String currentCategory;

    public ShopMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readUtf());
    }

    public ShopMenu(int containerId, Inventory inventory, String initialCategory) {
        super(ShopMenuType.SHOP_MENU.get(), containerId);
        this.player = inventory.player;
        this.currentCategory = initialCategory.isEmpty()
            ? getDefaultCategory() : initialCategory;
    }

    private String getDefaultCategory() {
        List<String> cats = ShopRegistry.getInstance().getCategories();
        return cats.isEmpty() ? "general" : cats.get(0);
    }

    // ── 구매 ────────────────────────────────────────────────────────
    // 0=성공(일부 드롭 포함) 1=잔액부족 2=항목없음 4=코인백없음

    public int processBuy(ServerPlayer player, int entryIndex, int quantity) {
        if (ShopConfig.REQUIRE_COINS_BAG.get()
                && !net.sirgrantd.sg_economy.api.SGEconomyApi.get().hasCoinsBag(player)) {
            return 4;
        }

        List<ShopEntry> entries = ShopRegistry.getInstance().getByCategory(currentCategory);
        if (entryIndex < 0 || entryIndex >= entries.size()) return 2;

        ShopEntry entry = entries.get(entryIndex);
        if (!entry.canBuy()) return 2;

        int    qty   = Math.max(1, Math.min(quantity, 576));
        double total = entry.buyPrice().getAsDouble() * qty;

        // 잔액 확인
        if (!CurrencyManager.canAfford(player, total)) return 1;

        // 재화 차감 — 차감 먼저 시도
        if (!CurrencyManager.withdraw(player, total)) return 1;

        // 아이템 지급 — 인벤토리 꽉 차면 바닥에 드롭
        List<ItemStack> toGive = buildStacks(entry.item(), qty);
        for (ItemStack stack : toGive) {
            // inv.add()가 false면 남은 수량이 stack에 남아있음
            if (!player.getInventory().add(stack)) {
                // 인벤토리에 못 넣은 아이템 → 발 위치에 드롭
                dropAtPlayer(player, stack);
            }
        }
        return 0;
    }

    // ── 판매 ────────────────────────────────────────────────────────
    // 0=성공 1=아이템부족 2=항목없음 4=코인백없음

    public int processSell(ServerPlayer player, int entryIndex, int quantity) {
        if (ShopConfig.REQUIRE_COINS_BAG.get()
                && !net.sirgrantd.sg_economy.api.SGEconomyApi.get().hasCoinsBag(player)) {
            return 4;
        }

        List<ShopEntry> entries = ShopRegistry.getInstance().getByCategory(currentCategory);
        if (entryIndex < 0 || entryIndex >= entries.size()) return 2;

        ShopEntry entry = entries.get(entryIndex);
        if (!entry.canSell()) return 2;

        int qty         = Math.max(1, Math.min(quantity, 576));
        int totalNeeded = entry.item().getCount() * qty;

        if (countItem(player, entry.item()) < totalNeeded) return 1;

        removeItems(player, entry.item(), totalNeeded);
        CurrencyManager.deposit(player, entry.sellPrice().getAsDouble() * qty);
        return 0;
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────

    /** 지급할 ItemStack 목록 생성 (최대 스택 단위 분할) */
    private List<ItemStack> buildStacks(ItemStack template, int qty) {
        List<ItemStack> result = new ArrayList<>();
        int maxStack  = template.getMaxStackSize();
        int remaining = template.getCount() * qty;
        while (remaining > 0) {
            int give  = Math.min(remaining, maxStack);
            ItemStack s = template.copy();
            s.setCount(give);
            result.add(s);
            remaining -= give;
        }
        return result;
    }

    /** 아이템을 플레이어 발 위치에 드롭 */
    private void dropAtPlayer(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        Level level = player.level();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        ItemEntity entity = new ItemEntity(level, x, y, z, stack.copy());
        // 줍기 딜레이 없음 — 바로 주울 수 있게
        entity.setPickUpDelay(0);
        // 속도 0으로 드롭 (발 아래에 조용히)
        entity.setDeltaMovement(0, 0.1, 0);
        level.addFreshEntity(entity);
        ShopMod.LOGGER.debug("[ShopMod] 인벤토리 꽉 참 — 아이템 드롭: {} x{}",
            stack.getItem().getDescriptionId(), stack.getCount());
    }

    private int countItem(Player player, ItemStack target) {
        int count = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, target))
                count += s.getCount();
        }
        return count;
    }

    private void removeItems(Player player, ItemStack target, int amount) {
        int rem   = amount;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && rem > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, target)) {
                int take = Math.min(rem, s.getCount());
                s.shrink(take);
                rem -= take;
            }
        }
    }

    // ── Getter / Setter ─────────────────────────────────────────────

    public String  getCurrentCategory()         { return currentCategory; }
    public void    setCurrentCategory(String c) { this.currentCategory = c; }
    public double  getCachedBalance()           { return cachedBalance; }
    public void    setCachedBalance(double v)   { this.cachedBalance = v; }
    public boolean isDecimalSystem()            { return decimalSystem; }
    public void    setDecimalSystem(boolean v)  { this.decimalSystem = v; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) { return true; }
}
