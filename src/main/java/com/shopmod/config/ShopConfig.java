package com.shopmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * ShopMod 서버 설정.
 * 화폐 관련 설정은 sg_economy 모드에서 담당하므로 제거됨.
 */
public class ShopConfig {

    public static final ModConfigSpec SERVER_SPEC;

    /** 상점 명령어 권한 레벨 (0=모든 플레이어, 2=OP) */
    public static final ModConfigSpec.IntValue SHOP_COMMAND_PERMISSION;

    /** 코인백 없어도 상점 이용 가능 여부 */
    public static final ModConfigSpec.BooleanValue REQUIRE_COINS_BAG;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("=== ShopMod 서버 설정 ===").push("shop");

        SHOP_COMMAND_PERMISSION = builder
            .comment("상점 명령어 권한 레벨 (0=모든 플레이어, 2=OP 이상)")
            .defineInRange("shop_command_permission", 0, 0, 4);

        REQUIRE_COINS_BAG = builder
            .comment(
                "true: sg_economy 코인백 보유 시에만 상점 이용 가능",
                "false: 코인백 없어도 상점 이용 가능"
            )
            .define("require_coins_bag", false);

        builder.pop();
        SERVER_SPEC = builder.build();
    }
}
