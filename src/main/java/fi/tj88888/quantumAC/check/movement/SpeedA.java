package fi.tj88888.quantumAC.check.movement;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.util.CheckUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.LinkedList;

/**
 * SpeedA - Movement Speed & Acceleration Check
 *
 * This check monitors player movement speeds and acceleration to detect both horizontal
 * and vertical speed violations. It uses a circular buffer system to track movement data
 * and detect patterns of suspicious behavior, including small, consistent acceleration modifications.
 * Includes improved false-positive prevention with graduated violation system.
 *
 * Optimized for faster detection and reduced false positives while falling.
 */
public class SpeedA extends Check {

    // Violation thresholds - lowered for faster detection
    private static final int MAX_VIOLATIONS_BEFORE_ALERT = 2;
    private static final int MAX_VIOLATIONS_BEFORE_ACCEL_ALERT = 2; // Reduced from 3 to 2

    // Violation decay
    private static final double VIOLATION_DECAY_RATE = 0.5;
    private static final long VIOLATION_DECAY_TIME = 2000; // 2 seconds
    private long lastViolationTime = 0;

    // Movement limits - speed
    private static final double MAX_HORIZONTAL_SPEED = 0.43;
    private static final double SUSPICIOUS_VERTICAL_SPEED = 0.46;
    private static final double MAX_VERTICAL_SPEED = 0.65;

    // Falling detection to prevent false positives
    private static final double FALLING_VERTICAL_THRESHOLD = -0.7; // Negative because falling is negative Y
    private boolean isFalling = false;
    private int fallingTicks = 0;
    private static final int FALLING_EXEMPT_TICKS = 5; // Exempt after falling for this many ticks

    // Movement limits - acceleration
    private static final double SUSPICIOUS_HORIZONTAL_ACCELERATION = 0.025; // Detect small, consistent acceleration
    private static final double MAX_HORIZONTAL_ACCELERATION = 0.10; // Maximum reasonable horizontal acceleration
    private static final int CONSECUTIVE_ACCEL_THRESHOLD = 4; // Reduced from 6 to 4 for faster detection

    // Buffer settings
    private static final int BUFFER_SIZE = 20; // Reduced from 30 to 20 for faster response
    private static final int MIN_SAMPLES_REQUIRED = 6; // Reduced from 10 to 6
    private static final int ACCEL_PATTERN_SIZE = 6; // Reduced from 8 to 6

    // Exemption durations
    private static final long TELEPORT_EXEMPT_TIME = 4000;
    private static final long DAMAGE_EXEMPT_TIME = 1000;
    private static final long VELOCITY_EXEMPT_TIME = 3000;
    private static final long BLOCK_CHANGE_EXEMPT_TIME = 320;
    private static final long JOIN_EXEMPT_TIME = 3000;
    private static final long WORLD_CHANGE_EXEMPT_TIME = 5000;

    // Movement tracking
    private final CircularBuffer movementBuffer;
    private double violations = 0;
    private long lastWorldChangeTime = 0;
    private String lastWorldName = "";

    // Special event timers
    private long joinTime = 0;
    private long lastDamageTime = 0;
    private long lastVelocityTime = 0;
    private long lastTeleportTime = 0;
    private long lastBlockChangeTime = 0;
    private Vector pendingVelocity = null;

    // Acceleration tracking
    private int consecutiveAccelerationCount = 0;
    private double lastHorizontalSpeed = 0;
    private double lastVerticalSpeed = 0;

    public SpeedA(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "SpeedA", "Movement");
        this.movementBuffer = new CircularBuffer(BUFFER_SIZE);
        this.joinTime = System.currentTimeMillis();
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (!isPositionPacket(event.getPacketType())) {
            return;
        }

        Player player = event.getPlayer();

        // Decay violations over time
        decayViolations();

        // Skip if the player is exempt from checks
        if (isExempt(player)) {
            return;
        }

        // Check for world change
        if (!player.getWorld().getName().equals(lastWorldName)) {
            lastWorldName = player.getWorld().getName();
            lastWorldChangeTime = System.currentTimeMillis();
            return;
        }

        PacketContainer packet = event.getPacket();
        double x = packet.getDoubles().read(0);
        double y = packet.getDoubles().read(1);
        double z = packet.getDoubles().read(2);

        MovementData lastMovement = (MovementData) movementBuffer.getLast();

        if (lastMovement != null) {
            double deltaX = x - lastMovement.x;
            double deltaY = y - lastMovement.y;
            double deltaZ = z - lastMovement.z;

            double horizontalSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

            // Calculate acceleration (changes in speed)
            double horizontalAcceleration = horizontalSpeed - lastHorizontalSpeed;
            double verticalAcceleration = deltaY - lastVerticalSpeed;

            // Store current speed for next acceleration calculation
            lastHorizontalSpeed = horizontalSpeed;
            lastVerticalSpeed = deltaY;

            // Track falling state to prevent false positives
            updateFallingState(deltaY);

            // Skip rest of check if exempt conditions are met
            boolean exempt = isRecentlyTeleported() ||
                    isRecentlyDamaged() ||
                    isRecentlyVelocity() ||
                    isRecentlyBlockChange() ||
                    isRecentlyJoined() ||
                    isRecentlyWorldChanged() ||
                    isInLiquid(player) ||
                    isOnIce(player) ||
                    isOnSlime(player) ||
                    isOnStairs(player) ||
                    isOnSlab(player) ||
                    isCloseToClimbable(player) ||
                    isCloseToHoney(player) ||
                    isInWeb(player) ||
                    isInPowderSnow(player) ||
                    (isFalling && fallingTicks >= FALLING_EXEMPT_TICKS); // Exempt if falling for too long

            if (exempt) {
                violations = Math.max(0, violations - 1); // Reduce violations when exempt
                consecutiveAccelerationCount = 0; // Reset acceleration counter when exempt
                movementBuffer.add(new MovementData(x, y, z, horizontalSpeed, deltaY, horizontalAcceleration, verticalAcceleration));
                return;
            }

            MovementData currentMovement = new MovementData(x, y, z, horizontalSpeed, deltaY, horizontalAcceleration, verticalAcceleration);
            movementBuffer.add(currentMovement);

            // Only proceed if we have enough samples
            if (movementBuffer.getCount() < MIN_SAMPLES_REQUIRED) {
                return;
            }

            // Check for acceleration pattern violations - accelerated to detect faster
            if (isAccelerationPatternSuspicious()) {
                handleAccelerationViolation(player);
            }
            // Check for consistent speed violations
            else if (isConsistentlySuspicious()) {
                handleSpeedViolation(player);
            }
            // Reduce violations if no issues detected
            else {
                violations = Math.max(0, violations - 0.5);
                consecutiveAccelerationCount = 0; // Reset acceleration counter on normal movement
            }
        } else {
            // First packet, just add the initial position
            movementBuffer.add(new MovementData(x, y, z, 0, 0, 0, 0));
        }
    }

    /**
     * Track player falling state to prevent false positives
     */
    private void updateFallingState(double deltaY) {
        // Check if player is falling
        if (deltaY < FALLING_VERTICAL_THRESHOLD) {
            isFalling = true;
            fallingTicks++;
        } else {
            // Reset falling state if not falling anymore or on ground
            if (isFalling) {
                isFalling = false;
                fallingTicks = 0;
            }
        }
    }

    /**
     * Check if acceleration pattern is suspicious
     * Detects small but consistent acceleration that might indicate speed hacks
     * Optimized for faster detection
     */
    private boolean isAccelerationPatternSuspicious() {
        // Get recent acceleration values
        double[] accelerations = getRecentAccelerations(ACCEL_PATTERN_SIZE);

        // Skip if we don't have enough samples
        if (accelerations.length < ACCEL_PATTERN_SIZE) {
            return false;
        }

        // Check for player sprint/movement direction changes
        // This helps filter out natural acceleration variations
        if (hasDirectionChange(accelerations)) {
            consecutiveAccelerationCount = 0;
            return false;
        }

        // Count positive accelerations within the suspicious range
        int suspiciousCount = 0;
        double sum = 0;

        for (double accel : accelerations) {
            if (accel > SUSPICIOUS_HORIZONTAL_ACCELERATION && accel < MAX_HORIZONTAL_ACCELERATION) {
                suspiciousCount++;
                sum += accel;
            } else if (accel < -SUSPICIOUS_HORIZONTAL_ACCELERATION) {
                // Negative acceleration (deceleration) resets the counter
                consecutiveAccelerationCount = 0;
                return false;
            }
        }

        // Check the pattern of accelerations - more aggressive thresholds for faster detection
        if (suspiciousCount >= ACCEL_PATTERN_SIZE * 0.7) { // Reduced from 0.75 to 0.7
            // Calculate average acceleration
            double avgAcceleration = sum / suspiciousCount;

            // Get standard deviation to check consistency
            double variance = 0;
            for (double accel : accelerations) {
                if (accel > SUSPICIOUS_HORIZONTAL_ACCELERATION) {
                    variance += Math.pow(accel - avgAcceleration, 2);
                }
            }
            double stdDeviation = Math.sqrt(variance / suspiciousCount);

            // If acceleration is consistent (low standard deviation) and not too high
            if (stdDeviation < 0.008 && avgAcceleration < MAX_HORIZONTAL_ACCELERATION) { // Increased tolerance slightly
                consecutiveAccelerationCount++;
                return consecutiveAccelerationCount >= CONSECUTIVE_ACCEL_THRESHOLD;
            }
        }

        // Reset counter if pattern not found
        consecutiveAccelerationCount = 0;
        return false;
    }

    /**
     * Check if player has changed direction (indicated by acceleration sign changes)
     */
    private boolean hasDirectionChange(double[] accelerations) {
        boolean hasPositive = false;
        boolean hasNegative = false;

        for (double accel : accelerations) {
            if (accel > 0.01) hasPositive = true;
            if (accel < -0.01) hasNegative = true;

            if (hasPositive && hasNegative) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get recent horizontal acceleration values
     */
    private double[] getRecentAccelerations(int count) {
        int available = Math.min(count, movementBuffer.getCount());
        double[] result = new double[available];

        for (int i = 0; i < available; i++) {
            int idx = (movementBuffer.getIndex() - 1 - i + movementBuffer.getSize()) % movementBuffer.getSize();
            if (idx >= 0 && idx < movementBuffer.getSize() && movementBuffer.getData()[idx] != null) {
                result[i] = ((MovementData)movementBuffer.getData()[idx]).horizontalAcceleration;
            }
        }

        return result;
    }

    /**
     * Handle acceleration-based violations
     * Increased violation weight for faster detection
     */
    private void handleAccelerationViolation(Player player) {
        violations += 1.25; // Increased from 0.75 to 1.25
        lastViolationTime = System.currentTimeMillis();

        if (violations >= MAX_VIOLATIONS_BEFORE_ACCEL_ALERT) {
            // Get average acceleration to include in violation details
            double avgAcceleration = getAverageAcceleration();
            double maxSpeed = getMaxHorizontalSpeed();

            String details = String.format("MicroAccel=%.4f, MaxSpeed=%.2f, ConsistentAccel=%d, Violations=%.1f",
                    avgAcceleration, maxSpeed, consecutiveAccelerationCount, violations);
            flag(1.0, details);
            violations = Math.max(0, violations - 1.0); // Reduced penalty after flagging
        }
    }

    /**
     * Calculate average acceleration from recent samples
     */
    private double getAverageAcceleration() {
        return movementBuffer.getAverage(data ->
                Math.max(0, ((MovementData) data).horizontalAcceleration)
        );
    }

    /**
     * Find maximum horizontal speed in recent samples
     */
    private double getMaxHorizontalSpeed() {
        double max = 0;
        for (int i = 0; i < Math.min(ACCEL_PATTERN_SIZE, movementBuffer.getCount()); i++) {
            int idx = (movementBuffer.getIndex() - 1 - i + movementBuffer.getSize()) % movementBuffer.getSize();
            if (idx >= 0 && idx < movementBuffer.getSize() && movementBuffer.getData()[idx] != null) {
                max = Math.max(max, ((MovementData)movementBuffer.getData()[idx]).horizontalSpeed);
            }
        }
        return max;
    }

    /**
     * Decay violations over time
     */
    private void decayViolations() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastViolation = currentTime - lastViolationTime;

        if (timeSinceLastViolation > VIOLATION_DECAY_TIME && violations > 0) {
            violations = Math.max(0, violations - VIOLATION_DECAY_RATE);
            lastViolationTime = currentTime;
        }
    }

    /**
     * Handle normal speed violations - increased violation weight for faster flagging
     */
    private void handleSpeedViolation(Player player) {
        violations += 1.5; // Increased from 1.0 to 1.5
        lastViolationTime = System.currentTimeMillis();

        if (violations >= MAX_VIOLATIONS_BEFORE_ALERT) {
            double trimmedHorizontalSpeed = getTrimmedHorizontalSpeed();
            double trimmedVerticalSpeed = getTrimmedVerticalSpeed();

            String details = String.format("HSpeed=%.2f, VSpeed=%.2f, Violations=%.1f",
                    trimmedHorizontalSpeed, trimmedVerticalSpeed, violations);
            flag(1.0, details);
            violations = Math.max(0, violations - 1.5); // Reduced from 2.0 to 1.5
        }
    }

    /**
     * Calculate trimmed average horizontal speed from buffer (removes outliers)
     */
    private double getTrimmedHorizontalSpeed() {
        return movementBuffer.getTrimmedAverage(data -> ((MovementData) data).horizontalSpeed, 0.2);
    }

    /**
     * Calculate trimmed average vertical speed from buffer (absolute value, removes outliers)
     */
    private double getTrimmedVerticalSpeed() {
        return movementBuffer.getTrimmedAverage(data -> Math.abs(((MovementData) data).verticalSpeed), 0.2);
    }

    /**
     * Check for consistent suspicious movement patterns
     * Using trimmed means to reduce impact of outliers
     * Added exemption for falling to prevent false positives
     */
    private boolean isConsistentlySuspicious() {
        double trimmedVerticalSpeed = getTrimmedVerticalSpeed();
        double trimmedHorizontalSpeed = getTrimmedHorizontalSpeed();

        // Check if speed potion or jump boost might be affecting movement
        Player player = playerData.getPlayer();
        int speedAmplifier = getEffectAmplifier(player, PotionEffectType.SPEED);
        int jumpAmplifier = getEffectAmplifier(player, PotionEffectType.JUMP_BOOST);

        // Adjust thresholds based on potion effects
        double adjustedHorizontalThreshold = MAX_HORIZONTAL_SPEED * (1 + (speedAmplifier * 0.2));
        double adjustedVerticalThreshold = SUSPICIOUS_VERTICAL_SPEED * (1 + (jumpAmplifier * 0.25));

        // Ignore vertical speed check if falling
        boolean verticalSuspicious = !isFalling &&
                trimmedVerticalSpeed > adjustedVerticalThreshold &&
                trimmedVerticalSpeed <= MAX_VERTICAL_SPEED;

        boolean horizontalSuspicious = trimmedHorizontalSpeed > adjustedHorizontalThreshold;

        return verticalSuspicious || horizontalSuspicious;
    }

    /**
     * Get amplifier level of a potion effect
     */
    private int getEffectAmplifier(Player player, PotionEffectType type) {
        if (player.hasPotionEffect(type)) {
            return player.getPotionEffect(type).getAmplifier() + 1;
        }
        return 0;
    }

    /**
     * Check if packet contains position data
     */
    private boolean isPositionPacket(PacketType type) {
        return type == PacketType.Play.Client.POSITION ||
                type == PacketType.Play.Client.POSITION_LOOK;
    }

    /**
     * Process teleport events
     */
    public void onPlayerTeleport() {
        lastTeleportTime = System.currentTimeMillis();
        violations = Math.max(0, violations - 1);
        consecutiveAccelerationCount = 0; // Reset acceleration counter on teleport
        fallingTicks = 0; // Reset falling tracker on teleport
        isFalling = false;
    }

    /**
     * Process damage events
     */
    public void onPlayerDamage() {
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Process velocity updates
     */
    public void onPlayerVelocity(Vector velocity) {
        pendingVelocity = velocity.clone();
        lastVelocityTime = System.currentTimeMillis();
        consecutiveAccelerationCount = 0; // Reset acceleration counter on velocity change

        // If velocity is negative Y, track as potential fall
        if (velocity.getY() < -0.1) {
            isFalling = true;
            fallingTicks = 0;
        }
    }

    /**
     * Process block change events around player
     */
    public void onBlockChange() {
        lastBlockChangeTime = System.currentTimeMillis();
    }

    // Time-based exemption checks
    private boolean isRecentlyDamaged() {
        return System.currentTimeMillis() - lastDamageTime < DAMAGE_EXEMPT_TIME;
    }

    private boolean isRecentlyTeleported() {
        return System.currentTimeMillis() - lastTeleportTime < TELEPORT_EXEMPT_TIME;
    }

    private boolean isRecentlyBlockChange() {
        return System.currentTimeMillis() - lastBlockChangeTime < BLOCK_CHANGE_EXEMPT_TIME;
    }

    private boolean isRecentlyVelocity() {
        return System.currentTimeMillis() - lastVelocityTime < VELOCITY_EXEMPT_TIME;
    }

    private boolean isRecentlyJoined() {
        return System.currentTimeMillis() - joinTime < JOIN_EXEMPT_TIME;
    }

    private boolean isRecentlyWorldChanged() {
        return System.currentTimeMillis() - lastWorldChangeTime < WORLD_CHANGE_EXEMPT_TIME;
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
                player.isGliding() || // Has elytra deployed
                player.isRiptiding() || // Using trident with riptide
                player.hasPotionEffect(PotionEffectType.JUMP_BOOST) && player.getVelocity().getY() > 0 || // Jumping with jump boost
                playerData.isExempt();
    }

    /**
     * Additional environment checks to reduce false positives
     */
    private boolean isCloseToClimbable(Player player) {
        return CheckUtil.isCloseToClimbable(player);
    }

    private boolean isCloseToHoney(Player player) {
        return CheckUtil.isCloseToBlock(player, Material.HONEY_BLOCK);
    }

    private boolean isInWeb(Player player) {
        return CheckUtil.isInMaterial(player, Material.COBWEB);
    }

    private boolean isInPowderSnow(Player player) {
        return CheckUtil.isInMaterial(player, Material.POWDER_SNOW);
    }

    /**
     * Environment checks - using utilities
     */
    private boolean isInLiquid(Player player) {
        return CheckUtil.isInLiquid(player);
    }

    private boolean isOnIce(Player player) {
        return CheckUtil.isOnIce(player) ||
                CheckUtil.isOnPackedIce(player) ||
                CheckUtil.isOnBlueIce(player);
    }

    private boolean isOnSlime(Player player) {
        return CheckUtil.isOnSlime(player);
    }

    private boolean isOnStairs(Player player) {
        return CheckUtil.isOnStairs(player);
    }

    private boolean isOnSlab(Player player) {
        return CheckUtil.isOnSlab(player);
    }

    /**
     * Movement data storage class - enhanced with acceleration data
     */
    private static class MovementData {
        private final double x;
        private final double y;
        private final double z;
        private final double horizontalSpeed;
        private final double verticalSpeed;
        private final double horizontalAcceleration;
        private final double verticalAcceleration;

        public MovementData(double x, double y, double z, double horizontalSpeed, double verticalSpeed,
                            double horizontalAcceleration, double verticalAcceleration) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.horizontalSpeed = horizontalSpeed;
            this.verticalSpeed = verticalSpeed;
            this.horizontalAcceleration = horizontalAcceleration;
            this.verticalAcceleration = verticalAcceleration;
        }
    }

    /**
     * Circular buffer for storing movement data with improved statistics
     */
    private static class CircularBuffer {
        private final Object[] data;
        private int index = 0;
        private int count = 0;

        public CircularBuffer(int size) {
            this.data = new Object[size];
        }

        public void add(Object value) {
            data[index] = value;
            index = (index + 1) % data.length;
            if (count < data.length) {
                count++;
            }
        }

        public Object getLast() {
            if (count == 0) return null;
            int lastIndex = (index - 1 + data.length) % data.length;
            return data[lastIndex];
        }

        public int getCount() {
            return count;
        }

        public int getIndex() {
            return index;
        }

        public int getSize() {
            return data.length;
        }

        public Object[] getData() {
            return data;
        }

        public double getAverage(ValueExtractor extractor) {
            if (count == 0) return 0;
            double sum = 0;
            for (int i = 0; i < count; i++) {
                int idx = (index - 1 - i + data.length) % data.length;
                sum += extractor.extract(data[idx]);
            }
            return sum / count;
        }

        /**
         * Get trimmed average (removes percentage of highest and lowest values)
         * @param extractor Function to extract value from data object
         * @param trimPercent Percentage to trim from each end (0.0-0.5)
         * @return Trimmed average
         */
        public double getTrimmedAverage(ValueExtractor extractor, double trimPercent) {
            if (count == 0) return 0;
            if (trimPercent < 0 || trimPercent >= 0.5) trimPercent = 0.1; // Default if invalid

            // Extract values
            double[] values = new double[count];
            for (int i = 0; i < count; i++) {
                int idx = (index - 1 - i + data.length) % data.length;
                values[i] = extractor.extract(data[idx]);
            }

            // Sort values
            java.util.Arrays.sort(values);

            // Calculate trim count
            int trimCount = (int) Math.floor(count * trimPercent);

            // Calculate trimmed average
            double sum = 0;
            for (int i = trimCount; i < count - trimCount; i++) {
                sum += values[i];
            }

            return sum / (count - 2 * trimCount);
        }
    }

    /**
     * Functional interface for extracting values from buffer objects
     */
    @FunctionalInterface
    private interface ValueExtractor {
        double extract(Object data);
    }
}