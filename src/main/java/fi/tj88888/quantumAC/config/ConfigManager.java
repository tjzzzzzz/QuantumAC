package fi.tj88888.quantumAC.config;

import fi.tj88888.quantumAC.QuantumAC;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final QuantumAC plugin;
    private FileConfiguration config;
    private File configFile;

    private FileConfiguration checksConfig;
    private File checksConfigFile;

    private FileConfiguration messagesConfig;
    private File messagesConfigFile;

    // TPS monitoring
    private double currentTPS = 20.0;
    private final double[] recentTPS = new double[3];

    public ConfigManager(QuantumAC plugin) {
        this.plugin = plugin;
        setupConfigs();
        startTPSMonitor();
    }

    public void loadConfig() {
        createDefaultConfigs();
        loadConfigurations();
    }

    private void setupConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");
        checksConfigFile = new File(plugin.getDataFolder(), "checks.yml");
        messagesConfigFile = new File(plugin.getDataFolder(), "messages.yml");
    }

    private void createDefaultConfigs() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        if (!checksConfigFile.exists()) {
            plugin.saveResource("checks.yml", false);
        }

        if (!messagesConfigFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
    }

    private void loadConfigurations() {
        config = YamlConfiguration.loadConfiguration(configFile);
        checksConfig = YamlConfiguration.loadConfiguration(checksConfigFile);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesConfigFile);
    }

    public void reloadConfigs() {
        loadConfigurations();
    }

    public void saveConfigs() {
        try {
            config.save(configFile);
            checksConfig.save(checksConfigFile);
            messagesConfig.save(messagesConfigFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save configs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // MongoDB settings
    public String getMongoUri() {
        return config.getString("database.mongodb.uri", "");
    }

    public String getMongoDatabaseName() {
        return config.getString("database.mongodb.database", "quantumac");
    }

    // Alert settings
    public boolean isAlertsEnabled() {
        return config.getBoolean("alerts.enabled", true);
    }

    public String getAlertPrefix() {
        return messagesConfig.getString("prefix", "&7[&bQuantum&7] ");
    }

    public String getAlertFormat() {
        return messagesConfig.getString("alert-format",
                "%prefix% &b%player% &7failed &b%check% &7(&b%type%&7) &7VL: &b%vl% &7| Ping: &b%ping%ms &7| TPS: &b%tps%");
    }

    // Check settings
    public boolean isCheckEnabled(String checkName) {
        return checksConfig.getBoolean("checks." + checkName + ".enabled", true);
    }

    public int getMaxVL(String checkName) {
        return checksConfig.getInt("checks." + checkName + ".max-vl", 20);
    }

    public String getPunishCommand(String checkName) {
        return checksConfig.getString("checks." + checkName + ".punish-command", "kick %player% %check% violation");
    }

    public Map<String, Object> getCheckSettings(String checkName) {
        Map<String, Object> settings = new HashMap<>();

        if (checksConfig.contains("checks." + checkName + ".settings")) {
            for (String key : checksConfig.getConfigurationSection("checks." + checkName + ".settings").getKeys(false)) {
                settings.put(key, checksConfig.get("checks." + checkName + ".settings." + key));
            }
        }

        return settings;
    }

    // TPS monitoring
    private void startTPSMonitor() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private long lastPoll = System.nanoTime();

            @Override
            public void run() {
                final long startTime = System.nanoTime();
                long timeSpent = (startTime - lastPoll) / 1000;

                if (timeSpent == 0) {
                    timeSpent = 1;
                }

                if (recentTPS[0] > 20.0) {
                    recentTPS[0] = 20.0;
                }

                currentTPS = (double) Math.min(1000000000L / timeSpent, 20) * 0.8 + recentTPS[0] * 0.2;

                System.arraycopy(recentTPS, 0, recentTPS, 1, recentTPS.length - 1);
                recentTPS[0] = currentTPS;

                lastPoll = startTime;
            }
        }, 1L, 1L);
    }

    public double getCurrentTPS() {
        return Math.min(currentTPS, 20.0);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getChecksConfig() {
        return checksConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public String getVerboseAlertFormat() {
        return messagesConfig.getString("verbose-alert-format",
                "%prefix% &b%player% &7failed &b%check% &7(&b%type%&7) &7VL: &b%vl% &7| Details: &b%details% &7| Loc: &b%world% &7(&b%x%, %y%, %z%&7) | Ping: &b%ping%ms &7| TPS: &b%tps%");
    }
}