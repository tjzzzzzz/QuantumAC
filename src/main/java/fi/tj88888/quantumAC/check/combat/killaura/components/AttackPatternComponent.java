package fi.tj88888.quantumAC.check.combat.killaura.components;

import java.util.LinkedList;
import java.util.Queue;

/**
 * AttackPatternComponent - Detects if a player's attack pattern is suspiciously consistent
 * This can indicate auto-clicker or macro usage
 */
public class AttackPatternComponent {

    // Detection constants
    private static final int SAMPLE_SIZE = 20;
    private static final double MIN_STANDARD_DEVIATION = 30.0; // ms
    private static final long RESET_VIOLATION_TIME = 10000; // ms
    private static final int CONSECUTIVE_THRESHOLD = 2;
    private static final int VL_THRESHOLD = 2;

    // State tracking
    private final Queue<Long> attackTimes = new LinkedList<>();
    private final Queue<Long> attackIntervals = new LinkedList<>();
    private int patternVL = 0;
    private long lastFlag = 0;
    private int consecutiveDetections = 0;

    /**
     * Checks if the player's attack pattern is suspiciously consistent
     * 
     * @param attackTime The time of the current attack
     * @return A violation message if detected, null otherwise
     */
    public String checkAttackPattern(long attackTime) {
        // Reset violations after time
        if (shouldResetViolations()) {
            patternVL = Math.max(0, patternVL - 1);
            consecutiveDetections = 0;
        }

        // Add the current attack time to our samples
        if (!attackTimes.isEmpty()) {
            Long lastAttack = attackTimes.peek();
            if (lastAttack == null) {
                attackTimes.add(attackTime);
                return null;
            }
            
            long interval = attackTime - lastAttack;
            
            // Only consider reasonable intervals (between 50ms and 2000ms)
            if (interval >= 50 && interval <= 2000) {
                attackIntervals.add(interval);
                
                // Keep only the most recent intervals
                while (attackIntervals.size() > SAMPLE_SIZE) {
                    attackIntervals.poll();
                }
            }
        }
        
        attackTimes.add(attackTime);
        
        // Keep only the most recent attack times
        while (attackTimes.size() > SAMPLE_SIZE + 1) {
            attackTimes.poll();
        }
        
        // Need at least 10 intervals to analyze pattern
        if (attackIntervals.size() < 10) {
            return null;
        }
        
        // Calculate standard deviation and average of attack intervals
        double[] stats = calculateStats(attackIntervals);
        double stdDev = stats[0];
        double avg = stats[1];
        
        // Check if the attack pattern is too consistent
        if (stdDev < MIN_STANDARD_DEVIATION) {
            consecutiveDetections++;
            
            // Require multiple consecutive detections before flagging
            if (consecutiveDetections >= CONSECUTIVE_THRESHOLD) {
                patternVL++;
                
                if (patternVL >= VL_THRESHOLD) {
                    String violation = "Attack pattern too consistent: StdDev=" + String.format("%.2f", stdDev) + 
                                      "ms, Avg=" + String.format("%.2f", avg) + "ms";
                    lastFlag = System.currentTimeMillis();
                    patternVL = 0;
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
     * Calculate standard deviation and average of attack intervals
     * 
     * @param intervals Queue of attack intervals
     * @return Array with [standardDeviation, average]
     */
    private double[] calculateStats(Queue<Long> intervals) {
        double sum = 0;
        double sumSquared = 0;
        int count = intervals.size();
        
        for (long interval : intervals) {
            sum += interval;
            sumSquared += interval * interval;
        }
        
        double avg = sum / count;
        double variance = (sumSquared / count) - (avg * avg);
        double stdDev = Math.sqrt(variance);
        
        return new double[] { stdDev, avg };
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
        attackIntervals.clear();
        patternVL = 0;
        lastFlag = 0;
        consecutiveDetections = 0;
    }
} 