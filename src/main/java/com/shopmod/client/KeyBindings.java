package com.shopmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.shopmod.ShopMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {

    public static final String CATEGORY = "key.categories." + ShopMod.MOD_ID;

    /** 상점 열기 — 기본 O */
    public static final KeyMapping OPEN_SHOP = new KeyMapping(
        "key.shopmod.open_shop",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_O,
        CATEGORY
    );

    /** Edit Mode 열기 — 기본 없음 (OP만 사용) */
    public static final KeyMapping OPEN_EDIT_MODE = new KeyMapping(
        "key.shopmod.open_edit_mode",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        InputConstants.UNKNOWN.getValue(),
        CATEGORY
    );
}
