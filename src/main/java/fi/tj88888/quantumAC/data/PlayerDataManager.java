package fi.tj88888.quantumAC.data;

import fi.tj88888.quantumAC.QuantumAC;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final QuantumAC plugin;
    private final Map<UUID, PlayerData> playerDataMap;

    public PlayerDataManager(QuantumAC plugin) {
        this.plugin = plugin;
        this.playerDataMap = new ConcurrentHashMap<>();
    }

    public void createPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData playerData = new PlayerData(uuid, player.getName());
        playerData.setJoinTime(System.currentTimeMillis());

        playerDataMap.put(uuid, playerData);

        // Load previous data from MongoDB if available
        CompletableFuture<PlayerData> future = plugin.getMongoManager().loadPlayerData(uuid);
        future.thenAccept(loadedData -> {
            if (loadedData != null) {
                playerData.setTotalViolations(loadedData.getTotalViolations());
                // Other historical data can be loaded here
            }
        });
    }

    public void removePlayerData(UUID uuid) {
        PlayerData data = playerDataMap.get(uuid);
        if (data != null) {
            // Save data to MongoDB before removing
            plugin.getMongoManager().savePlayerData(data);
            playerDataMap.remove(uuid);
        }
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    public PlayerData getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId());
    }

    public void saveAllPlayerData() {
        playerDataMap.forEach((uuid, data) -> {
            plugin.getMongoManager().savePlayerData(data);
        });
    }

    public boolean hasData(UUID uuid) {
        return playerDataMap.containsKey(uuid);
    }

    public int getActivePlayerCount() {
        return playerDataMap.size();
    }
}