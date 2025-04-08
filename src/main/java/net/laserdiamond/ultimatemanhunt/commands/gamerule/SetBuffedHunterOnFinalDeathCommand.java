package net.laserdiamond.ultimatemanhunt.commands.gamerule;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.laserdiamond.ultimatemanhunt.capability.speedrunner.PlayerSpeedRunner;
import net.laserdiamond.ultimatemanhunt.event.ForgeServerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command used to specify if speed runners should become buffed hunters after losing their last life
 */
public class SetBuffedHunterOnFinalDeathCommand {

    private static final int PERMISSION_LEVEL = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                Commands.literal("buffed_hunter_on_final_death")
                        .requires(sourceStack -> ForgeServerEvents.permission(sourceStack, PERMISSION_LEVEL))
                        .then(
                                Commands.argument("isBuffedHunter", BoolArgumentType.bool())
                                        .executes(commandContext -> setIsBuffedHunterOnFinalDeath(commandContext.getSource(), BoolArgumentType.getBool(commandContext, "isBuffedHunter")))
                        )
        );
    }

    private static void logBuffedHunterOnFinalDeathUpdate(CommandSourceStack sourceStack, boolean isBuffedOnDeath)
    {
        if (isBuffedOnDeath)
        {
            sourceStack.sendSuccess(() -> Component.literal("Set Speed Runners to become buffed hunters on their final death"), true);
            return;
        }
        sourceStack.sendFailure(Component.literal(ChatFormatting.RED + "Cannot change game rules when a game has been started. " +
                "Please stop the game and run this command again if you wish to change a game rule. " +
                "\nisBuffedHunterOnFinalDeath is currently: " + PlayerSpeedRunner.getIsBuffedHunterOnFinalDeath()));
    }

    private static int setIsBuffedHunterOnFinalDeath(CommandSourceStack sourceStack, boolean isBuffedHunter)
    {
        int i = 0;

        logBuffedHunterOnFinalDeathUpdate(sourceStack, isBuffedHunter);
        if (PlayerSpeedRunner.setIsBuffedHunterOnFinalDeath(isBuffedHunter))
        {
            i++;
            return i;
        }

        return i;
    }
}
