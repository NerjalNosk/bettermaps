package com.nerjal.bettermaps.mixins;

import com.nerjal.bettermaps.Bettermaps;
import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow private volatile boolean paused;

    @Inject(method = "tick", at = @At("HEAD"))
    private void clientTickStopMapThreadsIfPaused(CallbackInfo ci) {
        if (this.paused && Bettermaps.isClientPaused()) {
            Bettermaps.locateMapTaskThreads.forEach((s,t) -> {
                try {
                    t.interrupt();
                } catch (SecurityException ex) {
                    LogManager.getLogger().error(ex);
                }
            });
            Bettermaps.setClientPaused(true);
        } else if (Bettermaps.isClientPaused()) {
            Iterator<Map.Entry<String, Bettermaps.LocateTask>> taskIterator =
                    Bettermaps.locateMapTaskThreads.entrySet().iterator();
            Map<String, Bettermaps.LocateTask> tasks = new HashMap<>();
            while (taskIterator.hasNext()) {
                Map.Entry<String, Bettermaps.LocateTask> entry = taskIterator.next();
                Bettermaps.LocateTask task = entry.getValue();
                if (task.isInterrupted()) {
                    tasks.put(entry.getKey(), new Bettermaps.LocateTask(task.task, entry.getKey()));
                    taskIterator.remove();
                }
            }
            tasks.forEach((id, task) -> {
                Bettermaps.locateMapTaskThreads.put(id, task);
                task.start();
            });
            Bettermaps.setClientPaused(false);
        }
    }
}
