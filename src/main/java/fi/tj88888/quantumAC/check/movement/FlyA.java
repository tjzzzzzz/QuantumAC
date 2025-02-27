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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * FlyA - Specialized in detecting gravity and vertical movement violations
 *
 * This check focuses on:
 * 1. Gravity violations (not falling when you should)
 * 2. Vertical acceleration analysis (going up faster than possible)
 * 3. Terminal velocity violations (falling too slowly)
 * 4. Vertical motion inconsistencies
 */
public class FlyA extends Check {

    // Physics constants for Minecraft
    private static final double GRAVITY_ACCELERATION = 0.08;
    private static final double TERMINAL_VELOCITY = 3.92; // Max falling speed
    private static final double MAX_JUMP_HEIGHT = 1.25; // Max jump height with no effects
    private static final double MAX_UP_VELOCITY = 0.42; // Max upward velocity normally
    private static final double DRAG_FACTOR = 0.98; // Air resistance factor
    private static final int MAX_HOVER_TICKS = 7; // Increased from 4 to 7 to reduce false positives
    private static final double MIN_EXPECTED_FALL = 0.015; // Reduced from 0.02 to be more lenient

    // Detection settings - adjusted to reduce false positives
    private static final int BUFFER_THRESHOLD = 12; // Increased from 8 to 12
    private static final int BUFFER_DECREMENT = 2; // Increased from 1 to 2 to decay faster
    private static final double HOVER_THRESHOLD = 0.01; // Increased from 0.005 to be more lenient
    private static final double EPSILON = 0.001; // Small value for comparisons

    // Jump boost effect multiplier
    private static final double JUMP_BOOST_MULTIPLIER = 0.1;

    // Exempt timers - increased durations
    private static final long TELEPORT_EXEMPT_TIME = 4000; // Increased from 3000ms to 4000ms
    private static final long DAMAGE_EXEMPT_TIME = 2000; // Increased from 1500ms to 2000ms
    private static final long VELOCITY_EXEMPT_TIME = 2500; // Increased from 2000ms to 2500ms
    private static final long SPECIAL_BLOCK_EXEMPT_TIME = 1000; // Increased from 500ms to 1000ms
    private static final long GROUND_EXIT_EXEMPT_TIME = 500; // New exemption time after leaving ground

    // Climbable blocks
    private static final Set<Material> CLIMBABLE_MATERIALS = new HashSet<>();
    private static final Set<Material> NON_SOLID_MATERIALS = new HashSet<>();
    private static final Set<Material> LIQUID_MATERIALS = new HashSet<>();
    private static final Set<Material> SPECIAL_BLOCKS = new HashSet<>();
    private static final Set<Material> BOUNCE_BLOCKS = new HashSet<>(); // Added bounce blocks category

    static {
        // Setup climbable materials
        CLIMBABLE_MATERIALS.add(Material.LADDER);
        CLIMBABLE_MATERIALS.add(Material.VINE);
        CLIMBABLE_MATERIALS.add(Material.SCAFFOLDING);
        CLIMBABLE_MATERIALS.add(Material.TWISTING_VINES);
        CLIMBABLE_MATERIALS.add(Material.WEEPING_VINES);

        // Setup non-solid materials
        NON_SOLID_MATERIALS.add(Material.AIR);
        NON_SOLID_MATERIALS.add(Material.CAVE_AIR);
        NON_SOLID_MATERIALS.add(Material.VOID_AIR);

        // Setup liquid materials
        LIQUID_MATERIALS.add(Material.WATER);
        LIQUID_MATERIALS.add(Material.LAVA);

        // Special blocks that affect movement
        SPECIAL_BLOCKS.add(Material.SLIME_BLOCK);
        SPECIAL_BLOCKS.add(Material.HONEY_BLOCK);
        SPECIAL_BLOCKS.add(Material.COBWEB);
        SPECIAL_BLOCKS.add(Material.SOUL_SAND);

        // Blocks that can cause bouncing or irregular movement
        BOUNCE_BLOCKS.add(Material.SLIME_BLOCK);
        BOUNCE_BLOCKS.addAll(Arrays.asList(
                Material.RED_BED,
                Material.BLUE_BED,
                Material.GREEN_BED,
                Material.YELLOW_BED,
                Material.PURPLE_BED,
                Material.WHITE_BED,
                Material.BLACK_BED,
                Material.ORANGE_BED,
                Material.BROWN_BED,
                Material.CYAN_BED,
                Material.LIGHT_BLUE_BED,
                Material.LIME_BED,
                Material.MAGENTA_BED,
                Material.PINK_BED,
                Material.LIGHT_GRAY_BED,
                Material.GRAY_BED
        ));

        BOUNCE_BLOCKS.add(Material.PISTON);
        BOUNCE_BLOCKS.add(Material.STICKY_PISTON);
    }

    // Player state tracking
    private boolean wasOnGround = true;
    private double lastY = 0.0;
    private double lastVelocityY = 0.0;
    private int buffer = 0;
    private int hoverTicks = 0;
    private boolean wasInLiquid = false;
    private boolean wasInWeb = false;
    private boolean wasOnClimbable = false;
    private boolean predictedPositionValid = false;
    private double predictedY = 0.0;
    private boolean wasNearGround = true; // Track if was near ground previously

    // Detailed vertical movement tracking
    private final Deque<Double> verticalMovements = new ArrayDeque<>();
    private final Deque<Double> verticalVelocities = new ArrayDeque<>();
    private final Deque<Long> verticalTimestamps = new ArrayDeque<>();
    private final Deque<Boolean> groundStateHistory = new ArrayDeque<>();
    private final int MAX_SAMPLES = 30;

    // Special case timers
    private long lastTeleportTime = 0;
    private long lastDamageTime = 0;
    private long lastVelocityTime = 0;
    private long lastSpecialBlockTime = 0;
    private long lastGroundExitTime = 0; // New timer for when player leaves ground
    private long lastBounceTime = 0; // New timer for bounce blocks

    // Additional tracking to reduce false positives
    private int consecutiveGravityViolations = 0;
    private int consecutiveHoverViolations = 0;
    private double lastHorizontalSpeed = 0.0; // Track horizontal movement

    public FlyA(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "FlyA", "Movement");
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

        // Calculate vertical movement
        double dy = to.getY() - from.getY();

        // Calculate horizontal movement for context
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        lastHorizontalSpeed = horizontalDistance;

        // Analyze environment
        boolean onGround = player.isOnGround();
        boolean inLiquid = isInLiquid(player);
        boolean onClimbable = isOnClimbable(player);
        boolean nearGround = isNearGround(player);
        boolean nearCeiling = isNearCeiling(player);
        boolean inWeb = isInWeb(player);
        boolean hasLevitation = player.hasPotionEffect(PotionEffectType.LEVITATION);
        boolean hasSlowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
        boolean onBounceBlock = isOnBounceBlock(player);

        // Track when player leaves the ground
        if (wasOnGround && !onGround) {
            lastGroundExitTime = System.currentTimeMillis();
        }

        // Check for special blocks that can affect movement
        boolean onSpecialBlock = isOnSpecialBlock(player);
        if (onSpecialBlock) {
            lastSpecialBlockTime = System.currentTimeMillis();
        }

        // Update vertical movement history
        updateVerticalHistory(dy, to.getY(), onGround);

        // Check for bounce blocks
        if (onBounceBlock) {
            lastBounceTime = System.currentTimeMillis();
        }

        // Special exemption checks - added more exemptions to reduce false positives
        if (isRecentlyTeleported() ||
                isRecentlyDamaged() ||
                isRecentlyVelocity() ||
                isRecentlyOnSpecialBlock() ||
                isRecentlyLeftGround() || // New check for recently left ground
                isRecentlyBounced() || // New check for recently bounced
                inLiquid ||
                onClimbable ||
                inWeb ||
                hasLevitation) {

            wasOnGround = onGround;
            wasNearGround = nearGround;
            wasInLiquid = inLiquid;
            wasOnClimbable = onClimbable;
            wasInWeb = inWeb;
            lastY = to.getY();
            playerData.setLastLocation(to);
            return;
        }

        // Apply accurate prediction only if we have valid state from previous tick
        if (predictedPositionValid && !wasOnGround) {
            checkGravityViolation(player, dy, to.getY(), onGround, hasSlowFalling, nearGround, nearCeiling, horizontalDistance);
        }

        // Update predicted position for next iteration
        predictedY = predictNextYPosition(player, to.getY(), onGround, hasSlowFalling);
        predictedPositionValid = true;

        // Check for hovering (minimal vertical movement when not on ground)
        if (!onGround && !nearGround && !inLiquid && !onClimbable && !inWeb && !hasLevitation && !hasSlowFalling) {
            checkHoverViolation(player, dy, horizontalDistance);
        } else {
            hoverTicks = 0;
            consecutiveHoverViolations = 0;
        }

        // Check for vertical acceleration violations
        if (!onGround && wasOnGround) {
            // Player just jumped or started falling
            lastVelocityY = dy;
        } else if (!onGround && !wasOnGround) {
            // Player is in air, check for unusual upward acceleration
            checkAccelerationViolation(player, dy, hasSlowFalling);
        }

        // Update state for next check
        wasOnGround = onGround;
        wasNearGround = nearGround;
        wasInLiquid = inLiquid;
        wasOnClimbable = onClimbable;
        wasInWeb = inWeb;
        lastY = to.getY();
        playerData.setLastLocation(to);
    }

    /**
     * Update vertical movement history
     */
    private void updateVerticalHistory(double dy, double currentY, boolean onGround) {
        // Record movement
        verticalMovements.addLast(dy);
        verticalVelocities.addLast(dy);
        verticalTimestamps.addLast(System.currentTimeMillis());
        groundStateHistory.addLast(onGround);

        // Maintain maximum size
        if (verticalMovements.size() > MAX_SAMPLES) {
            verticalMovements.removeFirst();
            verticalVelocities.removeFirst();
            verticalTimestamps.removeFirst();
            groundStateHistory.removeFirst();
        }
    }

    /**
     * Check for gravity violations (not falling when expected)
     * Added horizontal speed context to reduce false positives
     */
    private void checkGravityViolation(Player player, double dy, double currentY, boolean onGround,
                                       boolean hasSlowFalling, boolean nearGround, boolean nearCeiling,
                                       double horizontalSpeed) {
        double expectedY = predictedY;
        double difference = currentY - expectedY;
        double absDifference = Math.abs(difference);

        // More lenient tolerance when moving horizontally
        double dynamicTolerance = calculateTolerance(player);
        if (horizontalSpeed > 0.15) {
            dynamicTolerance += horizontalSpeed * 0.05; // More tolerance when moving fast horizontally
        }

        // If player should be falling but isn't
        if (difference > dynamicTolerance && !nearGround && !nearCeiling) {
            // Not falling as expected
            String details = String.format(
                    "gravity-violation: current-y=%.3f, expected-y=%.3f, diff=%.3f, dy=%.3f, h-speed=%.3f",
                    currentY, expectedY, difference, dy, horizontalSpeed
            );

            // Increment consecutive violations
            consecutiveGravityViolations++;

            // Adjust buffer increment based on difference magnitude and horizontal speed
            int bufferIncrement = 1;
            if (absDifference > 0.2) {
                bufferIncrement = 2;
            }

            // Reduce buffer increment if moving horizontally (could be lag or packet issues)
            if (horizontalSpeed > 0.2) {
                bufferIncrement = Math.max(1, bufferIncrement - 1);
            }

            buffer += bufferIncrement;

            // Only flag after consistent violations and sufficient buffer
            if (consecutiveGravityViolations >= 3 && buffer >= BUFFER_THRESHOLD) {
                flag(1.0, details);
                buffer = Math.max(0, buffer - 6); // Larger buffer reduction after flagging
                consecutiveGravityViolations = 0; // Reset consecutive counter
            }
        } else {
            // Gradually decrease consecutive violations
            if (System.currentTimeMillis() % 3 == 0) { // Only decrease occasionally to prevent too quick decay
                consecutiveGravityViolations = Math.max(0, consecutiveGravityViolations - 1);
            }

            // Decay buffer faster when expected movement
            buffer = Math.max(0, buffer - BUFFER_DECREMENT);
        }
    }

    /**
     * Check for hovering (minimal vertical movement)
     * Added horizontal context to reduce false positives
     */
    private void checkHoverViolation(Player player, double dy, double horizontalSpeed) {
        if (Math.abs(dy) < HOVER_THRESHOLD) {
            hoverTicks++;

            // Allow more hover ticks if player is moving horizontally
            int adjustedMaxHoverTicks = MAX_HOVER_TICKS;
            if (horizontalSpeed > 0.1) {
                adjustedMaxHoverTicks += (int)(horizontalSpeed * 5); // Allow more hover ticks when moving
            }

            if (hoverTicks > adjustedMaxHoverTicks) {
                // Calculate the expected fall amount
                double expectedFall = MIN_EXPECTED_FALL * hoverTicks;
                double tolerance = calculateTolerance(player);

                // Players should be falling at some rate when in air
                String details = String.format(
                        "hover-violation: dy=%.5f, hover-ticks=%d, expected-fall=%.5f, h-speed=%.3f",
                        dy, hoverTicks, expectedFall, horizontalSpeed
                );

                consecutiveHoverViolations++;
                buffer++;

                // Only flag for hover violation if it's persistent and not moving much horizontally
                if (consecutiveHoverViolations >= 2 && horizontalSpeed < 0.2 && buffer >= BUFFER_THRESHOLD) {
                    flag(1.0, details);
                    buffer = Math.max(0, buffer - 5);
                    hoverTicks = 0;
                    consecutiveHoverViolations = 0;
                }
            }
        } else {
            // If there was significant movement, reduce hover counter
            hoverTicks = Math.max(0, hoverTicks - 1);
            consecutiveHoverViolations = 0;
        }
    }

    /**
     * Check for upward acceleration violations
     */
    private void checkAccelerationViolation(Player player, double dy, boolean hasSlowFalling) {
        // Check for upward acceleration (not possible in normal Minecraft physics)
        if (dy > 0 && dy > lastVelocityY && lastVelocityY <= 0) {
            // Player is accelerating upward after reaching the peak of jump
            String details = String.format(
                    "acceleration-violation: dy=%.3f, last-velocity=%.3f",
                    dy, lastVelocityY
            );

            buffer += 2;

            if (buffer >= BUFFER_THRESHOLD) {
                flag(1.0, details);
                buffer = Math.max(0, buffer - 4);
            }
        }

        // Check for too slow falling (terminal velocity violation)
        // More lenient check for slow falling
        if (!hasSlowFalling && dy < 0 && dy > -MIN_EXPECTED_FALL && !isRecentlyJumped() && lastHorizontalSpeed < 0.1) {
            String details = String.format(
                    "slow-fall-violation: fall-rate=%.5f, expected-min=%.5f",
                    dy, MIN_EXPECTED_FALL
            );

            buffer++;

            if (buffer >= BUFFER_THRESHOLD) {
                flag(1.0, details);
                buffer = Math.max(0, buffer - 4);
            }
        }

        lastVelocityY = dy;
    }

    /**
     * Predict the next Y position based on Minecraft physics
     * Improved to be more forgiving
     */
    private double predictNextYPosition(Player player, double currentY, boolean onGround, boolean hasSlowFalling) {
        if (onGround) {
            return currentY;
        }

        // Basic vertical velocity calculation with gravity
        double verticalVelocity = lastVelocityY;

        // Apply gravity
        verticalVelocity -= GRAVITY_ACCELERATION;

        // Apply drag
        verticalVelocity *= DRAG_FACTOR;

        // Apply slow falling effect (if present)
        if (hasSlowFalling) {
            verticalVelocity *= 0.4; // Slow falling significantly reduces fall speed
        }

        // Terminal velocity cap
        if (verticalVelocity < -TERMINAL_VELOCITY) {
            verticalVelocity = -TERMINAL_VELOCITY;
        }

        // Calculate next position
        return currentY + verticalVelocity;
    }

    /**
     * Calculate tolerance based on player state
     * Improved with more factors to reduce false positives
     */
    private double calculateTolerance(Player player) {
        double tolerance = 0.025; // Increased base tolerance from 0.015

        // Increase tolerance for special conditions
        if (isRecentlyVelocity() || isRecentlyDamaged()) {
            tolerance += 0.07; // Increased from 0.05
        }

        // More tolerance right after leaving ground
        if (isRecentlyLeftGround()) {
            tolerance += 0.05;
        }

        // Consider ping - more forgiving ping adjustment
        int ping = playerData.getAveragePing();
        if (ping > 100) {
            tolerance += (ping - 100) / 2000.0; // More tolerance for higher ping
        }

        // Consider server TPS
        double tps = plugin.getConfigManager().getCurrentTPS();
        if (tps < 19.5) {
            tolerance += (19.5 - tps) * 0.02; // More tolerance for server lag
        }

        // More tolerance for faster horizontal movement
        if (lastHorizontalSpeed > 0.1) {
            tolerance += lastHorizontalSpeed * 0.03;
        }

        return tolerance;
    }

    /**
     * Reset detection state when player becomes exempt
     */
    private void resetDetectionState() {
        buffer = 0;
        hoverTicks = 0;
        consecutiveGravityViolations = 0;
        consecutiveHoverViolations = 0;
        verticalMovements.clear();
        verticalVelocities.clear();
        verticalTimestamps.clear();
        groundStateHistory.clear();
        predictedPositionValid = false;
    }

    /**
     * Check if player is exempt from fly checks
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
     * Check if a packet is a movement packet
     */
    private boolean isMovementPacket(PacketType type) {
        return type == PacketType.Play.Client.POSITION ||
                type == PacketType.Play.Client.POSITION_LOOK ||
                type == PacketType.Play.Client.LOOK ||
                type == PacketType.Play.Client.FLYING;
    }

    /**
     * Check if player is in a liquid
     */
    private boolean isInLiquid(Player player) {
        Material material = player.getLocation().getBlock().getType();
        return LIQUID_MATERIALS.contains(material);
    }

    /**
     * Check if player is on a climbable block
     */
    private boolean isOnClimbable(Player player) {
        Location loc = player.getLocation();
        Material material = loc.getBlock().getType();

        // Special case for scaffolding (can be climbed from inside)
        Block blockBelow = loc.getBlock().getRelative(BlockFace.DOWN);

        return CLIMBABLE_MATERIALS.contains(material) ||
                CLIMBABLE_MATERIALS.contains(blockBelow.getType());
    }

    /**
     * Check if player is in web
     */
    private boolean isInWeb(Player player) {
        return player.getLocation().getBlock().getType() == Material.COBWEB;
    }

    /**
     * Check if player is on or near a special block
     * Expanded radius check
     */
    private boolean isOnSpecialBlock(Player player) {
        Location loc = player.getLocation();

        // Check current block and blocks below
        for (int i = 0; i <= 3; i++) { // Increased from 2 to 3
            Block block = loc.clone().subtract(0, i, 0).getBlock();
            if (SPECIAL_BLOCKS.contains(block.getType())) {
                return true;
            }
        }

        // Also check horizontally adjacent blocks (for corner cases)
        Block blockNorth = loc.clone().add(0, 0, -1).getBlock();
        Block blockSouth = loc.clone().add(0, 0, 1).getBlock();
        Block blockEast = loc.clone().add(1, 0, 0).getBlock();
        Block blockWest = loc.clone().add(-1, 0, 0).getBlock();

        return SPECIAL_BLOCKS.contains(blockNorth.getType()) ||
                SPECIAL_BLOCKS.contains(blockSouth.getType()) ||
                SPECIAL_BLOCKS.contains(blockEast.getType()) ||
                SPECIAL_BLOCKS.contains(blockWest.getType());
    }

    /**
     * Check if player is on a bounce block
     */
    private boolean isOnBounceBlock(Player player) {
        Location loc = player.getLocation();

        // Check block below player
        for (int i = 0; i <= 3; i++) {
            Block block = loc.clone().subtract(0, i, 0).getBlock();
            if (BOUNCE_BLOCKS.contains(block.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if player is near the ground
     * Expanded radius check to reduce false positives
     */
    private boolean isNearGround(Player player) {
        Location loc = player.getLocation();
        double feetY = loc.getY();

        // Check a few blocks below for ground (wider horizontal radius)
        for (double x = -0.4; x <= 0.4; x += 0.4) {
            for (double z = -0.4; z <= 0.4; z += 0.4) {
                for (double y = 0; y <= 2; y += 0.5) {
                    Block block = new Location(loc.getWorld(),
                            loc.getX() + x,
                            feetY - y,
                            loc.getZ() + z).getBlock();
                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if player is near a ceiling
     * Expanded radius check
     */
    private boolean isNearCeiling(Player player) {
        Location loc = player.getLocation();
        double headY = loc.getY() + 1.8; // Player height

        // Check a few blocks above for ceiling (wider horizontal radius)
        for (double x = -0.4; x <= 0.4; x += 0.4) {
            for (double z = -0.4; z <= 0.4; z += 0.4) {
                for (double y = 0; y <= 2; y += 0.5) {
                    Block block = new Location(loc.getWorld(),
                            loc.getX() + x,
                            headY + y,
                            loc.getZ() + z).getBlock();
                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if player recently jumped
     */
    private boolean isRecentlyJumped() {
        if (groundStateHistory.size() < 6) { // Increased from 4 to 6
            return false;
        }

        // Check if player was on ground and then left ground in the last few ticks
        Object[] states = groundStateHistory.toArray();
        for (int i = states.length - 6; i < states.length - 1; i++) {
            if ((Boolean)states[i] && !(Boolean)states[i+1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if player was recently teleported
     */
    private boolean isRecentlyTeleported() {
        return System.currentTimeMillis() - lastTeleportTime < TELEPORT_EXEMPT_TIME;
    }

    /**
     * Check if player was recently damaged
     */
    private boolean isRecentlyDamaged() {
        return System.currentTimeMillis() - lastDamageTime < DAMAGE_EXEMPT_TIME;
    }

    /**
     * Check if player recently received velocity
     */
    private boolean isRecentlyVelocity() {
        return System.currentTimeMillis() - lastVelocityTime < VELOCITY_EXEMPT_TIME;
    }

    /**
     * Check if player was recently on a special block
     */
    private boolean isRecentlyOnSpecialBlock() {
        return System.currentTimeMillis() - lastSpecialBlockTime < SPECIAL_BLOCK_EXEMPT_TIME;
    }

    /**
     * Check if player recently left ground
     */
    private boolean isRecentlyLeftGround() {
        return System.currentTimeMillis() - lastGroundExitTime < GROUND_EXIT_EXEMPT_TIME;
    }

    /**
     * Check if player recently bounced
     */
    private boolean isRecentlyBounced() {
        return System.currentTimeMillis() - lastBounceTime < SPECIAL_BLOCK_EXEMPT_TIME;
    }

    /**
     * Called when player teleports
     */
    public void onPlayerTeleport() {
        lastTeleportTime = System.currentTimeMillis();
        resetDetectionState();
    }

    /**
     * Called when player takes damage
     */
    public void onPlayerDamage() {
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Called when player receives velocity
     */
    public void onPlayerVelocity(Vector velocity) {
        lastVelocityTime = System.currentTimeMillis();
    }
}