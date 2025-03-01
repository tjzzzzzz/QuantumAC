package fi.tj88888.quantumAC.check;

import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.log.ViolationLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Enhanced Check base class with improved alert handling.
 */
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

    // Abstract method for processing packet events
    public abstract void processPacket(PacketEvent event);

    /**
     * Flags a player for a violation with a specified violation amount
     *
     * @param violationAmount The amount to increase the violation level by
     * @param details Technical details about the violation
     */
    protected void flag(double violationAmount, String details) {
        if (!enabled) return;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        // Increment the player's violation level
        playerData.incrementViolationLevel(this.getClass(), violationAmount);
        double vl = playerData.getViolationLevel(this.getClass());

        // Create a violation log
        Location loc = player.getLocation();
        ViolationLog violationLog = new ViolationLog(
                player.getName(),
                checkName,
                checkType,
                vl,
                details,
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                player.getPing(),
                plugin.getConfigManager().getCurrentTPS()
        );

        // Send to alert manager
        plugin.getAlertManager().sendAlert(violationLog);

        // Log to console with detailed info
        plugin.getLogger().info(String.format(
                "[%s] %s failed %s check (VL: %.1f): %s",
                checkType, player.getName(), checkName, vl, details
        ));

        // Check if maximum violation level is reached for punishment
        if (vl >= maxVL && maxVL > 0 && !punishCommand.isEmpty()) {
            executePunishment(player);
        }
    }

    /**
     * Overloaded method to flag a player for a violation with a specified violation level
     * This is used by component-based checks
     *
     * @param player The player who violated
     * @param details Technical details about the violation
     * @param violationLevel The violation level
     */
    protected void flag(Player player, String details, int violationLevel) {
        // Convert the violation level to a double and call the main flag method
        flag((double) violationLevel, details);
    }

    /**
     * Executes a punishment command on the player when they reach the max violation level.
     *
     * @param player The player to punish.
     */
    private void executePunishment(Player player) {
        // Replace placeholders in the punishment command
        String command = punishCommand.replace("%player%", player.getName())
                .replace("%check%", checkName)
                .replace("%vl%", String.valueOf((int) playerData.getViolationLevel(this.getClass())));

        // Execute the command on the main server thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            plugin.getLogger().info("Executed punishment command: " + command);
        });

        // Reset violation level after punishment
        playerData.setViolationLevel(this.getClass(), 0.0);
    }

    /**
     * Getter for enabled state of the check.
     *
     * @return True if the check is enabled, false otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the check name
     *
     * @return The check name
     */
    public String getCheckName() {
        return checkName;
    }

    /**
     * Gets the check type
     *
     * @return The check type
     */
    public String getCheckType() {
        return checkType;
    }
}