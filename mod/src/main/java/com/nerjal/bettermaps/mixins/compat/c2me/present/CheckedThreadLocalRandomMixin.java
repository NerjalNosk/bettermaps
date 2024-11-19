package com.nerjal.bettermaps.mixins.compat.c2me.present;

import com.ishland.c2me.fixes.worldgen.threading_issues.common.CheckedThreadLocalRandom;
import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.util.math.random.LocalRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CheckedThreadLocalRandom.class, remap = false)
public class CheckedThreadLocalRandomMixin extends LocalRandom {
    CheckedThreadLocalRandomMixin(long l) {
        super(l);
    }

    @Inject(method = "isSafe", at = @At("HEAD"), cancellable = true)
    private void onSetSeedCheckIfMapLocationThread(CallbackInfoReturnable<Boolean> cir) {
        Thread t = Thread.currentThread();
        if (Bettermaps.locateMapTaskThreads.containsValue(t)) {
            cir.setReturnValue(true);
        }
    }
}
