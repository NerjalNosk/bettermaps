package com.nerjal.bettermaps.mixins;

import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapIcon;
import net.minecraft.tag.TagKey;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(TradeOffers.SellMapFactory.class)
public abstract class TradeOffersSellMapFactoryMixin {
    @Shadow @Final private MapIcon.Type iconType;
    @Shadow @Final private String nameKey;
    @Shadow @Final private int price;
    @Shadow @Final private int maxUses;
    @Shadow @Final private int experience;
    @Shadow @Final private TagKey<ConfiguredStructureFeature<?, ?>> structure;
    @Unique
    private Identifier explorationTargetWorld; // useless up to this day, will try to follow the vanilla behaviour and search in current dimension

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private void createInjector(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> cir) {
        if (!Bettermaps.isTradeEnabled(entity.world)) {
            return;
        }
        Identifier target = explorationTargetWorld == null ? entity.world.getRegistryKey().getValue() : explorationTargetWorld;
        ItemStack stack = Bettermaps.createMap(entity.getPos(), entity.world, structure, target,
                iconType.getId(), (byte) 2, 100, true, new TranslatableText(nameKey));
        stack.setCustomName(new TranslatableText(nameKey));
        cir.setReturnValue(new TradeOffer(new ItemStack(Items.EMERALD, price), new ItemStack(Items.COMPASS), stack, maxUses, experience, 0.2F));
        cir.cancel();
    }
}
