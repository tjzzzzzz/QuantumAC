package fi.tj88888.quantumAC.check.combat.killaura.components;

import org.bukkit.entity.Player;
import fi.tj88888.quantumAC.check.ViolationData;

/**
 * Component to detect KillAura "keep sprint" cheats by checking if players maintain almost full sprint speed when attacking.
 * In vanilla Minecraft, players should slow down by approximately 60% when hitting entities while sprinting.
 * Many cheats implement a very tiny slowdown (0.0001-0.005) to bypass anti-cheat systems.
 * This component specifically targets these sophisticated cheats using proven threshold-based detection.
 */
public class SprintSpeedComponent {

    // Detection constants
    private static final double ATTACK_SLOWDOWN_THRESHOLD = 0.6; // Expected slowdown percentage for legitimate players (60%)
    private static final double CHEAT_DETECTION_MULTIPLIER = 0.2; // Flag if slowdown is less than 20% of expected
    private static final int RESET_VIOLATION_TIME = 5000; // Time in ms to reset violations
    
    // Movement tracking
    private final double[] recentSpeeds = new double[5]; // Tracks most recent speeds
    private int speedIndex = 0;
    private boolean speedArrayFilled = false;
    private double lastDeltaXZ = 0.0;
    
    // State tracking
    private double threshold = 0.0;
    private int hits = 0;
    private long lastAttackTime = 0;
    private long lastFlagTime = 0;
    private double lastActualSlowdown = 0.0;
    private double lastExpectedSlowdown = 0.0;

    /**
     * Checks for keep sprint violations when a player attacks
     * 
     * @param player The player to check
     * @param currentSpeed The current horizontal movement speed
     * @param baseSpeed The expected base movement speed
     * @param sprinting Whether the player is sprinting
     * @param attackTime The time of the attack
     * @param tolerance Allowed tolerance for speed variations
     * @return ViolationData if a violation was detected, null otherwise
     */
    public ViolationData checkSprintSpeed(Player player, double currentSpeed, double baseSpeed, 
                                        boolean sprinting, long attackTime, double tolerance) {
        long now = System.currentTimeMillis();
        
        // Store current speed in the circular buffer
        recentSpeeds[speedIndex] = currentSpeed;
        speedIndex = (speedIndex + 1) % recentSpeeds.length;
        if (speedIndex == 0) {
            speedArrayFilled = true;
        }
        
        // Check if this is a new attack
        boolean isNewAttack = (attackTime > lastAttackTime);
        if (isNewAttack) {
            hits = 1;
            lastAttackTime = attackTime;
        } else {
            // Only check for a limited number of hits after attack
            if (hits > 0 && hits <= 3) {
                // Only check if player is sprinting and on ground (assumed from caller)
                if (sprinting && player.isOnGround()) {
                    // Calculate expected and actual slowdown
                    double expectedSlowdown = baseSpeed * ATTACK_SLOWDOWN_THRESHOLD;
                    double actualSlowdown = getSpeedDifference();
                    
                    // Save for logging
                    lastActualSlowdown = actualSlowdown;
                    lastExpectedSlowdown = expectedSlowdown;
                    
                    // Check for very low slowdowns (less than 20% of expected)
                    if (actualSlowdown < expectedSlowdown * CHEAT_DETECTION_MULTIPLIER && 
                            isConsistentMovement() && 
                            currentSpeed > baseSpeed * 0.8) {
                        
                        // Build threshold for low slowdown detection
                        threshold += 0.75;
                        
                        // Only flag on high threshold
                        if (threshold > 15) {
                            // Calculate VL based on severity
                            double vlIncrement = 1.0 + (threshold / 20.0);
                            
                            // Reset threshold partially after flagging
                            threshold = Math.max(0, threshold - 2);
                            lastFlagTime = now;
                            
                            // Create violation data with detailed information
                            return new ViolationData(
                                String.format(
                                    "keepSprint detected: very low slowdown=%.5f, expected=%.3f, threshold=%.1f",
                                    actualSlowdown, expectedSlowdown, threshold
                                ),
                                (int)vlIncrement
                            );
                        }
                    } else {
                        // Normal slowdown, decrease threshold
                        threshold = Math.max(0, threshold - 2.0);
                    }
                } else {
                    // Not sprinting or not on ground, decrease threshold
                    threshold = Math.max(0, threshold - 1.5);
                }
            } else if ((now - lastAttackTime) >= 500) {
                // Reset hits counter if it's been too long since the last attack
                hits = 0;
                
                // Rapidly decrease threshold between combat encounters
                if ((now - lastAttackTime) > 2000) { // 2 seconds without combat
                    threshold = Math.max(0, threshold - 3.0);
                } else {
                    threshold = Math.max(0, threshold - 1.0);
                }
            }
            
            // Reset hit counter after several movement packets
            if (hits > 8) {
                hits = 0;
                threshold = Math.max(0, threshold - 2.0);
            }
            
            // Always gradually decrease threshold over time
            if (threshold > 0) {
                threshold = Math.max(0, threshold - 0.1);
            }
            
            // Increment hit count
            hits++;
        }
        
        // Save current speed as last speed
        lastDeltaXZ = currentSpeed;
        
        return null;
    }
    
    /**
     * Calculate the speed difference (slowdown) based on recent speeds
     */
    private double getSpeedDifference() {
        if (!speedArrayFilled) return 0.0;
        
        double maxSpeed = 0;
        double minSpeed = Double.MAX_VALUE;
        
        for (double speed : recentSpeeds) {
            if (speed > maxSpeed) maxSpeed = speed;
            if (speed < minSpeed) minSpeed = speed;
        }
        
        return maxSpeed - minSpeed;
    }
    
    /**
     * Check if player's movement is consistent (common trait of cheats)
     */
    private boolean isConsistentMovement() {
        if (!speedArrayFilled) return false;
        
        double sum = 0;
        double count = 0;
        
        for (double speed : recentSpeeds) {
            if (speed > 0) {
                sum += speed;
                count++;
            }
        }
        
        if (count < 2) return false;
        
        double avg = sum / count;
        double variance = 0;
        
        for (double speed : recentSpeeds) {
            if (speed > 0) {
                variance += Math.pow(speed - avg, 2);
            }
        }
        
        // Check variance - low variance means consistent movement (suspicious)
        double stdDev = Math.sqrt(variance / count);
        return (stdDev / avg) < 0.05; // Coefficient of variation < 5%
    }
    
    /**
     * Reset violation tracking
     */
    public void resetViolations() {
        threshold = 0;
        hits = 0;
    }
    
    /**
     * Get the current threshold value for debugging
     */
    public double getThreshold() {
        return threshold;
    }
    
    /**
     * Get the last detected actual slowdown for debugging
     */
    public double getLastActualSlowdown() {
        return lastActualSlowdown;
    }
    
    /**
     * Get the last expected slowdown for debugging
     */
    public double getLastExpectedSlowdown() {
        return lastExpectedSlowdown;
    }
    
    /**
     * Reset the component's state
     */
    public void reset() {
        threshold = 0;
        hits = 0;
        lastAttackTime = 0;
        lastFlagTime = 0;
        lastActualSlowdown = 0.0;
        lastExpectedSlowdown = 0.0;
        lastDeltaXZ = 0.0;
        speedIndex = 0;
        speedArrayFilled = false;
        
        for (int i = 0; i < recentSpeeds.length; i++) {
            recentSpeeds[i] = 0.0;
        }
    }
} 