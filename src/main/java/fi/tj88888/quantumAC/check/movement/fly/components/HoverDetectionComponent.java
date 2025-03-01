package fi.tj88888.quantumAC.check.movement.fly.components;

import fi.tj88888.quantumAC.check.ViolationData;
import org.bukkit.entity.Player;

/**
 * Component to detect players hovering in the air without sufficient vertical movement.
 * This detects cases where a player stays at nearly the same Y level for an extended time.
 */
public class HoverDetectionComponent {

    // Detection constants
    private static final double HOVER_DISTANCE_THRESHOLD = 0.03; // Max allowed hover difference
    private static final long HOVER_TIME_THRESHOLD = 1500; // 1.5 seconds for hover timeout
    private static final int BUFFER_THRESHOLD = 8;
    private static final int BUFFER_DECREMENT = 1;

    // State tracking
    private int hoverBuffer = 0;
    private int hoverVL = 0;
    private int consecutiveDetections = 0;
    private long lastFlag = 0;
    
    // Hover tracking
    private int hoverSamples = 0;
    private double baseHoverY = 0.0;
    private long hoverStartTime = 0;
    
    /**
     * Checks for hovering violations
     * 
     * @param player The player to check
     * @param currentY The current Y position of the player
     * @param dy The vertical movement since the last position
     * @param tolerance Additional tolerance to apply to threshold
     * @return ViolationData if a violation was detected, null otherwise
     */
    public ViolationData checkHovering(Player player, double currentY, double dy, double tolerance) {
        // Track hover position and time
        double adjustedThreshold = HOVER_DISTANCE_THRESHOLD + tolerance;
        
        // Initialize hover tracking if not already started
        if (hoverSamples == 0) {
            baseHoverY = currentY;
            hoverStartTime = System.currentTimeMillis();
            hoverSamples = 1;
            return null;
        }
        
        // Increment sample count
        hoverSamples++;
        
        // Calculate distance from base hover position
        double hoverDistance = Math.abs(currentY - baseHoverY);
        
        // Check if player is hovering (minimal vertical movement)
        boolean isHovering = hoverDistance <= adjustedThreshold;
        
        // Calculate hover duration
        long hoverDuration = System.currentTimeMillis() - hoverStartTime;
        
        // Violation if hovering for too long
        if (isHovering && hoverDuration >= HOVER_TIME_THRESHOLD && hoverSamples >= 10) {
            hoverBuffer++;
            
            // Only flag if buffer threshold is reached
            if (hoverBuffer >= BUFFER_THRESHOLD) {
                // Reset buffer partially after flagging
                hoverBuffer = Math.max(0, hoverBuffer - 2);
                
                // Update tracking variables
                hoverVL++;
                lastFlag = System.currentTimeMillis();
                consecutiveDetections++;
                
                // Create violation data with detailed information
                return new ViolationData(
                    String.format(
                        "hover: time=%dms, y=%.2f, distance=%.4f, samples=%d, consecutive=%d",
                        hoverDuration, currentY, hoverDistance, hoverSamples, consecutiveDetections
                    ),
                    hoverVL
                );
            }
        } else if (hoverDistance > adjustedThreshold) {
            // Not hovering anymore, update base position
            baseHoverY = currentY;
            hoverStartTime = System.currentTimeMillis();
            hoverSamples = 1;
            
            // Decrease buffer
            hoverBuffer = Math.max(0, hoverBuffer - BUFFER_DECREMENT);
        }
        
        return null;
    }
    
    /**
     * Resets the hover detection state
     */
    public void reset() {
        hoverBuffer = 0;
        hoverVL = 0;
        consecutiveDetections = 0;
        lastFlag = 0;
        hoverSamples = 0;
        baseHoverY = 0.0;
        hoverStartTime = 0;
    }
} 