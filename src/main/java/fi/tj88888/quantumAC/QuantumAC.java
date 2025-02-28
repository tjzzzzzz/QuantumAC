package fi.tj88888.quantumAC;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.api.APIManager;
import fi.tj88888.quantumAC.check.CheckManager;
import fi.tj88888.quantumAC.config.ConfigManager;
import fi.tj88888.quantumAC.data.PlayerDataManager;
import fi.tj88888.quantumAC.database.MongoManager;
import fi.tj88888.quantumAC.listener.ConnectionListener;
import fi.tj88888.quantumAC.listener.PacketListener;
import fi.tj88888.quantumAC.log.LogManager;
import fi.tj88888.quantumAC.alert.AlertManager;
import fi.tj88888.quantumAC.util.UpdateChecker;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class QuantumAC extends JavaPlugin {

    private static QuantumAC instance;
    private ProtocolManager protocolManager;
    private MongoManager mongoManager;
    private PlayerDataManager playerDataManager;
    private CheckManager checkManager;
    private LogManager logManager;
    private AlertManager alertManager;
    private ConfigManager configManager;
    private APIManager apiManager;
    private ExecutorService packetExecutor;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialize executors for async processing
        this.packetExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );

        // Load configuration first
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfig();

        // Initialize ProtocolLib
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        // Initialize other managers
        this.mongoManager = new MongoManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.checkManager = new CheckManager(this);
        this.logManager = new LogManager(this);
        this.alertManager = new AlertManager(this);
        this.apiManager = new APIManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);

        // Register packet listeners
        registerPacketListeners();

        // Check for updates
        new UpdateChecker(this).checkForUpdate();

        // Register commands
        getCommand("quantumac").setExecutor(new CommandHandler(this));

        getLogger().info(ChatColor.GREEN + "QuantumAC has been enabled!");
        getLogger().info(ChatColor.GREEN + "Async packet-based anticheat core loaded successfully.");
        getServer().getScheduler().runTaskTimer(this,
                () -> playerDataManager.updatePlayerCachedData(), 1L, 5L);
    }

    @Override
    public void onDisable() {
        // Save all player data and logs
        playerDataManager.saveAllPlayerData();

        // Shutdown executors gracefully
        packetExecutor.shutdown();

        // Close MongoDB connection
        if (mongoManager != null) {
            mongoManager.closeConnection();
        }

        getLogger().info(ChatColor.RED + "QuantumAC has been disabled!");
    }

    private void registerPacketListeners() {
        // Register packet listening through ProtocolLib
        PacketListener packetListener = new PacketListener(this);

        // Register for specific packets - expand this list as needed
        protocolManager.addPacketListener(
                new PacketAdapter(this, ListenerPriority.NORMAL,
                        // Movement packets
                        PacketType.Play.Client.POSITION,
                        PacketType.Play.Client.POSITION_LOOK,
                        PacketType.Play.Client.LOOK,
                        PacketType.Play.Client.FLYING,

                        // Entity interaction
                        PacketType.Play.Client.USE_ENTITY,
                        PacketType.Play.Client.ARM_ANIMATION,

                        // Block interaction
                        PacketType.Play.Client.BLOCK_DIG,
                        PacketType.Play.Client.BLOCK_PLACE,

                        // Player state
                        PacketType.Play.Client.ABILITIES,

                        // New packet types needed for BadPackets checks
                        PacketType.Play.Client.ENTITY_ACTION,  // For sprint/sneak detection
                        PacketType.Play.Client.TRANSACTION,    // For transaction timing checks
                        PacketType.Play.Client.KEEP_ALIVE,     // For lag/timer detection
                        PacketType.Play.Client.WINDOW_CLICK,   // For inventory actions
                        PacketType.Play.Client.CUSTOM_PAYLOAD, // For client detection
                        PacketType.Play.Client.SETTINGS,       // For client settings checks
                        PacketType.Play.Client.CLOSE_WINDOW    // For inventory tracking
                ) {

                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        // Process all packets asynchronously to prevent server lag
                        CompletableFuture.runAsync(() -> {
                            packetListener.onPacketReceive(event);
                        }, packetExecutor).exceptionally(ex -> {
                            getLogger().log(Level.SEVERE, "Error processing packet", ex);
                            return null;
                        });
                    }
                });
    }

    // Utility methods
    public static QuantumAC getInstance() {
        return instance;
    }

    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }

    public MongoManager getMongoManager() {
        return mongoManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public AlertManager getAlertManager() {
        return alertManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public APIManager getApiManager() {
        return apiManager;
    }

    public ExecutorService getPacketExecutor() {
        return packetExecutor;
    }
}