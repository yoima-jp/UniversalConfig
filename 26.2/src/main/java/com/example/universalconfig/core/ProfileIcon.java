package com.example.universalconfig.core;

import java.util.Set;

/**
 * Profile icons are persisted as stable IDs rather than resource paths so profiles remain portable
 * across loaders and future texture-pack changes. Rendering stays client-side.
 */
public final class ProfileIcon {
    public static final String GRASS_BLOCK = "grass_block";
    public static final String CRAFTING_TABLE = "crafting_table";
    public static final String BOOKSHELF = "bookshelf";
    public static final String COBBLESTONE = "cobblestone";
    public static final String TNT = "tnt";
    public static final String CHEST = "chest";
    public static final String FURNACE = "furnace";
    public static final String DIAMOND_BLOCK = "diamond_block";

    private static final Set<String> KNOWN_IDS = Set.of(
            GRASS_BLOCK, CRAFTING_TABLE, BOOKSHELF, COBBLESTONE,
            TNT, CHEST, FURNACE, DIAMOND_BLOCK);

    private ProfileIcon() {
    }

    public static String normalize(String iconId) {
        if (iconId != null && KNOWN_IDS.contains(iconId)) {
            return iconId;
        }
        // 初期版の抽象アイコンIDも読み込めるよう、意味の近いブロックへ移行する。
        return switch (iconId == null ? "" : iconId) {
            case "laptop" -> CRAFTING_TABLE;
            // Keep accepting persisted IDs and migrate them to a block available across supported clients.
            case "camera", "amethyst_block" -> COBBLESTONE;
            case "book" -> BOOKSHELF;
            default -> GRASS_BLOCK;
        };
    }
}
