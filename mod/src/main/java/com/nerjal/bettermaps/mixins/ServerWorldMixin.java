package com.nerjal.bettermaps.mixins;

import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ServerWorldMixin {
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void serverShutdownStopMapThreadsInjector(CallbackInfo ci) {
        Bettermaps.locateMapTaskThreads.forEach((id, task) -> {
            try {
                task.interrupt();
            } catch (SecurityException ex) {
                LogManager.getLogger().error(ex);
            }
        });
    }

    @Inject(method = "stop", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;join()V", shift = At.Shift.BEFORE))
    private void beforeStopInterruptMapThreadsInjector(boolean waitForShutdown, CallbackInfo ci) {
        Bettermaps.locateMapTaskThreads.forEach((id, task) -> {
            try {
                task.interrupt();
            } catch (SecurityException ex) {
                LogManager.getLogger().error(ex);
            }
        });
    }
}
