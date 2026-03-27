package com.shopmod.currency;

import com.shopmod.ShopMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.sirgrantd.sg_economy.api.EconomyEventProvider;
import net.sirgrantd.sg_economy.api.SGEconomyApi;

/**
 * sg_economy API를 래핑하는 화폐 관리자.
 * 모든 거래는 SGEconomyApi.get()을 통해 처리.
 */
public class CurrencyManager {

    private static EconomyEventProvider api() {
        return SGEconomyApi.get();
    }

    /** 잔액 조회 */
    public static double getBalance(Player player) {
        try {
            return api().getBalance(player);
        } catch (Exception e) {
            ShopMod.LOGGER.error("[ShopMod] getBalance 실패: {}", e.getMessage());
            return 0.0;
        }
    }

    /** 잔액 충분 여부 확인 */
    public static boolean canAfford(Player player, double amount) {
        try {
            return api().hasBalance(player, amount);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 출금 (잔액 부족 시 false)
     */
    public static boolean withdraw(Player player, double amount) {
        try {
            return api().withdrawBalance(player, amount);
        } catch (Exception e) {
            ShopMod.LOGGER.error("[ShopMod] withdraw 실패: {}", e.getMessage());
            return false;
        }
    }

    /** 입금 */
    public static void deposit(Player player, double amount) {
        try {
            api().depositBalance(player, amount);
        } catch (Exception e) {
            ShopMod.LOGGER.error("[ShopMod] deposit 실패: {}", e.getMessage());
        }
    }

    /**
     * 소수점 사용 여부 (sg_economy 설정에 따름)
     * false → 정수로 반올림 표시
     */
    public static boolean isDecimalSystem() {
        try {
            return api().isDecimalSystem();
        } catch (Exception e) {
            return false;
        }
    }

    /** UI 표시용 잔액 문자열 */
    public static String formatBalance(double balance) {
        if (isDecimalSystem()) {
            return String.format("%.2f", balance);
        } else {
            return String.valueOf((long) balance);
        }
    }

    /** 가격 문자열 포맷 */
    public static String formatPrice(double price) {
        return formatBalance(price);
    }
}
