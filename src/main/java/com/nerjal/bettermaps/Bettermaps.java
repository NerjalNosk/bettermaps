package com.nerjal.bettermaps;

import net.minecraft.world.GameRules;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class Bettermaps {
    public static final String DO_BETTERMAPS = "doBetterMaps";
    public static final String DO_BETTERMAP_FROM_PLAYER_POS = "doBetterMapFromPlayerPos";

    public static final String NBT_IS_BETTER_MAP = "isExplorationMap";
    public static final String NBT_POS_DATA = "pos";
    public static final String NBT_EXPLORATION_DATA = "exploration";
    public static final String NBT_EXPLORATION_ICON = "decoration";
    public static final String NBT_EXPLORATION_DEST = "destination";
    public static final String NBT_EXPLORATION_DIM = "dimension";
    public static final String NBT_EXPLORATION_RADIUS = "searchRadius";
    public static final String NBT_EXPLORATION_SKIP = "skipExistingChunks";
    public static final String NBT_EXPLORATION_ZOOM = "zoom";

    private static final Map<String, GameRules.Key<GameRules.BooleanRule>> rules = new HashMap<>();

    public static void set(@NotNull String key, GameRules.Key<GameRules.BooleanRule> value) {
        if (value == null) {
            throw new NullPointerException();
        }
        Bettermaps.rules.putIfAbsent(key, value);
    }

    public static GameRules.Key<GameRules.BooleanRule> get(@NotNull String key) {
        return Bettermaps.rules.get(key);
    }
}
