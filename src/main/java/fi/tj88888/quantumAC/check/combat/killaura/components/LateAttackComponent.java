package fi.tj88888.quantumAC.check.combat.killaura.components;

/**
 * Component for detecting late attacks (attacks that occur too long after arm swing)
 */
public class LateAttackComponent {

    // Detection constants
    private static final long MAX_TIME_BETWEEN_SWING_ATTACK = 350; // ms
    private static final long RESET_VIOLATION_TIME = 10000; // ms

    // State tracking
    private int lateAttackVL = 0;
    private long lastFlag = 0;
    private int consecutiveDetections = 0;

    /**
     * Checks if an attack was sent too late after arm animation
     * 
     * @param attackTime The time of the attack
     * @param armAnimTime The time of the arm animation
     * @param ping The player's ping
     * @return Violation details or null if no violation
     */
    public String checkLateAttack(long attackTime, long armAnimTime, int ping) {
        // Skip check if no arm animation was detected yet
        if (armAnimTime == 0) return null;

        // Calculate time difference with ping compensation
        ping = Math.max(ping, 100); // Minimum ping consideration
        long timeDiff = attackTime - armAnimTime;
        long maxAllowedTime = MAX_TIME_BETWEEN_SWING_ATTACK + ping;

        // Reset violations after time
        if (attackTime - lastFlag > RESET_VIOLATION_TIME) {
            lateAttackVL = Math.max(0, lateAttackVL - 1);
            consecutiveDetections = 0;
        }

        // Check if the attack was sent too late
        if (timeDiff > maxAllowedTime) {
            consecutiveDetections++;

            // Require multiple consecutive detections before flagging
            if (consecutiveDetections >= 3) {
                lateAttackVL++;

                if (lateAttackVL >= 3) {
                    String violation = String.format("Late attack: %dms after swing (max allowed: %dms)", 
                                                    timeDiff, maxAllowedTime);
                    lastFlag = attackTime;
                    lateAttackVL = 0;
                    consecutiveDetections = 0;
                    return violation;
                }
            }
        } else {
            // Gradually reduce consecutive detections for non-violations
            if (consecutiveDetections > 0 && attackTime - lastFlag > 2000) {
                consecutiveDetections--;
            }
        }

        return null;
    }

    /**
     * Resets the state of the late attack check
     */
    public void reset() {
        lateAttackVL = 0;
        lastFlag = 0;
        consecutiveDetections = 0;
    }
} 