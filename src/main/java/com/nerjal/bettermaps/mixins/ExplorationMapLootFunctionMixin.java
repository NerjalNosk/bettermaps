package com.nerjal.bettermaps.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nerjal.bettermaps.BetterMapItem;
import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.function.ExplorationMapLootFunction;
import net.minecraft.registry.entry.RegistryEntry;
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
import java.util.function.Function;

@Mixin(ExplorationMapLootFunction.class)
public abstract class ExplorationMapLootFunctionMixin implements BetterMapItem {

    @Shadow @Final private TagKey<Structure> destination;
    @Shadow @Final private byte zoom;
    @Shadow @Final private int searchRadius;
    @Shadow @Final private boolean skipExistingChunks;
    @Shadow @Final private RegistryEntry<MapDecorationType> decoration;

    @Unique private Identifier explorationTargetWorldId;

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

        Identifier targetWorld = context.getWorld().getRegistryKey().getValue();
        if (this.explorationTargetWorldId != null && !this.explorationTargetWorldId.equals(Bettermaps.NULL_ID)) {
            targetWorld = this.explorationTargetWorldId;
        }

        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        ItemStack newStack = Bettermaps.createMap(origin, context.getWorld(), destination, targetWorld,
                decoration, zoom, searchRadius, skipExistingChunks, name);
        newStack.setCount(stack.getCount());

        cir.setReturnValue(newStack);
        cir.cancel();
    }

    @Override
    public void bettermaps$withDimension(Identifier id) {
        this.explorationTargetWorldId = id;
    }

    @ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/codecs/RecordCodecBuilder;mapCodec(Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"))
    private static MapCodec<ExplorationMapLootFunction> codecExpand(MapCodec<ExplorationMapLootFunction> original) {
        return RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        original.forGetter(Function.identity()),
                        Identifier.CODEC.optionalFieldOf(Bettermaps.NBT_EXPLORATION_DIM, Bettermaps.NULL_ID)
                                .forGetter(o -> ((ExplorationMapLootFunctionMixin)((Object)o)).explorationTargetWorldId)
                ).apply(instance, (i,a) -> {
                    ((ExplorationMapLootFunctionMixin)((Object)i)).bettermaps$withDimension(a);
                    return i;
                })
        );
    }
}
