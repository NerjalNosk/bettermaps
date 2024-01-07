package com.nerjal.bettermaps.mixins;

import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EmptyMapItem;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapIcon;
import net.minecraft.item.map.MapState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.tag.TagKey;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EmptyMapItem.class)
public abstract class MapItemMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void explorationMapUseInjector(
            @NotNull World w,
            PlayerEntity user,
            Hand hand,
            CallbackInfoReturnable<TypedActionResult<ItemStack>> cir
    ) {
        // basic checks
        if (w.isClient()) return;
        ServerWorld world = (ServerWorld) w;
        ItemStack stack = user.getStackInHand(hand);
        if (stack.isEmpty()) return;
        NbtCompound sourceNbt = stack.getNbt();
        if (sourceNbt == null) return;
        NbtCompound nbt = sourceNbt.copy();
        if (! nbt.contains(Bettermaps.NBT_IS_BETTER_MAP, NbtElement.BYTE_TYPE)) return;
        if (! nbt.getBoolean(Bettermaps.NBT_IS_BETTER_MAP)) return;
        if (! nbt.contains(Bettermaps.NBT_EXPLORATION_DATA, NbtElement.COMPOUND_TYPE)) return;

        if (nbt.contains(Bettermaps.NBT_MAP_LOCK)) {
            String s = nbt.getString(Bettermaps.NBT_MAP_LOCK);
            if (Bettermaps.locateMapTaskThreads.containsKey(s)) {
                user.sendMessage(new LiteralText("x").formatted(Formatting.GOLD, Formatting.BOLD), true);
                cir.setReturnValue(TypedActionResult.consume(stack));
                cir.cancel();
                return;
            }
        }

        NbtCompound explorationNbt = nbt.getCompound(Bettermaps.NBT_EXPLORATION_DATA);

        // locate
        int radius = explorationNbt.getInt(Bettermaps.NBT_EXPLORATION_RADIUS);
        boolean skip = explorationNbt.getBoolean(Bettermaps.NBT_EXPLORATION_SKIP);
        Vec3d pos = user.getPos();

        Identifier destId = Identifier.tryParse(explorationNbt.getString(Bettermaps.NBT_EXPLORATION_DEST));
        TagKey<ConfiguredStructureFeature<?, ?>> destination = TagKey.of(Registry.CONFIGURED_STRUCTURE_FEATURE_KEY, destId);
        NbtCompound posNbt = nbt.getCompound(Bettermaps.NBT_POS_DATA);
        if (world.getGameRules().getBoolean(Bettermaps.get(Bettermaps.DO_BETTERMAP_FROM_PLAYER_POS))) {
            pos = new Vec3d(posNbt.getDouble("x"), posNbt.getDouble("y"), posNbt.getDouble("z"));
        }
        if (!world.getRegistryKey().getValue().toString().equals(posNbt.getString(Bettermaps.NBT_EXPLORATION_DIM))) {
            user.sendMessage(new LiteralText("x").formatted(Formatting.RED, Formatting.BOLD), true);
            cir.setReturnValue(TypedActionResult.consume(stack));
            cir.cancel();
            return;
        }

        // locate task setup
        Vec3d fPos = pos;
        String id = String.format("%d-%s", Bettermaps.taskCounter.incrementAndGet(), user.getDisplayName().getString());
        Runnable task = ()->locationTask(world, stack, nbt, explorationNbt, radius, skip, fPos, destination, user, id);
        if (! user.isCreative()) sourceNbt.putString(Bettermaps.NBT_MAP_LOCK, id);

        // task run
        if (world.getGameRules().getBoolean(Bettermaps.get(Bettermaps.DO_BETTERMAP_DYNAMIC_LOCATING))) {
            Bettermaps.LocateTask locateTask = new Bettermaps.LocateTask(task, id);
            Bettermaps.locateMapTaskThreads.putIfAbsent(id, locateTask);
            locateTask.start();
            user.sendMessage(new LiteralText("\u2714").formatted(Formatting.GREEN, Formatting.BOLD), true);
        } else {
            task.run();
        }

        cir.setReturnValue(TypedActionResult.consume(stack));
        cir.cancel();
    }

    @Unique
    private static void locationTask(@NotNull ServerWorld world, @NotNull ItemStack stack, @NotNull NbtCompound nbt,
                                     @NotNull NbtCompound explorationNbt, int radius, boolean skip, Vec3d pos,
                                     @NotNull TagKey<ConfiguredStructureFeature<?, ?>> destination,
                                     @NotNull PlayerEntity user, String lock) {
        BlockPos blockPos = world.locateStructure(destination, new BlockPos(pos), radius, skip);

        if (blockPos == null) {
            user.sendMessage(new LiteralText("x").formatted(Formatting.DARK_BLUE, Formatting.BOLD), true);
            Bettermaps.locateMapTaskThreads.remove(lock);
            return;
        }

        Bettermaps.mapTaskSafeLock.lock();
        // check user holds map
        if ((!user.getStackInHand(Hand.MAIN_HAND).isEmpty() && !user.getStackInHand(Hand.MAIN_HAND).equals(stack))
                && !user.getStackInHand(Hand.OFF_HAND).isEmpty() && !user.getStackInHand(Hand.OFF_HAND).equals(stack)) {
            Bettermaps.locateMapTaskThreads.remove(lock);
            Bettermaps.mapTaskSafeLock.unlock();
            return;
        }

        // map data
        byte zoom = explorationNbt.getByte(Bettermaps.NBT_EXPLORATION_ZOOM);
        MapIcon.Type decoration = MapIcon.Type.byId(explorationNbt.getByte(Bettermaps.NBT_EXPLORATION_ICON));

        // map creation
        ItemStack itemStack = FilledMapItem.createMap(world, blockPos.getX(), blockPos.getZ(), zoom, true, true);
        FilledMapItem.fillExplorationMap(world, itemStack);
        MapState.addDecorationsNbt(itemStack, blockPos, "+", decoration);
        nbt.remove(Bettermaps.NBT_POS_DATA);
        nbt.remove(Bettermaps.NBT_EXPLORATION_DATA);
        nbt.remove(Bettermaps.NBT_IS_BETTER_MAP);
        nbt.remove(Bettermaps.NBT_MAP_LOCK);
        itemStack.getOrCreateNbt().copyFrom(nbt);

        // give to user
        if (! user.getInventory().insertStack(itemStack.copy())) {
            user.dropItem(itemStack, false);
        }
        if (! user.isCreative()) stack.decrement(1);
        Bettermaps.locateMapTaskThreads.remove(lock);
        Bettermaps.mapTaskSafeLock.unlock();
        Thread.currentThread().interrupt();
    }
}
