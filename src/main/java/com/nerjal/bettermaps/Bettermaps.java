package com.nerjal.bettermaps;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class Bettermaps {
    public static final String DO_BETTERMAPS = "doBetterMaps";
    public static final String DO_BETTERMAPS_LOOT = "doBetterMapsLoot";
    public static final String DO_BETTERMAPS_TRADE = "doBetterMapsTrades";
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
    public static final Map<String, LocateTask> locateMapTaskThreads = new LinkedHashMap<>();
    public static final AtomicInteger taskCounter = new AtomicInteger();
    public static final Lock mapTaskSafeLock = new ReentrantLock();

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
        private final String id;

        public LocateTask(Runnable task, String id) {
            this.task = task;
            this.id = id;
        }

        @Override
        public void run() {
            this.task.run();
        }

        @Override
        public void interrupt() {
            locateMapTaskThreads.remove(id);
            super.interrupt();
        }
    }

    public static boolean isLootEnabled(World w) {
        return isEnabled(w) && w.getGameRules().getBoolean(Bettermaps.get(Bettermaps.DO_BETTERMAPS_LOOT));
    }

    public static boolean isTradeEnabled(World w) {
        return isEnabled(w) && w.getGameRules().getBoolean(Bettermaps.get(Bettermaps.DO_BETTERMAPS_TRADE));
    }

    public static boolean isEnabled(World w) {
        return w.getGameRules().getBoolean(Bettermaps.get(Bettermaps.DO_BETTERMAPS));
    }

    public static ItemStack createMap(Vec3d origin, World sourceWorld, TagKey<ConfiguredStructureFeature<?, ?>> destination,
                                      Identifier destWorld, byte decoration, byte zoom, int radius,
                                      boolean skipExistingChunks, @Nullable Text displayName) {
        // pos logic
        NbtCompound nbtPos = new NbtCompound();
        nbtPos.putString(Bettermaps.NBT_EXPLORATION_DIM, sourceWorld.getRegistryKey().getValue().toString());
        nbtPos.putDouble("x", origin.getX());
        nbtPos.putDouble("y", origin.getY());
        nbtPos.putDouble("z", origin.getZ());

        // function args logic
        NbtCompound explorationNbt = new NbtCompound();
        explorationNbt.putString(Bettermaps.NBT_EXPLORATION_DEST, destination.id().toString());
        explorationNbt.putByte(Bettermaps.NBT_EXPLORATION_ICON, decoration);
        explorationNbt.putByte(Bettermaps.NBT_EXPLORATION_ZOOM, zoom);
        explorationNbt.putInt(Bettermaps.NBT_EXPLORATION_RADIUS, radius);
        explorationNbt.putBoolean(Bettermaps.NBT_EXPLORATION_SKIP, skipExistingChunks);

        // function result logic
        ItemStack stack = new ItemStack(Items.MAP);
        NbtCompound nbt = new NbtCompound();
        nbt.put(Bettermaps.NBT_POS_DATA, nbtPos);
        nbt.putBoolean(Bettermaps.NBT_IS_BETTER_MAP, true);
        nbt.put(Bettermaps.NBT_EXPLORATION_DATA, explorationNbt);

        NbtCompound display = new NbtCompound();
        NbtList lore = new NbtList();
        lore.add(NbtString.of(String.format("{\"text\": \"[%s]\", \"color\": \"dark_gray\", \"italic\":false}", destWorld)));
        display.put("Lore", lore);
        nbt.put("display", display);
        if (displayName != null) {
            stack.setCustomName(displayName);
        }

        stack.setNbt(nbt);
        stack.setCount(1);
        return stack;
    }
}
