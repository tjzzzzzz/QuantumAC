package fi.tj88888.quantumAC.check.combat.killaura.components;

import org.bukkit.entity.Player;
import fi.tj88888.quantumAC.check.ViolationData;

/**
 * Component to detect KillAura "keep sprint" cheats by checking if players maintain almost full sprint speed when attacking.
 * In vanilla Minecraft, players should slow down by approximately 60% when hitting entities while sprinting.
 * Many cheats implement a very tiny slowdown (0.0001-0.005) to bypass anti-cheat systems.
 * This component specifically targets these sophisticated cheats.
 */
public class SprintSpeedComponent {

    // Detection constants
    private static final double ATTACK_SLOWDOWN_THRESHOLD = 0.55; // Expected slowdown percentage for legitimate players
    private static final double MIN_CHEAT_SLOWDOWN = 0.0001; // Minimum slowdown cheats typically implement
    private static final double MAX_CHEAT_SLOWDOWN = 0.05; // Maximum slowdown cheats typically implement
    private static final int RESET_VIOLATION_TIME = 5000; // Time in ms to reset violations
    private static final int BUFFER_THRESHOLD = 2; // Reduced threshold for buffering violations (was 3)
    private static final int BUFFER_DECREMENT = 1; // Rate at which buffer decreases on legitimate moves

    // State tracking
    private int sprintAttackVL = 0;
    private long lastFlag = 0;
    private int consecutiveDetections = 0;
    private int buffer = 0;
    
    // Movement tracking
    private final double[] recentSpeeds = new double[5]; // Tracks most recent speeds
    private double lastAttackSpeed = 0.0;
    private double lastBaseSpeed = 0.0;
    private long lastAttackTime = 0;

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
        // Only check if the player is sprinting
        if (!sprinting) {
            // Decrease buffer on legitimate moves
            buffer = Math.max(0, buffer - BUFFER_DECREMENT);
            return null;
        }

        // Update attack time tracking
        boolean isNewAttack = (attackTime - lastAttackTime) > 100; // Ensure it's not the same attack
        lastAttackTime = attackTime;

        // Store current speed in the circular buffer
        System.arraycopy(recentSpeeds, 0, recentSpeeds, 1, recentSpeeds.length - 1);
        recentSpeeds[0] = currentSpeed;

        // Calculate expected speed reduction for legitimate players
        double expectedSlowdown = baseSpeed * ATTACK_SLOWDOWN_THRESHOLD;
        double expectedSpeed = baseSpeed - expectedSlowdown;
        
        // Calculate actual slowdown percentage (how much they actually slowed down)
        double actualSlowdown = baseSpeed - currentSpeed;
        double slowdownPercentage = actualSlowdown / baseSpeed;
        
        // Check for "keep sprint" cheats (very minimal slowdown)
        boolean isKeepSprint = slowdownPercentage >= MIN_CHEAT_SLOWDOWN && 
                              slowdownPercentage <= MAX_CHEAT_SLOWDOWN && 
                              currentSpeed > (expectedSpeed + tolerance);

        // Enhanced debug information (for logging purposes)
        String debugInfo = String.format(
            "currentSpeed=%.5f, expectedSpeed=%.5f, slowdownPercentage=%.5f, isKeepSprint=%s, buffer=%d",
            currentSpeed, expectedSpeed, slowdownPercentage, isKeepSprint, buffer
        );

        // Violation detected if "keep sprint" pattern is identified
        if (isKeepSprint && isNewAttack) {
            buffer++;
            
            // Only flag if buffer threshold is reached
            if (buffer >= BUFFER_THRESHOLD) {
                // Reset buffer partially after flagging
                buffer = Math.max(0, buffer - 1);
                
                // Check for violations reset timeout
                if (shouldResetViolations()) {
                    resetViolations();
                    consecutiveDetections = 1;
                } else {
                    consecutiveDetections++;
                }
                
                // Update tracking variables
                lastFlag = System.currentTimeMillis();
                sprintAttackVL++;
                lastAttackSpeed = currentSpeed;
                lastBaseSpeed = baseSpeed;
                
                // Create violation data with detailed information
                return new ViolationData(
                    String.format(
                        "keepSprint detected: slowdown=%.5f%%, speed=%.5f, expected=%.5f, base=%.5f, consecutive=%d",
                        slowdownPercentage * 100, currentSpeed, expectedSpeed, baseSpeed, consecutiveDetections
                    ),
                    sprintAttackVL
                );
            }
        } else if (slowdownPercentage > MAX_CHEAT_SLOWDOWN && slowdownPercentage < ATTACK_SLOWDOWN_THRESHOLD) {
            // If slowdown is more than what cheats typically implement but less than legitimate,
            // we decrease buffer but not as much as with legitimate moves
            buffer = Math.max(0, buffer - 1);
        } else if (slowdownPercentage >= ATTACK_SLOWDOWN_THRESHOLD) {
            // Decrease buffer more for clearly legitimate moves
            buffer = Math.max(0, buffer - BUFFER_DECREMENT);
        }
        
        return null;
    }
    
    /**
     * Reset violation tracking
     */
    public void resetViolations() {
        sprintAttackVL = 0;
        consecutiveDetections = 0;
        buffer = 0;
    }
    
    /**
     * Check if enough time has passed to reset violations
     */
    private boolean shouldResetViolations() {
        return (System.currentTimeMillis() - lastFlag) > RESET_VIOLATION_TIME;
    }
    
    /**
     * Reset the component's state
     */
    public void reset() {
        sprintAttackVL = 0;
        consecutiveDetections = 0;
        buffer = 0;
        lastFlag = 0;
        lastAttackSpeed = 0.0;
        lastBaseSpeed = 0.0;
        lastAttackTime = 0;
        for (int i = 0; i < recentSpeeds.length; i++) {
            recentSpeeds[i] = 0.0;
        }
    }
} 