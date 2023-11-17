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
import net.minecraft.text.Text;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
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

import java.util.Objects;

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
        if (!Bettermaps.isLootEnabled(context.getWorld())) {
            return;
        }
        if ((!stack.isOf(Items.MAP)) && (!stack.isOf(Items.FILLED_MAP))) {
            return;
        }

        Vec3d origin = context.get(LootContextParameters.ORIGIN);
        Objects.requireNonNull(origin);

        Identifier targetWorld = context.getWorld().getDimensionKey().getValue();
        if (this.explorationTargetWorldId != null) {
            targetWorld = this.explorationTargetWorldId;
        }

        Text name = null;
        if (stack.hasCustomName()) {
            name = stack.getName();
        }
        ItemStack newStack = Bettermaps.createMap(origin, context.getWorld(), destination, targetWorld,
                decoration.getId(), zoom, searchRadius, skipExistingChunks, name);
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
