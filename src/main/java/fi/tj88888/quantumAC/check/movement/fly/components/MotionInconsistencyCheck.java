package fi.tj88888.quantumAC.check.movement.fly.components;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Component for checking vertical motion inconsistencies
 */
public class MotionInconsistencyCheck {

    // Detection settings
    private static final int BUFFER_THRESHOLD = 10;
    private static final int BUFFER_DECREMENT = 1;
    private static final double INCONSISTENCY_THRESHOLD = 0.1;
    private static final int MAX_SAMPLES = 10;

    // State tracking
    private int buffer = 0;
    private int consecutiveViolations = 0;
    private final Deque<Double> recentVerticalMovements = new ArrayDeque<>();

    /**
     * Checks for vertical motion inconsistencies (erratic vertical movement)
     * 
     * @param player The player to check
     * @param dy Vertical movement amount
     * @param onGround Whether the player is on ground
     * @param isExempt Whether the player is exempt from checks
     * @param tolerance Tolerance value for checks
     * @return Violation details or null if no violation
     */
    public String checkMotionInconsistency(Player player, double dy, boolean onGround, boolean isExempt, double tolerance) {
        if (isExempt) {
            // Reset violation counters when exempt
            if (buffer > 0) buffer = Math.max(0, buffer - BUFFER_DECREMENT);
            consecutiveViolations = 0;
            recentVerticalMovements.clear();
            return null;
        }

        // Update recent movement history
        recentVerticalMovements.addLast(dy);
        if (recentVerticalMovements.size() > MAX_SAMPLES) {
            recentVerticalMovements.removeFirst();
        }

        // Need enough samples to check for inconsistency
        if (recentVerticalMovements.size() < 3) {
            return null;
        }

        // Check for sudden direction changes or erratic movement
        Double[] movements = recentVerticalMovements.toArray(new Double[0]);
        for (int i = 1; i < movements.length - 1; i++) {
            double prev = movements[i-1];
            double current = movements[i];
            double next = movements[i+1];
            
            // Check for direction change without touching ground
            if (!onGround && 
                ((prev < 0 && current > INCONSISTENCY_THRESHOLD) || 
                 (prev > INCONSISTENCY_THRESHOLD && current < -INCONSISTENCY_THRESHOLD))) {
                
                consecutiveViolations++;
                buffer += 2;
                
                if (buffer >= BUFFER_THRESHOLD && consecutiveViolations >= 2) {
                    return String.format("Vertical motion inconsistency (prev: %.5f, current: %.5f, next: %.5f)", 
                                        prev, current, next);
                }
            }
        }

        // Decay buffer when motion is consistent
        if (buffer > 0) buffer = Math.max(0, buffer - BUFFER_DECREMENT);
        
        // Reset consecutive violations when on ground
        if (onGround) {
            consecutiveViolations = 0;
        }

        return null;
    }

    /**
     * Resets the state of the motion inconsistency check
     */
    public void reset() {
        buffer = 0;
        consecutiveViolations = 0;
        recentVerticalMovements.clear();
    }
} 