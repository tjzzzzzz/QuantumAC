package fi.tj88888.quantumAC.check.movement;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.util.ChatUtil;
import fi.tj88888.quantumAC.util.CheckUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * SpeedB - Air Strafe Detection
 *
 * This check specifically targets "air strafe" features in speed hacks, which allow
 * players to control their movement in air better than vanilla Minecraft physics permit.
 * The check analyzes the player's airborne movement patterns, particularly focusing on
 * direction changes and acceleration in air that shouldn't be possible in vanilla gameplay.
 *
 * Significantly improved to reduce false positives with normal gameplay.
 */
public class SpeedB extends Check {

    // Violation thresholds - SIGNIFICANTLY INCREASED to reduce false positives
    private static final int MAX_VIOLATIONS_BEFORE_ALERT = 7;  // Was 5
    private static final double VIOLATION_DECAY_RATE = 0.5;
    private static final long VIOLATION_DECAY_TIME = 2000; // 2 seconds
    private long lastViolationTime = 0;
    private double violations = 0;

    // Air movement detection parameters - RELAXED
    private static final double MAX_AIR_DIRECTION_CHANGE = 0.55;  // Was 0.45
    private static final double SUSPICIOUS_AIR_ACCELERATION = 0.07;  // Was 0.05
    private static final double MAX_AIR_ACCELERATION = 0.15;  // Was 0.12
    private static final int AIR_CHANGE_THRESHOLD = 5;  // Was 4

    // Movement tracking
    private boolean wasInAir = false;
    private int airTicks = 0;
    private int groundTicks = 0;
    private int directionChangesInAir = 0;
    private double lastYaw = 0.0;
    private double lastHorizontalSpeed = 0.0;
    private Vector lastMoveDirection = null;

    // New tracking for jumping
    private boolean wasJumping = false;
    private long jumpStartTime = 0;
    private long lastJumpTime = 0;
    private static final long JUMP_EXEMPT_TIME = 1000; // 1 second exemption after jumping

    // Buffer settings
    private static final int BUFFER_SIZE = 20;
    private final AirMovementBuffer airMovementBuffer;

    // Exemption durations - FURTHER INCREASED
    private static final long TELEPORT_EXEMPT_TIME = 8000;  // Was 5000
    private static final long DAMAGE_EXEMPT_TIME = 3000;    // Was 2000
    private static final long VELOCITY_EXEMPT_TIME = 4000;  // Was 3000
    private static final long BLOCK_CHANGE_EXEMPT_TIME = 1500;  // Was 1000
    private static final long JOIN_EXEMPT_TIME = 7000;      // Was 5000
    private static final long WORLD_CHANGE_EXEMPT_TIME = 15000;  // Was 10000

    // Special event timers
    private long joinTime = 0;
    private long lastDamageTime = 0;
    private long lastVelocityTime = 0;
    private long lastTeleportTime = 0;
    private long lastBlockChangeTime = 0;
    private long lastWorldChangeTime = 0;
    private String lastWorldName = "";
    private Vector pendingVelocity = null;

    // Debug mode flag
    private boolean debugMode = false;

    // New tracking for client prediction
    private long lastPacketTime = 0;
    private double lastPacketDistance = 0;
    private int packetBurstCount = 0;

    public SpeedB(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "SpeedB", "Movement");
        this.airMovementBuffer = new AirMovementBuffer(BUFFER_SIZE);
        this.joinTime = System.currentTimeMillis();
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (!isPositionPacket(event.getPacketType())) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player has debug permission
        debugMode = player.hasPermission("quantumac.debug.speedb");

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

        // Get packet data
        PacketContainer packet = event.getPacket();
        double x = packet.getDoubles().read(0);
        double y = packet.getDoubles().read(1);
        double z = packet.getDoubles().read(2);
        float yaw = player.getLocation().getYaw(); // Get current yaw for direction analysis

        // Track packet timing for burst detection (client-side prediction can cause bursts)
        long now = System.currentTimeMillis();
        long packetTimeDelta = now - lastPacketTime;
        lastPacketTime = now;

        // Detect packet bursts which can cause false positives
        if (packetTimeDelta < 10) { // If packets arrive very close together
            packetBurstCount++;
            if (packetBurstCount > 3) {
                // Skip during packet bursts as they can cause false positives
                if (debugMode) {
                    ChatUtil.sendDebug(player, "Skipping check during packet burst");
                }
                return;
            }
        } else {
            packetBurstCount = Math.max(0, packetBurstCount - 1);
        }

        // Skip rest of check if exempt conditions are met
        if (isRecentlyTeleported() ||
                isRecentlyDamaged() ||
                isRecentlyVelocity() ||
                isRecentlyBlockChange() ||
                isRecentlyJoined() ||
                isRecentlyWorldChanged() ||
                isRecentlyJumped() ||  // Added jump exemption
                isInLiquid(player) ||
                isOnIce(player) ||
                isOnSlime(player) ||
                isCloseToClimbable(player) ||
                isCloseToHoney(player) ||
                isInWeb(player) ||
                isInPowderSnow(player) ||
                isNearStairs(player) ||  // Added stairs exemption
                isNearSlab(player)) {    // Added slab exemption
            resetTrackingData();
            return;
        }

        // Check if the player is on ground
        boolean onGround = player.isOnGround();

        // IMPORTANT NEW JUMP DETECTION
        // This helps distinguish between actual jumps and other air movement
        if (onGround && wasInAir && airTicks > 5) {
            // Player just landed from being in air
            wasJumping = false;
            lastJumpTime = System.currentTimeMillis();
        } else if (!onGround && !wasInAir) {
            // Player just left the ground - likely a jump
            wasJumping = true;
            jumpStartTime = System.currentTimeMillis();
        }

        // Get previous position from the buffer
        AirMovementData lastPosition = airMovementBuffer.getLast();

        if (lastPosition != null) {
            double deltaX = x - lastPosition.x;
            double deltaZ = z - lastPosition.z;
            double horizontalSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            double horizontalAcceleration = horizontalSpeed - lastHorizontalSpeed;
            lastPacketDistance = horizontalSpeed;  // Remember for next iteration

            // Create a movement vector for direction analysis
            Vector moveDirection = new Vector(deltaX, 0, deltaZ);

            // Only normalize if the length is non-zero to avoid division by zero
            if (moveDirection.lengthSquared() > 0.000001) {
                moveDirection = moveDirection.normalize();
            } else {
                // Handle near-zero movement case
                moveDirection = new Vector(0, 0, 0);
            }

            // Track air time
            if (!onGround) {
                airTicks++;
                groundTicks = 0;

                // If player just left the ground, reset tracking
                if (!wasInAir) {
                    directionChangesInAir = 0;
                    wasInAir = true;
                }

                // Only analyze air movement after the player has been in air for at least 4 ticks
                // Increased to avoid false positives during jump startup
                if (airTicks > 4 && lastMoveDirection != null) {
                    // Skip analysis if player is barely moving
                    if (horizontalSpeed > 0.02) {  // Minimum speed to analyze
                        // Calculate angle between current and previous movement
                        double angle = calculateAngleBetween(moveDirection, lastMoveDirection);

                        // Check for direction change in air - but only if actually moving
                        if (angle > MAX_AIR_DIRECTION_CHANGE && horizontalSpeed > 0.08) {  // Increased minimum speed for direction changes
                            directionChangesInAir++;

                            if (debugMode) {
                                ChatUtil.sendDebug(player, String.format(
                                        "Air direction change: angle=%.2f, speed=%.3f, airTicks=%d",
                                        angle, horizontalSpeed, airTicks));
                            }
                        }

                        // Record this air movement for analysis
                        airMovementBuffer.add(new AirMovementData(
                                x, y, z, horizontalSpeed, horizontalAcceleration, angle, moveDirection, yaw, airTicks
                        ));

                        // Only check for air strafe after we have enough data
                        // Increased from 6 to 8 ticks to reduce false positives
                        if (airTicks > 8) {
                            if (isAirStrafeDetected(player)) {  // Pass player to function for potion checks
                                handleAirStrafeViolation(player, horizontalSpeed, angle, horizontalAcceleration);
                            }
                        }
                    } else {
                        // For very small movements, just track position but don't analyze
                        airMovementBuffer.add(new AirMovementData(
                                x, y, z, horizontalSpeed, 0, 0, moveDirection, yaw, airTicks
                        ));
                    }
                }
            } else {
                // On ground
                groundTicks++;
                if (groundTicks > 1) {
                    airTicks = 0;
                    wasInAir = false;
                    directionChangesInAir = 0;
                }
            }

            // Remember this movement
            lastHorizontalSpeed = horizontalSpeed;
            lastMoveDirection = moveDirection;
            lastYaw = yaw;
        } else {
            // First packet, just initialize the position
            airMovementBuffer.add(new AirMovementData(x, y, z, 0, 0, 0, new Vector(0, 0, 0), yaw, 0));
        }
    }

    /**
     * Analyzes recent air movements to detect air strafe hacks
     */
    private boolean isAirStrafeDetected(Player player) {
        if (airMovementBuffer.size() < 5) {
            return false;
        }

        // Get horizontal speed amplifier if player has speed potion
        int speedAmplifier = getEffectAmplifier(player, PotionEffectType.SPEED);
        int jumpAmplifier = getEffectAmplifier(player, PotionEffectType.JUMP_BOOST);

        // Adjust thresholds based on potion effects
        double adjustedAccelerationThreshold = SUSPICIOUS_AIR_ACCELERATION * (1 + (speedAmplifier * 0.3) + (jumpAmplifier * 0.2));

        // IMPORTANT: Scale thresholds based on connection quality (estimated by packet timing)
        if (packetBurstCount > 0) {
            adjustedAccelerationThreshold *= (1 + (packetBurstCount * 0.2));
        }

        // Get recent air movements for analysis
        AirMovementData[] recentMovements = airMovementBuffer.getRecentAirMovements(5);

        // Multiple detection criteria
        int directionChangeCount = 0;
        int accelerationViolations = 0;
        boolean hasYawCorrelation = false;
        double totalAngleChange = 0;
        double maxSpeed = 0;

        for (AirMovementData movement : recentMovements) {
            // Skip if this movement isn't in air
            if (movement == null) {
                return false; // Exit early
            }

            // Track maximum speed
            maxSpeed = Math.max(maxSpeed, movement.horizontalSpeed);

            // Only analyze movements after being in air for 4+ ticks
            if (movement.airTicks < 5) {
                continue;
            }

            // Check for direction changes
            if (movement.angleChange > MAX_AIR_DIRECTION_CHANGE) {
                directionChangeCount++;
                totalAngleChange += movement.angleChange;
            }

            // Check for suspicious acceleration in air
            if (movement.horizontalAcceleration > adjustedAccelerationThreshold) {
                accelerationViolations++;
            }

            // Check for correlation between yaw change and movement direction change
            // This is a very strong indicator of air strafe (player turns camera and immediately changes direction)
            double yawDiff = Math.abs(movement.yaw - lastYaw);
            if (yawDiff > 15 && movement.angleChange > 0.3) {  // Increased thresholds
                hasYawCorrelation = true;
            }
        }

        // Send detailed debug info if enabled
        if (debugMode) {
            ChatUtil.sendDebug(player, "Air strafe detection data:");
            ChatUtil.sendDebug(player, String.format(
                    "  Direction changes: %d, Accel violations: %d, Yaw correlation: %s",
                    directionChangeCount, accelerationViolations, hasYawCorrelation ? "YES" : "NO"));
            ChatUtil.sendDebug(player, String.format(
                    "  Air changes: %d, Total angle: %.2f, Max speed: %.3f",
                    directionChangesInAir, totalAngleChange, maxSpeed));
            ChatUtil.sendDebug(player, String.format(
                    "  Air ticks: %d, Potion modifiers: Speed=%d, Jump=%d",
                    airTicks, speedAmplifier, jumpAmplifier));
        }

        // COMBINATION CRITERIA
        // Each criterion alone is not enough - we need multiple indicators
        // to reduce false positives

        // 1. Direction changes with yaw correlation (most reliable)
        if (directionChangeCount >= 3 && hasYawCorrelation && maxSpeed > 0.12) {
            return true;
        }

        // 2. Acceleration with direction changes
        if (accelerationViolations >= 5 && directionChangeCount >= 3 && maxSpeed > 0.15) {
            return true;
        }

        // 3. Excessive direction changes overall
        if (directionChangesInAir >= AIR_CHANGE_THRESHOLD && maxSpeed > 0.2) {
            return true;
        }

        return false;
    }

    /**
     * Calculate angle between two movement vectors
     */
    private double calculateAngleBetween(Vector v1, Vector v2) {
        // Handle zero vectors to prevent NaN results
        if (v1.lengthSquared() < 0.000001 || v2.lengthSquared() < 0.000001) {
            return 0;
        }

        double dot = v1.dot(v2);
        double cos = dot / (v1.length() * v2.length());

        // Handle floating point precision issues
        cos = Math.max(-1.0, Math.min(1.0, cos));

        return Math.acos(cos);
    }

    /**
     * Handle air strafe violation
     */
    private void handleAirStrafeViolation(Player player, double speed, double angle, double acceleration) {
        violations += 1.0;
        lastViolationTime = System.currentTimeMillis();

        if (debugMode) {
            ChatUtil.sendWarning(player, String.format(
                    "Possible SpeedB violation: Speed=%.2f, AirTime=%d, DirChanges=%d, Angle=%.2f, Accel=%.3f",
                    speed, airTicks, directionChangesInAir, angle, acceleration));
        }

        if (violations >= MAX_VIOLATIONS_BEFORE_ALERT) {
            String details = String.format("AirStrafe: Speed=%.2f, AirTime=%d, DirChanges=%d, Angle=%.2f, Accel=%.3f, Violations=%.1f",
                    speed, airTicks, directionChangesInAir, angle, acceleration, violations);
            flag(player, "Air movement control", details);
            violations = Math.max(0, violations - 1.5);
        }
    }

    /**
     * Reset all tracking data when exemption conditions are met
     */
    private void resetTrackingData() {
        airTicks = 0;
        groundTicks = 0;
        wasInAir = false;
        directionChangesInAir = 0;
        lastMoveDirection = null;
        packetBurstCount = 0;
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

    // NEW EXEMPTION METHODS

    /**
     * Check if player recently jumped
     */
    private boolean isRecentlyJumped() {
        return System.currentTimeMillis() - lastJumpTime < JUMP_EXEMPT_TIME;
    }

    /**
     * Check if player is near stairs
     */
    private boolean isNearStairs(Player player) {
        return CheckUtil.isCloseToBlock(player, Material.OAK_STAIRS) ||
                CheckUtil.isCloseToBlock(player, Material.STONE_STAIRS) ||
                CheckUtil.isCloseToBlock(player, Material.BIRCH_STAIRS) ||
                CheckUtil.isCloseToBlock(player, Material.SPRUCE_STAIRS) ||
                CheckUtil.isCloseToBlock(player, Material.JUNGLE_STAIRS) ||
                CheckUtil.isCloseToBlock(player, Material.ACACIA_STAIRS) ||
                CheckUtil.isCloseToBlock(player, Material.DARK_OAK_STAIRS) ||
                CheckUtil.isCloseToBlock(player, Material.CRIMSON_STAIRS) ||
                CheckUtil.isCloseToBlock(player, Material.WARPED_STAIRS) ||
                CheckUtil.isCloseToBlock(player, Material.NETHER_BRICK_STAIRS) ||
                CheckUtil.isCloseToBlock(player, Material.SANDSTONE_STAIRS);
    }

    /**
     * Check if player is near slabs
     */
    private boolean isNearSlab(Player player) {
        return CheckUtil.isCloseToBlock(player, Material.STONE_SLAB) ||
                CheckUtil.isCloseToBlock(player, Material.SANDSTONE_SLAB) ||
                CheckUtil.isCloseToBlock(player, Material.OAK_SLAB) ||
                CheckUtil.isCloseToBlock(player, Material.SPRUCE_SLAB) ||
                CheckUtil.isCloseToBlock(player, Material.BIRCH_SLAB) ||
                CheckUtil.isCloseToBlock(player, Material.JUNGLE_SLAB) ||
                CheckUtil.isCloseToBlock(player, Material.ACACIA_SLAB) ||
                CheckUtil.isCloseToBlock(player, Material.DARK_OAK_SLAB);
    }

    /**
     * Process teleport events
     */
    public void onPlayerTeleport() {
        lastTeleportTime = System.currentTimeMillis();
        violations = Math.max(0, violations - 1);
        resetTrackingData();
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
        // Reset air movement tracking when player receives velocity
        if (wasInAir && velocity.lengthSquared() > 0.04) {
            directionChangesInAir = 0;
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

    /**
     * Air movement data storage class
     */
    private static class AirMovementData {
        private final double x;
        private final double y;
        private final double z;
        private final double horizontalSpeed;
        private final double horizontalAcceleration;
        private final double angleChange;
        private final Vector moveDirection;
        private final double yaw;
        private final int airTicks;

        public AirMovementData(double x, double y, double z, double horizontalSpeed,
                               double horizontalAcceleration, double angleChange,
                               Vector moveDirection, double yaw, int airTicks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.horizontalSpeed = horizontalSpeed;
            this.horizontalAcceleration = horizontalAcceleration;
            this.angleChange = angleChange;
            this.moveDirection = moveDirection;
            this.yaw = yaw;
            this.airTicks = airTicks;
        }
    }

    /**
     * Buffer for storing air movement data
     */
    private static class AirMovementBuffer {
        private final Deque<AirMovementData> buffer;
        private final int maxSize;

        public AirMovementBuffer(int maxSize) {
            this.buffer = new ArrayDeque<>(maxSize);
            this.maxSize = maxSize;
        }

        public void add(AirMovementData data) {
            if (buffer.size() >= maxSize) {
                buffer.pollFirst();
            }
            buffer.addLast(data);
        }

        public AirMovementData getLast() {
            return buffer.isEmpty() ? null : buffer.peekLast();
        }

        public int size() {
            return buffer.size();
        }

        /**
         * Get most recent movements that occurred while airborne
         */
        public AirMovementData[] getRecentAirMovements(int count) {
            int available = Math.min(count, buffer.size());
            AirMovementData[] result = new AirMovementData[available];

            int index = 0;
            for (AirMovementData movement : buffer) {
                if (movement.airTicks > 0 && index < available) {
                    result[available - 1 - index] = movement;
                    index++;
                }

                if (index >= available) {
                    break;
                }
            }

            return result;
        }
    }
}