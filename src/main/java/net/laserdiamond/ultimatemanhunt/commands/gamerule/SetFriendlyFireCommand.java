package net.laserdiamond.ultimatemanhunt.commands.gamerule;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.laserdiamond.ultimatemanhunt.UMGame;
import net.laserdiamond.ultimatemanhunt.UltimateManhunt;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command used to specify if friendly fire should be allowed between players of the same team
 */
public class SetFriendlyFireCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                Commands.literal("um_friendly_fire")
                        .requires(UltimateManhunt::hasPermission)
                        .then(
                                Commands.argument("isFriendlyFire", BoolArgumentType.bool())
                                        .executes(commandContext -> setFriendlyFire(commandContext, BoolArgumentType.getBool(commandContext, "isFriendlyFire")))
                        )
        );
    }

    private static void logFriendlyFireUpdate(CommandSourceStack source, boolean isFriendlyFire)
    {
        source.sendSuccess(() -> Component.literal("Set Friendly Fire to: " + isFriendlyFire), true);
    }

    private static void logFailFriendlyFireUpdate(CommandSourceStack source)
    {
        source.sendFailure(Component.literal(ChatFormatting.RED + "Cannot change game rules when a game has been started. " +
                "Please stop the game and run this command again if you wish to change a game rule. " +
                "\nisFriendlyFire is currently: " + UMGame.isFriendlyFire()));
    }

    private static int setFriendlyFire(CommandContext<CommandSourceStack> commandContext, boolean isFriendlyFire)
    {
        int i = 0;
        if (UMGame.State.hasGameBeenStarted())
        {
            // Game started. Do not change
            logFailFriendlyFireUpdate(commandContext.getSource());
            return 0;
        }
        UMGame.setFriendlyFire(isFriendlyFire);
        logFriendlyFireUpdate(commandContext.getSource(), isFriendlyFire);
        i++;
        return i;
    }
}
