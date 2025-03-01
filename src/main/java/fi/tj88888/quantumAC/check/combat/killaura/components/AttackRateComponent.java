package fi.tj88888.quantumAC.check.combat.killaura.components;

import java.util.LinkedList;
import java.util.Queue;

/**
 * AttackRateComponent - Detects if a player is attacking too quickly
 * This can indicate modified client attack speed or auto-clicker usage
 */
public class AttackRateComponent {

    // Detection constants
    private static final int MAX_ATTACKS_PER_SECOND = 20;
    private static final int SAMPLE_SIZE = 10;
    private static final long RESET_VIOLATION_TIME = 10000; // ms
    private static final int CONSECUTIVE_THRESHOLD = 2;
    private static final int VL_THRESHOLD = 2;

    // State tracking
    private final Queue<Long> attackTimes = new LinkedList<>();
    private int attackRateVL = 0;
    private long lastFlag = 0;
    private int consecutiveDetections = 0;

    /**
     * Checks if the player's attack rate is suspiciously high
     * 
     * @param attackTime The time of the current attack
     * @return A violation message if detected, null otherwise
     */
    public String checkAttackRate(long attackTime) {
        // Reset violations after time
        if (shouldResetViolations()) {
            attackRateVL = Math.max(0, attackRateVL - 1);
            consecutiveDetections = 0;
        }

        // Add the current attack time to our samples
        attackTimes.add(attackTime);
        
        // Keep only the most recent samples
        while (attackTimes.size() > SAMPLE_SIZE) {
            attackTimes.poll();
        }
        
        // Need at least 5 samples to calculate a rate
        if (attackTimes.size() < 5) {
            return null;
        }
        
        // Calculate attacks per second
        long oldestTime = attackTimes.peek();
        long timeSpan = attackTime - oldestTime;
        
        // Avoid division by zero
        if (timeSpan <= 0) {
            timeSpan = 1;
        }
        
        // Convert to attacks per second
        double attacksPerSecond = (attackTimes.size() - 1) * 1000.0 / timeSpan;
        
        // Check if the attack rate exceeds our threshold
        if (attacksPerSecond > MAX_ATTACKS_PER_SECOND) {
            consecutiveDetections++;
            
            // Require multiple consecutive detections before flagging
            if (consecutiveDetections >= CONSECUTIVE_THRESHOLD) {
                attackRateVL++;
                
                if (attackRateVL >= VL_THRESHOLD) {
                    String violation = "Attack rate too high: " + String.format("%.2f", attacksPerSecond) + 
                                      " attacks/sec (max: " + MAX_ATTACKS_PER_SECOND + ")";
                    lastFlag = System.currentTimeMillis();
                    attackRateVL = 0;
                    consecutiveDetections = 0;
                    return violation;
                }
            }
        } else {
            // Valid pattern, reset consecutive detection
            consecutiveDetections = 0;
        }
        
        return null;
    }
    
    /**
     * Checks if enough time has passed since the last flag to reset violations
     */
    private boolean shouldResetViolations() {
        return System.currentTimeMillis() - lastFlag > RESET_VIOLATION_TIME;
    }
    
    /**
     * Resets the state of this component
     */
    public void reset() {
        attackTimes.clear();
        attackRateVL = 0;
        lastFlag = 0;
        consecutiveDetections = 0;
    }
} 