package fi.tj88888.quantumAC.check.movement.fly.components;

import org.bukkit.entity.Player;

/**
 * Component for checking gravity-related violations
 */
public class GravityCheck {

    // Detection settings
    private static final int BUFFER_THRESHOLD = 12;
    private static final int BUFFER_DECREMENT = 2;
    private static final double HOVER_THRESHOLD = 0.01;
    private static final double EPSILON = 0.001;
    private static final int MAX_HOVER_TICKS = 7;
    private static final double MIN_EXPECTED_FALL = 0.015;

    // State tracking
    private int buffer = 0;
    private int hoverTicks = 0;
    private int consecutiveGravityViolations = 0;
    private int consecutiveHoverViolations = 0;

    /**
     * Checks for gravity violations (not falling when expected)
     * 
     * @param player The player to check
     * @param dy Vertical movement amount
     * @param onGround Whether the player is on ground
     * @param isExempt Whether the player is exempt from checks
     * @param tolerance Tolerance value for checks
     * @return Violation details or null if no violation
     */
    public String checkGravityViolation(Player player, double dy, boolean onGround, boolean isExempt, double tolerance) {
        if (isExempt || onGround) {
            // Reset violation counters when exempt or on ground
            if (buffer > 0) buffer = Math.max(0, buffer - BUFFER_DECREMENT);
            hoverTicks = 0;
            consecutiveGravityViolations = 0;
            consecutiveHoverViolations = 0;
            return null;
        }

        // Check for hovering (minimal vertical movement)
        if (Math.abs(dy) < HOVER_THRESHOLD) {
            hoverTicks++;
            
            if (hoverTicks > MAX_HOVER_TICKS) {
                consecutiveHoverViolations++;
                buffer += 2;
                
                if (buffer >= BUFFER_THRESHOLD && consecutiveHoverViolations >= 3) {
                    return String.format("Hovering in air for %d ticks (dy: %.5f)", hoverTicks, dy);
                }
            }
        } else {
            hoverTicks = 0;
        }

        // Check for insufficient falling (gravity violation)
        if (dy > -MIN_EXPECTED_FALL && dy < HOVER_THRESHOLD) {
            consecutiveGravityViolations++;
            buffer++;
            
            if (buffer >= BUFFER_THRESHOLD && consecutiveGravityViolations >= 3) {
                return String.format("Insufficient falling (dy: %.5f, expected: %.5f)", dy, -MIN_EXPECTED_FALL);
            }
        } else {
            // Decay buffer when complying with gravity
            if (buffer > 0) buffer = Math.max(0, buffer - BUFFER_DECREMENT);
            consecutiveGravityViolations = 0;
        }

        return null;
    }

    /**
     * Resets the state of the gravity check
     */
    public void reset() {
        buffer = 0;
        hoverTicks = 0;
        consecutiveGravityViolations = 0;
        consecutiveHoverViolations = 0;
    }
} 