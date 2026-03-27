package com.shopmod.editmode;

import com.shopmod.network.ModPackets;
import com.shopmod.shop.ShopEntry;
import com.shopmod.shop.ShopRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class EditModeScreen extends Screen {

    private static final int GUI_W    = 340;
    private static final int GUI_H    = 220;
    private static final int LIST_W   = 120;
    private static final int SEP_X    = LIST_W + 10;
    private static final int DETAIL_X = SEP_X + 8;
    private static final int ROW_H    = 13;
    private static final int VISIBLE  = 12;
    private static final int LIST_TOP = 22;

    // ── 필드 설명 ────────────────────────────────────────────────────
    // 각 필드 옆에 툴팁/설명을 렌더링하기 위한 상수
    private static final String[][] FIELD_INFO = {
        { "아이템 ID",   "minecraft:diamond 형태. 손에 들고 [새 항목]을 누르면 자동 입력." },
        { "구매가",      "플레이어가 이 아이템을 살 때 지불하는 금액. 비우면 구매 불가." },
        { "판매가",      "플레이어가 이 아이템을 팔 때 받는 금액. 비우면 판매 불가." },
        { "카테고리",    "상점 사이드바에 표시될 탭 이름. 같은 이름끼리 묶임." },
        { "수량",        "거래 1회당 아이템 개수. 예: 64이면 64개 단위로 구매/판매." },
        { "정렬 순서",   "같은 카테고리 내 표시 순서. 숫자가 낮을수록 앞에 표시." },
    };

    private List<ShopEntry> entries = new ArrayList<>();
    private int  listScroll   = 0;
    private int  selectedIdx  = -1;

    // 편집 필드 캐시 (rebuildScreen 시 값 유지)
    private String fItemId    = "";
    private String fBuyPrice  = "";
    private String fSellPrice = "";
    private String fCategory  = "general";
    private String fCount     = "1";
    private String fSort      = "0";

    private EditBox boxItemId, boxBuyPrice, boxSellPrice, boxCategory, boxCount, boxSort;

    // 마우스가 올라가 있는 필드 인덱스 (툴팁용)
    private int hoveredField = -1;

    private String statusMsg   = "";
    private int    statusColor = 0xFF55FF55;
    private long   statusUntil = 0;

    public EditModeScreen() {
        super(Component.literal("§6[Edit Mode] §f상점 편집"));
    }

    @Override
    protected void init() {
        entries.clear();
        entries.addAll(ShopRegistry.getInstance().getAll());

        int bx = (width  - GUI_W) / 2;
        int by = (height - GUI_H) / 2;

        // ── 목록 ────────────────────────────────────────────────────
        int maxScroll = Math.max(0, entries.size() - VISIBLE);
        addRenderableWidget(Button.builder(Component.literal("▲"),
                b -> { if (listScroll > 0) { listScroll--; rebuildScreen(); } })
            .bounds(bx + 4, by + LIST_TOP, LIST_W, 11).build());

        int listY = by + LIST_TOP + 13;
        int visEnd = Math.min(listScroll + VISIBLE, entries.size());
        for (int i = listScroll; i < visEnd; i++) {
            ShopEntry e = entries.get(i);
            int row = i - listScroll;
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(itemLabel(e)),
                    b -> clickEntry(idx))
                .bounds(bx + 4, listY + row * ROW_H, LIST_W, ROW_H - 1).build());
        }

        addRenderableWidget(Button.builder(Component.literal("▼"),
                b -> { if (listScroll < maxScroll) { listScroll++; rebuildScreen(); } })
            .bounds(bx + 4, listY + VISIBLE * ROW_H + 1, LIST_W, 11).build());

        // ── 편집 필드 ────────────────────────────────────────────────
        // 라벨 너비를 줘서 필드가 오른쪽으로 밀림
        int lw  = 58;  // 라벨 너비
        int dx  = bx + DETAIL_X + lw;
        int fw  = GUI_W - DETAIL_X - lw - 6 - bx + bx;  // 입력칸 너비
        int dy  = by + LIST_TOP;
        int gap = 22;

        fw = GUI_W - (DETAIL_X + lw) - 6;

        boxItemId    = makeBox(dx, dy,           fw, fItemId);
        boxBuyPrice  = makeBox(dx, dy + gap,     fw, fBuyPrice);
        boxSellPrice = makeBox(dx, dy + gap * 2, fw, fSellPrice);
        boxCategory  = makeBox(dx, dy + gap * 3, fw, fCategory);
        boxCount     = makeBox(dx, dy + gap * 4, fw, fCount);
        boxSort      = makeBox(dx, dy + gap * 5, fw, fSort);

        boxBuyPrice .setFilter(s -> s.isEmpty() || s.matches("[0-9]*\\.?[0-9]*"));
        boxSellPrice.setFilter(s -> s.isEmpty() || s.matches("[0-9]*\\.?[0-9]*"));
        boxCount    .setFilter(s -> s.isEmpty() || s.matches("\\d*"));
        boxSort     .setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));

        addRenderableWidget(boxItemId);
        addRenderableWidget(boxBuyPrice);
        addRenderableWidget(boxSellPrice);
        addRenderableWidget(boxCategory);
        addRenderableWidget(boxCount);
        addRenderableWidget(boxSort);

        // ── 액션 버튼 ────────────────────────────────────────────────
        int btnY = by + LIST_TOP + gap * 6 + 4;
        addRenderableWidget(Button.builder(Component.literal("§a저장"),
                b -> saveEntry())
            .bounds(bx + DETAIL_X, btnY, 60, 16).build());

        Button del = addRenderableWidget(Button.builder(Component.literal("§c삭제"),
                b -> deleteEntry())
            .bounds(bx + DETAIL_X + 64, btnY, 60, 16).build());
        del.active = selectedIdx >= 0;

        // ── 하단 ─────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(
                Component.literal("§e+ 새 항목 (손에 든 아이템)"),
                b -> prepareNew())
            .bounds(bx + 4, by + GUI_H - 18, 152, 14).build());

        addRenderableWidget(Button.builder(Component.literal("§7닫기"),
                b -> onClose())
            .bounds(bx + GUI_W - 54, by + GUI_H - 18, 50, 14).build());
    }

    // ── 항목 클릭 ────────────────────────────────────────────────────

    private void clickEntry(int idx) {
        selectedIdx  = idx;
        ShopEntry e  = entries.get(idx);
        fItemId      = BuiltInRegistries.ITEM.getKey(e.item().getItem()).toString();
        fBuyPrice    = e.canBuy()  ? fmt(e.buyPrice().getAsDouble())  : "";
        fSellPrice   = e.canSell() ? fmt(e.sellPrice().getAsDouble()) : "";
        fCategory    = e.category();
        fCount       = String.valueOf(e.item().getCount());
        fSort        = String.valueOf(e.sortOrder());
        rebuildScreen();
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ── 새 항목 ──────────────────────────────────────────────────────

    private void prepareNew() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) {
            setStatus("§c손에 아이템을 들고 버튼을 누르세요.", false);
            return;
        }
        selectedIdx  = -1;
        fItemId      = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        fBuyPrice    = "10";
        fSellPrice   = "5";
        fCategory    = "general";
        fCount       = "1";
        fSort        = "0";
        rebuildScreen();
    }

    // ── 저장 ─────────────────────────────────────────────────────────

    private void saveEntry() {
        syncFields();

        if (fItemId.isEmpty()) { setStatus("§c아이템 ID를 입력하세요.", false); return; }
        String itemId = fItemId.contains(":") ? fItemId : "minecraft:" + fItemId;

        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == null || item == Items.AIR) {
            setStatus("§c존재하지 않는 아이템: " + itemId, false); return;
        }

        double buy = -1, sell = -1;
        try { if (!fBuyPrice.isEmpty())  buy  = Double.parseDouble(fBuyPrice);  } catch (NumberFormatException e) { setStatus("§c구매가 형식 오류.", false); return; }
        try { if (!fSellPrice.isEmpty()) sell = Double.parseDouble(fSellPrice); } catch (NumberFormatException e) { setStatus("§c판매가 형식 오류.", false); return; }
        if (buy <= 0 && sell <= 0) { setStatus("§c구매가 또는 판매가 중 하나는 필요합니다.", false); return; }

        int count = 1, sort = 0;
        try { count = Math.max(1, fCount.isEmpty() ? 1 : Integer.parseInt(fCount)); } catch (NumberFormatException ignored) {}
        try { sort  = fSort.isEmpty() ? 0 : Integer.parseInt(fSort); }              catch (NumberFormatException ignored) {}
        String cat = fCategory.isEmpty() ? "general" : fCategory;

        String entryId = selectedIdx >= 0 ? entries.get(selectedIdx).id().toString() : "";
        PacketDistributor.sendToServer(new ModPackets.EditEntryPayload(
            entryId, itemId, buy, sell, cat, sort, count, false));
        setStatus("§e저장 요청 전송 중...", true);
    }

    // ── 삭제 ─────────────────────────────────────────────────────────

    private void deleteEntry() {
        if (selectedIdx < 0 || selectedIdx >= entries.size()) return;
        ShopEntry e = entries.get(selectedIdx);
        String itemId = BuiltInRegistries.ITEM.getKey(e.item().getItem()).toString();
        PacketDistributor.sendToServer(new ModPackets.EditEntryPayload(
            e.id().toString(), itemId, -1, -1, "", 0, 1, true));
        selectedIdx = -1;
        fItemId = fBuyPrice = fSellPrice = fSort = "";
        fCategory = "general"; fCount = "1";
        setStatus("§e삭제 요청 전송 중...", true);
        rebuildScreen();
    }

    public void onEditResult(boolean success, String msg) {
        setStatus(msg, success);
        rebuildScreen();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────

    private void syncFields() {
        if (boxItemId    != null) fItemId    = boxItemId.getValue().trim();
        if (boxBuyPrice  != null) fBuyPrice  = boxBuyPrice.getValue().trim();
        if (boxSellPrice != null) fSellPrice = boxSellPrice.getValue().trim();
        if (boxCategory  != null) fCategory  = boxCategory.getValue().trim();
        if (boxCount     != null) fCount     = boxCount.getValue().trim();
        if (boxSort      != null) fSort      = boxSort.getValue().trim();
    }

    private void setStatus(String msg, boolean ok) {
        statusMsg   = msg;
        statusColor = ok ? 0xFF55FF55 : 0xFFFF5555;
        statusUntil = System.currentTimeMillis() + 3000;
    }

    protected void rebuildScreen() {
        syncFields();
        clearWidgets();
        init();
    }

    private EditBox makeBox(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y, w, 14, Component.empty());
        box.setValue(value);
        box.setMaxLength(200);
        return box;
    }

    private String itemLabel(ShopEntry e) {
        String path = BuiltInRegistries.ITEM.getKey(e.item().getItem()).getPath();
        String cat  = e.category();
        String lbl  = "[" + cat + "] " + path;
        return lbl.length() > 19 ? lbl.substring(0, 18) + "…" : lbl;
    }

    // ── 렌더 ─────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // ② 배경 흐림 없음 — 직접 단색 배경만
        g.fill(0, 0, width, height, 0xAA000000);

        int bx = (width  - GUI_W) / 2;
        int by = (height - GUI_H) / 2;

        // 패널
        g.fill(bx, by, bx + GUI_W, by + GUI_H, 0xFF373737);
        g.fill(bx + 1, by + 1, bx + GUI_W - 1, by + GUI_H - 1, 0xFFC6C6C6);
        g.fill(bx + 1, by + 19, bx + GUI_W - 1, by + 20, 0xFF555555);
        g.fill(bx + 1, by + 20, bx + GUI_W - 1, by + 21, 0xFFFFFFFF);
        g.fill(bx + SEP_X, by + 19, bx + SEP_X + 1, by + GUI_H - 4, 0xFF888888);

        g.drawString(font, title, bx + 5, by + 6, 0xFF404040, false);

        // ── 필드 라벨 + ① 설명 ──────────────────────────────────────
        int lw  = 58;
        int dx  = bx + DETAIL_X;
        int dy  = by + LIST_TOP;
        int gap = 22;

        hoveredField = -1;
        for (int i = 0; i < FIELD_INFO.length; i++) {
            int labelX = dx - lw;
            int labelY = dy + gap * i + 3;
            String name = FIELD_INFO[i][0];

            // 라벨 영역 hover 감지
            boolean hovered = mx >= labelX && mx < dx && my >= labelY - 2 && my < labelY + 12;
            if (hovered) hoveredField = i;

            // 라벨 (hover 시 밝게)
            int labelColor = hovered ? 0xFFFFFF55 : 0xFF555555;
            g.drawString(font, "§7" + name + ":", labelX, labelY, labelColor, false);

            // hover 시 오른쪽 아래에 설명 툴팁
            if (hovered) {
                String desc = FIELD_INFO[i][1];
                int tw = font.width(desc) + 6;
                int tx = Math.min(mx + 8, width - tw - 4);
                int ty = my + 12;
                g.fill(tx - 3, ty - 2, tx + tw - 3, ty + 10, 0xFF222222);
                g.fill(tx - 2, ty - 1, tx + tw - 4, ty + 9,  0xFF444444);
                g.drawString(font, desc, tx - 2, ty, 0xFFDDDDDD, false);
            }
        }

        // 선택 항목 하이라이트
        if (selectedIdx >= 0) {
            int row = selectedIdx - listScroll;
            if (row >= 0 && row < VISIBLE) {
                int hy = by + LIST_TOP + 13 + row * ROW_H;
                g.fill(bx + 4, hy, bx + 4 + LIST_W, hy + ROW_H - 1, 0x55FFFF00);
            }
        }

        // 선택 상태 표시
        int infoY = by + GUI_H - 32;
        if (selectedIdx >= 0 && selectedIdx < entries.size()) {
            g.drawString(font, "§e편집 중: §f" + itemLabel(entries.get(selectedIdx)),
                bx + 4, infoY, 0xFFFFFFFF, true);
        } else {
            g.drawString(font, "§7새 항목 입력 중 (아직 저장되지 않음)",
                bx + 4, infoY, 0xFFAAAAAA, false);
        }

        g.drawString(font, "§7항목 " + entries.size() + "개", bx + 4, by + GUI_H - 20, 0xFF888888, false);

        // 상태 메시지
        if (!statusMsg.isEmpty() && System.currentTimeMillis() < statusUntil) {
            int sw = font.width(statusMsg);
            g.drawString(font, statusMsg, bx + GUI_W - sw - 6, by + GUI_H - 20, statusColor, true);
        }

        super.render(g, mx, my, pt);
    }

    // renderBackground 완전 차단 — 직접 처리
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int bx = (width - GUI_W) / 2;
        if (mx >= bx + 4 && mx < bx + 4 + LIST_W) {
            int max = Math.max(0, entries.size() - VISIBLE);
            if (dy > 0 && listScroll > 0)        { listScroll--; rebuildScreen(); return true; }
            if (dy < 0 && listScroll < max)       { listScroll++; rebuildScreen(); return true; }
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == 256) { onClose(); return true; }
        return super.keyPressed(key, scan, mod);
    }

    @Override public boolean isPauseScreen() { return false; }
}
