package fi.tj88888.quantumAC.check.movement.fly.components;

import fi.tj88888.quantumAC.check.ViolationData;
import org.bukkit.entity.Player;

/**
 * Component to detect players gliding/slow-falling without permission.
 * This detects cases where a player moves horizontally more than they should when falling.
 */
public class GlideDetectionComponent {

    // Detection constants
    private static final double GLIDE_RATIO_MAX = 3.5; // Maximum glide ratio (horizontal:vertical)
    private static final int BUFFER_THRESHOLD = 10;
    private static final int BUFFER_DECREMENT = 1;

    // State tracking
    private int glideBuffer = 0;
    private int glideVL = 0;
    private int consecutiveDetections = 0;
    private long lastFlag = 0;
    
    // Glide pattern tracking
    private double lastHorizontalDistance = 0.0;
    private double lastVerticalDistance = 0.0;
    private int abnormalGlidePatterns = 0;
    
    /**
     * Checks for gliding violations
     * 
     * @param player The player to check
     * @param horizontalDistance The horizontal movement distance
     * @param dy The vertical movement since the last position
     * @param tolerance Additional tolerance to apply to threshold
     * @return ViolationData if a violation was detected, null otherwise
     */
    public ViolationData checkGliding(Player player, double horizontalDistance, double dy, double tolerance) {
        // Skip if not falling
        if (dy >= 0) {
            glideBuffer = Math.max(0, glideBuffer - BUFFER_DECREMENT);
            return null;
        }
        
        // Convert dy to positive for ratio calculation
        double verticalDistance = Math.abs(dy);
        
        // Skip if vertical movement is too small (division by zero protection)
        if (verticalDistance < 0.005) {
            return null;
        }
        
        // Calculate glide ratio (how far horizontally compared to vertically)
        double glideRatio = horizontalDistance / verticalDistance;
        
        // Adjust max ratio based on tolerance
        double adjustedMaxRatio = GLIDE_RATIO_MAX + tolerance;
        
        // Track consecutive abnormal glide patterns
        if (glideRatio > adjustedMaxRatio) {
            abnormalGlidePatterns++;
        } else {
            abnormalGlidePatterns = Math.max(0, abnormalGlidePatterns - 1);
        }
        
        // Check if the player's glide ratio exceeds the maximum
        if (glideRatio > adjustedMaxRatio && horizontalDistance > 0.1 && abnormalGlidePatterns >= 3) {
            glideBuffer++;
            
            // Only flag if buffer threshold is reached
            if (glideBuffer >= BUFFER_THRESHOLD) {
                // Reset buffer partially after flagging
                glideBuffer = Math.max(0, glideBuffer - 2);
                
                // Update tracking variables
                glideVL++;
                lastFlag = System.currentTimeMillis();
                consecutiveDetections++;
                
                // Create violation data with detailed information
                return new ViolationData(
                    String.format(
                        "glide: ratio=%.2f, h-speed=%.2f, v-speed=%.3f, max-ratio=%.1f, consecutive=%d",
                        glideRatio, horizontalDistance, verticalDistance, adjustedMaxRatio, consecutiveDetections
                    ),
                    glideVL
                );
            }
        } else {
            // Legitimate glide pattern, decrease buffer
            glideBuffer = Math.max(0, glideBuffer - BUFFER_DECREMENT);
        }
        
        // Update tracking variables
        lastHorizontalDistance = horizontalDistance;
        lastVerticalDistance = verticalDistance;
        
        return null;
    }
    
    /**
     * Resets the glide detection state
     */
    public void reset() {
        glideBuffer = 0;
        glideVL = 0;
        consecutiveDetections = 0;
        lastFlag = 0;
        lastHorizontalDistance = 0.0;
        lastVerticalDistance = 0.0;
        abnormalGlidePatterns = 0;
    }
} 