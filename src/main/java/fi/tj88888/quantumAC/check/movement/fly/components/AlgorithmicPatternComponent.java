package fi.tj88888.quantumAC.check.movement.fly.components;

import fi.tj88888.quantumAC.check.ViolationData;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Component to detect algorithmic flying patterns like step functions or sine waves.
 * This detects when a player's movement follows mathematical patterns that aren't possible
 * in legitimate gameplay.
 */
public class AlgorithmicPatternComponent {

    // Detection constants
    private static final int PATTERN_BUFFER_THRESHOLD = 10;
    private static final int BUFFER_DECREMENT = 1;
    private static final int MIN_TRAJECTORY_POINTS = 10;
    private static final int MAX_TRAJECTORY_POINTS = 40;
    private static final double STEP_PATTERN_THRESHOLD = 0.002; // Max variance for step pattern
    private static final double SINE_PATTERN_THRESHOLD = 0.05; // Max variance from sine curve
    private static final double REGULARITY_THRESHOLD = 0.98; // Regularity score threshold (0-1)

    // State tracking
    private int patternBuffer = 0;
    private int patternVL = 0;
    private int consecutiveDetections = 0;
    private long lastFlag = 0;
    
    // Pattern tracking
    private final List<TrajectoryPoint> trajectoryPoints = new ArrayList<>();
    private double lastPatternMatchScore = 0.0;
    private long lastPatternDetectionTime = 0;
    private int consecutivePatternMatches = 0;
    
    /**
     * Checks for algorithmic pattern violations
     * 
     * @param player The player to check
     * @param location The current location of the player
     * @param dx The X movement since the last position
     * @param dy The Y movement since the last position
     * @param dz The Z movement since the last position
     * @param horizontalDistance The horizontal movement distance
     * @param distance3D The 3D movement distance
     * @param airTicks The number of ticks the player has been in air
     * @param tolerance Additional tolerance to apply to threshold
     * @return ViolationData if a violation was detected, null otherwise
     */
    public ViolationData checkAlgorithmicPattern(Player player, Location location, 
                                              double dx, double dy, double dz,
                                              double horizontalDistance, double distance3D,
                                              int airTicks, double tolerance) {
        // Add trajectory point
        addTrajectoryPoint(location, dx, dy, dz, horizontalDistance, distance3D);
        
        // Skip if not enough points
        if (trajectoryPoints.size() < MIN_TRAJECTORY_POINTS || airTicks <= 5) {
            return null;
        }
        
        // Get patterns to check
        double stepPatternScore = detectStepPattern();
        double sinePatternScore = detectSinePattern();
        double regularityScore = calculateRegularityScore();
        
        // Get the highest pattern match score
        double highestScore = Math.max(Math.max(stepPatternScore, sinePatternScore), regularityScore);
        lastPatternMatchScore = highestScore;
        
        // Adjust threshold based on tolerance
        double adjustedThreshold = REGULARITY_THRESHOLD - tolerance;
        
        // If we found a consistent pattern
        if (highestScore > adjustedThreshold) {
            // Determine which pattern type matched
            String patternType = "unknown";
            if (stepPatternScore > adjustedThreshold) {
                patternType = "step";
            } else if (sinePatternScore > adjustedThreshold) {
                patternType = "sine";
            } else if (regularityScore > adjustedThreshold) {
                patternType = "regular";
            }
            
            // Check for consecutive pattern matches
            long timeSinceLastDetection = System.currentTimeMillis() - lastPatternDetectionTime;
            if (timeSinceLastDetection < 2000) {
                consecutivePatternMatches++;
            } else {
                consecutivePatternMatches = 1;
            }
            
            lastPatternDetectionTime = System.currentTimeMillis();
            
            // Increment buffer for high pattern match score
            patternBuffer++;
            
            // Only flag if buffer threshold is reached and we have multiple consecutive matches
            if (patternBuffer >= PATTERN_BUFFER_THRESHOLD && consecutivePatternMatches >= 3) {
                // Reset buffer partially after flagging
                patternBuffer = Math.max(0, patternBuffer - 2);
                
                // Update tracking variables
                patternVL++;
                lastFlag = System.currentTimeMillis();
                consecutiveDetections++;
                
                // Create violation data with detailed information
                return new ViolationData(
                    String.format(
                        "algorithmic-pattern: type=%s, score=%.3f, consecutive=%d, air-ticks=%d",
                        patternType, highestScore, consecutivePatternMatches, airTicks
                    ),
                    patternVL
                );
            }
        } else {
            // Decrease buffer on legitimate moves
            patternBuffer = Math.max(0, patternBuffer - BUFFER_DECREMENT);
        }
        
        return null;
    }
    
    /**
     * Add a trajectory point to the history
     */
    private void addTrajectoryPoint(Location location, double dx, double dy, double dz, 
                                   double horizontalDistance, double distance3D) {
        TrajectoryPoint point = new TrajectoryPoint(
            location.getX(), location.getY(), location.getZ(),
            dx, dy, dz, horizontalDistance, distance3D,
            System.currentTimeMillis()
        );
        
        trajectoryPoints.add(point);
        
        // Maintain maximum size
        if (trajectoryPoints.size() > MAX_TRAJECTORY_POINTS) {
            trajectoryPoints.remove(0);
        }
    }
    
    /**
     * Detect step function pattern in vertical movement
     */
    private double detectStepPattern() {
        if (trajectoryPoints.size() < MIN_TRAJECTORY_POINTS) {
            return 0.0;
        }
        
        List<Double> yValues = new ArrayList<>();
        for (TrajectoryPoint point : trajectoryPoints) {
            yValues.add(point.y);
        }
        
        double totalVariance = 0.0;
        int segments = 0;
        
        for (int i = 1; i < yValues.size() - 1; i++) {
            // Calculate local variance around this point
            double prevDiff = Math.abs(yValues.get(i) - yValues.get(i-1));
            double nextDiff = Math.abs(yValues.get(i) - yValues.get(i+1));
            double localVariance = (prevDiff + nextDiff) / 2.0;
            
            totalVariance += localVariance;
            segments++;
        }
        
        if (segments == 0) return 0.0;
        
        double avgVariance = totalVariance / segments;
        
        // Lower variance indicates more step-like pattern
        return avgVariance < STEP_PATTERN_THRESHOLD ? 1.0 - (avgVariance / STEP_PATTERN_THRESHOLD) : 0.0;
    }
    
    /**
     * Detect sine wave pattern in vertical movement
     */
    private double detectSinePattern() {
        if (trajectoryPoints.size() < MIN_TRAJECTORY_POINTS) {
            return 0.0;
        }
        
        List<Double> yValues = new ArrayList<>();
        for (TrajectoryPoint point : trajectoryPoints) {
            yValues.add(point.y);
        }
        
        // Try different frequencies
        double bestScore = 0.0;
        for (double freq = 0.1; freq <= 2.0; freq += 0.1) {
            double score = calculateSineMatchScore(yValues, freq);
            if (score > bestScore) {
                bestScore = score;
            }
        }
        
        return bestScore;
    }
    
    /**
     * Calculate how well a set of Y values matches a sine wave
     */
    private double calculateSineMatchScore(List<Double> values, double frequency) {
        if (values.size() < 8) return 0.0;
        
        // Normalize the values
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (double val : values) {
            min = Math.min(min, val);
            max = Math.max(max, val);
        }
        
        double range = max - min;
        if (range < 0.1) return 0.0; // Too small range to analyze
        
        // Calculate theoretical sine wave
        double[] expectedValues = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            double x = (double)i / values.size() * 2 * Math.PI * frequency;
            expectedValues[i] = min + range * 0.5 * (1 + Math.sin(x));
        }
        
        // Calculate total deviation from sine wave
        double totalDev = 0.0;
        for (int i = 0; i < values.size(); i++) {
            double normVal = (values.get(i) - min) / range;
            double normExpected = (expectedValues[i] - min) / range;
            totalDev += Math.abs(normVal - normExpected);
        }
        
        double avgDev = totalDev / values.size();
        
        // Convert to score (1.0 = perfect match)
        return avgDev < SINE_PATTERN_THRESHOLD ? 1.0 - (avgDev / SINE_PATTERN_THRESHOLD) : 0.0;
    }
    
    /**
     * Calculate regularity score of movement
     */
    private double calculateRegularityScore() {
        if (trajectoryPoints.size() < MIN_TRAJECTORY_POINTS) {
            return 0.0;
        }
        
        // Extract values to analyze
        List<Double> dxValues = new ArrayList<>();
        List<Double> dyValues = new ArrayList<>();
        List<Double> dzValues = new ArrayList<>();
        List<Double> horizDistValues = new ArrayList<>();
        List<Long> timeDeltas = new ArrayList<>();
        
        for (int i = 1; i < trajectoryPoints.size(); i++) {
            TrajectoryPoint current = trajectoryPoints.get(i);
            TrajectoryPoint prev = trajectoryPoints.get(i-1);
            
            dxValues.add(current.dx);
            dyValues.add(current.dy);
            dzValues.add(current.dz);
            horizDistValues.add(current.horizontalDistance);
            timeDeltas.add(current.timestamp - prev.timestamp);
        }
        
        // Calculate coefficient of variation (lower = more regular)
        double dxCV = calculateDoubleCV(dxValues);
        double dyCV = calculateDoubleCV(dyValues);
        double dzCV = calculateDoubleCV(dzValues);
        double horizDistCV = calculateDoubleCV(horizDistValues);
        double timeCV = calculateLongCV(timeDeltas);
        
        // Calculate combined regularity score
        double avgCV = (dxCV + dyCV + dzCV + horizDistCV + timeCV) / 5.0;
        
        // Convert to score (1.0 = extremely regular)
        double regularityScore = Math.max(0.0, 1.0 - avgCV);
        
        return regularityScore;
    }
    
    /**
     * Calculate coefficient of variation for double values
     */
    private double calculateDoubleCV(List<Double> values) {
        if (values.size() < 2) return 1.0;
        
        double sum = 0.0;
        for (double val : values) {
            sum += val;
        }
        double mean = sum / values.size();
        
        // Prevent division by zero
        if (Math.abs(mean) < 0.000001) return 1.0;
        
        double varSum = 0.0;
        for (double val : values) {
            varSum += Math.pow(val - mean, 2);
        }
        double stdDev = Math.sqrt(varSum / values.size());
        
        return stdDev / Math.abs(mean);
    }
    
    /**
     * Calculate coefficient of variation for long values
     */
    private double calculateLongCV(List<Long> values) {
        if (values.size() < 2) return 1.0;
        
        long sum = 0;
        for (long val : values) {
            sum += val;
        }
        double mean = (double)sum / values.size();
        
        // Prevent division by zero
        if (Math.abs(mean) < 0.000001) return 1.0;
        
        double varSum = 0.0;
        for (long val : values) {
            varSum += Math.pow(val - mean, 2);
        }
        double stdDev = Math.sqrt(varSum / values.size());
        
        return stdDev / mean;
    }
    
    /**
     * Clear all trajectory points
     */
    public void clearTrajectory() {
        trajectoryPoints.clear();
    }
    
    /**
     * Get the number of trajectory points
     */
    public int getTrajectorySize() {
        return trajectoryPoints.size();
    }
    
    /**
     * Resets the algorithmic pattern detection state
     */
    public void reset() {
        patternBuffer = 0;
        patternVL = 0;
        consecutiveDetections = 0;
        lastFlag = 0;
        trajectoryPoints.clear();
        lastPatternMatchScore = 0.0;
        lastPatternDetectionTime = 0;
        consecutivePatternMatches = 0;
    }
    
    /**
     * Class to store trajectory point data
     */
    private static class TrajectoryPoint {
        public final double x, y, z;
        public final double dx, dy, dz;
        public final double horizontalDistance;
        public final double distance3D;
        public final long timestamp;
        
        public TrajectoryPoint(double x, double y, double z, 
                               double dx, double dy, double dz, 
                               double horizontalDistance, double distance3D, 
                               long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.horizontalDistance = horizontalDistance;
            this.distance3D = distance3D;
            this.timestamp = timestamp;
        }
    }
}