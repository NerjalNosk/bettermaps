package com.nerjal.bettermaps;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

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
        context.getSource().sendFeedback(new LiteralText(
                String.join("\n", Bettermaps.locateMapTaskThreads.keySet().stream().toList())
        ).formatted(Formatting.YELLOW), false);
        return 0;
    }

    private static int stopTask(@NotNull CommandContext<ServerCommandSource> context) {
        String id = StringArgumentType.getString(context, "id");
        if (Bettermaps.locateMapTaskThreads.containsKey(id)) {
            Bettermaps.LocateTask task = Bettermaps.locateMapTaskThreads.get(id);
            task.interrupt();
            Bettermaps.locateMapTaskThreads.remove(id);
            context.getSource().sendFeedback(new LiteralText(id).formatted(Formatting.GREEN), false);
            return 0;
        }
        context.getSource().sendError(new LiteralText(id));
        return 1;
    }
}
