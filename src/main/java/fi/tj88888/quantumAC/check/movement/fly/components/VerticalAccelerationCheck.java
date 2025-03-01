package fi.tj88888.quantumAC.check.movement.fly.components;

import org.bukkit.entity.Player;

/**
 * Component for checking vertical acceleration violations
 */
public class VerticalAccelerationCheck {

    // Detection settings
    private static final int BUFFER_THRESHOLD = 10;
    private static final int BUFFER_DECREMENT = 1;
    private static final double MAX_UP_VELOCITY = 0.42;
    private static final double JUMP_BOOST_MULTIPLIER = 0.1;

    // State tracking
    private int buffer = 0;
    private int consecutiveViolations = 0;

    /**
     * Checks for vertical acceleration violations (going up faster than possible)
     * 
     * @param player The player to check
     * @param dy Vertical movement amount
     * @param onGround Whether the player is on ground
     * @param isExempt Whether the player is exempt from checks
     * @param jumpBoostLevel Jump boost effect level (0 if none)
     * @param tolerance Tolerance value for checks
     * @return Violation details or null if no violation
     */
    public String checkVerticalAcceleration(Player player, double dy, boolean onGround, boolean isExempt, 
                                           int jumpBoostLevel, double tolerance) {
        if (isExempt) {
            // Reset violation counters when exempt
            if (buffer > 0) buffer = Math.max(0, buffer - BUFFER_DECREMENT);
            consecutiveViolations = 0;
            return null;
        }

        // Calculate maximum allowed upward velocity based on jump boost
        double maxUpVelocity = MAX_UP_VELOCITY + (jumpBoostLevel * JUMP_BOOST_MULTIPLIER) + tolerance;

        // Check for excessive upward velocity
        if (dy > maxUpVelocity && !onGround) {
            consecutiveViolations++;
            buffer += 2;
            
            if (buffer >= BUFFER_THRESHOLD && consecutiveViolations >= 2) {
                return String.format("Excessive upward velocity (dy: %.5f, max: %.5f)", dy, maxUpVelocity);
            }
        } else {
            // Decay buffer when complying with acceleration limits
            if (buffer > 0) buffer = Math.max(0, buffer - BUFFER_DECREMENT);
            
            // Only reset consecutive violations when on ground or moving downward
            if (onGround || dy <= 0) {
                consecutiveViolations = 0;
            }
        }

        return null;
    }

    /**
     * Resets the state of the vertical acceleration check
     */
    public void reset() {
        buffer = 0;
        consecutiveViolations = 0;
    }
} 