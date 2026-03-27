package com.shopmod.network;

import com.shopmod.client.screen.ShopScreen;
import com.shopmod.currency.CurrencyManager;
import com.shopmod.editmode.EditModeScreen;
import com.shopmod.editmode.EditModeManager;
import com.shopmod.menu.ShopMenu;
import com.shopmod.shop.ShopEntry;
import com.shopmod.shop.ShopRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

public class ModPackets {

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.addListener(ModPackets::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1.0.0");

        // C→S
        reg.playToServer(OpenShopPayload.TYPE,       OpenShopPayload.STREAM_CODEC,       ModPackets::handleOpenShop);
        reg.playToServer(BuyPayload.TYPE,            BuyPayload.STREAM_CODEC,            ModPackets::handleBuy);
        reg.playToServer(SellPayload.TYPE,           SellPayload.STREAM_CODEC,           ModPackets::handleSell);
        reg.playToServer(ChangeCategoryPayload.TYPE, ChangeCategoryPayload.STREAM_CODEC, ModPackets::handleChangeCategory);
        reg.playToServer(OpenEditModePayload.TYPE,   OpenEditModePayload.STREAM_CODEC,   ModPackets::handleOpenEditMode);
        reg.playToServer(EditEntryPayload.TYPE,      EditEntryPayload.STREAM_CODEC,      ModPackets::handleEditEntry);

        // S→C
        reg.playToClient(SyncBalancePayload.TYPE,    SyncBalancePayload.STREAM_CODEC,    ModPackets::handleSyncBalance);
        reg.playToClient(SoundResultPayload.TYPE,    SoundResultPayload.STREAM_CODEC,    ModPackets::handleSoundResult);
        reg.playToClient(OpenEditScreenPayload.TYPE, OpenEditScreenPayload.STREAM_CODEC, ModPackets::handleOpenEditScreen);
        reg.playToClient(EditResultPayload.TYPE,     EditResultPayload.STREAM_CODEC,     ModPackets::handleEditResult);
    }

    // ── 페이로드 정의 ────────────────────────────────────────────────

    public record OpenShopPayload(String category) implements CustomPacketPayload {
        public static final Type<OpenShopPayload> TYPE = new Type<>(rl("open_shop"));
        public static final StreamCodec<FriendlyByteBuf, OpenShopPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, OpenShopPayload::category, OpenShopPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BuyPayload(int entryIndex, int quantity) implements CustomPacketPayload {
        public static final Type<BuyPayload> TYPE = new Type<>(rl("buy"));
        public static final StreamCodec<FriendlyByteBuf, BuyPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, BuyPayload::entryIndex,
                ByteBufCodecs.INT, BuyPayload::quantity, BuyPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SellPayload(int entryIndex, int quantity) implements CustomPacketPayload {
        public static final Type<SellPayload> TYPE = new Type<>(rl("sell"));
        public static final StreamCodec<FriendlyByteBuf, SellPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, SellPayload::entryIndex,
                ByteBufCodecs.INT, SellPayload::quantity, SellPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ChangeCategoryPayload(String category) implements CustomPacketPayload {
        public static final Type<ChangeCategoryPayload> TYPE = new Type<>(rl("change_category"));
        public static final StreamCodec<FriendlyByteBuf, ChangeCategoryPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ChangeCategoryPayload::category, ChangeCategoryPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenEditModePayload() implements CustomPacketPayload {
        public static final Type<OpenEditModePayload> TYPE = new Type<>(rl("open_edit_mode"));
        public static final StreamCodec<FriendlyByteBuf, OpenEditModePayload> STREAM_CODEC =
            StreamCodec.unit(new OpenEditModePayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * 항목 편집/삭제 요청.
     * itemId: "minecraft:diamond" 형태의 실제 아이템 ID
     * entryId: ShopRegistry 내 기존 항목 ID (수정 시 사용, 새 항목이면 빈 문자열)
     * StreamCodec.of 사용 — composite 최대 6필드 제한 우회.
     */
    public record EditEntryPayload(
        String entryId, String itemId, double buyPrice, double sellPrice,
        String category, int sortOrder, int count, boolean delete
    ) implements CustomPacketPayload {
        public static final Type<EditEntryPayload> TYPE = new Type<>(rl("edit_entry"));
        public static final StreamCodec<FriendlyByteBuf, EditEntryPayload> STREAM_CODEC =
            StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.entryId());
                    buf.writeUtf(p.itemId());
                    buf.writeDouble(p.buyPrice());
                    buf.writeDouble(p.sellPrice());
                    buf.writeUtf(p.category());
                    buf.writeInt(p.sortOrder());
                    buf.writeInt(p.count());
                    buf.writeBoolean(p.delete());
                },
                buf -> new EditEntryPayload(
                    buf.readUtf(),   // entryId
                    buf.readUtf(),   // itemId
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean()
                )
            );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SyncBalancePayload(double balance, boolean decimal) implements CustomPacketPayload {
        public static final Type<SyncBalancePayload> TYPE = new Type<>(rl("sync_balance"));
        public static final StreamCodec<FriendlyByteBuf, SyncBalancePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.DOUBLE, SyncBalancePayload::balance,
                ByteBufCodecs.BOOL, SyncBalancePayload::decimal, SyncBalancePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SoundResultPayload(boolean success) implements CustomPacketPayload {
        public static final Type<SoundResultPayload> TYPE = new Type<>(rl("sound_result"));
        public static final StreamCodec<FriendlyByteBuf, SoundResultPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, SoundResultPayload::success, SoundResultPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 서버가 OP 검증 후 클라이언트에 EditMode 화면 열기 허가 */
    public record OpenEditScreenPayload() implements CustomPacketPayload {
        public static final Type<OpenEditScreenPayload> TYPE = new Type<>(rl("open_edit_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenEditScreenPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenEditScreenPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 편집 결과 피드백 */
    public record EditResultPayload(boolean success, String message) implements CustomPacketPayload {
        public static final Type<EditResultPayload> TYPE = new Type<>(rl("edit_result"));
        public static final StreamCodec<FriendlyByteBuf, EditResultPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, EditResultPayload::success,
                ByteBufCodecs.STRING_UTF8, EditResultPayload::message, EditResultPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── 핸들러 ──────────────────────────────────────────────────────

    private static void handleOpenShop(OpenShopPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (ShopRegistry.getInstance().isEmpty()) {
                player.displayClientMessage(Component.translatable("shop.shopmod.empty"), false);
                return;
            }
            player.openMenu(new com.shopmod.menu.ShopMenuProvider(payload.category()),
                buf -> buf.writeUtf(payload.category()));
            sendBalanceSync(player);
        });
    }

    private static void handleBuy(BuyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (!(player.containerMenu instanceof ShopMenu shopMenu)) return;

            String cat = shopMenu.getCurrentCategory();
            List<ShopEntry> entries = ShopRegistry.getInstance().getByCategory(cat);
            int idx = payload.entryIndex();
            if (idx < 0 || idx >= entries.size() || !entries.get(idx).canBuy()) {
                PacketDistributor.sendToPlayer(player, new SoundResultPayload(false));
                return;
            }

            int qty    = Math.max(1, Math.min(payload.quantity(), 576));
            int result = shopMenu.processBuy(player, idx, qty);
            sendFeedback(player, result, true);
            PacketDistributor.sendToPlayer(player, new SoundResultPayload(result == 0));
            sendBalanceSync(player);
        });
    }

    private static void handleSell(SellPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (!(player.containerMenu instanceof ShopMenu shopMenu)) return;

            String cat = shopMenu.getCurrentCategory();
            List<ShopEntry> entries = ShopRegistry.getInstance().getByCategory(cat);
            int idx = payload.entryIndex();
            if (idx < 0 || idx >= entries.size() || !entries.get(idx).canSell()) {
                PacketDistributor.sendToPlayer(player, new SoundResultPayload(false));
                return;
            }

            int qty    = Math.max(1, Math.min(payload.quantity(), 576));
            int result = shopMenu.processSell(player, idx, qty);
            sendFeedback(player, result, false);
            PacketDistributor.sendToPlayer(player, new SoundResultPayload(result == 0));
            sendBalanceSync(player);
        });
    }

    private static void handleChangeCategory(ChangeCategoryPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (!(player.containerMenu instanceof ShopMenu sm)) return;
            sm.setCurrentCategory(payload.category());
        });
    }

    private static void handleOpenEditMode(OpenEditModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            // OP 레벨 2 이상만 허용
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                    Component.literal("§c권한이 없습니다."), true);
                return;
            }
            PacketDistributor.sendToPlayer(player, new OpenEditScreenPayload());
        });
    }

    private static void handleEditEntry(EditEntryPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (!player.hasPermissions(2)) {
                PacketDistributor.sendToPlayer(player, new EditResultPayload(false, "권한 없음"));
                return;
            }
            // ShopRegistry 직접 갱신 (즉각 반영) + JSON 파일 저장
            boolean ok = EditModeManager.applyEdit(player.server, payload);
            String msg = ok
                ? (payload.delete() ? "§a삭제 완료." : "§a저장 완료.")
                : "§c저장 실패.";
            PacketDistributor.sendToPlayer(player, new EditResultPayload(ok, msg));
        });
    }

    private static void handleSyncBalance(SyncBalancePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof ShopMenu sm) {
                sm.setCachedBalance(payload.balance());
                sm.setDecimalSystem(payload.decimal());
            }
        });
    }

    private static void handleSoundResult(SoundResultPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ShopScreen ss) ss.playResultSound(payload.success());
        });
    }

    private static void handleOpenEditScreen(OpenEditScreenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(new EditModeScreen()));
    }

    private static void handleEditResult(EditResultPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            // EditModeScreen이 열려있으면 onEditResult 호출 → 목록 즉시 갱신
            if (mc.screen instanceof com.shopmod.editmode.EditModeScreen editScreen) {
                editScreen.onEditResult(payload.success(), payload.message());
            } else if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal("[ShopMod] " + payload.message()), false);
            }
        });
    }

    // ── 유틸 ────────────────────────────────────────────────────────

    public static void sendBalanceSync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncBalancePayload(
            CurrencyManager.getBalance(player), CurrencyManager.isDecimalSystem()));
    }

    private static void sendFeedback(ServerPlayer player, int result, boolean isBuy) {
        String key = switch (result) {
            case 0  -> isBuy ? "shop.shopmod.buy.success"            : "shop.shopmod.sell.success";
            case 1  -> isBuy ? "shop.shopmod.buy.insufficient_funds" : "shop.shopmod.sell.no_item";
            case 2  -> "shop.shopmod.invalid_entry";
            case 4  -> "shop.shopmod.no_coins_bag";
            default -> "shop.shopmod.error";
        };
        player.displayClientMessage(Component.translatable(key), true);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("shopmod", path);
    }
}
