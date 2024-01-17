package com.nerjal.bettermaps.mixins.compat.c2me.present;

import com.ishland.c2me.fixes.worldgen.threading_issues.common.CheckedThreadLocalRandom;
import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.util.math.random.LocalRandom;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(value = CheckedThreadLocalRandom.class, remap = false)
public class CheckedThreadLocalRandomMixin extends LocalRandom {
    @Shadow @Final private Supplier<Thread> owner;

    CheckedThreadLocalRandomMixin(long l) {
        super(l);
    }

    @Inject(method = "setSeed", at = @At("HEAD"), cancellable = true)
    private void onSetSeedCheckIfMapLocationThread(long seed, CallbackInfo ci) {
        Thread t = this.owner != null ? this.owner.get() : null;
        if (Bettermaps.locateMapTaskThreads.containsValue(t)) {
            super.setSeed(seed);
            ci.cancel();
        }
    }

    @Inject(method = "next", at = @At("HEAD"), cancellable = true)
    private void onNextCheckIfMapLocationThread(int bits, CallbackInfoReturnable<Integer> cir) {
        Thread t = this.owner != null ? this.owner.get() : null;
        if (Bettermaps.locateMapTaskThreads.containsValue(t)) {
            cir.setReturnValue(super.next(bits));
        }
    }
}
