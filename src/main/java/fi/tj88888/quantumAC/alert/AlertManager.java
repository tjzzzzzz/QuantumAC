package fi.tj88888.quantumAC.alert;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.log.ViolationLog;
import fi.tj88888.quantumAC.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles alert notifications to staff members and logging violations
 */
public class AlertManager {

    private final QuantumAC plugin;
    private final Set<UUID> alertSubscribers = new HashSet<>();
    private final Set<UUID> verboseSubscribers = new HashSet<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public AlertManager(QuantumAC plugin) {
        this.plugin = plugin;
    }

    /**
     * Sends an alert to all subscribed staff members and logs the violation
     *
     * @param violationLog The violation log to send
     */
    public void sendAlert(ViolationLog violationLog) {
        if (!plugin.getConfigManager().isAlertsEnabled()) {
            return;
        }

        // Send regular alerts
        sendAlertToSubscribers(violationLog, false);

        // Send verbose alerts to those with verbose mode enabled
        sendAlertToSubscribers(violationLog, true);

        // Log violation to database
        if (plugin.getMongoManager().isConnected()) {
            plugin.getMongoManager().logViolation(violationLog);
        }
    }

    /**
     * Send alerts to subscribers based on verbose mode
     *
     * @param violationLog The violation log
     * @param verbose Whether to send verbose alerts
     */
    private void sendAlertToSubscribers(ViolationLog violationLog, boolean verbose) {
        // Get appropriate format
        String format = verbose ?
                plugin.getConfigManager().getVerboseAlertFormat() :
                plugin.getConfigManager().getAlertFormat();

        // Format message
        String message = formatAlertMessage(violationLog, format);

        // Send to appropriate subscribers
        Set<UUID> subscribers = verbose ? verboseSubscribers : alertSubscribers;

        for (UUID uuid : subscribers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                String permission = verbose ? "quantumac.verbose" : "quantumac.alerts";
                if (player.hasPermission(permission)) {
                    player.sendMessage(message);
                }
            }
        }

        // Log to console if it's a regular alert
        if (!verbose) {
            Bukkit.getConsoleSender().sendMessage(message);
        }
    }

    /**
     * Format the alert message with placeholders
     *
     * @param log The violation log
     * @param format The message format
     * @return Formatted message
     */
    private String formatAlertMessage(ViolationLog log, String format) {
        return ChatUtil.colorize(format
                .replace("%prefix%", plugin.getConfigManager().getAlertPrefix())
                .replace("%player%", log.getPlayerName())
                .replace("%check%", log.getCheckName())
                .replace("%type%", log.getCheckType())
                .replace("%vl%", String.format("%.1f", log.getVl()))
                .replace("%details%", log.getDetails())
                .replace("%world%", log.getWorld())
                .replace("%x%", String.format("%.1f", log.getX()))
                .replace("%y%", String.format("%.1f", log.getY()))
                .replace("%z%", String.format("%.1f", log.getZ()))
                .replace("%ping%", String.valueOf(log.getPing()))
                .replace("%tps%", String.format("%.1f", log.getTps()))
                .replace("%time%", timeFormat.format(new Date(log.getTimestamp()))));
    }

    /**
     * Toggle alert notifications for a player
     *
     * @param uuid Player UUID
     * @return true if alerts were enabled, false if disabled
     */
    public boolean toggleAlerts(UUID uuid) {
        if (alertSubscribers.contains(uuid)) {
            alertSubscribers.remove(uuid);
            return false;
        } else {
            alertSubscribers.add(uuid);
            return true;
        }
    }

    /**
     * Check if a player is subscribed to alerts
     *
     * @param uuid Player UUID
     * @return true if subscribed
     */
    public boolean hasAlertsEnabled(UUID uuid) {
        return alertSubscribers.contains(uuid);
    }

    /**
     * Toggle verbose mode for a player
     *
     * @param uuid Player UUID
     * @return true if verbose mode was enabled, false if disabled
     */
    public boolean toggleVerbose(UUID uuid) {
        if (verboseSubscribers.contains(uuid)) {
            verboseSubscribers.remove(uuid);
            return false;
        } else {
            verboseSubscribers.add(uuid);
            return true;
        }
    }

    /**
     * Check if a player has verbose mode enabled
     *
     * @param uuid Player UUID
     * @return true if verbose mode is enabled
     */
    public boolean hasVerboseEnabled(UUID uuid) {
        return verboseSubscribers.contains(uuid);
    }

    /**
     * Add a player to alert notifications
     *
     * @param uuid Player UUID
     */
    public void addAlertSubscriber(UUID uuid) {
        alertSubscribers.add(uuid);
    }

    /**
     * Remove a player from alert notifications
     *
     * @param uuid Player UUID
     */
    public void removeAlertSubscriber(UUID uuid) {
        alertSubscribers.remove(uuid);
    }

    /**
     * Add a player to verbose mode
     *
     * @param uuid Player UUID
     */
    public void addVerboseSubscriber(UUID uuid) {
        verboseSubscribers.add(uuid);
    }

    /**
     * Remove a player from verbose mode
     *
     * @param uuid Player UUID
     */
    public void removeVerboseSubscriber(UUID uuid) {
        verboseSubscribers.remove(uuid);
    }
}