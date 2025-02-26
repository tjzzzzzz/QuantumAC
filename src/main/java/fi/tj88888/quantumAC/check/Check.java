package fi.tj88888.quantumAC.check;

import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.log.ViolationLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public abstract class Check {

    protected final QuantumAC plugin;
    protected final PlayerData playerData;
    protected final UUID uuid;
    protected final String checkName;
    protected final String checkType;
    protected boolean enabled;
    protected int maxVL;
    protected String punishCommand;

    public Check(QuantumAC plugin, PlayerData playerData, String checkName, String checkType) {
        this.plugin = plugin;
        this.playerData = playerData;
        this.uuid = playerData.getUuid();
        this.checkName = checkName;
        this.checkType = checkType;

        // Load config settings
        this.enabled = plugin.getConfigManager().isCheckEnabled(checkName);
        this.maxVL = plugin.getConfigManager().getMaxVL(checkName);
        this.punishCommand = plugin.getConfigManager().getPunishCommand(checkName);
    }

    // Abstract methods
    public abstract void processPacket(PacketEvent event);

    // Utility methods
    protected void flag(double violationAmount, String details) {
        if (!enabled) return;

        // Increment the VL
        playerData.incrementViolationLevel(this.getClass(), violationAmount);
        double vl = playerData.getViolationLevel(this.getClass());

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        Location location = player.getLocation();

        // Log the violation
        ViolationLog violationLog = new ViolationLog(
                uuid,
                playerData.getPlayerName(),
                checkName,
                checkType,
                vl,
                details,
                System.currentTimeMillis(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                playerData.getAveragePing(),
                plugin.getConfigManager().getCurrentTPS()
        );

        // Add to database asynchronously
        plugin.getMongoManager().logViolation(violationLog);

        // Send alert to staff
        plugin.getAlertManager().sendAlert(violationLog);

        // Log to file
        plugin.getLogManager().logViolation(violationLog);

        // Check if max VL reached for punishment
        if (vl >= maxVL && maxVL > 0 && !punishCommand.isEmpty()) {
            executePunishment(player);
        }
    }

    private void executePunishment(Player player) {
        String command = punishCommand.replace("%player%", player.getName())
                .replace("%check%", checkName)
                .replace("%vl%", String.valueOf((int) playerData.getViolationLevel(this.getClass())));

        // Execute punishment command
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            plugin.getLogger().info("Executed punishment command: " + command);
        });

        // Reset VL after punishment
        playerData.setViolationLevel(this.getClass(), 0.0);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCheckName() {
        return checkName;
    }

    public String getCheckType() {
        return checkType;
    }

    public int getMaxVL() {
        return maxVL;
    }

    public void setMaxVL(int maxVL) {
        this.maxVL = maxVL;
    }

    public String getPunishCommand() {
        return punishCommand;
    }

    public void setPunishCommand(String punishCommand) {
        this.punishCommand = punishCommand;
    }
}