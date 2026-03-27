package com.shopmod.client.screen;

import com.shopmod.menu.ShopMenu;
import com.shopmod.network.ModPackets;
import com.shopmod.shop.ShopEntry;
import com.shopmod.shop.ShopRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class ShopScreen extends AbstractContainerScreen<ShopMenu> {

    // ── 레이아웃 ────────────────────────────────────────────────────
    private static final int GUI_W        = 280;
    private static final int GUI_H        = 220;
    private static final int HEADER_H     = 22;
    private static final int SIDEBAR_X    = 4;
    private static final int SIDEBAR_W    = 58;
    private static final int MODE_BTN_H   = 16;
    private static final int CAT_BTN_H    = 14;
    private static final int CAT_ITEM_H   = CAT_BTN_H + 2;
    private static final int CAT_MAX_ROWS = 6;
    private static final int CAT_AREA_H   = CAT_MAX_ROWS * CAT_ITEM_H;
    private static final int CAT_START_Y  = HEADER_H + MODE_BTN_H + 6 + 14;
    private static final int GRID_X       = SIDEBAR_X + SIDEBAR_W + 6;
    private static final int GRID_Y       = HEADER_H + 4;
    private static final int SLOT_OUTER   = 20;
    private static final int SLOT_INNER   = 18;
    private static final int COLS         = 8;
    private static final int ROWS         = 5;
    private static final int FOOTER_Y     = GUI_H - 18;

    // ── 상태 ────────────────────────────────────────────────────────
    private boolean         isBuyMode  = true;
    private int             itemScroll = 0;
    private int             catScroll  = 0;
    private String          selectedCat = "";
    private List<ShopEntry> filtered   = new ArrayList<>();

    public ShopScreen(ShopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = GUI_W;
        this.imageHeight = GUI_H;
        this.selectedCat = menu.getCurrentCategory();
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = GUI_H + 999;
        this.titleLabelY     = GUI_H + 999;
        rebuildAll();
    }

    // ── UI 빌드 ─────────────────────────────────────────────────────

    private void rebuildAll() {
        clearWidgets();
        filtered = buildFiltered();

        int bx = leftPos, by = topPos;
        int modeY = by + HEADER_H + 2;

        // 모드 버튼
        addRenderableWidget(Button.builder(
                Component.translatable("shop.shopmod.mode.buy"),
                b -> { isBuyMode = true; itemScroll = 0; rebuildAll(); })
            .bounds(bx + SIDEBAR_X, modeY, 28, MODE_BTN_H).build());
        addRenderableWidget(Button.builder(
                Component.translatable("shop.shopmod.mode.sell"),
                b -> { isBuyMode = false; itemScroll = 0; rebuildAll(); })
            .bounds(bx + SIDEBAR_X + 30, modeY, 28, MODE_BTN_H).build());

        // 카테고리 스크롤
        List<String> cats = ShopRegistry.getInstance().getCategories();
        int maxCatScroll  = Math.max(0, cats.size() - CAT_MAX_ROWS);
        int catUpY        = modeY + MODE_BTN_H + 2;

        Button catUp = addRenderableWidget(Button.builder(Component.literal("▲"),
                b -> { if (catScroll > 0) { catScroll--; rebuildAll(); } })
            .bounds(bx + SIDEBAR_X, catUpY, SIDEBAR_W, 12).build());
        catUp.active = catScroll > 0;

        int visEnd = Math.min(catScroll + CAT_MAX_ROWS, cats.size());
        for (int i = catScroll; i < visEnd; i++) {
            String cat = cats.get(i);
            int row = i - catScroll;
            addRenderableWidget(Button.builder(Component.literal(cat), b -> {
                        selectedCat = cat;
                        itemScroll  = 0;
                        PacketDistributor.sendToServer(new ModPackets.ChangeCategoryPayload(cat));
                        rebuildAll();
                    })
                .bounds(bx + SIDEBAR_X, by + CAT_START_Y + row * CAT_ITEM_H, SIDEBAR_W, CAT_BTN_H)
                .build());
        }

        // ▼ 버튼 — CAT 영역 바로 아래 (잔액과 겹치지 않도록 고정)
        int catDownY = by + CAT_START_Y + CAT_AREA_H + 2;
        Button catDown = addRenderableWidget(Button.builder(Component.literal("▼"),
                b -> { if (catScroll < maxCatScroll) { catScroll++; rebuildAll(); } })
            .bounds(bx + SIDEBAR_X, catDownY, SIDEBAR_W, 12).build());
        catDown.active = catScroll < maxCatScroll;

        // 아이템 슬롯 버튼 — 클릭 시 팝업 오픈
        int start = itemScroll * COLS;
        int end   = Math.min(start + COLS * ROWS, filtered.size());
        for (int i = start; i < end; i++) {
            int local = i - start;
            int col   = local % COLS;
            int row   = local / COLS;
            int sx    = bx + GRID_X + col * SLOT_OUTER;
            int sy    = by + GRID_Y + row * SLOT_OUTER;
            final int entryIdx = i; // filtered 내 인덱스
            addRenderableWidget(Button.builder(Component.empty(), b -> openPopup(entryIdx))
                .bounds(sx, sy, SLOT_INNER, SLOT_INNER).build());
        }
    }

    // ── 팝업 오픈 ───────────────────────────────────────────────────

    private void openPopup(int filteredIdx) {
        if (filteredIdx < 0 || filteredIdx >= filtered.size()) return;
        ShopEntry entry = filtered.get(filteredIdx);

        // 서버 검증을 위해 전체 카테고리 내 실제 인덱스를 계산
        List<ShopEntry> allEntries = ShopRegistry.getInstance().getByCategory(selectedCat);
        int realIdx = -1;
        for (int i = 0; i < allEntries.size(); i++) {
            if (allEntries.get(i).id().equals(entry.id())) {
                realIdx = i;
                break;
            }
        }
        if (realIdx < 0) return;

        Minecraft.getInstance().setScreen(
            new ShopPopupScreen(this, entry, realIdx, isBuyMode));
    }

    // ── 렌더링 ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        renderContent(g, mx, my);
        renderSlotTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int bx = leftPos, by = topPos;

        drawPanel(g, bx, by, GUI_W, GUI_H);
        drawDivider(g, bx + 1, by + HEADER_H, GUI_W - 2);
        drawDivider(g, bx + 1, by + FOOTER_Y - 2, GUI_W - 2);
        drawInsetPanel(g, bx + SIDEBAR_X - 1, by + CAT_START_Y - 1, SIDEBAR_W + 2, CAT_AREA_H + 2);
        drawInsetPanel(g, bx + 4, by + FOOTER_Y - 1, GUI_W - 8, 16);

        // 아이템 슬롯
        int start = itemScroll * COLS;
        int end   = Math.min(start + COLS * ROWS, filtered.size());
        for (int i = start; i < end; i++) {
            int local = i - start;
            int sx = bx + GRID_X + (local % COLS) * SLOT_OUTER - 1;
            int sy = by + GRID_Y + (local / COLS) * SLOT_OUTER - 1;
            drawSlot(g, sx, sy, SLOT_INNER + 2, false);
        }
    }

    private void renderContent(GuiGraphics g, int mx, int my) {
        int bx = leftPos, by = topPos;

        g.drawString(font, Component.translatable("container.shopmod.shop"),
            bx + 5, by + 7, 0xFF404040, false);

        String modeStr = isBuyMode ? "§a▶ 구매" : "§c▶ 판매";
        g.drawString(font, modeStr, bx + 150, by + 7, 0xFFFFFFFF, true);

        // 잔액
        ShopMenu menu = getMenu();
        String balStr = menu.isDecimalSystem()
            ? String.format("§6$ %.2f", menu.getCachedBalance())
            : String.format("§6$ %d", (long) menu.getCachedBalance());
        g.drawString(font, balStr, bx + 7, by + FOOTER_Y + 1, 0xFFFFFFFF, true);

        // 선택 카테고리 강조
        List<String> cats = ShopRegistry.getInstance().getCategories();
        int visEnd = Math.min(catScroll + CAT_MAX_ROWS, cats.size());
        for (int i = catScroll; i < visEnd; i++) {
            if (cats.get(i).equals(selectedCat)) {
                int row = i - catScroll;
                int cy  = by + CAT_START_Y + row * CAT_ITEM_H;
                g.fill(bx + SIDEBAR_X, cy, bx + SIDEBAR_X + SIDEBAR_W, cy + CAT_BTN_H, 0x66FFFF00);
                break;
            }
        }

        // 아이템 아이콘
        int start = itemScroll * COLS;
        int end   = Math.min(start + COLS * ROWS, filtered.size());
        for (int i = start; i < end; i++) {
            ShopEntry e = filtered.get(i);
            int local = i - start;
            int sx = bx + GRID_X + (local % COLS) * SLOT_OUTER + 1;
            int sy = by + GRID_Y + (local / COLS) * SLOT_OUTER + 1;
            g.renderItem(e.item(), sx, sy);
            g.renderItemDecorations(font, e.item(), sx, sy);
        }

        // 스크롤 인디케이터
        int maxScroll = getMaxItemScroll();
        if (maxScroll > 0) {
            String ind = (itemScroll + 1) + "/" + (maxScroll + 1);
            g.drawString(font, "§7" + ind,
                bx + GRID_X + COLS * SLOT_OUTER - font.width(ind),
                by + GRID_Y + ROWS * SLOT_OUTER + 1, 0xFFAAAAAA, false);
        }
    }

    private void renderSlotTooltip(GuiGraphics g, int mx, int my) {
        int bx = leftPos, by = topPos;
        ShopMenu menu = getMenu();
        int start = itemScroll * COLS;
        int end   = Math.min(start + COLS * ROWS, filtered.size());

        for (int i = start; i < end; i++) {
            ShopEntry e = filtered.get(i);
            int local = i - start;
            int sx = bx + GRID_X + (local % COLS) * SLOT_OUTER;
            int sy = by + GRID_Y + (local / COLS) * SLOT_OUTER;

            if (mx >= sx && mx < sx + SLOT_INNER && my >= sy && my < sy + SLOT_INNER) {
                List<Component> lines = new ArrayList<>();
                lines.add(e.item().getHoverName());
                if (e.canBuy()) {
                    String p = menu.isDecimalSystem()
                        ? String.format("$%.2f", e.buyPrice().getAsDouble())
                        : String.format("$%d", (long) e.buyPrice().getAsDouble());
                    lines.add(Component.translatable("shop.shopmod.tooltip.buy_price", p)
                        .withStyle(s -> s.withColor(0xFF5555)));
                }
                if (e.canSell()) {
                    String p = menu.isDecimalSystem()
                        ? String.format("$%.2f", e.sellPrice().getAsDouble())
                        : String.format("$%d", (long) e.sellPrice().getAsDouble());
                    lines.add(Component.translatable("shop.shopmod.tooltip.sell_price", p)
                        .withStyle(s -> s.withColor(0x55FF55)));
                }
                g.renderTooltip(font, lines, java.util.Optional.empty(), mx, my);
                break;
            }
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {}

    // ── 스크롤 ──────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int bx = leftPos, by = topPos;
        if (mx >= bx + SIDEBAR_X && mx < bx + SIDEBAR_X + SIDEBAR_W
                && my >= by + CAT_START_Y && my < by + CAT_START_Y + CAT_AREA_H) {
            int max = Math.max(0, ShopRegistry.getInstance().getCategories().size() - CAT_MAX_ROWS);
            catScroll = dy < 0 ? Math.min(catScroll + 1, max) : Math.max(catScroll - 1, 0);
            rebuildAll();
            return true;
        }
        itemScroll = dy < 0
            ? Math.min(itemScroll + 1, getMaxItemScroll())
            : Math.max(itemScroll - 1, 0);
        rebuildAll();
        return true;
    }

    // ── 사운드 ──────────────────────────────────────────────────────

    public void playResultSound(boolean success) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (success) mc.player.playSound(SoundEvents.VILLAGER_YES, 1.0f, 1.0f);
        else         mc.player.playSound(SoundEvents.VILLAGER_NO,  1.0f, 1.0f);
    }

    // ── 드로우 헬퍼 ─────────────────────────────────────────────────

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
    }

    void drawInsetPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF555555);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF888888);
    }

    private void drawDivider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y,     x + w, y + 1, 0xFF555555);
        g.fill(x, y + 1, x + w, y + 2, 0xFFFFFFFF);
    }

    private void drawSlot(GuiGraphics g, int x, int y, int size, boolean selected) {
        g.fill(x, y, x + size, y + size, selected ? 0xFFFFFF00 : 0xFF373737);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF8B8B8B);
        g.fill(x + 1, y + 1, x + size - 2, y + size - 2, 0xFF636363);
    }

    // ── 유틸 ────────────────────────────────────────────────────────

    private List<ShopEntry> buildFiltered() {
        if (selectedCat.isEmpty()) {
            List<String> cats = ShopRegistry.getInstance().getCategories();
            if (!cats.isEmpty()) selectedCat = cats.get(0);
        }
        return ShopRegistry.getInstance().getByCategory(selectedCat)
            .stream().filter(e -> isBuyMode ? e.canBuy() : e.canSell()).toList();
    }

    private int getMaxItemScroll() {
        return Math.max(0, (filtered.size() - 1) / COLS - ROWS + 1);
    }

    // 팝업에서 돌아왔을 때 잔액 갱신 반영을 위해 rebuildAll 노출
    public void onReturnFromPopup() {
        rebuildAll();
    }
    /** 팝업에서 배경 렌더링용으로 직접 호출 */
    public void renderBgPublic(GuiGraphics g, float pt, int mx, int my) {
        renderBg(g, pt, mx, my);
    }

    /** 팝업에서 콘텐츠 렌더링용으로 직접 호출 */
    public void renderContentPublic(GuiGraphics g, int mx, int my) {
        renderContent(g, mx, my);
    }


    // ③ 상점 UI 배경 완전 제거
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // 아무것도 그리지 않음 — 게임 화면 그대로 보임
    }


}
