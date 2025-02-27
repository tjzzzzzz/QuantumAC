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
 * SpeedA - Movement Speed Check
 *
 * This check monitors player movement speeds to detect both horizontal and vertical speed violations.
 * It uses a circular buffer system to track movement data and detect patterns of suspicious behavior.
 * Includes improved false-positive prevention with graduated violation system.
 */
public class SpeedA extends Check {

    // Violation thresholds - increased to reduce false positives
    private static final int MAX_VIOLATIONS_BEFORE_ALERT = 4;
    private static final int MAX_VIOLATIONS_BEFORE_PULLDOWN_ALERT = 3;

    // Violation decay - to handle occasional legitimate fast movements
    private static final double VIOLATION_DECAY_RATE = 0.5;
    private static final long VIOLATION_DECAY_TIME = 2000; // 2 seconds
    private long lastViolationTime = 0;

    // Movement limits - slightly adjusted to be more lenient
    private static final double MAX_HORIZONTAL_SPEED = 0.48; // Increased from 0.42
    private static final double SUSPICIOUS_VERTICAL_SPEED = 0.46; // Increased from 0.43
    private static final double MAX_VERTICAL_SPEED = 0.65; // Increased from 0.6
    private static final double STRICT_PULLDOWN_SPEED = -0.42; // Adjusted from -0.38

    // Buffer settings - increased for better pattern recognition
    private static final int BUFFER_SIZE = 25; // Increased from 20
    private static final int MIN_SAMPLES_REQUIRED = 10; // Minimum samples before checking

    // Exemption durations - increased to provide more leniency
    private static final long TELEPORT_EXEMPT_TIME = 4000; // Increased from 3000
    private static final long DAMAGE_EXEMPT_TIME = 3000; // Increased from 2000
    private static final long VELOCITY_EXEMPT_TIME = 3000; // Increased from 2000
    private static final long BLOCK_CHANGE_EXEMPT_TIME = 2000; // Increased from 1000
    private static final long JOIN_EXEMPT_TIME = 6000; // Increased from 5000
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
                    isInPowderSnow(player);

            if (exempt) {
                violations = Math.max(0, violations - 1); // Reduce violations when exempt
                movementBuffer.add(new MovementData(x, y, z, horizontalSpeed, deltaY));
                return;
            }

            MovementData currentMovement = new MovementData(x, y, z, horizontalSpeed, deltaY);
            movementBuffer.add(currentMovement);

            // Only proceed if we have enough samples
            if (movementBuffer.getCount() < MIN_SAMPLES_REQUIRED) {
                return;
            }

            // Check for pulldown hack (fast downward movement)
            if (isPulldownDetected(currentMovement)) {
                handlePulldownViolation(player, deltaY);
            }
            // Check for consistent speed violations
            else if (isConsistentlySuspicious()) {
                handleSpeedViolation(player);
            }
            // Reduce violations if no issues detected
            else {
                violations = Math.max(0, violations - 0.5);
            }
        } else {
            // First packet, just add the initial position
            movementBuffer.add(new MovementData(x, y, z, 0, 0));
        }
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
     * Detect pulldown hacks (rapid descent that bypasses fall damage)
     */
    private boolean isPulldownDetected(MovementData movementData) {
        return movementData.verticalSpeed < STRICT_PULLDOWN_SPEED;
    }

    /**
     * Check for consistent suspicious movement patterns
     * Using trimmed means to reduce impact of outliers
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

        boolean verticalSuspicious = trimmedVerticalSpeed > adjustedVerticalThreshold &&
                trimmedVerticalSpeed <= MAX_VERTICAL_SPEED;
        boolean horizontalSuspicious = trimmedHorizontalSpeed > adjustedHorizontalThreshold;

        return verticalSuspicious || horizontalSuspicious;
    }

    /**
     * Handle pulldown violation with quick alerts
     */
    private void handlePulldownViolation(Player player, double deltaY) {
        violations += 1.0;
        lastViolationTime = System.currentTimeMillis();

        if (violations >= MAX_VIOLATIONS_BEFORE_PULLDOWN_ALERT) {
            String details = String.format("Pulldown.. DownSpeed=%.2f, Violations=%.1f",
                    deltaY, violations);
            flag(2.0, details);
            violations = Math.max(0, violations - 2.0); // Reduce violations after flagging
        }
    }

    /**
     * Handle normal speed violations
     */
    private void handleSpeedViolation(Player player) {
        violations += 1.0;
        lastViolationTime = System.currentTimeMillis();

        if (violations >= MAX_VIOLATIONS_BEFORE_ALERT) {
            double trimmedHorizontalSpeed = getTrimmedHorizontalSpeed();
            double trimmedVerticalSpeed = getTrimmedVerticalSpeed();

            String details = String.format("HSpeed=%.2f, VSpeed=%.2f, Violations=%.1f",
                    trimmedHorizontalSpeed, trimmedVerticalSpeed, violations);
            flag(1.0, details);
            violations = Math.max(0, violations - 2.0); // Reduce violations after flagging
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
                player.isSprinting() && player.hasPotionEffect(PotionEffectType.SPEED) || // Sprinting with speed
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
     * Movement data storage class
     */
    private static class MovementData {
        private final double x;
        private final double y;
        private final double z;
        private final double horizontalSpeed;
        private final double verticalSpeed;

        public MovementData(double x, double y, double z, double horizontalSpeed, double verticalSpeed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.horizontalSpeed = horizontalSpeed;
            this.verticalSpeed = verticalSpeed;
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