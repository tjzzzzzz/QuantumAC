package fi.tj88888.quantumAC.check.movement.fly.components;

import org.bukkit.entity.Player;

/**
 * Component for checking terminal velocity violations
 */
public class TerminalVelocityCheck {

    // Detection settings
    private static final int BUFFER_THRESHOLD = 8;
    private static final int BUFFER_DECREMENT = 1;
    private static final double TERMINAL_VELOCITY = 3.92;
    private static final double SLOW_FALLING_MAX_VELOCITY = 0.05;

    // State tracking
    private int buffer = 0;
    private int consecutiveViolations = 0;

    /**
     * Checks for terminal velocity violations (falling too slowly)
     * 
     * @param player The player to check
     * @param dy Vertical movement amount
     * @param onGround Whether the player is on ground
     * @param isExempt Whether the player is exempt from checks
     * @param hasSlowFalling Whether the player has slow falling effect
     * @param tolerance Tolerance value for checks
     * @return Violation details or null if no violation
     */
    public String checkTerminalVelocity(Player player, double dy, boolean onGround, boolean isExempt, 
                                       boolean hasSlowFalling, double tolerance) {
        if (isExempt || onGround || dy >= 0) {
            // Reset violation counters when exempt, on ground, or moving upward
            if (buffer > 0) buffer = Math.max(0, buffer - BUFFER_DECREMENT);
            consecutiveViolations = 0;
            return null;
        }

        // Determine expected minimum fall speed
        double minFallSpeed;
        if (hasSlowFalling) {
            minFallSpeed = -SLOW_FALLING_MAX_VELOCITY - tolerance;
        } else {
            // For normal falling, we expect a minimum fall speed after several ticks
            // This is not the actual terminal velocity, but a reasonable minimum for detection
            minFallSpeed = -0.5 - tolerance;
        }

        // Check for insufficient falling speed (after being in air for a while)
        if (dy > minFallSpeed && consecutiveViolations >= 5) {
            buffer++;
            consecutiveViolations++;
            
            if (buffer >= BUFFER_THRESHOLD) {
                return String.format("Insufficient falling speed (dy: %.5f, min expected: %.5f)", dy, minFallSpeed);
            }
        } else if (dy < 0) {
            // Count consecutive falling ticks
            consecutiveViolations++;
            
            // Decay buffer when falling at reasonable speed
            if (buffer > 0) buffer = Math.max(0, buffer - BUFFER_DECREMENT);
        }

        return null;
    }

    /**
     * Resets the state of the terminal velocity check
     */
    public void reset() {
        buffer = 0;
        consecutiveViolations = 0;
    }
} 