package fi.tj88888.quantumAC.check.combat.killaura.components;

import org.bukkit.entity.Player;
import fi.tj88888.quantumAC.check.ViolationData;

/**
 * Component to detect KillAura cheats by checking if players maintain sprint speed when attacking.
 * In vanilla Minecraft, players should slow down when hitting entities while sprinting.
 */
public class SprintSpeedComponent {

    // Detection constants
    private static final double ATTACK_SLOWDOWN_THRESHOLD = 0.6; // Expected slowdown percentage
    private static final double CONSISTENCY_THRESHOLD = 0.05; // Maximum allowed variance in speed patterns
    private static final int RESET_VIOLATION_TIME = 5000; // Time in ms to reset violations
    private static final int BUFFER_THRESHOLD = 5; // Threshold for buffering violations
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
     * Checks for sprint speed violations when a player attacks
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

        // Calculate expected speed reduction
        double expectedSlowdown = baseSpeed * ATTACK_SLOWDOWN_THRESHOLD;
        double expectedSpeed = baseSpeed - expectedSlowdown;
        
        // Calculate speed consistency (variation)
        double speedConsistency = calculateConsistency(recentSpeeds);
        
        // Check if the player's speed after attack is too high
        boolean speedTooHigh = currentSpeed > (expectedSpeed + tolerance);
        
        // Check for consistent speeds (minimal variation) which is suspicious
        boolean tooConsistent = speedConsistency < CONSISTENCY_THRESHOLD && currentSpeed > 0.1;

        // Violation detected if speed is too high or too consistent after attack
        if ((speedTooHigh || tooConsistent) && isNewAttack) {
            buffer++;
            
            // Only flag if buffer threshold is reached
            if (buffer >= BUFFER_THRESHOLD) {
                // Reset buffer partially after flagging
                buffer = Math.max(0, buffer - 2);
                
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
                        "speed=%.2f, expected=%.2f, base=%.2f, consistent=%.3f, consecutive=%d",
                        currentSpeed, expectedSpeed, baseSpeed, speedConsistency, consecutiveDetections
                    ),
                    sprintAttackVL
                );
            }
        } else {
            // Decrease buffer on legitimate moves
            buffer = Math.max(0, buffer - BUFFER_DECREMENT);
        }
        
        return null;
    }
    
    /**
     * Calculate the consistency (lack of variance) in the recent speeds
     * Lower values indicate more consistent (potentially suspicious) speeds
     */
    private double calculateConsistency(double[] speeds) {
        double sum = 0;
        int count = 0;
        
        for (double speed : speeds) {
            if (speed > 0) {
                sum += speed;
                count++;
            }
        }
        
        if (count < 2) {
            return 1.0; // Not enough data to calculate consistency
        }
        
        double avg = sum / count;
        
        // Calculate standard deviation
        double variance = 0;
        for (double speed : speeds) {
            if (speed > 0) {
                variance += Math.pow(speed - avg, 2);
            }
        }
        
        return Math.sqrt(variance / count) / avg; // Coefficient of variation
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