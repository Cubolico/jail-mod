package com.example.jailmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.io.*;
import java.util.*;

public class JailMod implements ModInitializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/jailmod/config.json");
    private static final File LANGUAGE_FILE = new File("config/jailmod/language.txt");
    private static final File JAIL_DATA_FILE = new File("config/jailmod/jail_data.json"); // File to save jail status
    private static Config config;
    private static Map<String, String> languageStrings = new HashMap<>();
    private static Map<UUID, JailData> jailedPlayers = new HashMap<>(); // Saved jail status of players

    private static MinecraftServer serverInstance;

    // Configuration class
    public static class Config {
        public boolean use_previous_position = true;
        public Position release_position = new Position(100, 65, 100);
        public Position jail_position = new Position(0, 60, 0);

        public static class Position {
            public int x;
            public int y;
            public int z;

            public Position(int x, int y, int z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
        }
    }

    // Class to store jailed players with release time in ticks
    private static class JailData {
        public UUID playerUUID;
        public BlockPos originalSpawnPos;
        public RegistryKey<World> originalSpawnDimension;
        public boolean hadSpawnPoint;
        public String reason;
        public int remainingTicks; // Remaining time in ticks (1 second = 20 ticks)

        public JailData(UUID playerUUID, BlockPos originalSpawnPos, RegistryKey<World> originalSpawnDimension, boolean hadSpawnPoint, String reason, int remainingTicks) {
            this.playerUUID = playerUUID;
            this.originalSpawnPos = originalSpawnPos;
            this.originalSpawnDimension = originalSpawnDimension;
            this.hadSpawnPoint = hadSpawnPoint;
            this.reason = reason;
            this.remainingTicks = remainingTicks;
        }
    }

    @Override
    public void onInitialize() {
        // Print a green bold message when the mod is loaded
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
            Text message = Text.literal("[Jail-Mod] Loaded")
                    .styled(style -> style.withColor(0x00FF00).withBold(true));
            server.getCommandSource().sendFeedback(() -> message, false);
        });
        // Load or create the config, language, and jail status files
        loadConfig();
        loadLanguage();
        loadJailData();
    
        // Handle ticks to check for player releases
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<UUID> playersToRelease = new ArrayList<>();
            for (Map.Entry<UUID, JailData> entry : jailedPlayers.entrySet()) {
                UUID playerUUID = entry.getKey();
                JailData jailData = entry.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUUID);
                if (player != null) {
                    jailData.remainingTicks--;
                    if (jailData.remainingTicks <= 0) {
                        playersToRelease.add(playerUUID);
                    }
                }
            }
            for (UUID playerUUID : playersToRelease) {
                releasePlayer(playerUUID);
            }
        });
    
        // Block interactions if the player is in jail
        registerInteractionListeners();
    
        // Handle player login to restore jail status
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID playerUUID = player.getUuid();
            if (jailedPlayers.containsKey(playerUUID)) {
                JailData jailData = jailedPlayers.get(playerUUID);
                if (jailData.remainingTicks > 0) {
                    jailPlayer(player, jailData);
                } else {
                    releasePlayer(playerUUID);
                }
            }
        });
    
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Command /jail
            dispatcher.register(CommandManager.literal("jail")
                // Subcommand /jail imprison <player> <time> <reason> (Admin only)
                .then(CommandManager.literal("imprison")
                    .requires(source -> source.hasPermissionLevel(2)) // Only admins
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .then(CommandManager.argument("time", IntegerArgumentType.integer(1))
                            .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String playerName = StringArgumentType.getString(context, "player");
                                    int timeInSeconds = IntegerArgumentType.getInteger(context, "time");
                                    String reason = StringArgumentType.getString(context, "reason");
                                    ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
        
                                    if (player != null) {
                                        jailPlayer(player, timeInSeconds, reason);
                                        context.getSource().sendFeedback(() -> Text.of("Player " + playerName + " jailed for " + timeInSeconds + " seconds."), true);
                                    } else {
                                        context.getSource().sendError(Text.of("Player not found!"));
                                    }
                                    return 1;
                                })
                            )
                        )
                    )
                )
                // Subcommand /jail reload (Admin only)
                .then(CommandManager.literal("reload")
                    .requires(source -> source.hasPermissionLevel(2)) // Only admins
                    .executes(context -> {
                        loadConfig(); // Reload the config
                        loadLanguage(); // Reload language strings
                        context.getSource().sendFeedback(() -> Text.of("Configuration and language strings successfully reloaded!"), true);
                        return 1;
                    })
                )
                // Subcommand /jail set x y z (Admin only)
                .then(CommandManager.literal("set")
                    .requires(source -> source.hasPermissionLevel(2)) // Only admins
                    .then(CommandManager.argument("x", IntegerArgumentType.integer())
                        .then(CommandManager.argument("y", IntegerArgumentType.integer())
                            .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                .executes(context -> {
                                    int x = IntegerArgumentType.getInteger(context, "x");
                                    int y = IntegerArgumentType.getInteger(context, "y");
                                    int z = IntegerArgumentType.getInteger(context, "z");
        
                                    // Update jail position in the config
                                    config.jail_position = new Config.Position(x, y, z);
                                    saveConfig();
        
                                    // Send feedback to the player
                                    context.getSource().sendFeedback(() -> Text.of("Jail position set to (" + x + ", " + y + ", " + z + ")"), true);
                                    return 1;
                                })
                            )
                        )
                    )
                )
                // Subcommand /jail info (Accessible to everyone)
                .then(CommandManager.literal("info")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player != null && isPlayerInJail(player)) {
                            JailData jailData = jailedPlayers.get(player.getUuid());
                            int remainingSeconds = jailData.remainingTicks / 20;
                            String reason = jailData.reason;
                            String message = languageStrings.get("jail_info_message")
                                .replace("{time}", String.valueOf(remainingSeconds))
                                .replace("{reason}", reason);
                            player.sendMessage(Text.of(message), false);
                            return 1;
                        } else {
                            String notInJailMessage = languageStrings.get("not_in_jail_message");
                            context.getSource().sendFeedback(() -> Text.of(notInJailMessage), false);
                            return 0;
                        }
                    })
                )
            );
        
            // Command /unjail (Admin only)
            dispatcher.register(CommandManager.literal("unjail")
                .requires(source -> source.hasPermissionLevel(2)) // Only admins
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String playerName = StringArgumentType.getString(context, "player");
                        ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
                        if (player != null) {
                            unjailPlayer(player, true);
                            context.getSource().sendFeedback(() -> Text.of("Player " + playerName + " has been released from jail."), true);
                        } else {
                            context.getSource().sendError(Text.of("Player not found!"));
                        }
                        return 1;
                    })
                )
            );
        });
    
        // Save jail status when the server is stopped
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> saveJailData());
    }
    

    private void registerInteractionListeners() {
        // Block interactions with blocks
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && isPlayerInJail(serverPlayer)) {
                serverPlayer.sendMessage(Text.of(languageStrings.get("block_interaction_denied")), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Block interactions with entities
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && isPlayerInJail(serverPlayer)) {
                serverPlayer.sendMessage(Text.of(languageStrings.get("entity_interaction_denied")), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Block use of lava and water buckets
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && isPlayerInJail(serverPlayer)) {
                ItemStack itemStack = player.getStackInHand(hand);

                // Check if the item is a lava or water bucket
                if (itemStack.isOf(Items.LAVA_BUCKET) || itemStack.isOf(Items.WATER_BUCKET)) {
                    serverPlayer.sendMessage(Text.of(languageStrings.get("bucket_use_denied")), true);
                    return TypedActionResult.fail(itemStack); // Block bucket usage
                }

                serverPlayer.sendMessage(Text.of(languageStrings.get("item_use_denied")), true);
                return TypedActionResult.fail(itemStack);
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });

        // Block breaking of blocks
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && isPlayerInJail(serverPlayer)) {
                serverPlayer.sendMessage(Text.of(languageStrings.get("block_break_denied")), true);
                return false; // Prevents breaking blocks
            }
            return true;
        });
    }

    private boolean isPlayerInJail(ServerPlayerEntity player) {
        return jailedPlayers.containsKey(player.getUuid());
    }

    private void jailPlayer(ServerPlayerEntity player, int timeInSeconds, String reason) {
        // Save the player's original spawn position
        BlockPos originalSpawnPos = player.getSpawnPointPosition();
        RegistryKey<World> originalSpawnDimension = player.getWorld().getRegistryKey();
        boolean hadSpawnPoint = originalSpawnPos != null;

        // Calculate remaining time in ticks
        int remainingTicks = timeInSeconds * 20; // Convert seconds to ticks

        // Save player data
        JailData jailData = new JailData(player.getUuid(), originalSpawnPos, originalSpawnDimension, hadSpawnPoint, reason, remainingTicks);
        jailedPlayers.put(player.getUuid(), jailData);

        // Teleport the player to jail
        jailPlayer(player, jailData);

        // Save data
        saveJailData();
    }

    private void jailPlayer(ServerPlayerEntity player, JailData jailData) {
        // Get the player's current world
        ServerWorld world = player.getServerWorld();

        // Teleport the player to jail
        BlockPos jailPos = new BlockPos(config.jail_position.x, config.jail_position.y, config.jail_position.z);
        player.teleport(world, jailPos.getX() + 0.5, jailPos.getY(), jailPos.getZ() + 0.5, player.getYaw(), player.getPitch());

        // Set spawn point in jail
        player.setSpawnPoint(world.getRegistryKey(), jailPos, 0.0f, true, false);

        // Send a private message to the player
        String messageToPlayer = languageStrings.get("jail_player")
                .replace("{time}", String.valueOf(jailData.remainingTicks / 20))
                .replace("{reason}", jailData.reason);
        player.sendMessage(Text.of(messageToPlayer), false);

        // Broadcast a message to the server chat
        String jailMessage = languageStrings.get("jail_broadcast")
                .replace("{player}", player.getName().getString())
                .replace("{time}", String.valueOf(jailData.remainingTicks / 20))
                .replace("{reason}", jailData.reason);
        serverInstance.getPlayerManager().broadcast(Text.of(jailMessage), false);
    }

    private void unjailPlayer(ServerPlayerEntity player, boolean isManual) {
        JailData jailData = jailedPlayers.remove(player.getUuid());

        if (jailData != null) {
            // Restore the original spawn point
            if (jailData.hadSpawnPoint) {
                player.setSpawnPoint(jailData.originalSpawnDimension, jailData.originalSpawnPos, 0.0f, true, false);
            } else {
                player.setSpawnPoint(null, null, 0.0f, false, false); // Remove spawn point
            }

            // Get the player's current world
            ServerWorld world = player.getServerWorld();

            // Teleport the player out of jail (release position)
            if (config.use_previous_position) {
                player.teleport(world, jailData.originalSpawnPos.getX(), jailData.originalSpawnPos.getY(), jailData.originalSpawnPos.getZ(), player.getYaw(), player.getPitch());
            } else {
                BlockPos releasePos = new BlockPos(config.release_position.x, config.release_position.y, config.release_position.z);
                player.teleport(world, releasePos.getX() + 0.5, releasePos.getY(), releasePos.getZ() + 0.5, player.getYaw(), player.getPitch());
            }

            // Send custom messages
            if (isManual) {
                String messageToPlayer = languageStrings.get("unjail_player_manual");
                player.sendMessage(Text.of(messageToPlayer), false);

                String broadcastMessage = languageStrings.get("unjail_broadcast_manual")
                        .replace("{player}", player.getName().getString());
                serverInstance.getPlayerManager().broadcast(Text.of(broadcastMessage), false);
            } else {
                String messageToPlayer = languageStrings.get("unjail_player_auto");
                player.sendMessage(Text.of(messageToPlayer), false);

                String broadcastMessage = languageStrings.get("unjail_broadcast_auto")
                        .replace("{player}", player.getName().getString());
                serverInstance.getPlayerManager().broadcast(Text.of(broadcastMessage), false);
            }

            // Save updated data
            saveJailData();
        }
    }

    private void releasePlayer(UUID playerUUID) {
        ServerPlayerEntity player = serverInstance.getPlayerManager().getPlayer(playerUUID);
        if (player != null) {
            unjailPlayer(player, false); // We pass false because it's an automatic release
        }
    }

    // Load jail status from file
    private void loadJailData() {
        if (JAIL_DATA_FILE.exists()) {
            try (FileReader reader = new FileReader(JAIL_DATA_FILE)) {
                JailData[] loadedData = GSON.fromJson(reader, JailData[].class);
                for (JailData data : loadedData) {
                    jailedPlayers.put(data.playerUUID, data); // Use the player's UUID directly
                }
                System.out.println("Jail status loaded.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Save jail status to file
    private void saveJailData() {
        try (FileWriter writer = new FileWriter(JAIL_DATA_FILE)) {
            GSON.toJson(jailedPlayers.values().toArray(new JailData[0]), writer);
            System.out.println("Jail status saved.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        // Create the config directory if it doesn't exist
        if (!CONFIG_FILE.getParentFile().exists()) {
            CONFIG_FILE.getParentFile().mkdirs();
        }

        // If the config file exists, load it
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, Config.class);
                System.out.println("Configuration loaded: " + CONFIG_FILE.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // If it doesn't exist, create the file with default values
            config = new Config();
            saveConfig();
            System.out.println("Default config file created: " + CONFIG_FILE.getAbsolutePath());
        }
    }

    private void loadLanguage() {
        // Create the config directory if it doesn't exist
        if (!LANGUAGE_FILE.getParentFile().exists()) {
            LANGUAGE_FILE.getParentFile().mkdirs();
        }

        // If the language file exists, load it
        if (LANGUAGE_FILE.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(LANGUAGE_FILE))) {
                languageStrings.clear();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        languageStrings.put(parts[0].trim(), parts[1].trim());
                    }
                }
                System.out.println("Language file loaded: " + LANGUAGE_FILE.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // If it doesn't exist, create the file with default values
            createDefaultLanguageFile();
            System.out.println("Default language file created: " + LANGUAGE_FILE.getAbsolutePath());
        }
    }

    private void createDefaultLanguageFile() {
        languageStrings.put("jail_player", "You have been jailed for {time} seconds! Reason: {reason}");
        languageStrings.put("jail_broadcast", "{player} has been jailed for {time} seconds. Reason: {reason}");
        languageStrings.put("unjail_player_manual", "You have been manually released from jail!");
        languageStrings.put("unjail_broadcast_manual", "{player} has been manually released from jail!");
        languageStrings.put("unjail_player_auto", "You have been released after serving your sentence.");
        languageStrings.put("unjail_broadcast_auto", "{player} has been released after serving their sentence.");
        languageStrings.put("block_interaction_denied", "You cannot interact with blocks while in jail!");
        languageStrings.put("entity_interaction_denied", "You cannot interact with entities while in jail!");
        languageStrings.put("bucket_use_denied", "You cannot use lava or water buckets while in jail!");
        languageStrings.put("item_use_denied", "You cannot use items while in jail!");
        languageStrings.put("block_break_denied", "You cannot break blocks while in jail!");

        // New strings for /jail info command
        languageStrings.put("jail_info_message", "You are in jail for another {time} seconds. Reason: {reason}.");
        languageStrings.put("not_in_jail_message", "You are not in jail!");

        saveLanguage();
    }

    private void saveLanguage() {
        try (FileWriter writer = new FileWriter(LANGUAGE_FILE)) {
            for (Map.Entry<String, String> entry : languageStrings.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
            }
            System.out.println("Language file saved: " + LANGUAGE_FILE.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
            System.out.println("Configuration saved: " + CONFIG_FILE.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
