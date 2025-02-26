package fi.tj88888.quantumAC.check.movement;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.util.MovementData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;

public class SpeedA extends Check {

    // Base speed constants
    private static final double WALK_BASE = 0.2173;
    private static final double SPRINT_BASE = 0.2806;
    private static final double SPRINT_JUMP = 0.65;

    // Surface modifiers
    private static final double ICE_MODIFIER = 1.55;
    private static final double PACKED_ICE_MODIFIER = 1.6;
    private static final double BLUE_ICE_MODIFIER = 1.65;
    private static final double SLIME_MODIFIER = 1.25;
    private static final double SOUL_SAND_MODIFIER = 0.4;
    private static final double WATER_MODIFIER = 0.5;
    private static final double STAIRS_MODIFIER = 1.1;
    private static final double SLAB_MODIFIER = 1.05;

    // Detection settings - optimized for best detection
    private static final double BASE_TOLERANCE = 0.015;       // Very tight tolerance
    private static final double MAX_SPEED_CAP = 0.65;         // Hard cap on maximum speed
    private static final int BUFFER_THRESHOLD = 5;            // Buffer needed to flag violations
    private static final int STREAK_THRESHOLD = 2;            // Consecutive violations needed
    private static final int BUFFER_DECREMENT = 1;            // How quickly buffer decreases
    private static final long SUSTAINED_SPEED_TIME = 500;     // Time window for sustained detection

    // Teleport exemption duration
    private static final long TELEPORT_EXEMPT_TIME = 2000;    // 2 seconds exempt after teleport

    // Tracking state
    private boolean wasOnGround = true;
    private boolean wasInLiquid = false;
    private boolean wasOnIce = false;
    private boolean wasOnSlime = false;
    private double lastSpeed = 0.0;
    private double lastAllowedSpeed = 0.0;
    private int buffer = 0;
    private int consecutiveViolations = 0;
    private boolean flaggedThisCheck = false;

    // Pattern detection
    private long lastViolationTime = 0;

    // Special case timers
    private long lastJumpTime = 0;
    private long lastDamageTime = 0;
    private long lastVelocityTime = 0;
    private long lastIceTime = 0;
    private long lastTeleportTime = 0;
    private Vector lastVelocity = new Vector(0, 0, 0);

    // Speed history
    private final Deque<Double> speedSamples = new ArrayDeque<>();
    private final Deque<Long> speedTimestamps = new ArrayDeque<>();
    private final Deque<Double> maxSpeedSamples = new ArrayDeque<>();
    private final Deque<Boolean> groundStateHistory = new ArrayDeque<>();

    public SpeedA(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "SpeedA", "Movement");
    }

    @Override
    public void processPacket(PacketEvent event) {
        // Reset flag status for this check iteration
        flaggedThisCheck = false;

        // Filter to just movement packets
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

        // Calculate horizontal movement
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Analyze environment
        boolean onGround = player.isOnGround();
        boolean inLiquid = isInLiquid(player);
        boolean onIce = isOnIce(player);
        boolean onPackedIce = isOnPackedIce(player);
        boolean onBlueIce = isOnBlueIce(player);
        boolean onSlime = isOnSlime(player);
        boolean onSoulSand = isOnSoulSand(player);
        boolean onStairs = isOnStairs(player);
        boolean onSlab = isOnSlab(player);

        // Update ice time tracker
        if (onIce || onPackedIce || onBlueIce) {
            lastIceTime = System.currentTimeMillis();
        }

        // Track jump (ground to air transition)
        if (wasOnGround && !onGround) {
            lastJumpTime = System.currentTimeMillis();
        }

        // Store history with timestamps
        long currentTime = System.currentTimeMillis();
        speedSamples.addLast(horizontalDistance);
        speedTimestamps.addLast(currentTime);
        groundStateHistory.addLast(onGround);
        if (speedSamples.size() > 20) {
            speedSamples.removeFirst();
            speedTimestamps.removeFirst();
            groundStateHistory.removeFirst();
        }

        // Calculate max allowed speed with all modifiers
        double maxAllowedSpeed = calculateMaxAllowedSpeed(player, onGround, inLiquid,
                onIce, onPackedIce, onBlueIce,
                onSlime, onSoulSand, onStairs, onSlab);

        // Apply velocity adjustments
        maxAllowedSpeed = adjustForVelocity(player, maxAllowedSpeed);

        // Apply extra buffer after teleport
        if (isRecentlyTeleported()) {
            maxAllowedSpeed *= 1.5; // 50% more lenient after teleport
        }

        // Apply hard cap on maximum speed
        if (maxAllowedSpeed > MAX_SPEED_CAP) {
            maxAllowedSpeed = MAX_SPEED_CAP;
        }

        // Store for reference
        maxSpeedSamples.addLast(maxAllowedSpeed);
        if (maxSpeedSamples.size() > 20) {
            maxSpeedSamples.removeFirst();
        }
        lastAllowedSpeed = maxAllowedSpeed;

        // Calculate actual tolerance for this measurement
        double effectiveTolerance = calculateTolerance(player, onGround);

        // Check for basic speed violation
        boolean speedViolation = horizontalDistance > maxAllowedSpeed + effectiveTolerance;

        // Only consider substantial violations
        if (speedViolation && horizontalDistance > 0.1) {
            // Calculate severity metrics
            double overSpeed = horizontalDistance - maxAllowedSpeed;
            double severity = horizontalDistance / maxAllowedSpeed;

            // Track consecutive violations and their timing
            consecutiveViolations++;
            long timeSinceLastViolation = currentTime - lastViolationTime;
            lastViolationTime = currentTime;

            // Check for sustained speed pattern (multiple violations in a short timeframe)
            boolean sustainedSpeedHack = consecutiveViolations >= STREAK_THRESHOLD &&
                    timeSinceLastViolation <= SUSTAINED_SPEED_TIME;

            // Check for consistent low-factor speed hack
            boolean lowFactorSpeedHack = checkForLowFactorSpeedHack(horizontalDistance, maxAllowedSpeed, onGround);

            // Increment buffer based on severity and pattern detection
            int bufferIncrement = calculateBufferIncrement(severity);
            if (sustainedSpeedHack) {
                bufferIncrement += 2;
            }
            if (lowFactorSpeedHack) {
                bufferIncrement += 2;
            }
            buffer += bufferIncrement;

            // Check if we should flag this violation
            boolean shouldFlag = buffer >= BUFFER_THRESHOLD ||
                    (consecutiveViolations >= STREAK_THRESHOLD && severity > 1.15) ||
                    (lowFactorSpeedHack && buffer >= 3);

            if (shouldFlag && !flaggedThisCheck) {
                // Format detailed violation information
                String details = formatViolationDetails(player, horizontalDistance, maxAllowedSpeed,
                        effectiveTolerance, onGround, severity,
                        lowFactorSpeedHack);

                // Flag with appropriate VL increment
                double vlIncrement = Math.max(1.0, severity - 1.0);
                flag(vlIncrement, details);
                flaggedThisCheck = true;

                // Reduce buffer after flagging
                buffer = Math.max(0, buffer - 3);
            }
        } else {
            // Reset consecutive violations and decay buffer
            consecutiveViolations = 0;
            buffer = Math.max(0, buffer - BUFFER_DECREMENT);
        }

        // Secondary check for consistent speed just above normal limits
        // This helps catch subtle speed hacks that try to stay under the radar
        if (!flaggedThisCheck) {
            checkForSubtleSpeedHack(horizontalDistance, maxAllowedSpeed, onGround);
        }

        // Update state
        updateState(player, to, onGround, inLiquid, onIce || onPackedIce || onBlueIce,
                onSlime, horizontalDistance);
    }

    /**
     * Check for subtle speed hacks that maintain speed just below detection thresholds
     */
    private void checkForSubtleSpeedHack(double currentSpeed, double maxAllowed, boolean onGround) {
        // We need a good amount of data to detect subtle patterns
        if (speedSamples.size() < 8 || !onGround) {
            return;
        }

        // For subtle speed hacks, look at the pattern over time rather than individual values
        int suspiciousCount = 0;
        double suspiciousThreshold = SPRINT_BASE * 1.08; // Speed that's just a bit too fast

        // Count how many samples are suspiciously fast but not blatantly violating
        for (double speed : speedSamples) {
            if (speed > SPRINT_BASE * 1.05 && speed < suspiciousThreshold) {
                suspiciousCount++;
            }
        }

        // If most recent samples are consistently just below violation threshold
        if (suspiciousCount >= 6 && currentSpeed > SPRINT_BASE * 1.03) {
            // Check average speed vs average allowed
            double avgSpeed = calculateAverageSpeed();
            double avgAllowed = calculateAverageAllowedSpeed();

            // If average is suspiciously high but still under the typical detection threshold
            if (avgSpeed > avgAllowed * 0.95 && avgSpeed < avgAllowed * 1.0) {
                String details = String.format(
                        "subtle-speed-hack: speed=%.3f, avg=%.3f, allowed=%.3f, sus-count=%d",
                        currentSpeed,
                        avgSpeed,
                        avgAllowed,
                        suspiciousCount
                );

                flag(1.0, details);
                flaggedThisCheck = true;
            }
        }
    }

    /**
     * Check for very low-factor speed hacks (small but consistent speed boosts)
     */
    private boolean checkForLowFactorSpeedHack(double speed, double maxAllowed, boolean onGround) {
        // Only apply this detection on ground
        if (!onGround) {
            return false;
        }

        // Calculate stats about recent movements
        double avgSpeed = calculateAverageSpeed();
        double avgAllowed = calculateAverageAllowedSpeed();
        double avgFactor = avgSpeed / avgAllowed;

        // Low factor hack characteristics:
        // 1. Consistently just slightly above limit (5-15%)
        // 2. Very consistent speed factor across multiple samples
        // 3. No clear legitimate explanation (ice, jump, damage)

        boolean consistentSlight = avgFactor > 1.03 && avgFactor < 1.15;
        boolean noLegitReason = !isRecentlyDamaged() && !isRecentlyJumped() &&
                !isRecentlyOnIce() && !isRecentlyTeleported();
        boolean strangeFactor = speed / maxAllowed > 1.03 && speed / maxAllowed < 1.15;

        return consistentSlight && strangeFactor && noLegitReason;
    }

    /**
     * Calculate the max allowed speed with all modifiers
     */
    private double calculateMaxAllowedSpeed(Player player, boolean onGround, boolean inLiquid,
                                            boolean onIce, boolean onPackedIce, boolean onBlueIce,
                                            boolean onSlime, boolean onSoulSand,
                                            boolean onStairs, boolean onSlab) {

        // Base speed according to sprint state
        double baseSpeed = player.isSprinting() ? SPRINT_BASE : WALK_BASE;

        // Handle jumping (air movement)
        if (!onGround) {
            // Initial jump boost vs sustained air movement
            if (wasOnGround) {
                baseSpeed = player.isSprinting() ? SPRINT_JUMP : SPRINT_BASE * 1.5;
            } else {
                // Air friction in Minecraft
                baseSpeed *= 0.91;
            }
        }

        // Apply surface modifiers
        if (onIce) {
            baseSpeed *= ICE_MODIFIER;
        } else if (onPackedIce) {
            baseSpeed *= PACKED_ICE_MODIFIER;
        } else if (onBlueIce) {
            baseSpeed *= BLUE_ICE_MODIFIER;
        }

        // Recent ice effect lingers (ice momentum)
        if (!onIce && !onPackedIce && !onBlueIce && isRecentlyOnIce()) {
            baseSpeed *= 1.15; // Ice momentum effect
        }

        if (onSlime || wasOnSlime) {
            baseSpeed *= SLIME_MODIFIER;
        }

        if (onSoulSand) {
            baseSpeed *= SOUL_SAND_MODIFIER;
        }

        if (inLiquid || wasInLiquid) {
            baseSpeed *= WATER_MODIFIER;
        }

        // Stairs and slabs can cause slightly faster movement
        if (onStairs) {
            baseSpeed *= STAIRS_MODIFIER;
        }

        if (onSlab) {
            baseSpeed *= SLAB_MODIFIER;
        }

        // Apply potion effects
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int level = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            baseSpeed *= 1.0 + (0.2 * level);
        }

        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            int level = player.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier() + 1;
            baseSpeed *= 1.0 - (0.15 * level);
        }

        // Small general safety buffer (kept minimal)
        baseSpeed *= 1.01;

        return baseSpeed;
    }

    /**
     * Calculate tolerance based on player state
     */
    private double calculateTolerance(Player player, boolean onGround) {
        double tolerance = BASE_TOLERANCE;

        // Increase tolerance for special conditions, but kept minimal
        if (isRecentlyDamaged()) {
            tolerance += 0.05; // After damage
        }

        if (isRecentlyJumped()) {
            tolerance += 0.03; // After jumping
        }

        if (isRecentlyOnIce()) {
            tolerance += 0.03; // After being on ice
        }

        if (isRecentlyTeleported()) {
            tolerance += 0.05; // After teleport
        }

        // Different tolerance for air vs ground
        if (!onGround) {
            tolerance += 0.01; // Air movement is less predictable
        }

        // Adjust tolerance for ping - minimal adjustment
        int ping = playerData.getAveragePing();
        if (ping > 150) {
            tolerance += (ping - 150) / 2000.0; // Small ping adjustment
        }

        return tolerance;
    }

    /**
     * Calculate how much to increment the buffer based on violation severity
     */
    private int calculateBufferIncrement(double severity) {
        // Very minor violations
        if (severity < 1.05) {
            return 1;
        }
        // Minor violations
        else if (severity < 1.1) {
            return 2;
        }
        // Moderate violations
        else if (severity < 1.2) {
            return 3;
        }
        // Severe violations
        else {
            return Math.min(6, (int)(severity * 4));
        }
    }

    /**
     * Adjust speed for external velocity sources (knockback, etc.)
     */
    private double adjustForVelocity(Player player, double maxSpeed) {
        Vector velocity = player.getVelocity();
        double horizontalVelocity = Math.sqrt(velocity.getX() * velocity.getX() +
                velocity.getZ() * velocity.getZ());

        // Detect significant velocity changes (knockback, explosions, etc.)
        if (horizontalVelocity > 0.1 && horizontalVelocity > lastVelocity.length() * 1.5) {
            lastVelocityTime = System.currentTimeMillis();
            maxSpeed += horizontalVelocity * 1.2;
        }

        // Apply lingering velocity effect with decay
        long timeSinceVelocity = System.currentTimeMillis() - lastVelocityTime;
        if (timeSinceVelocity < 800) { // Shorter grace period (800ms)
            double factor = Math.max(0, 1 - (timeSinceVelocity / 800.0));
            maxSpeed += lastVelocity.length() * factor * 1.2;
        }

        lastVelocity = velocity.clone();
        return maxSpeed;
    }

    /**
     * Check if player has been damaged recently
     */
    private boolean isRecentlyDamaged() {
        return System.currentTimeMillis() - lastDamageTime < 800; // 800ms window
    }

    /**
     * Check if player jumped recently
     */
    private boolean isRecentlyJumped() {
        return System.currentTimeMillis() - lastJumpTime < 400; // 400ms window
    }

    /**
     * Check if player was recently on ice (momentum effect)
     */
    private boolean isRecentlyOnIce() {
        return System.currentTimeMillis() - lastIceTime < 800; // 800ms window
    }

    /**
     * Check if player was recently teleported
     */
    private boolean isRecentlyTeleported() {
        return System.currentTimeMillis() - lastTeleportTime < TELEPORT_EXEMPT_TIME;
    }

    /**
     * Format detailed violation message
     */
    private String formatViolationDetails(Player player, double speed, double maxSpeed,
                                          double tolerance, boolean onGround, double severity,
                                          boolean lowFactorHack) {
        double avgSpeed = calculateAverageSpeed();

        return String.format(
                "speed=%.3f, max=%.3f, diff=%.1f%%, severity=%.2f, avg=%.3f, " +
                        "ground=%s, streak=%d, low-factor=%s, ping=%dms",
                speed,
                maxSpeed,
                (speed / maxSpeed - 1.0) * 100,
                severity,
                avgSpeed,
                onGround ? "true" : "false",
                consecutiveViolations,
                lowFactorHack ? "true" : "false",
                playerData.getAveragePing()
        );
    }

    /**
     * Calculate average speed from samples
     */
    private double calculateAverageSpeed() {
        if (speedSamples.isEmpty()) {
            return 0;
        }

        double sum = 0;
        for (double speed : speedSamples) {
            sum += speed;
        }
        return sum / speedSamples.size();
    }

    /**
     * Calculate average allowed speed from samples
     */
    private double calculateAverageAllowedSpeed() {
        if (maxSpeedSamples.isEmpty()) {
            return 0;
        }

        double sum = 0;
        for (double speed : maxSpeedSamples) {
            sum += speed;
        }
        return sum / maxSpeedSamples.size();
    }

    /**
     * Update tracking state
     */
    private void updateState(Player player, Location location, boolean onGround,
                             boolean inLiquid, boolean onIce, boolean onSlime,
                             double speed) {
        wasOnGround = onGround;
        wasInLiquid = inLiquid;
        wasOnIce = onIce;
        wasOnSlime = onSlime;
        lastSpeed = speed;
        playerData.setLastLocation(location);

        // Update movement data in player data
        MovementData movementData = playerData.getMovementData();
        if (movementData != null) {
            movementData.updatePosition(location.getX(), location.getY(), location.getZ());
            movementData.updateGroundState(onGround);
            movementData.updateBlockState(false, onIce, onSlime, inLiquid,
                    isOnStairs(player), isOnSlab(player));
        }
    }

    /**
     * Reset detection state
     */
    private void resetDetectionState() {
        buffer = 0;
        consecutiveViolations = 0;
        speedSamples.clear();
        speedTimestamps.clear();
        maxSpeedSamples.clear();
        groundStateHistory.clear();
    }

    /**
     * Check if player is exempt from checks
     */
    private boolean isExempt(Player player) {
        return player.isFlying() ||
                player.getAllowFlight() ||
                player.getGameMode() == GameMode.CREATIVE ||
                player.getGameMode() == GameMode.SPECTATOR ||
                player.getVehicle() != null ||
                playerData.isExempt();
    }

    /**
     * Check if packet is a movement packet
     */
    private boolean isMovementPacket(PacketType type) {
        return type == PacketType.Play.Client.POSITION ||
                type == PacketType.Play.Client.POSITION_LOOK ||
                type == PacketType.Play.Client.LOOK ||
                type == PacketType.Play.Client.FLYING;
    }

    /**
     * Block state checks
     */
    private boolean isInLiquid(Player player) {
        Material material = player.getLocation().getBlock().getType();
        return material == Material.WATER || material == Material.LAVA;
    }

    private boolean isOnIce(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return below.getBlock().getType() == Material.ICE;
    }

    private boolean isOnPackedIce(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return below.getBlock().getType() == Material.PACKED_ICE;
    }

    private boolean isOnBlueIce(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return below.getBlock().getType() == Material.BLUE_ICE;
    }

    private boolean isOnSlime(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return below.getBlock().getType() == Material.SLIME_BLOCK;
    }

    private boolean isOnSoulSand(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return below.getBlock().getType() == Material.SOUL_SAND;
    }

    private boolean isOnStairs(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        Material material = below.getBlock().getType();
        return material.name().contains("STAIRS");
    }

    private boolean isOnSlab(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        Material material = below.getBlock().getType();
        return material.name().contains("SLAB");
    }

    /**
     * Public methods to track special events
     */
    public void onPlayerDamage() {
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Called when player teleports
     */
    public void onPlayerTeleport() {
        lastTeleportTime = System.currentTimeMillis();
        resetDetectionState(); // Clear history after teleport
    }
}