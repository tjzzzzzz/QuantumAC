package fi.tj88888.quantumAC.check.combat.killaura.components;

import fi.tj88888.quantumAC.check.ViolationData;
import org.bukkit.entity.Player;

/**
 * Component to detect KillAura cheats by checking for actions performed while a player is dead.
 * In vanilla Minecraft, players should not be able to send attack packets or use entities while dead.
 */
public class DeadPlayerActionComponent {

    // Detection constants
    private static final int PACKET_WINDOW = 500; // Time window in ms to consider sequential packets
    private static final int RESET_VIOLATION_TIME = 10000; // Time in ms to reset violations
    private static final int BUFFER_THRESHOLD = 2; // Threshold for buffering violations
    private static final int BUFFER_DECREMENT = 1; // Rate at which buffer decreases on legitimate moves

    // State tracking
    private int deadActionVL = 0;
    private long lastFlag = 0;
    private int consecutiveDetections = 0;
    private int buffer = 0;
    
    // Action tracking
    private boolean sentUseEntity = false;
    private long lastUseEntityTime = 0;

    /**
     * Checks for dead player USE_ENTITY packet violations
     * 
     * @param player The player to check
     * @param isDead Whether the player is currently dead
     * @param isAttackAction Whether the current action is an attack
     * @return ViolationData if a violation was detected, null otherwise
     */
    public ViolationData checkDeadUseEntity(Player player, boolean isDead, boolean isAttackAction) {
        if (!isDead) {
            // Decrease buffer on legitimate moves
            buffer = Math.max(0, buffer - BUFFER_DECREMENT);
            return null;
        }

        // Dead player sending USE_ENTITY packet
        if (isAttackAction) {
            buffer++;
            sentUseEntity = true;
            lastUseEntityTime = System.currentTimeMillis();
            
            // Only flag if buffer threshold is reached
            if (buffer >= BUFFER_THRESHOLD) {
                // Check for violations reset timeout
                if (shouldResetViolations()) {
                    resetViolations();
                    consecutiveDetections = 1;
                } else {
                    consecutiveDetections++;
                }
                
                // Update tracking variables
                lastFlag = System.currentTimeMillis();
                deadActionVL++;
                
                // Create violation data with detailed information
                return new ViolationData(
                    String.format(
                        "dead-useentity: player=%s, consecutive=%d",
                        player.getName(), consecutiveDetections
                    ),
                    deadActionVL
                );
            }
        }
        
        return null;
    }
    
    /**
     * Checks for dead player arm animation packet violations
     * 
     * @param player The player to check
     * @param isDead Whether the player is currently dead
     * @param currentTime The current time in milliseconds
     * @return ViolationData if a violation was detected, null otherwise
     */
    public ViolationData checkDeadArmAnimation(Player player, boolean isDead, long currentTime) {
        if (!isDead) {
            // Decrease buffer on legitimate moves
            buffer = Math.max(0, buffer - BUFFER_DECREMENT);
            return null;
        }

        // Check if this arm animation follows a recent USE_ENTITY packet
        if (sentUseEntity && (currentTime - lastUseEntityTime) < PACKET_WINDOW) {
            buffer++;
            
            // Reset the USE_ENTITY flag since we've matched it with an arm animation
            sentUseEntity = false;
            
            // Only flag if buffer threshold is reached
            if (buffer >= BUFFER_THRESHOLD) {
                // Check for violations reset timeout
                if (shouldResetViolations()) {
                    resetViolations();
                    consecutiveDetections = 1;
                } else {
                    consecutiveDetections++;
                }
                
                // Update tracking variables
                lastFlag = System.currentTimeMillis();
                deadActionVL++;
                
                // Create violation data with detailed information
                return new ViolationData(
                    String.format(
                        "dead-armswing: player=%s, consecutive=%d, window=%d",
                        player.getName(), consecutiveDetections, 
                        (int)(currentTime - lastUseEntityTime)
                    ),
                    deadActionVL
                );
            }
        }
        
        return null;
    }
    
    /**
     * Reset violation tracking
     */
    public void resetViolations() {
        deadActionVL = 0;
        consecutiveDetections = 0;
        buffer = 0;
    }
    
    /**
     * Check if enough time has passed to reset violations
     */
    private boolean shouldResetViolations() {
        return (System.currentTimeMillis() - lastFlag) > RESET_VIOLATION_TIME;
    }
    
    /**
     * Reset the component's state
     */
    public void reset() {
        deadActionVL = 0;
        consecutiveDetections = 0;
        buffer = 0;
        lastFlag = 0;
        sentUseEntity = false;
        lastUseEntityTime = 0;
    }
} 