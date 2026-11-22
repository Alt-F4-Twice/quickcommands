package com.example.quickcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

/**
 * Shortcut commands. Every command here just runs the matching vanilla command
 * (/gamemode, /effect, /kill) as the person who typed it, so the permission
 * rules are the same as vanilla and nothing depends on Minecraft internals.
 */
public class QuickCommands implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            // /c /s /sp /a  [players]
            gamemode(dispatcher, "c", "creative");
            gamemode(dispatcher, "s", "survival");
            gamemode(dispatcher, "sp", "spectator");
            gamemode(dispatcher, "a", "adventure");

            // /heal [players] [alsoFeed]
            dispatcher.register(Commands.literal("heal")
                    .requires(canUse("effect"))
                    .executes(ctx -> heal(ctx.getSource(), "@s", 1, false))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> heal(ctx.getSource(), rawTargets(ctx), count(ctx), false))
                            .then(Commands.argument("alsoFeed", BoolArgumentType.bool())
                                    .executes(ctx -> heal(ctx.getSource(), rawTargets(ctx), count(ctx),
                                            BoolArgumentType.getBool(ctx, "alsoFeed"))))));

            // /feed [players]
            dispatcher.register(Commands.literal("feed")
                    .requires(canUse("effect"))
                    .executes(ctx -> feed(ctx.getSource(), "@s", 1))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> feed(ctx.getSource(), rawTargets(ctx), count(ctx)))));

            // /killall [shouldAlsoKillPlayers]
            dispatcher.register(Commands.literal("killall")
                    .requires(canUse("kill"))
                    .executes(ctx -> killAll(ctx.getSource(), false))
                    .then(Commands.argument("shouldAlsoKillPlayers", BoolArgumentType.bool())
                            .executes(ctx -> killAll(ctx.getSource(),
                                    BoolArgumentType.getBool(ctx, "shouldAlsoKillPlayers")))));
        });
    }

    // ---------------------------------------------------------------- gamemode

    private static void gamemode(CommandDispatcher<CommandSourceStack> dispatcher, String alias, String mode) {
        dispatcher.register(Commands.literal(alias)
                .requires(canUse("gamemode"))
                .executes(ctx -> run(ctx.getSource(), "gamemode " + mode))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> run(ctx.getSource(), "gamemode " + mode + " " + rawTargets(ctx)))));
    }

    // -------------------------------------------------------------------- heal

    private static int heal(CommandSourceStack source, String targets, int count, boolean alsoFeed)
            throws CommandSyntaxException {
        runSilently(source, "effect give " + targets + " minecraft:instant_health 1 10 true");
        if (alsoFeed) {
            runSilently(source, "effect give " + targets + " minecraft:saturation 1 4 true");
        }
        String what = alsoFeed ? "Healed and fed " : "Healed ";
        source.sendSuccess(() -> Component.literal(what + count + (count == 1 ? " player" : " players")), true);
        return count;
    }

    // -------------------------------------------------------------------- feed

    private static int feed(CommandSourceStack source, String targets, int count) throws CommandSyntaxException {
        runSilently(source, "effect give " + targets + " minecraft:saturation 1 4 true");
        source.sendSuccess(() -> Component.literal("Fed " + count + (count == 1 ? " player" : " players")), true);
        return count;
    }

    // ----------------------------------------------------------------- killall

    private static int killAll(CommandSourceStack source, boolean alsoPlayers) throws CommandSyntaxException {
        String selector = alsoPlayers ? "@e" : "@e[type=!minecraft:player]";
        int killed;
        try {
            killed = runSilently(source, "kill " + selector);
        } catch (CommandSyntaxException e) {
            // vanilla says "No entity was found" when there is nothing to kill
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Killed " + killed + (killed == 1 ? " entity" : " entities")), true);
        return killed;
    }

    // ----------------------------------------------------------------- helpers

    /** Runs a vanilla command as the same source, showing vanilla's own feedback. */
    private static int run(CommandSourceStack source, String command) throws CommandSyntaxException {
        return source.getServer().getCommands().getDispatcher().execute(command, source);
    }

    /** Runs a vanilla command as the same source but hides vanilla's feedback message. */
    private static int runSilently(CommandSourceStack source, String command) throws CommandSyntaxException {
        return source.getServer().getCommands().getDispatcher().execute(command, source.withSuppressedOutput());
    }

    /** Only lets people who could use the vanilla command use ours (and hides ours from tab-complete otherwise). */
    private static Predicate<CommandSourceStack> canUse(String vanillaCommand) {
        return source -> {
            CommandNode<CommandSourceStack> node = source.getServer().getCommands().getDispatcher()
                    .getRoot().getChild(vanillaCommand);
            return node != null && node.canUse(source);
        };
    }

    /** The exact text the person typed for the "targets" argument, e.g. "Steve" or "@a[distance=..10]". */
    private static String rawTargets(CommandContext<CommandSourceStack> ctx) {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (node.getNode().getName().equals("targets")) {
                return node.getRange().get(ctx.getInput());
            }
        }
        return "@s";
    }

    private static int count(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "targets").size();
    }
}
