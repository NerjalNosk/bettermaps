package com.nerjal.bettermaps.mixins;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.nerjal.bettermaps.BetterMapItem;
import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapIcon;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.function.ExplorationMapLootFunction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import net.minecraft.tag.TagKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExplorationMapLootFunction.class)
public abstract class ExplorationMapLootFunctionMixin implements BetterMapItem {

    @Shadow @Final TagKey<Structure> destination;
    @Shadow @Final MapIcon.Type decoration;
    @Shadow @Final byte zoom;
    @Shadow @Final int searchRadius;
    @Shadow @Final boolean skipExistingChunks;
    @Unique
    private Identifier explorationTargetWorldId;

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
        nbtPos.putDouble("x", pos.getX());
        nbtPos.putDouble("y", pos.getY());
        nbtPos.putDouble("z", pos.getZ());

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
        Identifier id = context.getWorld().getDimensionKey().getValue();
        if (this.explorationTargetWorldId != null) {
            id = this.explorationTargetWorldId;
        }
        lore.add(NbtString.of(String.format("{\"text\": \"[%s]\", \"color\": \"dark_gray\", \"italic\":false}", id)));
        display.put("Lore", lore);
        nbt.put("display", display);
        if (stack.hasCustomName()) {
            newStack.setCustomName(stack.getName());
        }

        newStack.setNbt(nbt);
        newStack.setCount(stack.getCount());
        cir.setReturnValue(newStack);
        cir.cancel();
    }

    @Override
    public void bettermaps$withDimension(Identifier id) {
        this.explorationTargetWorldId = id;
    }

    @Mixin(ExplorationMapLootFunction.Serializer.class)
    abstract static class SerializerMixin {
        @Inject(method = "fromJson(Lcom/google/gson/JsonObject;Lcom/google/gson/JsonDeserializationContext;[Lnet/minecraft/loot/condition/LootCondition;)Lnet/minecraft/loot/function/ExplorationMapLootFunction;", at = @At("TAIL"))
        private void fromJsonTailDimensionCapture(
                @NotNull JsonObject json, JsonDeserializationContext context, LootCondition[] conditions,
                CallbackInfoReturnable<ExplorationMapLootFunction> cir) {
            if (json.has(Bettermaps.NBT_EXPLORATION_DIM)) {
                ((BetterMapItem) cir.getReturnValue()).bettermaps$withDimension(
                        Identifier.tryParse(json.get(Bettermaps.NBT_EXPLORATION_DIM).getAsString()));
            }
        }
    }
}
