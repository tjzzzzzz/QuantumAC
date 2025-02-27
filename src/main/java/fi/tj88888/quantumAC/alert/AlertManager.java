package fi.tj88888.quantumAC.alert;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.log.ViolationLog;
import fi.tj88888.quantumAC.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AlertManager {

    private final QuantumAC plugin;
    private final Set<UUID> alertSubscribers;
    private final Set<UUID> verboseSubscribers;
    private final String alertPrefix;
    private final String alertFormat;
    private final String verboseAlertFormat;


    public AlertManager(QuantumAC plugin) {
        this.plugin = plugin;
        this.alertSubscribers = new HashSet<>();
        this.verboseSubscribers = new HashSet<>();
        this.alertPrefix = plugin.getConfigManager().getAlertPrefix();
        this.alertFormat = plugin.getConfigManager().getAlertFormat();
        this.verboseAlertFormat = plugin.getConfigManager().getVerboseAlertFormat();

    }

    public void sendAlert(ViolationLog violationLog) {
        // Check if alerts are enabled
        if (!plugin.getConfigManager().isAlertsEnabled()) {
            return;
        }

        for (UUID uuid : alertSubscribers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.hasPermission("quantumac.alerts")) {
                // Determine which format to send
                String alertMessage = verboseSubscribers.contains(uuid)
                        ? formatVerboseAlert(violationLog)
                        : formatAlert(violationLog);

                player.sendMessage(alertMessage);
            }
        }
    }

    private String formatAlert(ViolationLog violationLog) {
        return ChatUtil.colorize(
                alertFormat.replace("%prefix%", alertPrefix)
                        .replace("%player%", violationLog.getPlayerName())
                        .replace("%check%", violationLog.getCheckName())
                        .replace("%type%", violationLog.getCheckType())
                        .replace("%vl%", String.format("%.1f", violationLog.getViolationLevel()))
                        .replace("%details%", "") // No details in the non-verbose mode
                        .replace("%ping%", String.valueOf(violationLog.getPing()))
                        .replace("%tps%", String.format("%.1f", violationLog.getTps()))
        );
    }

    private String formatVerboseAlert(ViolationLog violationLog) {
        return ChatUtil.colorize(
                verboseAlertFormat.replace("%prefix%", alertPrefix)
                        .replace("%player%", violationLog.getPlayerName())
                        .replace("%check%", violationLog.getCheckName())
                        .replace("%type%", violationLog.getCheckType())
                        .replace("%vl%", String.format("%.1f", violationLog.getViolationLevel()))
                        .replace("%details%", violationLog.getDetails()) // Include detailed info
                        .replace("%ping%", String.valueOf(violationLog.getPing()))
                        .replace("%tps%", String.format("%.1f", violationLog.getTps()))
                        .replace("%world%", violationLog.getWorld())
                        .replace("%x%", String.format("%.2f", violationLog.getX()))
                        .replace("%y%", String.format("%.2f", violationLog.getY()))
                        .replace("%z%", String.format("%.2f", violationLog.getZ()))
        );
    }


    public void toggleAlerts(UUID uuid) {
        if (alertSubscribers.contains(uuid)) {
            alertSubscribers.remove(uuid);
        } else {
            alertSubscribers.add(uuid);
        }
    }

    public boolean hasAlertsEnabled(UUID uuid) {
        return alertSubscribers.contains(uuid);
    }

    public void removeSubscriber(UUID uuid) {
        alertSubscribers.remove(uuid);
    }

    public Set<UUID> getAlertSubscribers() {
        return new HashSet<>(alertSubscribers);
    }


    public void toggleVerbose(UUID uuid) {
        // Add/remove players from verbose list
        if (verboseSubscribers.contains(uuid)) {
            verboseSubscribers.remove(uuid);
        } else {
            verboseSubscribers.add(uuid);
        }
    }

    public boolean hasVerboseEnabled(UUID uuid) {
        return verboseSubscribers.contains(uuid);
    }

    public Set<UUID> getVerboseSubscribers() {
        return new HashSet<>(verboseSubscribers); // Return a copy to avoid modification
    }

}