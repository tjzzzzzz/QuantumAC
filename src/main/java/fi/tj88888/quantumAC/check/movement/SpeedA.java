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

/**
 * SpeedA - Advanced movement speed check with acceleration-ratio approach
 *
 * This check detects:
 * 1. Traditional speed hacks using acceleration-ratio analysis
 * 2. Low-factor speed modifications (small consistent boosts)
 * 3. Physically impossible movement patterns based on Minecraft physics
 */
public class SpeedA extends Check {

    // Base speed constants
    private static final double WALK_BASE = 0.217;
    private static final double SPRINT_BASE = 0.281;
    private static final double AIR_FRICTION = 0.91; // Air movement friction factor

    // Surface modifiers
    private static final double ICE_MODIFIER = 1.6;
    private static final double PACKED_ICE_MODIFIER = 1.65;
    private static final double BLUE_ICE_MODIFIER = 1.7;
    private static final double SLIME_MODIFIER = 1.3;
    private static final double SOUL_SAND_MODIFIER = 0.4;
    private static final double WATER_MODIFIER = 0.5;
    private static final double STAIRS_MODIFIER = 1.12;
    private static final double SLAB_MODIFIER = 1.07;

    // Acceleration detection constants
    private static final double MAX_ACCELERATION_RATIO = 1.0; // Maximum allowed acceleration ratio
    private static final double RATIO_LENIENCY = 1E-6; // Small buffer for precision errors
    private static final double MIN_SPEED_CHECK = 0.2; // Only check speeds above this value
    private static final double MAX_BUFFER = 12.0; // Maximum buffer value
    private static final double BUFFER_DECREMENT = 0.05; // Buffer decay rate
    private static final double VIOLATION_THRESHOLD = 9.0; // Buffer threshold for flagging

    // Exemption durations
    private static final long TELEPORT_EXEMPT_TIME = 2000; // 2 seconds exempt after teleport
    private static final long DAMAGE_EXEMPT_TIME = 1000; // 1 second exempt after damage
    private static final long VELOCITY_EXEMPT_TIME = 1500; // 1.5 seconds exempt after velocity
    private static final long ICE_EXEMPT_TIME = 1200; // 1.2 seconds exempt after leaving ice
    private static final long BLOCK_CHANGE_EXEMPT_TIME = 500; // 0.5 seconds exempt after block change
    private static final long JUMP_EXEMPT_TIME = 600; // 0.6 seconds exempt after jump

    // Motion tracking
    private double motionX = 0.0;
    private double motionZ = 0.0;
    private double buffer = 0.0;

    // State tracking
    private boolean wasOnGround = true;
    private boolean wasInLiquid = false;
    private boolean wasOnIce = false;
    private boolean wasOnSlime = false;
    private Material lastBlockType = Material.AIR;
    private int airTicks = 0;
    private int groundTicks = 0;

    // Special event timers
    private long lastJumpTime = 0;
    private long lastDamageTime = 0;
    private long lastVelocityTime = 0;
    private long lastIceTime = 0;
    private long lastTeleportTime = 0;
    private long lastBlockChangeTime = 0;
    private Vector pendingVelocity = null;

    public SpeedA(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "SpeedA", "Movement");
    }

    @Override
    public void processPacket(PacketEvent event) {
        // Filter to just movement packets
        if (!isMovementPacket(event.getPacketType())) {
            return;
        }

        // Skip position-less packets
        if (event.getPacketType() == PacketType.Play.Client.LOOK ||
                event.getPacketType() == PacketType.Play.Client.FLYING) {
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
        double dy = to.getY() - from.getY();
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

        // Get current block under player
        Material currentBlockType = getCurrentFloorType(player);

        // Check if block type changed
        boolean blockTypeChanged = currentBlockType != lastBlockType;
        if (blockTypeChanged) {
            lastBlockChangeTime = System.currentTimeMillis();
        }

        // Update ice state tracking
        boolean anyIce = onIce || onPackedIce || onBlueIce;
        if (anyIce) {
            lastIceTime = System.currentTimeMillis();
        }

        // Track jump (ground to air transition)
        if (wasOnGround && !onGround && dy > 0) {
            lastJumpTime = System.currentTimeMillis();
        }

        // Update ground/air tick counters
        if (onGround) {
            groundTicks++;
            airTicks = 0;
        } else {
            airTicks++;
            groundTicks = 0;
        }

        // Calculate friction based on surfaces
        float friction = (float) AIR_FRICTION;
        if (onGround) {
            // Apply surface friction
            if (onIce) {
                friction = 0.98F;
            } else if (onPackedIce) {
                friction = 0.98F;
            } else if (onBlueIce) {
                friction = 0.989F;
            } else if (onSlime) {
                friction = 0.8F;
            } else if (onSoulSand) {
                friction = 0.6F;
            } else {
                friction = 0.6F; // Default ground friction
            }
        }

        // Calculate maximum allowed move speed
        double movementSpeed = calculateBaseMovementSpeed(player, onGround);

        // Apply surface modifiers to movement speed
        movementSpeed = applyMovementModifiers(movementSpeed, player, onGround, inLiquid,
                onIce, onPackedIce, onBlueIce, onSlime, onSoulSand, onStairs, onSlab);

        // Apply pending velocity if available
        if (pendingVelocity != null) {
            motionX = pendingVelocity.getX();
            motionZ = pendingVelocity.getZ();
            pendingVelocity = null;
        }

        // Calculate expected motion with friction
        double expectedMotion = Math.sqrt(motionX * motionX + motionZ * motionZ);

        // Calculate acceleration ratio (core detection approach)
        double accelerationRatio = (horizontalDistance - expectedMotion) / movementSpeed;

        // Check for exemption conditions
        boolean exempt = isRecentlyTeleported() ||
                isRecentlyDamaged() ||
                isRecentlyVelocity() ||
                isRecentlyBlockChange() ||
                inLiquid;

        // Check for violations
        boolean invalid = accelerationRatio > MAX_ACCELERATION_RATIO + RATIO_LENIENCY && horizontalDistance > MIN_SPEED_CHECK;

        if (invalid && !exempt) {
            // Increment buffer based on severity
            buffer = Math.min(MAX_BUFFER, buffer + Math.min(3.0, accelerationRatio));

            if (buffer > VIOLATION_THRESHOLD) {
                // Format detailed violation information
                String details = String.format(
                        "accel-ratio=%.3f, speed=%.3f, expected=%.3f, max=%.3f, " +
                                "ground=%s, friction=%.3f, buffer=%.1f, ping=%dms",
                        accelerationRatio, horizontalDistance, expectedMotion, movementSpeed,
                        onGround ? "true" : "false", friction, buffer,
                        playerData.getAveragePing()
                );

                // Flag with appropriate VL increment
                flag(Math.min(2.0, accelerationRatio), details);

                // Reduce buffer after flagging
                buffer = Math.max(0, buffer - 3);
            }
        } else {
            // Decay buffer over time
            buffer = Math.max(0, buffer - BUFFER_DECREMENT);
        }

        // Update motion for next check, applying friction
        motionX = dx * friction;
        motionZ = dz * friction;

        // Apply minimum motion cutoff (matches NMS behavior)
        double minimumMotion = 0.005;
        if (Math.abs(motionX) < minimumMotion) motionX = 0.0;
        if (Math.abs(motionZ) < minimumMotion) motionZ = 0.0;

        // Update state for next check
        updateState(player, to, onGround, inLiquid, anyIce, onSlime, currentBlockType);
    }

    /**
     * Calculate the base movement speed
     */
    private double calculateBaseMovementSpeed(Player player, boolean onGround) {
        // Get base speed based on sprint state
        double speed = player.isSprinting() ? SPRINT_BASE : WALK_BASE;

        // Apply potion effects
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int level = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            speed *= 1.0 + (0.2 * level);
        }

        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            int level = player.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier() + 1;
            speed *= 1.0 - (0.15 * level);
        }

        return speed;
    }

    /**
     * Apply various movement modifiers to the base speed
     */
    private double applyMovementModifiers(double baseSpeed, Player player, boolean onGround, boolean inLiquid,
                                          boolean onIce, boolean onPackedIce, boolean onBlueIce,
                                          boolean onSlime, boolean onSoulSand, boolean onStairs, boolean onSlab) {
        double modifiedSpeed = baseSpeed;

        // Handle air vs ground movement
        if (!onGround) {
            // Initial jump boost vs sustained air movement
            if (wasOnGround) {
                modifiedSpeed = player.isSprinting() ? SPRINT_BASE * 1.5 : SPRINT_BASE * 1.25;
            } else {
                // Air movement
                modifiedSpeed = 0.02F + (0.02F * 0.3D);
            }
        } else {
            // Ground movement - apply friction factors
            modifiedSpeed *= 0.16277136F / (0.6F * 0.6F * 0.6F);
        }

        // Apply surface modifiers
        if (onIce) {
            modifiedSpeed *= ICE_MODIFIER;
        } else if (onPackedIce) {
            modifiedSpeed *= PACKED_ICE_MODIFIER;
        } else if (onBlueIce) {
            modifiedSpeed *= BLUE_ICE_MODIFIER;
        }

        if (onSlime) {
            modifiedSpeed *= SLIME_MODIFIER;
        }

        if (onSoulSand) {
            modifiedSpeed *= SOUL_SAND_MODIFIER;
        }

        if (inLiquid) {
            modifiedSpeed *= WATER_MODIFIER;
        }

        if (onStairs) {
            modifiedSpeed *= STAIRS_MODIFIER;
        }

        if (onSlab) {
            modifiedSpeed *= SLAB_MODIFIER;
        }

        // Apply exemption buffers
        if (isRecentlyJumped() && !onGround) {
            modifiedSpeed *= 1.15; // 15% more lenient right after jump
        }

        return modifiedSpeed;
    }

    /**
     * Update tracking state
     */
    private void updateState(Player player, Location location, boolean onGround,
                             boolean inLiquid, boolean onIce, boolean onSlime,
                             Material blockType) {
        wasOnGround = onGround;
        wasInLiquid = inLiquid;
        wasOnIce = onIce;
        wasOnSlime = onSlime;
        lastBlockType = blockType;
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
        buffer = 0.0;
        motionX = 0.0;
        motionZ = 0.0;
        groundTicks = 0;
        airTicks = 0;
    }

    /**
     * Process velocity updates
     */
    public void onPlayerVelocity(Vector velocity) {
        pendingVelocity = velocity.clone();
        lastVelocityTime = System.currentTimeMillis();
    }

    /**
     * Process teleport events
     */
    public void onPlayerTeleport() {
        lastTeleportTime = System.currentTimeMillis();
        resetDetectionState();
    }

    /**
     * Process damage events
     */
    public void onPlayerDamage() {
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Get current floor material under player
     */
    private Material getCurrentFloorType(Player player) {
        return player.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType();
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
     * Check if packet is a movement packet
     */
    private boolean isMovementPacket(PacketType type) {
        return type == PacketType.Play.Client.POSITION ||
                type == PacketType.Play.Client.POSITION_LOOK ||
                type == PacketType.Play.Client.LOOK ||
                type == PacketType.Play.Client.FLYING;
    }

    /**
     * Check if player has been damaged recently
     */
    private boolean isRecentlyDamaged() {
        return System.currentTimeMillis() - lastDamageTime < DAMAGE_EXEMPT_TIME;
    }

    /**
     * Check if player jumped recently
     */
    private boolean isRecentlyJumped() {
        return System.currentTimeMillis() - lastJumpTime < JUMP_EXEMPT_TIME;
    }

    /**
     * Check if player was recently on ice (momentum effect)
     */
    private boolean isRecentlyOnIce() {
        return System.currentTimeMillis() - lastIceTime < ICE_EXEMPT_TIME;
    }

    /**
     * Check if player was recently teleported
     */
    private boolean isRecentlyTeleported() {
        return System.currentTimeMillis() - lastTeleportTime < TELEPORT_EXEMPT_TIME;
    }

    /**
     * Check if player recently changed block types
     */
    private boolean isRecentlyBlockChange() {
        return System.currentTimeMillis() - lastBlockChangeTime < BLOCK_CHANGE_EXEMPT_TIME;
    }

    /**
     * Check if player recently received velocity
     */
    private boolean isRecentlyVelocity() {
        return System.currentTimeMillis() - lastVelocityTime < VELOCITY_EXEMPT_TIME;
    }

    /**
     * Environment checks
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
}