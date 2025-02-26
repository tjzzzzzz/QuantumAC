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
    private final String alertPrefix;
    private final String alertFormat;

    public AlertManager(QuantumAC plugin) {
        this.plugin = plugin;
        this.alertSubscribers = new HashSet<>();
        this.alertPrefix = plugin.getConfigManager().getAlertPrefix();
        this.alertFormat = plugin.getConfigManager().getAlertFormat();
    }

    public void sendAlert(ViolationLog violationLog) {
        // Check if alerts are enabled
        if (!plugin.getConfigManager().isAlertsEnabled()) {
            return;
        }

        // Format the alert message
        String alertMessage = formatAlert(violationLog);

        // Send alert to all subscribers
        for (UUID uuid : alertSubscribers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.hasPermission("quantumac.alerts")) {
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
                        .replace("%details%", violationLog.getDetails())
                        .replace("%ping%", String.valueOf(violationLog.getPing()))
                        .replace("%tps%", String.format("%.1f", violationLog.getTps()))
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
}