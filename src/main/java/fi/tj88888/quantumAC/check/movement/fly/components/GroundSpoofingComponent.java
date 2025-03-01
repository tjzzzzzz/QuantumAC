package fi.tj88888.quantumAC.check.movement.fly.components;

import fi.tj88888.quantumAC.check.ViolationData;
import org.bukkit.entity.Player;

/**
 * Component to detect when players are spoofing their ground status.
 * This checks for cases where the client claims to be on ground, but server verification shows otherwise.
 */
public class GroundSpoofingComponent {

    // Detection constants
    private static final int MAX_GROUND_SPOOFING_VIOLATIONS = 10; // Max allowed ground spoofing violations
    private static final int BUFFER_THRESHOLD = 8;
    private static final int BUFFER_DECREMENT = 1;

    // State tracking
    private int groundSpoofBuffer = 0;
    private int groundSpoofVL = 0;
    private int groundSpoofViolations = 0; // Count of ground spoofing instances
    private int consecutiveDetections = 0;
    private long lastFlag = 0;
    
    /**
     * Checks for ground spoofing violations
     * 
     * @param player The player to check
     * @param clientOnGround Whether the client claims to be on ground
     * @param serverOnGround Whether the server detects the player is on ground
     * @param isNearGroundBlock Whether the player is near any potential ground blocks
     * @param currentY The current Y position of the player
     * @param tolerance Additional tolerance to apply to threshold
     * @return ViolationData if a violation was detected, null otherwise
     */
    public ViolationData checkGroundSpoofing(Player player, boolean clientOnGround, boolean serverOnGround, 
                                           boolean isNearGroundBlock, double currentY, double tolerance) {
        // Reset violation count if player is legitimately on ground
        if (serverOnGround) {
            groundSpoofViolations = Math.max(0, groundSpoofViolations - 1);
            groundSpoofBuffer = Math.max(0, groundSpoofBuffer - BUFFER_DECREMENT);
            return null;
        }
        
        // Ground spoofing = client says on ground, but server says not on ground
        if (clientOnGround && !serverOnGround) {
            // Skip if player is near any potential ground blocks (reduce false positives)
            if (isNearGroundBlock) {
                return null;
            }
            
            // Increment violations
            groundSpoofViolations++;
            
            // Increment buffer based on violation count
            groundSpoofBuffer++;
            
            // Only flag if buffer threshold is reached and we have multiple violations
            if (groundSpoofBuffer >= BUFFER_THRESHOLD && groundSpoofViolations >= 3) {
                // Reset buffer partially after flagging
                groundSpoofBuffer = Math.max(0, groundSpoofBuffer - 2);
                
                // Update tracking variables
                groundSpoofVL++;
                lastFlag = System.currentTimeMillis();
                consecutiveDetections++;
                
                // Create violation data with detailed information
                return new ViolationData(
                    String.format(
                        "ground-spoof: y=%.2f, violations=%d, consecutive=%d", 
                        currentY, groundSpoofViolations, consecutiveDetections
                    ),
                    groundSpoofVL
                );
            }
        } else {
            // Legitimate ground state, decrease buffer
            groundSpoofBuffer = Math.max(0, groundSpoofBuffer - BUFFER_DECREMENT);
        }
        
        // Limit violations to prevent overflow
        if (groundSpoofViolations > MAX_GROUND_SPOOFING_VIOLATIONS) {
            groundSpoofViolations = MAX_GROUND_SPOOFING_VIOLATIONS;
        }
        
        return null;
    }
    
    /**
     * Gets the current number of ground spoofing violations
     */
    public int getGroundSpoofViolations() {
        return groundSpoofViolations;
    }
    
    /**
     * Resets the ground spoofing detection state
     */
    public void reset() {
        groundSpoofBuffer = 0;
        groundSpoofVL = 0;
        groundSpoofViolations = 0;
        consecutiveDetections = 0;
        lastFlag = 0;
    }
} 