package com.nerjal.bettermaps.mixins;

import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(TradeOffers.SellMapFactory.class)
public abstract class TradeOffersSellMapFactoryMixin {
    @Shadow @Final private TagKey<Structure> structure;
    @Shadow @Final private RegistryEntry<MapDecorationType> decoration;
    @Shadow @Final private String nameKey;
    @Shadow @Final private int price;
    @Shadow @Final private int maxUses;
    @Shadow @Final private int experience;
    @Unique
    private Identifier explorationTargetWorld; // useless up to this day, will try to follow the vanilla behaviour and search in current dimension

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private void createInjector(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> cir) {
        if (!Bettermaps.isTradeEnabled(entity.getWorld())) {
            return;
        }
        Identifier target = explorationTargetWorld == null ? entity.getWorld().getRegistryKey().getValue() : explorationTargetWorld;
        ItemStack stack = Bettermaps.createMap(entity.getPos(), entity.getWorld(), structure, target,
                decoration, (byte) 2, 100, true, Text.translatable(nameKey));
        cir.setReturnValue(new TradeOffer(new TradedItem(Items.EMERALD, price), Optional.of(new TradedItem(Items.COMPASS, 1)), stack, maxUses, experience, 0.2F));
        cir.cancel();
    }
}
