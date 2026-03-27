package com.shopmod.client.screen;

import com.shopmod.menu.ShopMenu;
import com.shopmod.network.ModPackets;
import com.shopmod.shop.ShopEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ShopPopupScreen extends Screen {

    // ── 레이아웃 ────────────────────────────────────────────────────
    private static final int POP_W     = 200;
    private static final int POP_H     = 130;
    private static final int HEADER_H  = 22;
    // 수량 행
    private static final int QTY_ROW_Y = HEADER_H + 56; // 팝업 내부 Y
    private static final int QTY_BOX_W = 46;
    private static final int BTN_W     = 18;
    private static final int BTN_H     = 14;
    // 합계 행 — 수량 행 아래 별도 줄
    private static final int TOTAL_ROW_Y = QTY_ROW_Y + 18;
    // 확인/취소 버튼
    private static final int BTN_ROW_Y   = POP_H - 24;

    private final ShopScreen parent;
    private final ShopEntry  entry;
    private final int        realIdx;
    private final boolean    isBuyMode;
    private int              maxQty = 1;

    private EditBox quantityBox;
    private int popX, popY; // 팝업 좌상단 절대 좌표

    public ShopPopupScreen(ShopScreen parent, ShopEntry entry, int realIdx, boolean isBuyMode) {
        super(Component.translatable(isBuyMode
            ? "shop.shopmod.popup.buy_title"
            : "shop.shopmod.popup.sell_title"));
        this.parent    = parent;
        this.entry     = entry;
        this.realIdx   = realIdx;
        this.isBuyMode = isBuyMode;
    }

    @Override
    protected void init() {
        maxQty = calcMaxQty();
        popX   = (width  - POP_W) / 2;
        popY   = (height - POP_H) / 2;

        // 수량 입력 박스
        int boxX = popX + 42;
        int boxY = popY + QTY_ROW_Y;
        quantityBox = new EditBox(font, boxX, boxY, QTY_BOX_W, BTN_H, Component.literal("1"));
        quantityBox.setValue("1");
        quantityBox.setMaxLength(5);
        quantityBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        quantityBox.setFocused(true);
        addRenderableWidget(quantityBox);

        // 수량 조작 버튼 — 박스 오른쪽에 나란히
        int arrowX = boxX + QTY_BOX_W + 3;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustQty(-1))
            .bounds(arrowX, boxY, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustQty(1))
            .bounds(arrowX + BTN_W + 1, boxY, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("MAX"), b -> setQty(maxQty))
            .bounds(arrowX + (BTN_W + 1) * 2, boxY, 26, BTN_H).build());

        // 확인 / 취소
        addRenderableWidget(Button.builder(
                Component.translatable(isBuyMode
                    ? "shop.shopmod.popup.confirm_buy"
                    : "shop.shopmod.popup.confirm_sell"),
                b -> confirm())
            .bounds(popX + 10, popY + BTN_ROW_Y, 80, 16).build());
        addRenderableWidget(Button.builder(
                Component.translatable("shop.shopmod.popup.cancel"),
                b -> close())
            .bounds(popX + POP_W - 90, popY + BTN_ROW_Y, 80, 16).build());
    }

    // ── 수량 ────────────────────────────────────────────────────────

    private void adjustQty(int delta) { setQty(parseQty() + delta); }

    private void setQty(int qty) {
        int clamped = Math.max(1, Math.min(qty, maxQty > 0 ? maxQty : 9999));
        if (quantityBox != null) quantityBox.setValue(String.valueOf(clamped));
    }

    private int calcMaxQty() {
        ShopMenu menu = getShopMenu();
        if (menu == null) return 9999;
        if (isBuyMode) {
            double price = entry.buyPrice().getAsDouble();
            if (price <= 0) return 9999;
            return Math.max(1, (int)(menu.getCachedBalance() / price));
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return 1;
            int unitCount = Math.max(1, entry.item().getCount());
            int held = 0;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, entry.item()))
                    held += s.getCount();
            }
            return Math.max(1, held / unitCount);
        }
    }

    // ── 거래 ────────────────────────────────────────────────────────

    private void confirm() {
        int qty = parseQty();
        if (isBuyMode) PacketDistributor.sendToServer(new ModPackets.BuyPayload(realIdx, qty));
        else           PacketDistributor.sendToServer(new ModPackets.SellPayload(realIdx, qty));
        close();
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
        parent.onReturnFromPopup();
    }

    // ── 입력 ────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256) { close(); return true; }
        if (key == 257 || key == 335) { confirm(); return true; }
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        adjustQty(dy > 0 ? 1 : -1);
        return true;
    }

    // ── 렌더링 ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // ③ 배경 없음 — 부모 ShopScreen만 표시
        if (parent != null) {
            parent.renderBgPublic(g, pt, -9999, -9999);
            parent.renderContentPublic(g, -9999, -9999);
        }

        int cx = popX, cy = popY;

        // 가벼운 어두운 오버레이만
        g.fill(cx - 4, cy - 4, cx + POP_W + 4, cy + POP_H + 4, 0x88000000);

        // 팝업 패널
        g.fill(cx,     cy,     cx + POP_W,     cy + POP_H,     0xFF373737);
        g.fill(cx + 1, cy + 1, cx + POP_W - 1, cy + POP_H - 1, 0xFFC6C6C6);
        g.fill(cx + 1, cy + HEADER_H,     cx + POP_W - 1, cy + HEADER_H + 1, 0xFF555555);
        g.fill(cx + 1, cy + HEADER_H + 1, cx + POP_W - 1, cy + HEADER_H + 2, 0xFFFFFFFF);

        g.drawString(font, title, cx + 6, cy + 7, 0xFF404040, false);

        // 아이템 아이콘 + 이름
        g.renderItem(entry.item(), cx + POP_W - 22, cy + HEADER_H + 4);
        String name = entry.item().getHoverName().getString();
        if (font.width(name) > POP_W - 34) name = font.plainSubstrByWidth(name, POP_W - 38) + "…";
        g.drawString(font, "§f" + name, cx + 6, cy + HEADER_H + 6, 0xFF404040, true);

        // 단가
        ShopMenu menu = getShopMenu();
        double unitPrice = isBuyMode
            ? entry.buyPrice().getAsDouble()
            : entry.sellPrice().getAsDouble();
        g.drawString(font, "§7단가: §f" + fmtPrice(menu, unitPrice),
            cx + 6, cy + HEADER_H + 20, 0xFF404040, false);

        // 수량 레이블 (박스 왼쪽)
        g.drawString(font, "§7수량:", cx + 6, cy + QTY_ROW_Y + 2, 0xFF404040, false);

        // ④ 합계 — 수량 행 아래 별도 줄 (버튼과 겹치지 않음)
        int qty = parseQty();
        double total = unitPrice * qty;
        String totalStr = isBuyMode
            ? "§c합계: -" + fmtPrice(menu, total)
            : "§a합계: +" + fmtPrice(menu, total);
        g.drawString(font, totalStr, cx + 6, cy + TOTAL_ROW_Y + 2, 0xFFFFFFFF, true);

        // 최대 수량
        g.drawString(font, "§8최대 " + maxQty + "개",
            cx + POP_W - font.width("§8최대 " + maxQty + "개") - 6,
            cy + TOTAL_ROW_Y + 2, 0xFFAAAAAA, false);

        super.render(g, mx, my, pt);
    }

    // renderBackground 완전 차단
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    // ── 유틸 ────────────────────────────────────────────────────────

    private String fmtPrice(ShopMenu menu, double v) {
        return (menu != null && menu.isDecimalSystem())
            ? String.format("$%.2f", v) : String.format("$%d", (long) v);
    }

    private ShopMenu getShopMenu() {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof ShopMenu m) return m;
        return null;
    }

    private int parseQty() {
        if (quantityBox == null || quantityBox.getValue().isEmpty()) return 1;
        try { return Math.max(1, Integer.parseInt(quantityBox.getValue())); }
        catch (NumberFormatException e) { return 1; }
    }

    @Override public boolean isPauseScreen() { return false; }
}
