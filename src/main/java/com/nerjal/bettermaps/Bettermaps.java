package com.nerjal.bettermaps;

import mc.recraftors.unruled_api.UnruledApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules.*;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static net.minecraft.component.DataComponentTypes.CUSTOM_DATA;
import static net.minecraft.component.DataComponentTypes.LORE;

public final class Bettermaps {
    public static final String MOD_ID = "bettermaps";

    private static final String DO_BETTERMAPS_KEY = "doBetterMaps";
    private static final String DO_BETTERMAPS_LOOT_KEY = "doBetterMapsLoot";
    private static final String DO_BETTERMAPS_TRADE_KEY = "doBetterMapsTrades";
    private static final String DO_BETTERMAP_FROM_PLAYER_POS_KEY = "doBetterMapFromPlayerPos";
    private static final String DO_BETTERMAP_DYNAMIC_LOCATING_KEY = "doBetterMapsDynamicLocating";

    public static final Key<BooleanRule> DO_BETTERMAPS = UnruledApi.registerBoolean(DO_BETTERMAPS_KEY, Category.PLAYER, true);
    public static final Key<BooleanRule> DO_BETTERMAPS_LOOT = UnruledApi.registerBoolean(DO_BETTERMAPS_LOOT_KEY, Category.DROPS, true);
    public static final Key<BooleanRule> DO_BETTERMAPS_TRADE = UnruledApi.registerBoolean(DO_BETTERMAPS_TRADE_KEY, Category.MOBS, true);
    public static final Key<BooleanRule> DO_BETTERMAP_FROM_PLAYER_POS = UnruledApi.registerBoolean(DO_BETTERMAP_FROM_PLAYER_POS_KEY, Category.PLAYER, false);
    public static final Key<BooleanRule> DO_BETTERMAP_DYNAMIC_LOCATING = UnruledApi.registerBoolean(DO_BETTERMAP_DYNAMIC_LOCATING_KEY, Category.PLAYER, true);

    public static final String NBT_POS_DATA = "pos";
    public static final String NBT_EXPLORATION_DATA = "exploration";
    public static final String NBT_EXPLORATION_ICON = "decoration";
    public static final String NBT_EXPLORATION_DEST = "destination";
    public static final String NBT_EXPLORATION_DIM = "dimension";
    public static final String NBT_EXPLORATION_RADIUS = "searchRadius";
    public static final String NBT_EXPLORATION_SKIP = "skipExistingChunks";
    public static final String NBT_EXPLORATION_ZOOM = "zoom";
    public static final String NBT_MAP_LOCK = "_lock";

    public static final Identifier NULL_ID = Identifier.of(Identifier.DEFAULT_NAMESPACE, "_null");

    public static final Map<String, LocateTask> locateMapTaskThreads = new LinkedHashMap<>();
    public static final AtomicInteger taskCounter = new AtomicInteger();
    public static final Lock mapTaskSafeLock = new ReentrantLock();

    private static volatile boolean clientPaused = false;

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
        return isEnabled(w) && w.getGameRules().getBoolean(DO_BETTERMAPS_LOOT);
    }

    public static boolean isTradeEnabled(World w) {
        return isEnabled(w) && w.getGameRules().getBoolean(Bettermaps.DO_BETTERMAPS_TRADE);
    }

    public static boolean isEnabled(World w) {
        return w.getGameRules().getBoolean(DO_BETTERMAPS);
    }

    public static ItemStack createMap(Vec3d origin, World sourceWorld, TagKey<Structure> destination,
                                      Identifier destWorld, RegistryEntry<MapDecorationType> decoration, byte zoom, int radius,
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
        explorationNbt.putString(Bettermaps.NBT_EXPLORATION_ICON, decoration.getKey().get().getValue().toString());
        explorationNbt.putByte(Bettermaps.NBT_EXPLORATION_ZOOM, zoom);
        explorationNbt.putInt(Bettermaps.NBT_EXPLORATION_RADIUS, radius);
        explorationNbt.putBoolean(Bettermaps.NBT_EXPLORATION_SKIP, skipExistingChunks);

        // function result logic
        ItemStack stack = new ItemStack(Items.MAP);
        NbtCompound nbt = new NbtCompound();
        nbt.put(Bettermaps.NBT_POS_DATA, nbtPos);
        nbt.put(Bettermaps.NBT_EXPLORATION_DATA, explorationNbt);

        MutableText lore = Text.literal(String.format("[%s]", destWorld)).formatted(Formatting.DARK_GRAY);
        if (displayName != null) {
            stack.set(DataComponentTypes.CUSTOM_NAME, displayName);
        }

        storeMapData(nbt, stack, lore);
        stack.setCount(1);
        return stack;
    }

    public static void storeMapData(NbtCompound nbt, ItemStack stack, Text lore) {
        NbtComponent component = stack.getComponents().getOrDefault(CUSTOM_DATA, NbtComponent.DEFAULT);
        component = component.apply(c -> c.put(MOD_ID, nbt));
        stack.set(CUSTOM_DATA, component);
        if (lore != null) {
            LoreComponent c = LoreComponent.DEFAULT.with(lore);
            stack.set(LORE, c);
        }
    }

    public static void lockMap(ItemStack stack, String id) {
        NbtComponent component = stack.getComponents().getOrDefault(CUSTOM_DATA, NbtComponent.DEFAULT);
        component.apply(c -> {
            if (!c.contains(MOD_ID)) return;
            NbtElement element = c.get(MOD_ID);
            if (!(element instanceof NbtCompound compound)) return;
            compound.put(NBT_MAP_LOCK, NbtString.of(id));
        });
    }
}
