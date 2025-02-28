package fi.tj88888.quantumAC.database;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.log.ViolationLog;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;

/**
 * Handles MongoDB connections and operations
 * Updated to work with new ViolationLog structure
 */
public class MongoManager {

    private final QuantumAC plugin;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> playerCollection;
    private MongoCollection<Document> violationCollection;

    public MongoManager(QuantumAC plugin) {
        this.plugin = plugin;
        connect();
    }

    /**
     * Connects to MongoDB
     */
    private void connect() {
        try {
            String uri = plugin.getConfigManager().getMongoUri();
            String dbName = plugin.getConfigManager().getMongoDatabaseName();

            if (uri.isEmpty()) {
                plugin.getLogger().warning("MongoDB URI is not set. Database features will be disabled.");
                return;
            }

            mongoClient = MongoClients.create(uri);
            database = mongoClient.getDatabase(dbName);

            // Initialize collections
            playerCollection = database.getCollection("players");
            violationCollection = database.getCollection("violations");

            plugin.getLogger().info("Successfully connected to MongoDB!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to MongoDB: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Closes the MongoDB connection
     */
    public void closeConnection() {
        if (mongoClient != null) {
            mongoClient.close();
            plugin.getLogger().info("Closed MongoDB connection.");
        }
    }

    /**
     * Saves player data to MongoDB
     *
     * @param playerData Player data to save
     * @return CompletableFuture for async operation
     */
    public CompletableFuture<Void> savePlayerData(PlayerData playerData) {
        return CompletableFuture.runAsync(() -> {
            if (mongoClient == null) return;

            try {
                UUID uuid = playerData.getUuid();
                Document filter = new Document("uuid", uuid.toString());

                Document document = new Document("uuid", uuid.toString())
                        .append("name", playerData.getPlayerName())
                        .append("ping", playerData.getAveragePing())
                        .append("joinTime", playerData.getJoinTime())
                        .append("violations", playerData.getTotalViolations())
                        .append("lastSeen", System.currentTimeMillis());

                // Additional data can be added here

                playerCollection.replaceOne(filter, document, new com.mongodb.client.model.ReplaceOptions().upsert(true));
            } catch (Exception e) {
                plugin.getLogger().severe("Error saving player data: " + e.getMessage());
                e.printStackTrace();
            }
        }, plugin.getPacketExecutor());
    }

    /**
     * Loads player data from MongoDB
     *
     * @param uuid Player UUID
     * @return CompletableFuture with PlayerData
     */
    public CompletableFuture<PlayerData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (mongoClient == null) return null;

            try {
                Document filter = new Document("uuid", uuid.toString());
                Document document = playerCollection.find(filter).first();

                if (document != null) {
                    PlayerData playerData = new PlayerData(uuid, document.getString("name"));
                    playerData.setAveragePing(document.getInteger("ping", 0));
                    playerData.setJoinTime(document.getLong("joinTime"));
                    playerData.setTotalViolations(document.getInteger("violations", 0));

                    // Load additional data here

                    return playerData;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading player data: " + e.getMessage());
                e.printStackTrace();
            }

            return null;
        }, plugin.getPacketExecutor());
    }

    /**
     * Logs a violation to MongoDB
     * Updated to work with new ViolationLog structure
     *
     * @param violationLog Violation log to save
     * @return CompletableFuture for async operation
     */
    public CompletableFuture<Void> logViolation(ViolationLog violationLog) {
        return CompletableFuture.runAsync(() -> {
            if (mongoClient == null) return;

            try {
                // Try to find UUID from player name
                UUID playerUuid = null;
                Player player = Bukkit.getPlayerExact(violationLog.getPlayerName());
                if (player != null) {
                    playerUuid = player.getUniqueId();
                }

                Document document = new Document()
                        .append("playerName", violationLog.getPlayerName())
                        .append("checkName", violationLog.getCheckName())
                        .append("checkType", violationLog.getCheckType())
                        .append("vl", violationLog.getVl())
                        .append("details", violationLog.getDetails())
                        .append("timestamp", violationLog.getTimestamp())
                        .append("world", violationLog.getWorld())
                        .append("x", violationLog.getX())
                        .append("y", violationLog.getY())
                        .append("z", violationLog.getZ())
                        .append("ping", violationLog.getPing())
                        .append("tps", violationLog.getTps());

                // Add UUID if available
                if (playerUuid != null) {
                    document.append("uuid", playerUuid.toString());
                }

                violationCollection.insertOne(document);
            } catch (Exception e) {
                plugin.getLogger().severe("Error logging violation: " + e.getMessage());
                e.printStackTrace();
            }
        }, plugin.getPacketExecutor());
    }

    /**
     * Gets player violations from MongoDB
     * Updated to work with new ViolationLog structure
     *
     * @param uuid Player UUID
     * @param limit Maximum number of violations to retrieve
     * @return CompletableFuture with list of ViolationLog
     */
    public CompletableFuture<List<ViolationLog>> getPlayerViolations(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<ViolationLog> logs = new ArrayList<>();
            if (mongoClient == null) return logs;

            try {
                Document filter = new Document("uuid", uuid.toString());
                violationCollection.find(filter)
                        .sort(new Document("timestamp", -1))
                        .limit(limit)
                        .forEach(doc -> {
                            ViolationLog log = new ViolationLog(
                                    doc.getString("playerName"),
                                    doc.getString("checkName"),
                                    doc.getString("checkType"),
                                    doc.getDouble("vl"),
                                    doc.getString("details"),
                                    doc.getString("world"),
                                    doc.getDouble("x"),
                                    doc.getDouble("y"),
                                    doc.getDouble("z"),
                                    doc.getInteger("ping"),
                                    doc.getDouble("tps")
                            );
                            logs.add(log);
                        });
            } catch (Exception e) {
                plugin.getLogger().severe("Error retrieving player violations: " + e.getMessage());
                e.printStackTrace();
            }

            return logs;
        }, plugin.getPacketExecutor());
    }

    /**
     * Gets all violations for a player by name
     *
     * @param playerName Player name
     * @param limit Maximum number of violations to retrieve
     * @return CompletableFuture with list of ViolationLog
     */
    public CompletableFuture<List<ViolationLog>> getPlayerViolationsByName(String playerName, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<ViolationLog> logs = new ArrayList<>();
            if (mongoClient == null) return logs;

            try {
                Document filter = new Document("playerName", playerName);
                violationCollection.find(filter)
                        .sort(new Document("timestamp", -1))
                        .limit(limit)
                        .forEach(doc -> {
                            ViolationLog log = new ViolationLog(
                                    doc.getString("playerName"),
                                    doc.getString("checkName"),
                                    doc.getString("checkType"),
                                    doc.getDouble("vl"),
                                    doc.getString("details"),
                                    doc.getString("world"),
                                    doc.getDouble("x"),
                                    doc.getDouble("y"),
                                    doc.getDouble("z"),
                                    doc.getInteger("ping"),
                                    doc.getDouble("tps")
                            );
                            logs.add(log);
                        });
            } catch (Exception e) {
                plugin.getLogger().severe("Error retrieving player violations by name: " + e.getMessage());
                e.printStackTrace();
            }

            return logs;
        }, plugin.getPacketExecutor());
    }

    /**
     * Gets recent violations across all players
     *
     * @param limit Maximum number of violations to retrieve
     * @return CompletableFuture with list of ViolationLog
     */
    public CompletableFuture<List<ViolationLog>> getRecentViolations(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<ViolationLog> logs = new ArrayList<>();
            if (mongoClient == null) return logs;

            try {
                violationCollection.find()
                        .sort(new Document("timestamp", -1))
                        .limit(limit)
                        .forEach(doc -> {
                            ViolationLog log = new ViolationLog(
                                    doc.getString("playerName"),
                                    doc.getString("checkName"),
                                    doc.getString("checkType"),
                                    doc.getDouble("vl"),
                                    doc.getString("details"),
                                    doc.getString("world"),
                                    doc.getDouble("x"),
                                    doc.getDouble("y"),
                                    doc.getDouble("z"),
                                    doc.getInteger("ping"),
                                    doc.getDouble("tps")
                            );
                            logs.add(log);
                        });
            } catch (Exception e) {
                plugin.getLogger().severe("Error retrieving recent violations: " + e.getMessage());
                e.printStackTrace();
            }

            return logs;
        }, plugin.getPacketExecutor());
    }

    /**
     * Check if connected to MongoDB
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return mongoClient != null;
    }

    /**
     * Get the MongoDB database
     *
     * @return MongoDB database
     */
    public MongoDatabase getDatabase() {
        return database;
    }
}