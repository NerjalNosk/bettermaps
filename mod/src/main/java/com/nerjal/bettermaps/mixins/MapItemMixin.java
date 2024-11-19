package com.nerjal.bettermaps.mixins;

import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EmptyMapItem;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Debug
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
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            return;
        }
        NbtCompound srcNbt = component.copyNbt();
        if (!srcNbt.contains(Bettermaps.MOD_ID)) return;
        NbtElement e = srcNbt.get(Bettermaps.MOD_ID);
        if (!(e instanceof NbtCompound nbt)) return;
        if (! nbt.contains(Bettermaps.NBT_EXPLORATION_DATA, NbtElement.COMPOUND_TYPE)) return;

        if (nbt.contains(Bettermaps.NBT_MAP_LOCK)) {
            String s = nbt.getString(Bettermaps.NBT_MAP_LOCK);
            if (Bettermaps.locateMapTaskThreads.containsKey(s)) {
                user.sendMessage(Text.literal("x").formatted(Formatting.GOLD, Formatting.BOLD), true);
                cir.setReturnValue(TypedActionResult.consume(stack));
                cir.cancel();
                return;
            }
        }

        NbtCompound explorationNbt = nbt.getCompound(Bettermaps.NBT_EXPLORATION_DATA);

        // locate
        int radius = explorationNbt.getInt(Bettermaps.NBT_EXPLORATION_RADIUS);
        boolean skip = explorationNbt.getBoolean(Bettermaps.NBT_EXPLORATION_SKIP);
        Vec3d p = user.getPos();
        Vec3i pos = new Vec3i((int) p.x, (int) p.y, (int) p.z);

        Identifier destId = Identifier.tryParse(explorationNbt.getString(Bettermaps.NBT_EXPLORATION_DEST));
        TagKey<Structure> destTag = TagKey.of(RegistryKeys.STRUCTURE, destId);
        Optional<RegistryEntryList.Named<Structure>> entryList = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getEntryList(destTag);
        Optional<RegistryEntry.Reference<Structure>> entry = world.getRegistryManager().get(RegistryKeys.STRUCTURE).getEntry(destId);

        NbtCompound posNbt = nbt.getCompound(Bettermaps.NBT_POS_DATA);
        if (world.getGameRules().getBoolean(Bettermaps.DO_BETTERMAP_FROM_PLAYER_POS)) {
            pos = new Vec3i(posNbt.getInt("x"), posNbt.getInt("y"), posNbt.getInt("z"));
        }
        if (!world.getRegistryKey().getValue().toString().equals(posNbt.getString(Bettermaps.NBT_EXPLORATION_DIM))) {
            user.sendMessage(Text.literal("x").formatted(Formatting.RED, Formatting.BOLD), true);
            cir.setReturnValue(TypedActionResult.consume(stack));
            cir.cancel();
            return;
        }

        // locate task setup
        Vec3i fPos = pos;
        //noinspection DataFlowIssue
        String id = String.format("%d-%s", Bettermaps.taskCounter.incrementAndGet(), user.getDisplayName().getString());
        RegistryEntryList<Structure> entries = entryList.map(s -> (RegistryEntryList<Structure>) s).or(() -> entry.map(RegistryEntryList::of)).orElseThrow();
        Runnable task = ()->locationTask(world, stack, nbt, explorationNbt, radius, skip, fPos, entries, user, id);
        if (! user.isCreative()) Bettermaps.lockMap(stack, id);

        // task run
        if (world.getGameRules().getBoolean(Bettermaps.DO_BETTERMAP_DYNAMIC_LOCATING)) {
            Bettermaps.LocateTask locateTask = new Bettermaps.LocateTask(world, task, id);
            Bettermaps.locateMapTaskThreads.putIfAbsent(id, locateTask);
            if (w.getGameRules().getBoolean(Bettermaps.DO_BETTERMAPS_FEEDBACK)) {
                world.getServer().getCommandSource().sendFeedback(() -> Text.translatable("bettermaps.feedback", user.getDisplayName(), id), true);
            }
            locateTask.start();
            user.sendMessage(Text.literal("\u2714").formatted(Formatting.GREEN, Formatting.BOLD), true);
        } else {
            task.run();
        }

        cir.setReturnValue(TypedActionResult.consume(stack));
        cir.cancel();
    }

    @Unique
    private static void locationTask(@NotNull ServerWorld world, @NotNull ItemStack stack, @NotNull NbtCompound nbt,
                                     @NotNull NbtCompound explorationNbt, int radius, boolean skip, @NotNull Vec3i pos,
                                     RegistryEntryList<Structure> entries, @NotNull PlayerEntity user, String lock) {
        //noinspection DataFlowIssue
        BlockPos blockPos = world.getChunkManager().getChunkGenerator().locateStructure(world, entries, new BlockPos(pos), radius, skip).getFirst();

        if (blockPos == null) {
            user.sendMessage(Text.literal("x").formatted(Formatting.DARK_BLUE, Formatting.BOLD), true);
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
        RegistryEntry<MapDecorationType> decoration = Registries.MAP_DECORATION_TYPE.getEntry(
                Registries.MAP_DECORATION_TYPE.get(
                        Identifier.tryParse(explorationNbt.getString(Bettermaps.NBT_EXPLORATION_ICON))
                )
        );

        // map creation
        ItemStack itemStack = FilledMapItem.createMap(world, blockPos.getX(), blockPos.getZ(), zoom, true, true);
        FilledMapItem.fillExplorationMap(world, itemStack);
        MapState.addDecorationsNbt(itemStack, blockPos, "+", decoration);
        nbt.remove(Bettermaps.NBT_POS_DATA);
        nbt.remove(Bettermaps.NBT_EXPLORATION_DATA);
        nbt.remove(Bettermaps.NBT_MAP_LOCK);
        Bettermaps.storeMapData(nbt, itemStack, null);

        // give to user
        if (! user.getInventory().insertStack(itemStack.copy())) {
            user.dropItem(itemStack, false);
        }
        if (! user.isCreative()) stack.decrement(1);
        Bettermaps.locateMapTaskThreads.remove(lock);
        Bettermaps.mapTaskSafeLock.unlock();
    }
}
