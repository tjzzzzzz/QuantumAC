package fi.tj88888.quantumAC.database;


import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.log.ViolationLog;
import org.bson.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;

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

    public void closeConnection() {
        if (mongoClient != null) {
            mongoClient.close();
            plugin.getLogger().info("Closed MongoDB connection.");
        }
    }

    // Player data operations
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

    // Violation log operations
    public CompletableFuture<Void> logViolation(ViolationLog violationLog) {
        return CompletableFuture.runAsync(() -> {
            if (mongoClient == null) return;

            try {
                Document document = new Document("uuid", violationLog.getPlayerUuid().toString())
                        .append("playerName", violationLog.getPlayerName())
                        .append("checkName", violationLog.getCheckName())
                        .append("checkType", violationLog.getCheckType())
                        .append("vl", violationLog.getViolationLevel())
                        .append("details", violationLog.getDetails())
                        .append("timestamp", violationLog.getTimestamp())
                        .append("world", violationLog.getWorld())
                        .append("x", violationLog.getX())
                        .append("y", violationLog.getY())
                        .append("z", violationLog.getZ())
                        .append("ping", violationLog.getPing())
                        .append("tps", violationLog.getTps());

                violationCollection.insertOne(document);
            } catch (Exception e) {
                plugin.getLogger().severe("Error logging violation: " + e.getMessage());
                e.printStackTrace();
            }
        }, plugin.getPacketExecutor());
    }

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
                                    UUID.fromString(doc.getString("uuid")),
                                    doc.getString("playerName"),
                                    doc.getString("checkName"),
                                    doc.getString("checkType"),
                                    doc.getDouble("vl"),
                                    doc.getString("details"),
                                    doc.getLong("timestamp"),
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

    // Check if collections exist
    public boolean isConnected() {
        return mongoClient != null;
    }

    public MongoDatabase getDatabase() {
        return database;
    }
}