package com.nerjal.bettermaps.mixins;

import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow private volatile boolean paused;

    @Inject(method = "tick", at = @At("HEAD"))
    private void clientTickStopMapThreadsIfPaused(CallbackInfo ci) {
        if (this.paused && Bettermaps.isClientPaused()) {
            Bettermaps.locateMapTaskThreads.forEach(t -> {
                try {
                    t.interrupt();
                } catch (SecurityException ex) {
                    LogManager.getLogger().error(ex);
                }
            });
            Bettermaps.setClientPaused(true);
        } else if (Bettermaps.isClientPaused()) {
            Iterator<Bettermaps.LocateTask> taskIterator = Bettermaps.locateMapTaskThreads.iterator();
            Collection<Bettermaps.LocateTask> tasks = new ArrayList<>();
            while (taskIterator.hasNext()) {
                Bettermaps.LocateTask task = taskIterator.next();
                if (task.isInterrupted()) {
                    tasks.add(new Bettermaps.LocateTask(task.task));
                    taskIterator.remove();
                }
            }
            tasks.forEach(task -> {
                Bettermaps.locateMapTaskThreads.add(task);
                task.start();
            });
            Bettermaps.setClientPaused(false);
        }
    }
}
