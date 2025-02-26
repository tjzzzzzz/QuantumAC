package fi.tj88888.quantumAC.util;

import fi.tj88888.quantumAC.QuantumAC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class to check for plugin updates
 */
public class UpdateChecker implements Listener {

    private final QuantumAC plugin;
    private String latestVersion;
    private boolean updateAvailable;

    public UpdateChecker(QuantumAC plugin) {
        this.plugin = plugin;
        this.latestVersion = plugin.getDescription().getVersion();
        this.updateAvailable = false;

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Checks for updates
     */
    public void checkForUpdate() {
        if (!plugin.getConfigManager().getConfig().getBoolean("update-checker", true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Replace this URL with your actual update check URL
                String updateUrl = "https://api.example.com/quantumac/version";
                HttpURLConnection connection = (HttpURLConnection) new URL(updateUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                String latestVersionFromServer = response.toString().trim();
                String currentVersion = plugin.getDescription().getVersion();

                if (!currentVersion.equals(latestVersionFromServer)) {
                    this.latestVersion = latestVersionFromServer;
                    this.updateAvailable = true;

                    plugin.getLogger().info("A new version of QuantumAC is available: " + latestVersionFromServer);
                    plugin.getLogger().info("Your current version: " + currentVersion);
                    plugin.getLogger().info("Download the latest version from: https://example.com/quantumac");

                    // Notify online operators
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.isOp() || player.hasPermission("quantumac.update")) {
                            notifyPlayer(player);
                        }
                    }
                } else {
                    plugin.getLogger().info("QuantumAC is up to date!");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });
    }

    /**
     * Notifies a player about an available update
     *
     * @param player Player to notify
     */
    private void notifyPlayer(Player player) {
        player.sendMessage(ChatColor.GRAY + "=== " + ChatColor.AQUA + "QuantumAC Update" + ChatColor.GRAY + " ===");
        player.sendMessage(ChatColor.GRAY + "A new version is available: " + ChatColor.AQUA + latestVersion);
        player.sendMessage(ChatColor.GRAY + "Your current version: " + ChatColor.AQUA + plugin.getDescription().getVersion());
        player.sendMessage(ChatColor.GRAY + "Download at: " + ChatColor.AQUA + "https://example.com/quantumac");
    }

    /**
     * Event handler to notify operators about updates when they join
     *
     * @param event PlayerJoinEvent
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (updateAvailable && (player.isOp() || player.hasPermission("quantumac.update"))) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> notifyPlayer(player), 40L);
        }
    }

    /**
     * Checks if an update is available
     *
     * @return True if an update is available
     */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    /**
     * Gets the latest version
     *
     * @return Latest version string
     */
    public String getLatestVersion() {
        return latestVersion;
    }
}