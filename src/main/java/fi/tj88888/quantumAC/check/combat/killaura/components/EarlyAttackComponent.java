package fi.tj88888.quantumAC.check.combat.killaura.components;

/**
 * EarlyAttackComponent - Detects if a player attacks before swinging their arm
 * This can indicate modified client attack sequence or packet manipulation
 */
public class EarlyAttackComponent {

    // Detection constants
    private static final long MIN_TIME_BETWEEN_ATTACK_SWING = 50; // ms
    private static final long RESET_VIOLATION_TIME = 10000; // ms
    private static final int CONSECUTIVE_THRESHOLD = 3;
    private static final int VL_THRESHOLD = 3;

    // State tracking
    private int earlyAttackVL = 0;
    private long lastFlag = 0;
    private int consecutiveDetections = 0;

    /**
     * Checks if an attack was sent before the arm animation
     * 
     * @param attackTime The time of the attack packet
     * @param armAnimTime The time of the arm animation packet
     * @param ping The player's ping in milliseconds
     * @return A violation message if detected, null otherwise
     */
    public String checkEarlyAttack(long attackTime, long armAnimTime, int ping) {
        // Skip if we haven't seen both events yet
        if (attackTime == 0 || armAnimTime == 0) return null;
        
        // Reset violations after time
        if (shouldResetViolations()) {
            earlyAttackVL = Math.max(0, earlyAttackVL - 1);
            consecutiveDetections = 0;
        }

        // Calculate time difference with ping compensation
        long timeDiff = attackTime - armAnimTime;
        
        // Check if the attack was sent before the arm animation
        // Negative time difference means attack came before swing
        if (timeDiff < 0 && Math.abs(timeDiff) > MIN_TIME_BETWEEN_ATTACK_SWING) {
            consecutiveDetections++;
            
            // Require multiple consecutive detections before flagging
            if (consecutiveDetections >= CONSECUTIVE_THRESHOLD) {
                earlyAttackVL++;
                
                if (earlyAttackVL >= VL_THRESHOLD) {
                    String violation = "Early attack: " + Math.abs(timeDiff) + 
                                      "ms before swing (min expected: " + MIN_TIME_BETWEEN_ATTACK_SWING + "ms)";
                    lastFlag = System.currentTimeMillis();
                    earlyAttackVL = 0;
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
        earlyAttackVL = 0;
        lastFlag = 0;
        consecutiveDetections = 0;
    }
} 