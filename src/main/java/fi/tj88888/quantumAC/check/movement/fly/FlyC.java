package fi.tj88888.quantumAC.check.movement.fly;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.ViolationData;
import fi.tj88888.quantumAC.check.movement.fly.components.AlgorithmicPatternComponent;
import fi.tj88888.quantumAC.check.movement.fly.components.PhaseDetectionComponent;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * FlyC - Specialized in detecting algorithmic flight patterns and trajectories
 * This has been refactored to use the component-based approach.
 *
 * This check focuses on:
 * 1. Detecting unnatural flight trajectories and patterns
 * 2. Finding mathematical regularities in movement (step function, sine wave, etc.)
 * 3. Analyzing 3D movement consistency over time
 * 4. Detecting "phase" fly hacks (moving through blocks)
 */
public class FlyC extends FlyCheck {

    // Components for different detection types
    private final AlgorithmicPatternComponent algorithmicPatternComponent;
    private final PhaseDetectionComponent phaseDetectionComponent;
    
    // Detection constants
    private static final int ARC_BUFFER_THRESHOLD = 8;
    private static final int BUFFER_DECREMENT = 1;
    
    // Arc trajectory detection
    private int arcTrajectoryBuffer = 0;
    private int arcTrajectoryVL = 0;
    
    // State tracking
    private boolean wasOnGround = true;
    private int airTicks = 0;

    public FlyC(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "FlyC");
        this.algorithmicPatternComponent = new AlgorithmicPatternComponent();
        this.phaseDetectionComponent = new PhaseDetectionComponent();
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (!isMovementPacket(event.getPacketType())) {
            return;
        }

        Player player = event.getPlayer();

        // Skip if player is exempt from checks
        if (isExempt(player)) {
            resetDetectionState();
            return;
        }

        // Get locations for movement calculation
        Location from = playerData.getLastLocation();
        Location to = player.getLocation();

        if (from == null || !from.getWorld().equals(to.getWorld())) {
            playerData.setLastLocation(to);
            return;
        }

        // Calculate movement
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double distance3D = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Analyze environment
        boolean onGround = player.isOnGround();
        boolean inLiquid = isInLiquid(player);
        boolean onClimbable = isOnClimbable(player);
        boolean inWeb = isInWeb(player);
        boolean hasLevitation = player.hasPotionEffect(PotionEffectType.LEVITATION);
        boolean hasSlowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
        boolean nearGround = isNearGround(player);
        boolean nearCeiling = isNearCeiling(player);

        // Update air time tracking
        if (onGround) {
            airTicks = 0;
            algorithmicPatternComponent.clearTrajectory();
        } else {
            airTicks++;
        }

        // Skip checks for exempt conditions
        if (isRecentlyTeleported() ||
                isRecentlyDamaged() ||
                isRecentlyVelocity() ||
                inLiquid ||
                onClimbable ||
                inWeb ||
                hasLevitation) {

            updateState(to, onGround);
            return;
        }

        // Calculate tolerance based on ping and conditions
        double tolerance = calculateTolerance(player);
        
        // Only run pattern analysis with enough trajectory points and when in air
        if (!onGround && airTicks > 5) {
            // Analyze movement patterns using component
            ViolationData patternData = algorithmicPatternComponent.checkAlgorithmicPattern(
                player, to, dx, dy, dz, horizontalDistance, distance3D, airTicks, tolerance
            );
            
            if (patternData != null) {
                flag(player, patternData.getDetails(), patternData.getViolationLevel());
            }

            // Detect arc trajectory violations
            detectArcTrajectoryViolations(player, dy);
        }

        // Phase detection (moving through blocks) using component
        ViolationData phaseData = phaseDetectionComponent.checkPhasing(
            player, from, to, distance3D, tolerance
        );
        
        if (phaseData != null) {
            flag(player, phaseData.getDetails(), phaseData.getViolationLevel());
        }

        // Update state for next check
        updateState(to, onGround);
    }

    /**
     * Detect unnatural arc trajectory in jumps
     */
    private void detectArcTrajectoryViolations(Player player, double dy) {
        // Skip if not enough trajectory points
        if (algorithmicPatternComponent.getTrajectorySize() < 10) {
            return;
        }
        
        // In a normal jump arc, the player should be either:
        // 1. Accelerating upward at the start of jump (positive dy, increasing)
        // 2. Decelerating at the top of jump (dy approaching 0)
        // 3. Accelerating downward due to gravity (negative dy, increasing magnitude)
        
        // Abnormal patterns include constant upward velocity or non-accelerating descents
        
        // This is a simplified check for demonstration purposes
        // A full implementation would analyze the entire trajectory curve
        
        // Increment buffer if we have a suspiciously constant dy value when moving upward
        if (dy > 0.05 && Math.abs(dy - 0.1) < 0.02 && airTicks > 10) {
            arcTrajectoryBuffer++;
            
            if (arcTrajectoryBuffer >= ARC_BUFFER_THRESHOLD) {
                // Reset buffer partially after flagging
                arcTrajectoryBuffer = Math.max(0, arcTrajectoryBuffer - 2);
                
                // Update tracking variables
                arcTrajectoryVL++;
                
                // Flag violation
                String details = String.format(
                    "abnormal-arc: dy=%.4f, air-ticks=%d, constant-upward-velocity",
                    dy, airTicks
                );
                
                flag(player, details, arcTrajectoryVL);
            }
        } else {
            // Decrease buffer on legitimate moves
            arcTrajectoryBuffer = Math.max(0, arcTrajectoryBuffer - BUFFER_DECREMENT);
        }
    }

    /**
     * Update state for next check
     */
    private void updateState(Location location, boolean onGround) {
        wasOnGround = onGround;
    }

    /**
     * Reset all detection state
     */
    private void resetDetectionState() {
        algorithmicPatternComponent.reset();
        phaseDetectionComponent.reset();
        
        arcTrajectoryBuffer = 0;
        arcTrajectoryVL = 0;
        
        airTicks = 0;
        wasOnGround = true;
    }

    @Override
    public void reset() {
        resetDetectionState();
    }
} 