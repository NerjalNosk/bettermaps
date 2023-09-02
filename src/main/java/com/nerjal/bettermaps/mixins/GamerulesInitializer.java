package com.nerjal.bettermaps.mixins;

import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRules.class)
public abstract class GamerulesInitializer {

    @Contract(pure = true)
    @Shadow
    private static <T extends GameRules.Rule<T>> GameRules.@Nullable Key<T> register(
            String name, GameRules.Category category, GameRules.Type<T> type
    ) {
        return null;
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void staticInitInjector(CallbackInfo ci) {
        Bettermaps.set(Bettermaps.DO_BETTERMAPS,
                register(
                        Bettermaps.DO_BETTERMAPS,
                        GameRules.Category.PLAYER,
                        GameRules.BooleanRule.create(true)
                )
        );
        Bettermaps.set(
                Bettermaps.DO_BETTERMAP_FROM_PLAYER_POS,
                register(
                        Bettermaps.DO_BETTERMAP_FROM_PLAYER_POS,
                        GameRules.Category.PLAYER,
                        GameRules.BooleanRule.create(false))
        );
        Bettermaps.set(Bettermaps.DO_BETTERMAP_DYNAMIC_LOCALISING,
                register(
                        Bettermaps.DO_BETTERMAP_DYNAMIC_LOCALISING,
                        GameRules.Category.PLAYER,
                        GameRules.BooleanRule.create(true)
                ));
    }
}
