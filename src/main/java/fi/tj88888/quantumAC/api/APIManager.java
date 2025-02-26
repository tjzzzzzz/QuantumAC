package fi.tj88888.quantumAC.api;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * API for external plugins to interact with QuantumAC
 */
public class APIManager {

    private final QuantumAC plugin;

    public APIManager(QuantumAC plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets player data for the given UUID
     *
     * @param uuid Player UUID
     * @return PlayerData or null if not found
     */
    public PlayerData getPlayerData(UUID uuid) {
        return plugin.getPlayerDataManager().getPlayerData(uuid);
    }

    /**
     * Gets player data for the given player
     *
     * @param player Player
     * @return PlayerData or null if not found
     */
    public PlayerData getPlayerData(Player player) {
        return plugin.getPlayerDataManager().getPlayerData(player);
    }

    /**
     * Gets all active checks for a player
     *
     * @param uuid Player UUID
     * @return Set of active checks
     */
    public Set<Check> getChecks(UUID uuid) {
        return plugin.getCheckManager().getChecks(uuid);
    }

    /**
     * Adds an exemption to a player for a given duration
     *
     * @param uuid Player UUID
     * @param reason Reason for exemption
     * @param duration Duration in milliseconds
     */
    public void exempt(UUID uuid, String reason, long duration) {
        PlayerData data = getPlayerData(uuid);
        if (data != null) {
            data.setExempt(reason, duration);
        }
    }

    /**
     * Removes an exemption from a player
     *
     * @param uuid Player UUID
     * @param reason Reason for exemption
     */
    public void unexempt(UUID uuid, String reason) {
        PlayerData data = getPlayerData(uuid);
        if (data != null) {
            data.removeExempt(reason);
        }
    }

    /**
     * Checks if a player is exempt from checks
     *
     * @param uuid Player UUID
     * @return True if player is exempt
     */
    public boolean isExempt(UUID uuid) {
        PlayerData data = getPlayerData(uuid);
        return data != null && data.isExempt();
    }

    /**
     * Gets the violation level of a specific check for a player
     *
     * @param uuid Player UUID
     * @param checkClass Check class
     * @return Violation level
     */
    public double getViolationLevel(UUID uuid, Class<? extends Check> checkClass) {
        PlayerData data = getPlayerData(uuid);
        return data != null ? data.getViolationLevel(checkClass) : 0.0;
    }

    /**
     * Sets the violation level of a specific check for a player
     *
     * @param uuid Player UUID
     * @param checkClass Check class
     * @param violationLevel New violation level
     */
    public void setViolationLevel(UUID uuid, Class<? extends Check> checkClass, double violationLevel) {
        PlayerData data = getPlayerData(uuid);
        if (data != null) {
            data.setViolationLevel(checkClass, violationLevel);
        }
    }

    /**
     * Gets the current TPS (Ticks Per Second)
     *
     * @return Current TPS
     */
    public double getCurrentTPS() {
        return plugin.getConfigManager().getCurrentTPS();
    }
}