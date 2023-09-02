package com.nerjal.bettermaps;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class Bettermaps {
    public static final String DO_BETTERMAPS = "doBetterMaps";
    public static final String DO_BETTERMAP_FROM_PLAYER_POS = "doBetterMapFromPlayerPos";
    public static final String DO_BETTERMAP_DYNAMIC_LOCATING = "doBetterMapsDynamicLocating";

    public static final String NBT_IS_BETTER_MAP = "isExplorationMap";
    public static final String NBT_POS_DATA = "pos";
    public static final String NBT_EXPLORATION_DATA = "exploration";
    public static final String NBT_EXPLORATION_ICON = "decoration";
    public static final String NBT_EXPLORATION_DEST = "destination";
    public static final String NBT_EXPLORATION_DIM = "dimension";
    public static final String NBT_EXPLORATION_RADIUS = "searchRadius";
    public static final String NBT_EXPLORATION_SKIP = "skipExistingChunks";
    public static final String NBT_EXPLORATION_ZOOM = "zoom";
    public static final String NBT_MAP_LOCK = "_lock";

    private static final Map<String, GameRules.Key<GameRules.BooleanRule>> rules = new HashMap<>();
    public static final Collection<LocateTask> locateMapTaskThreads = new LinkedHashSet<>();
    public static final Lock mapTaskSafeLock = new ReentrantLock();

    @Environment(EnvType.CLIENT)
    private static volatile boolean clientPaused = false;

    public static void set(@NotNull String key, GameRules.Key<GameRules.BooleanRule> value) {
        if (value == null) {
            throw new NullPointerException();
        }
        Bettermaps.rules.putIfAbsent(key, value);
    }

    public static GameRules.Key<GameRules.BooleanRule> get(@NotNull String key) {
        return Bettermaps.rules.get(key);
    }

    @Environment(EnvType.CLIENT)
    public static void setClientPaused(boolean b) {
        Bettermaps.clientPaused = b;
    }

    @Environment(EnvType.CLIENT)
    public static boolean isClientPaused() {
        return clientPaused;
    }

    public static class LocateTask extends Thread {
        public final Runnable task;

        public LocateTask(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            this.task.run();
        }
    }
}
