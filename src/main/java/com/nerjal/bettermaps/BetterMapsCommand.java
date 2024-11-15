package com.nerjal.bettermaps;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public final class BetterMapsCommand {
    private BetterMapsCommand(){}

    public static final SuggestionProvider<ServerCommandSource> TASKS = SuggestionProviders.register(
            new Identifier("tasks"),
            (context, builder) -> CommandSource.suggestMatching(Bettermaps.locateMapTaskThreads.keySet(), builder)
    );

    public static void register(@NotNull CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("bettermaps")
                .requires(s -> s.hasPermissionLevel(2))
                .then(
                        literal("list").executes(BetterMapsCommand::executeList)
                )
                .then(
                        literal("stop")
                                .then(CommandManager.argument("id", StringArgumentType.string())
                                        .suggests(TASKS)
                                        .executes(BetterMapsCommand::stopTask)
                                )
                )
        );
    }

    private static int executeList(@NotNull CommandContext<ServerCommandSource> context) {
        Bettermaps.mapTaskStepLock.lock();
        if (Bettermaps.locateMapTaskThreads.isEmpty()) {
            context.getSource().sendMessage(Text.literal("- 0 -"));
            return 0;
        }
        MutableText t = Text.empty();
        List<MutableText> list = Bettermaps.locateMapTaskThreads.entrySet().stream()
                .sorted(Comparator.comparing(e -> (e.getValue().isQueued() ? 1 : 0) + e.getValue().getTaskId()))
                .map(e -> {
                    if (e.getValue().isQueued()) {
                        return Text.literal(String.format("[Queued] %s", e.getKey())).formatted(Formatting.GOLD);
                    } else {
                        return Text.literal(e.getKey()).formatted(Formatting.YELLOW);
                    }
                }).toList();
        Bettermaps.mapTaskStepLock.unlock();
        for (int i = 0; i < list.size(); i++) {
            t.append(list.get(i));
            if (i+1 < list.size()) t.append(Text.literal("\n"));
        }
        context.getSource().sendMessage(t);
        return 0;
    }

    private static int stopTask(@NotNull CommandContext<ServerCommandSource> context) {
        String id = StringArgumentType.getString(context, "id");
        if (Bettermaps.locateMapTaskThreads.containsKey(id)) {
            Bettermaps.LocateTask task = Bettermaps.locateMapTaskThreads.get(id);
            task.interrupt();
            context.getSource().sendFeedback(
                    () -> Text.literal(id).formatted(Formatting.GREEN),
                    false
            );
            return 0;
        }
        context.getSource().sendError(Text.literal(id));
        return 1;
    }
}
