package fi.tj88888.quantumAC.data;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.util.MovementData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PlayerDataManager {

    private final QuantumAC plugin;
    private final Map<UUID, PlayerData> playerDataMap;

    // Performance optimization
    private long lastDataCleanup = 0;
    private static final long DATA_CLEANUP_INTERVAL = 300000; // 5 minutes
    private static final long PERIODIC_SAVE_INTERVAL = 900000; // 15 minutes

    public PlayerDataManager(QuantumAC plugin) {
        this.plugin = plugin;
        this.playerDataMap = new ConcurrentHashMap<>();

        // Schedule periodic data saves to prevent loss on crashes
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::periodicDataSave, 20 * 60, 20 * 60); // Run every minute
    }

    /**
     * Creates player data for a player
     *
     * @param player The player to create data for
     */
    public void createPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData playerData = new PlayerData(uuid, player.getName());
        playerData.setJoinTime(System.currentTimeMillis());

        // Always ensure the movementData field is initialized
        playerData.getMovementData(); // This should always return a valid instance

        playerDataMap.put(uuid, playerData);

        // Load previous data from MongoDB if available
        CompletableFuture<PlayerData> future = plugin.getMongoManager().loadPlayerData(uuid);
        future.thenAccept(loadedData -> {
            if (loadedData != null) {
                playerData.setTotalViolations(loadedData.getTotalViolations());
                // Other historical data can be loaded here
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load data for player: " + player.getName(), ex);
            return null;
        });

        plugin.getLogger().info("PlayerData created and initialized for: " + player.getName());
    }

    /**
     * Removes player data and saves it to the database
     *
     * @param uuid UUID of the player to remove data for
     */
    public void removePlayerData(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            // Save data to MongoDB before removing
            plugin.getMongoManager().savePlayerData(data);
            playerDataMap.remove(uuid);
        }
    }

    /**
     * Gets player data for a UUID
     *
     * @param uuid UUID to get data for
     * @return PlayerData object or null if not found
     */
    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    /**
     * Gets player data for a Player
     *
     * @param player Player to get data for
     * @return PlayerData object or null if not found
     */
    public PlayerData getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId());
    }

    /**
     * Saves all player data to the database
     * Should be called on server shutdown
     */
    public void saveAllPlayerData() {
        playerDataMap.forEach((uuid, data) -> {
            plugin.getMongoManager().savePlayerData(data);
        });
    }

    /**
     * Checks if player data exists for a UUID
     *
     * @param uuid UUID to check
     * @return true if data exists
     */
    public boolean hasData(UUID uuid) {
        return playerDataMap.containsKey(uuid);
    }

    /**
     * Gets the number of active players being tracked
     *
     * @return Count of active players
     */
    public int getActivePlayerCount() {
        return playerDataMap.size();
    }

    /**
     * Updates cached data for all players
     * This should be called from the main server thread
     */
    public void updatePlayerCachedData() {
        long now = System.currentTimeMillis();

        playerDataMap.values().forEach(data -> {
            try {
                // Update entity counts (important for performance-sensitive checks)
                data.updateNearbyEntityCount();

                // Decrease violation levels gradually over time
                data.decreaseViolationLevels();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error updating cached data for player: " + data.getPlayerName(), e);
            }
        });

        // Periodically clean up stale data
        if (now - lastDataCleanup > DATA_CLEANUP_INTERVAL) {
            cleanupStaleData();
            lastDataCleanup = now;
        }
    }

    /**
     * Periodically saves player data that has changed
     * Run asynchronously to avoid impacting server performance
     */
    private void periodicDataSave() {
        long now = System.currentTimeMillis();

        // Save any data that needs saving based on changes or interval
        playerDataMap.values().forEach(data -> {
            try {
                if (data.shouldSaveData()) {
                    plugin.getMongoManager().savePlayerData(data);
                    data.markDataSaved();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error saving data for player: " + data.getPlayerName(), e);
            }
        });
    }

    /**
     * Cleans up stale player data for players who are offline
     */
    private void cleanupStaleData() {
        playerDataMap.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            Player player = Bukkit.getPlayer(uuid);

            // If player is offline, save their data and remove from cache
            if (player == null || !player.isOnline()) {
                PlayerData data = entry.getValue();
                plugin.getMongoManager().savePlayerData(data);
                plugin.getLogger().info("Cleaned up stale data for: " + data.getPlayerName());
                return true;
            }
            return false;
        });
    }

    /**
     * Updates movement data for a player from a packet
     * This is a high-performance method that should be called from packet handling
     *
     * @param uuid Player UUID
     * @param x X position
     * @param y Y position
     * @param z Z position
     * @param yaw Yaw rotation
     * @param pitch Pitch rotation
     * @param onGround Ground state
     */
    public void updatePlayerMovement(UUID uuid, double x, double y, double z,
                                     float yaw, float pitch, boolean onGround) {
        PlayerData data = playerDataMap.get(uuid);
        if (data == null) return;

        try {
            // Back up current movement data before updating
            data.prepareMovementDataUpdate();

            // Get movement data and update it
            MovementData movementData = data.getMovementData();
            movementData.updatePosition(x, y, z);
            movementData.updateRotation(yaw, pitch);
            movementData.updateGroundState(onGround);

            // Additional block state will be updated in a separate call
            // as it requires more expensive calculations
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Error updating movement data for player: " + data.getPlayerName(), e);
        }
    }

    /**
     * Updates block state for a player's movement data
     * This is more expensive so it's split from the main movement update
     *
     * @param uuid Player UUID
     * @param insideBlock Whether player is inside a block
     * @param onIce Whether player is on ice
     * @param onSlime Whether player is on slime
     * @param inLiquid Whether player is in liquid
     * @param onStairs Whether player is on stairs
     * @param onSlab Whether player is on a slab
     */
    public void updatePlayerBlockState(UUID uuid, boolean insideBlock, boolean onIce, boolean onSlime,
                                       boolean inLiquid, boolean onStairs, boolean onSlab) {
        PlayerData data = playerDataMap.get(uuid);
        if (data == null) return;

        try {
            // Get movement data and update block state
            MovementData movementData = data.getMovementData();
            movementData.updateBlockState(insideBlock, onIce, onSlime, inLiquid, onStairs, onSlab);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Error updating block state for player: " + data.getPlayerName(), e);
        }
    }
}