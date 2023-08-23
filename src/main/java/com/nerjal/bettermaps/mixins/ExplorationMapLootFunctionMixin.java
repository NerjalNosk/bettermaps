package com.nerjal.bettermaps.mixins;

import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapIcon;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.function.ExplorationMapLootFunction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExplorationMapLootFunction.class)
public abstract class ExplorationMapLootFunctionMixin {

    @Shadow @Final TagKey<Structure> destination;
    @Shadow @Final MapIcon.Type decoration;
    @Shadow @Final byte zoom;
    @Shadow @Final int searchRadius;
    @Shadow @Final boolean skipExistingChunks;

    @Inject(method = "process", at = @At("HEAD"), cancellable = true)
    private void processInjector(
            @NotNull ItemStack stack,
            @NotNull LootContext context,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!context.getWorld().getGameRules().getBoolean(Bettermaps.get(Bettermaps.DO_BETTERMAPS))) {
            return;
        }
        if ((!stack.isOf(Items.MAP)) && (!stack.isOf(Items.FILLED_MAP))) {
            return;
        }

        // pos logic
        Vec3d pos = context.get(LootContextParameters.ORIGIN);
        NbtCompound nbtPos = new NbtCompound();
        nbtPos.putString(Bettermaps.NBT_EXPLORATION_DIM, context.getWorld().getDimensionKey().getValue().toString());
        nbtPos.putInt("x", (int) pos.getX());
        nbtPos.putInt("y", (int) pos.getY());
        nbtPos.putInt("z", (int) pos.getZ());

        // function args logic
        NbtCompound explorationNbt = new NbtCompound();
        explorationNbt.putString(Bettermaps.NBT_EXPLORATION_DEST, destination.id().toString());
        explorationNbt.putByte(Bettermaps.NBT_EXPLORATION_ICON, decoration.getId());
        explorationNbt.putByte(Bettermaps.NBT_EXPLORATION_ZOOM, zoom);
        explorationNbt.putInt(Bettermaps.NBT_EXPLORATION_RADIUS, searchRadius);
        explorationNbt.putBoolean(Bettermaps.NBT_EXPLORATION_SKIP, skipExistingChunks);

        // function result logic
        ItemStack newStack = new ItemStack(Items.MAP);
        NbtCompound nbt = new NbtCompound();
        nbt.put(Bettermaps.NBT_POS_DATA, nbtPos);
        nbt.putBoolean(Bettermaps.NBT_IS_BETTER_MAP, true);
        nbt.put(Bettermaps.NBT_EXPLORATION_DATA, explorationNbt);
        NbtCompound display = new NbtCompound();
        NbtList lore = new NbtList();
        lore.add(NbtString.of(String.format("{\"text\": \"[%s]\", \"color\": \"dark_gray\", \"italic\":false}",
                context.getWorld().getDimensionKey().getValue().toString())));
        display.put("Lore", lore);
        nbt.put("display", display);
        newStack.setCount(stack.getCount());
        newStack.setNbt(nbt);
        if (stack.hasCustomName()) {
            newStack.setCustomName(stack.getName());
        }
        cir.setReturnValue(newStack);
        cir.cancel();
    }
}
