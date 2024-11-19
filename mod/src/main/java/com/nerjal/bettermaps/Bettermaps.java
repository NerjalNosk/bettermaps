package com.nerjal.bettermaps;

import mc.recraftors.unruled_api.UnruledApi;
import mc.recraftors.unruled_api.impl.BoundedIntRuleValidatorAdapter;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules.*;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static net.minecraft.component.DataComponentTypes.CUSTOM_DATA;
import static net.minecraft.component.DataComponentTypes.LORE;

public final class Bettermaps {
    public static final Map<String, LocateTask> locateMapTaskThreads = new LinkedHashMap<>();
    public static final AtomicInteger taskCounter = new AtomicInteger();
    public static final Lock mapTaskSafeLock = new ReentrantLock();
    public static final Lock mapTaskStepLock = new ReentrantLock(true);
    private static int permits = 16;
    private static final AtomicInteger availablePermits = new AtomicInteger(permits);
    private static Semaphore mapTaskMaxSemaphore = new Semaphore(permits, true);

    public static final String MOD_ID = "bettermaps";

    private static final String DO_BETTERMAPS_KEY = "doBetterMaps";
    private static final String DO_BETTERMAPS_LOOT_KEY = "doBetterMapsLoot";
    private static final String DO_BETTERMAPS_TRADE_KEY = "doBetterMapsTrades";
    private static final String DO_BETTERMAP_FROM_PLAYER_POS_KEY = "doBetterMapFromPlayerPos";
    private static final String DO_BETTERMAP_DYNAMIC_LOCATING_KEY = "doBetterMapsDynamicLocating";
    private static final String DO_BETTERMAPS_FEEDBACK_KEY = "doBetterMapsFeedback";
    private static final String BETTERMAPS_MAX_TASKS_KEY = "betterMapsMaxTasks";
    private static final String BETTERMAPS_QUEUE_TASKS_KEY = "doBetterMapsTaskQueue";

    public static final Key<BooleanRule> DO_BETTERMAPS = UnruledApi.registerBoolean(DO_BETTERMAPS_KEY, Category.PLAYER, true);
    public static final Key<BooleanRule> DO_BETTERMAPS_LOOT = UnruledApi.registerBoolean(DO_BETTERMAPS_LOOT_KEY, Category.DROPS, true);
    public static final Key<BooleanRule> DO_BETTERMAPS_TRADE = UnruledApi.registerBoolean(DO_BETTERMAPS_TRADE_KEY, Category.MOBS, true);
    public static final Key<BooleanRule> DO_BETTERMAP_FROM_PLAYER_POS = UnruledApi.registerBoolean(DO_BETTERMAP_FROM_PLAYER_POS_KEY, Category.PLAYER, false);
    public static final Key<BooleanRule> DO_BETTERMAP_DYNAMIC_LOCATING = UnruledApi.registerBoolean(DO_BETTERMAP_DYNAMIC_LOCATING_KEY, Category.PLAYER, true);
    public static final Key<BooleanRule> DO_BETTERMAPS_FEEDBACK = UnruledApi.registerBoolean(DO_BETTERMAPS_FEEDBACK_KEY, Category.CHAT, false);
    @SuppressWarnings("unused")
    public static final Key<IntRule> BETTERMAPS_MAX_TASKS = UnruledApi.registerInt(BETTERMAPS_MAX_TASKS_KEY, Category.MISC, 8, (s, i) -> {
        mapTaskStepLock.lock();
        int j = i.get();
        availablePermits.set(j);
        Semaphore sem = new Semaphore(i.get());
        for (Map.Entry<String, LocateTask> e : locateMapTaskThreads.entrySet()) {
            if (!e.getValue().isQueued() && availablePermits.get() > 0 && !sem.tryAcquire()) {
                break;
            }
            availablePermits.decrementAndGet();
        }
        permits = i.get();
        mapTaskMaxSemaphore = sem;
        mapTaskStepLock.unlock();
    }, new BoundedIntRuleValidatorAdapter(1, 64));
    public static final Key<BooleanRule> BETTERMAPS_QUEUE_TASKS = UnruledApi.registerBoolean(BETTERMAPS_QUEUE_TASKS_KEY, Category.MISC, true);

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
        private final ServerWorld world;
        public final Runnable task;
        private final String id;
        private boolean queued = true;

        public LocateTask(ServerWorld w, Runnable task, String id) {
            this.world = w;
            this.task = task;
            this.id = id;
        }

        @Override
        public void run() {
            mapTaskStepLock.lock();
            int available = availablePermits.get();
            mapTaskStepLock.unlock();
            if (world.getGameRules().getBoolean(BETTERMAPS_QUEUE_TASKS) && available == 0) {
                queue();
            }
            this.queued = false;
            this.task.run();
            mapTaskMaxSemaphore.release();
        }

        private void queue() {
            boolean b = true;
            while (b) {
                Bettermaps.mapTaskStepLock.lock();
                b = mapTaskMaxSemaphore.tryAcquire();
                Bettermaps.mapTaskStepLock.unlock();
                if (!b) {
                    try {
                        Thread.currentThread().wait(500);
                    } catch (InterruptedException e) {
                        // I honestly don't know why wouldn't it just wait
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        @Override
        public void interrupt() {
            mapTaskSafeLock.lock();
            locateMapTaskThreads.remove(id);
            mapTaskSafeLock.unlock();
            mapTaskMaxSemaphore.release();
            super.interrupt();
        }

        public String getTaskId() {
            return this.id;
        }

        public boolean isQueued() {
            return queued;
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
        //noinspection OptionalGetWithoutIsPresent
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
