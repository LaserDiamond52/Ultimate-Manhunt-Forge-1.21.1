package net.laserdiamond.ultimatemanhunt.event;

import net.laserdiamond.ultimatemanhunt.UMGame;
import net.laserdiamond.ultimatemanhunt.UltimateManhunt;
import net.laserdiamond.ultimatemanhunt.api.event.UltimateManhuntGameStateEvent;
import net.laserdiamond.ultimatemanhunt.api.event.SpeedRunnerLifeLossEvent;
import net.laserdiamond.ultimatemanhunt.api.event.SpeedRunnerToHunterEvent;
import net.laserdiamond.ultimatemanhunt.capability.game.PlayerGameTimeCapability;
import net.laserdiamond.ultimatemanhunt.capability.hunter.PlayerHunter;
import net.laserdiamond.ultimatemanhunt.capability.hunter.PlayerHunterCapability;
import net.laserdiamond.ultimatemanhunt.capability.speedrunner.PlayerSpeedRunner;
import net.laserdiamond.ultimatemanhunt.capability.speedrunner.PlayerSpeedRunnerCapability;
import net.laserdiamond.ultimatemanhunt.commands.*;
import net.laserdiamond.ultimatemanhunt.commands.gamerule.SetBuffedHunterOnFinalDeathCommand;
import net.laserdiamond.ultimatemanhunt.commands.gamerule.SetFriendlyFireCommand;
import net.laserdiamond.ultimatemanhunt.commands.gamerule.SetHardcoreCommand;
import net.laserdiamond.ultimatemanhunt.commands.gamerule.SetWindTorchesCommand;
import net.laserdiamond.ultimatemanhunt.item.UMItems;
import net.laserdiamond.ultimatemanhunt.item.WindTorchItem;
import net.laserdiamond.ultimatemanhunt.network.UMPackets;
import net.laserdiamond.ultimatemanhunt.network.packet.game.GameStateS2CPacket;
import net.laserdiamond.ultimatemanhunt.network.packet.game.GameTimeCapabilitySyncS2CPacket;
import net.laserdiamond.ultimatemanhunt.network.packet.game.HardcoreUpdateS2CPacket;
import net.laserdiamond.ultimatemanhunt.network.packet.game.RemainingPlayerCountS2CPacket;
import net.laserdiamond.ultimatemanhunt.network.packet.hunter.HunterCapabilitySyncS2CPacket;
import net.laserdiamond.ultimatemanhunt.network.packet.hunter.HunterChangeS2CPacket;
import net.laserdiamond.ultimatemanhunt.network.packet.hunter.HunterGracePeriodDurationS2CPacket;
import net.laserdiamond.ultimatemanhunt.network.packet.speedrunner.SpeedRunnerCapabilitySyncS2CPacket;
import net.laserdiamond.ultimatemanhunt.network.packet.speedrunner.SpeedRunnerGracePeriodDurationS2CPacket;
import net.laserdiamond.ultimatemanhunt.network.packet.speedrunner.SpeedRunnerChangeS2CPacket;
import net.laserdiamond.ultimatemanhunt.network.packet.speedrunner.SpeedRunnerMaxLifeChangeS2CPacket;
import net.laserdiamond.ultimatemanhunt.sound.UMSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UltimateManhunt.MODID)
public class ForgeEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        ManageHuntersCommand.register(event.getDispatcher());
        UltimateManhuntGameCommands.register(event.getDispatcher());
        SetFriendlyFireCommand.register(event.getDispatcher());
        SetHardcoreCommand.register(event.getDispatcher());
        SetGracePeriodCommand.register(event.getDispatcher());
        SetRemainingSpeedRunnerLivesCommand.register(event.getDispatcher());
        MaxSpeedRunnerLivesCommand.register(event.getDispatcher());
        SetUMSpawnCommand.register(event.getDispatcher());
        SetWindTorchesCommand.register(event.getDispatcher());
        SetBuffedHunterOnFinalDeathCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (UMGame.State.isGameNotInProgress()) // Is a game in progress?
        {
            return; // No game in progress. End method
        }
        LivingEntity living = event.getEntity();
        if (living == null)
        {
            return; // Dead entity is null. End method
        }
        if (living instanceof Player deadPlayer) // Check if a player died
        {
            Level level = deadPlayer.level();
            if (level.isClientSide)
            {
                return; // ensure we are on the server
            }
            Entity sourceEntity = event.getSource().getEntity();
            deadPlayer.getCapability(PlayerHunterCapability.PLAYER_HUNTER).ifPresent(deadHunter ->
            {
                if (!deadHunter.isHunter()) // Was the dead player NOT a hunter?
                {
                    deadPlayer.getCapability(PlayerSpeedRunnerCapability.PLAYER_SPEED_RUNNER).ifPresent(deadSpeedRunner ->
                    {
                        deadSpeedRunner.setWasLastKilledByHunter(false);
                        UMPackets.sendToPlayer(new SpeedRunnerChangeS2CPacket(deadSpeedRunner), deadPlayer);
                        UMPackets.sendToAllTrackingEntityAndSelf(new SpeedRunnerCapabilitySyncS2CPacket(deadPlayer.getId(), deadSpeedRunner.toNBT()), deadPlayer);

                        if (UMGame.isHardcore()) // Hardcore?
                        {
                            // Remove a player life
                            MinecraftForge.EVENT_BUS.post(new SpeedRunnerLifeLossEvent(deadPlayer, false));

                            if (deadSpeedRunner.getLives() <= 0)
                            {
                                MinecraftForge.EVENT_BUS.post(new SpeedRunnerToHunterEvent(deadPlayer, PlayerSpeedRunner.getIsBuffedHunterOnFinalDeath(), true));

                                if (PlayerSpeedRunner.getRemainingSpeedRunners().isEmpty()) // Check if there are any remaining speed runners
                                {
                                    MinecraftForge.EVENT_BUS.post(new UltimateManhuntGameStateEvent.End(UltimateManhuntGameStateEvent.End.Reason.HUNTER_WIN, PlayerSpeedRunner.getRemainingSpeedRunners(), PlayerHunter.getHunters())); // No more speed runners. Hunters win!
                                }
                            }
                            return; // End method
                        }
                        if (sourceEntity instanceof Player killer) // Is killer a player?
                        {
                            killer.getCapability(PlayerHunterCapability.PLAYER_HUNTER).ifPresent(killerHunter ->
                            {
                                if (killerHunter.isHunter()) // was killer a hunter?
                                {
                                    MinecraftForge.EVENT_BUS.post(new SpeedRunnerLifeLossEvent(deadPlayer, true));

                                    if (deadSpeedRunner.getLives() <= 0) // Does the speed runner still have lives remaining?
                                    {
                                        MinecraftForge.EVENT_BUS.post(new SpeedRunnerToHunterEvent(deadPlayer, PlayerSpeedRunner.getIsBuffedHunterOnFinalDeath(), true));

                                        if (PlayerSpeedRunner.getRemainingSpeedRunners().isEmpty()) // Check if there are any remaining speed runners
                                        {
                                            MinecraftForge.EVENT_BUS.post(new UltimateManhuntGameStateEvent.End(UltimateManhuntGameStateEvent.End.Reason.HUNTER_WIN, PlayerSpeedRunner.getRemainingSpeedRunners(), PlayerHunter.getHunters())); // No more speed runners. Hunters win!
                                        }
                                    }
                                } else // Killer was not a hunter
                                {
                                    // Don't want speed runners to die to another entity to avoid losing a life when near hunters
                                    if (isNearHunter(deadPlayer)) // Check if the dead player was near a hunter when they died
                                    {
                                        MinecraftForge.EVENT_BUS.post(new SpeedRunnerLifeLossEvent(deadPlayer, true));

                                        if (deadSpeedRunner.getLives() <= 0)
                                        {
                                            MinecraftForge.EVENT_BUS.post(new SpeedRunnerToHunterEvent(deadPlayer, PlayerSpeedRunner.getIsBuffedHunterOnFinalDeath(), true));

                                            if (PlayerSpeedRunner.getRemainingSpeedRunners().isEmpty()) // Check if there are any remaining speed runners
                                            {
                                                MinecraftForge.EVENT_BUS.post(new UltimateManhuntGameStateEvent.End(UltimateManhuntGameStateEvent.End.Reason.HUNTER_WIN, PlayerSpeedRunner.getRemainingSpeedRunners(), PlayerHunter.getHunters())); // No more speed runners. Hunters win!
                                            }
                                        }
                                    }
                                }
                            });
                            return; // End method
                        }
                        // Player did not die to a hunter
                        if (isNearHunter(deadPlayer)) // Check if the dead player was near a hunter when they died
                        {
                            MinecraftForge.EVENT_BUS.post(new SpeedRunnerLifeLossEvent(deadPlayer, true));

                            if (deadSpeedRunner.getLives() <= 0)
                            {
                                MinecraftForge.EVENT_BUS.post(new SpeedRunnerToHunterEvent(deadPlayer, PlayerSpeedRunner.getIsBuffedHunterOnFinalDeath(), true));

                                if (PlayerSpeedRunner.getRemainingSpeedRunners().isEmpty()) // Check if there are any remaining speed runners
                                {
                                    MinecraftForge.EVENT_BUS.post(new UltimateManhuntGameStateEvent.End(UltimateManhuntGameStateEvent.End.Reason.HUNTER_WIN, PlayerSpeedRunner.getRemainingSpeedRunners(), PlayerHunter.getHunters())); // No more speed runners. Hunters win!
                                }
                            }
                        }
                    });
                } else // Player is a hunter
                {
                    if (UMGame.areHuntersOnGracePeriod()) // Are hunters on grace period?
                    {
                        // On grace period. Cancel death and set health back
                        event.setCanceled(true);
                        deadPlayer.setHealth(deadPlayer.getMaxHealth());
                    }
                }
            });
        }
    }

    private static boolean isNearHunter(Player playerSpeedRunner)
    {
        for (Player playerHunter : PlayerHunter.getHunters()) // Loop through all hunters
        {
            if (UMGame.isNearHunter(playerSpeedRunner, playerHunter))
            {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        LivingEntity hurtEntity = event.getEntity();
        if (UMGame.State.isGameNotInProgress()) // Is the game not in progress?
        {
            if (hurtEntity.getType() == EntityType.ENDER_DRAGON) // Is the hurt entity an Ender Dragon?
            {
                event.setCanceled(true); // Game is not in progress and hurt entity was Ender Dragon. Cancel event
            }
        } else if (UMGame.State.isGameRunning())
        {
            if (hurtEntity instanceof Player player)
            {
                player.getCapability(PlayerHunterCapability.PLAYER_HUNTER).ifPresent(playerHunter ->
                {
                    if (playerHunter.isHunter() && UMGame.areHuntersOnGracePeriod())
                    {
                        event.setCanceled(true); // Hunters cannot be hurt during their grace period
                    }
                });

            }
        }

    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event)
    {
        LivingEntity living = event.getEntity();
        if (living instanceof Player attackedPlayer)
        {
            Entity attacker = event.getSource().getEntity();
            if (attacker == null)
            {
                return;
            }
            if (attacker.level().isClientSide)
            {
                return; // Ensure we are on the client
            }
            if (attacker instanceof Player attackingPlayer)
            {
                attackingPlayer.getCapability(PlayerHunterCapability.PLAYER_HUNTER).ifPresent(attackingHunter ->
                {
                    attackedPlayer.getCapability(PlayerHunterCapability.PLAYER_HUNTER).ifPresent(attackedHunter ->
                    {
                        if (attackingHunter.isHunter()) // Is attacker a hunter
                        {
                            if (attackedHunter.isHunter()) // Is attacked a hunter?
                            {
                                if (!UMGame.isFriendlyFire()) // Friendly fire?
                                {
                                    event.setCanceled(true); // Hunters cannot hurt each other
                                }
                            } else // attacked is not a hunter
                            {
                                attackedPlayer.getCapability(PlayerSpeedRunnerCapability.PLAYER_SPEED_RUNNER).ifPresent(attackedSpeedRunner ->
                                {
                                    if (attackedSpeedRunner.getWasLastKilledByHunter() && UMGame.isSpeedRunnerOnGracePeriod(attackedPlayer)) // Was the speed runner last killed by a hunter and are they on grace period?
                                    {
                                        long duration = (attackedSpeedRunner.getGracePeriodTimeStamp() - UMGame.getCurrentGameTime()) / 20;
                                        attackingPlayer.sendSystemMessage(Component.literal(ChatFormatting.BLUE + "Player is immune to hunters for " + ChatFormatting.YELLOW + duration + ChatFormatting.BLUE + " seconds"));
                                        event.setCanceled(true); // Hunter cannot attack speed runners on grace period
                                    }
                                });
                            }
                        } else // Attacker is not a hunter
                        {
                            if (!attackedHunter.isHunter()) // Is the target not a hunter?
                            {
                                if (!UMGame.isFriendlyFire()) // Friendly fire?
                                {
                                    event.setCanceled(true); // Speed runners cannot hurt each other
                                }
                            }
                        }
                    });
                });
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event)
    {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide)
        {
            return;
        }

        UMPackets.sendToPlayer(new GameStateS2CPacket(UMGame.getCurrentGameState()), player); // Let player know the current game state as soon as they join
        UMPackets.sendToPlayer(new HardcoreUpdateS2CPacket(UMGame.isHardcore()), player); // Let the player know whether hardcore is enabled
        UMPackets.sendToPlayer(new HunterGracePeriodDurationS2CPacket(UMGame.getHunterGracePeriod()), player); // Let the player know the hunter grace period
        UMPackets.sendToPlayer(new SpeedRunnerGracePeriodDurationS2CPacket(UMGame.getSpeedRunnerGracePeriod()), player); // Let the player know the speed runner grace period
        UMPackets.sendToPlayer(new SpeedRunnerMaxLifeChangeS2CPacket(PlayerSpeedRunner.getMaxLives()), player); // Let the player know how many lives speed runners can have
        UMPackets.sendToPlayer(new RemainingPlayerCountS2CPacket(PlayerSpeedRunner.getRemainingSpeedRunners().size(), PlayerHunter.getHunters().size()), player); // Let the player know how many remaining players on each team
        // Game Time packets and updates are sent every tick. It is redundant to send them at this moment

        if (!UMGame.isWindTorchEnabled())
        {
            player.getInventory().clearOrCountMatchingItems(itemStack -> itemStack.getItem() instanceof WindTorchItem, -1, player.inventoryMenu.getCraftSlots());
        }

        if (UMGame.State.hasGameBeenStarted()) // Check if the game has been started
        {
            if (!UMGame.containsLoggedPlayerUUID(player)) // Is the player not part of this iteration (did they join when a game has already started)?
            {
                // Not already part of this iteration
                UMGame.logPlayerUUID(player); // Log them
                player.getCapability(PlayerSpeedRunnerCapability.PLAYER_SPEED_RUNNER).ifPresent(playerSpeedRunner ->
                {
                    // Assign new player their lives (assume they are a speed runner by default)
                    playerSpeedRunner.setLives(PlayerSpeedRunner.getMaxLives());
                    playerSpeedRunner.setWasLastKilledByHunter(false);
                    UMPackets.sendToPlayer(new SpeedRunnerChangeS2CPacket(playerSpeedRunner), player);
                    UMPackets.sendToAllTrackingEntityAndSelf(new SpeedRunnerCapabilitySyncS2CPacket(player.getId(), playerSpeedRunner.toNBT()), player);
                });
                player.getCapability(PlayerHunterCapability.PLAYER_HUNTER).ifPresent(playerHunter ->
                {
                    // Newly-joined player is not to be a hunter.
                    playerHunter.setHunter(false);
                    playerHunter.setBuffed(false);
                    UMPackets.sendToPlayer(new HunterChangeS2CPacket(playerHunter), player);
                    UMPackets.sendToAllTrackingEntityAndSelf(new HunterCapabilitySyncS2CPacket(player.getId(), playerHunter.toNBT()), player);
                });
                if (UMGame.isWindTorchEnabled()) // Is the Wind Torch enabled?
                {
                    player.getInventory().add(new ItemStack(UMItems.WIND_TORCH.get())); // Grant the player a Wind Torch
                }
            }
            player.getCapability(PlayerHunterCapability.PLAYER_HUNTER).ifPresent(playerHunter ->
            {
                if (playerHunter.isHunter())
                {
                    if (playerHunter.isBuffed() && UMGame.State.isGameRunning())
                    {
                        player.getAttributes().addTransientAttributeModifiers(PlayerHunter.createHunterAttributes()); // Grant player hunter attributes if they are a buffed hunter
                    }
                    if (UMGame.getCurrentGameTime() < UMGame.getHunterGracePeriod())
                    {
                        player.getAbilities().mayfly = true;
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                    }
                }
            });
        }
        player.getCapability(PlayerSpeedRunnerCapability.PLAYER_SPEED_RUNNER).ifPresent(playerSpeedRunner -> UMPackets.sendToAllTrackingEntityAndSelf(new SpeedRunnerCapabilitySyncS2CPacket(player.getId(), playerSpeedRunner.toNBT()), player));
        player.getCapability(PlayerHunterCapability.PLAYER_HUNTER).ifPresent(playerHunter -> UMPackets.sendToAllTrackingEntityAndSelf(new HunterCapabilitySyncS2CPacket(player.getId(), playerHunter.toNBT()), player));
        player.getCapability(PlayerGameTimeCapability.PLAYER_GAME_TIME).ifPresent(playerGameTime -> UMPackets.sendToAllTrackingEntityAndSelf(new GameTimeCapabilitySyncS2CPacket(player.getId(), playerGameTime.toNBT()), player));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        Player player = event.getEntity();
        if (!player.level().isClientSide)
        {
            UMSoundEvents.stopFlatlineSound(player); // Stop heart flatline on respawn

            // Player has just respawned. Set their grace period time stamp if they were previously killed by a hunter
            player.getCapability(PlayerSpeedRunnerCapability.PLAYER_SPEED_RUNNER).ifPresent(playerSpeedRunner ->
            {
                if (playerSpeedRunner.getWasLastKilledByHunter()) // Was the speed runner last killed by a hunter?
                {
                    long timeStamp = UMGame.getCurrentGameTime() + UMGame.getSpeedRunnerGracePeriod();
                    playerSpeedRunner.setGracePeriodTimeStamp(timeStamp);
                    player.sendSystemMessage(Component.literal("Grace period ends at game time: " + timeStamp));

                } else // Player was not killed by a hunter previously
                {
                    playerSpeedRunner.setGracePeriodTimeStamp(0);
                }
                UMPackets.sendToPlayer(new SpeedRunnerChangeS2CPacket(playerSpeedRunner), player);
                UMPackets.sendToAllTrackingEntityAndSelf(new SpeedRunnerCapabilitySyncS2CPacket(player.getId(), playerSpeedRunner.toNBT()), player);
            });
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event)
    {
        final Entity targetEntity = event.getTarget();
        final Player player = event.getEntity();

        if (targetEntity instanceof Player playerTarget)
        {
            playerTarget.getCapability(PlayerSpeedRunnerCapability.PLAYER_SPEED_RUNNER).ifPresent(playerSpeedRunner ->
            {
                // Send player the data about the entity they are tracking
                UMPackets.sendToAllTrackingEntity(new SpeedRunnerCapabilitySyncS2CPacket(playerTarget.getId(), playerSpeedRunner.toNBT()), player);
            });
            playerTarget.getCapability(PlayerHunterCapability.PLAYER_HUNTER).ifPresent(playerHunter ->
            {
                // Send player the data about the entity they are tracking
                UMPackets.sendToAllTrackingEntity(new HunterCapabilitySyncS2CPacket(playerTarget.getId(), playerHunter.toNBT()), player);
            });
            playerTarget.getCapability(PlayerGameTimeCapability.PLAYER_GAME_TIME).ifPresent(playerGameTime ->
            {
                // Send player the data about the current game time
                UMPackets.sendToAllTrackingEntity(new GameTimeCapabilitySyncS2CPacket(playerTarget.getId(), playerGameTime.toNBT()), player);
            });

            // TODO: When capability data changes, you need to send the new data to ALL tracking players and the player themselves.
            // May need to find where S2C packets are sent for the client values
        }
    }

    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event)
    {
        final Entity targetEntity = event.getEntity();
        final Player player = event.getEntity();

        if (targetEntity instanceof Player playerTarget)
        {
            playerTarget.getCapability(PlayerSpeedRunnerCapability.PLAYER_SPEED_RUNNER).ifPresent(playerSpeedRunner ->
            {
                // Send player the data about the entity they are tracking
                UMPackets.sendToAllTrackingEntity(new SpeedRunnerCapabilitySyncS2CPacket(playerTarget.getId(), playerSpeedRunner.toNBT()), player);
            });
            playerTarget.getCapability(PlayerHunterCapability.PLAYER_HUNTER).ifPresent(playerHunter ->
            {
                // Send player the data about the entity they are tracking
                UMPackets.sendToAllTrackingEntity(new HunterCapabilitySyncS2CPacket(playerTarget.getId(), playerHunter.toNBT()), player);
            });
            playerTarget.getCapability(PlayerGameTimeCapability.PLAYER_GAME_TIME).ifPresent(playerGameTime ->
            {
                // Send player the data about the current game time
                UMPackets.sendToAllTrackingEntity(new GameTimeCapabilitySyncS2CPacket(playerTarget.getId(), playerGameTime.toNBT()), player);
            });
        }
    }

//    @SubscribeEvent
//    public static void onGameStart(ReverseManhuntGameStateEvent.Start event)
//    {
//
//    }
//
//    @SubscribeEvent
//    public static void onGamePause(ReverseManhuntGameStateEvent.Pause event)
//    {
//
//    }
//
//    @SubscribeEvent
//    public static void onGameResume(ReverseManhuntGameStateEvent.Resume event)
//    {
//
//    }
//
//    @SubscribeEvent
//    public static void onGameEnd(ReverseManhuntGameStateEvent.End event)
//    {
//
//    }



}
