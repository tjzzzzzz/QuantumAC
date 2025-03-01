package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.base.CombatCheck;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Base class for all KillAura-related checks
 * Provides common functionality for KillAura detection
 */
public abstract class KillAuraCheck extends CombatCheck {

    // Common constants for KillAura checks
    protected static final long MAX_SWING_ATTACK_DELAY = 350; // ms
    protected static final long MIN_SWING_ATTACK_DELAY = 20;  // ms
    protected static final long RESET_VIOLATION_TIME = 10000; // ms
    
    // Common state tracking
    protected long lastFlag = 0;
    protected int consecutiveDetections = 0;

    public KillAuraCheck(QuantumAC plugin, PlayerData playerData, String checkType) {
        super(plugin, playerData, "KillAura", checkType);
    }

    /**
     * Processes common KillAura packet handling
     * 
     * @param event The packet event
     * @return True if the packet was processed
     */
    protected boolean processKillAuraPacket(PacketEvent event) {
        // Use the base combat packet processing
        boolean processed = processCombatPacket(event);
        
        // Additional KillAura-specific processing can be added here
        
        return processed;
    }

    /**
     * Checks if the time between swing and attack is suspicious
     * 
     * @param attackTime The attack time
     * @param swingTime The swing time
     * @param ping The player's ping
     * @return True if the timing is suspicious
     */
    protected boolean isSwingAttackTimingSuspicious(long attackTime, long swingTime, int ping) {
        if (swingTime == 0 || attackTime == 0) return false;
        
        long timeDiff = attackTime - swingTime;
        long maxAllowedTime = MAX_SWING_ATTACK_DELAY + ping;
        long minAllowedTime = MIN_SWING_ATTACK_DELAY;
        
        return timeDiff > maxAllowedTime || timeDiff < minAllowedTime;
    }

    /**
     * Checks if the attack rate is suspicious
     * 
     * @param attackRate The attack rate in attacks per second
     * @return True if the rate is suspicious
     */
    protected boolean isAttackRateSuspicious(double attackRate) {
        return attackRate > 20.0; // More than 20 attacks per second is suspicious
    }

    /**
     * Checks if the attack pattern is too consistent
     * 
     * @param stdDev The standard deviation of attack intervals
     * @param avg The average attack interval
     * @return True if the pattern is too consistent
     */
    protected boolean isAttackPatternTooConsistent(double stdDev, double avg) {
        if (avg <= 0 || stdDev < 0) return false;
        
        // Human clicking has natural variation
        return (stdDev / avg) < 0.05;
    }

    /**
     * Resets violation tracking
     */
    protected void resetViolations() {
        consecutiveDetections = 0;
        lastFlag = System.currentTimeMillis();
    }

    /**
     * Checks if enough time has passed since the last flag to reset violations
     * 
     * @return True if violations should be reset
     */
    protected boolean shouldResetViolations() {
        return System.currentTimeMillis() - lastFlag > RESET_VIOLATION_TIME;
    }
} 