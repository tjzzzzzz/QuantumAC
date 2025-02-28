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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * FlyB - Specialized in detecting hovering, gliding, and "boat fly" hacks
 * Fixed to prevent NullPointerException in isRecentlyInVehicle method
 */
public class FlyB extends Check {

    // Physics constants
    private static final double FALL_THRESHOLD = -0.03; // Min expected fall rate when in air
    private static final double MINOR_FALL_THRESHOLD = -0.01; // Extremely minimal fall rate

    // Detection constants
    private static final double EPSILON = 1E-6; // Very small number for comparisons
    private static final double HOVER_DISTANCE_THRESHOLD = 0.03; // Max allowed hover difference
    private static final long HOVER_TIME_THRESHOLD = 1500; // 1.5 seconds for hover timeout
    private static final double GLIDE_RATIO_MAX = 3.5; // Maximum glide ratio (horizontal:vertical)
    private static final double BALANCE_VELOCITY_THRESHOLD = 0.005; // Threshold for "balanced" vertical velocity

    // Ground verification
    private static final double GROUND_CHECK_DISTANCE = 0.01; // Distance below player to check for ground
    private static final int MAX_GROUND_SPOOFING_VIOLATIONS = 10; // Max allowed ground spoofing violations
    private static final int MAX_AIR_TICKS = 40; // Max allowed server-verified air ticks

    // Buffer settings
    private static final int HOVER_BUFFER_THRESHOLD = 8;
    private static final int GLIDE_BUFFER_THRESHOLD = 10;
    private static final int BOAT_FLY_BUFFER_THRESHOLD = 7;
    private static final int GROUND_SPOOF_BUFFER_THRESHOLD = 8; // New buffer for ground spoofing
    private static final int BUFFER_DECREMENT = 1;

    // Exemption times
    private static final long TELEPORT_EXEMPT_TIME = 3000; // 3 seconds
    private static final long DAMAGE_EXEMPT_TIME = 1500; // 1.5 seconds
    private static final long VELOCITY_EXEMPT_TIME = 2000; // 2 seconds
    private static final long SPECIAL_EXEMPT_TIME = 1000; // 1 second

    // Buffers for different detection types
    private int hoverBuffer = 0;
    private int glideBuffer = 0;
    private int boatFlyBuffer = 0;
    private int groundSpoofBuffer = 0; // New buffer for ground spoofing detection

    // Player state tracking
    private double lastY = 0.0;
    private double lastYVelocity = 0.0;
    private boolean wasOnGround = true;
    private boolean wasReallyOnGround = true; // Server-verified ground state
    private int airTicks = 0;
    private int serverVerifiedAirTicks = 0; // Server-verified air time tracking
    private int groundSpoofViolations = 0; // Count of ground spoofing instances
    private int hoverSamples = 0;
    private double baseHoverY = 0.0;
    private long hoverStartTime = 0;
    private long balanceStartTime = 0;
    private boolean wasInLiquid = false;
    private boolean wasInVehicle = false;
    private double lastHorizontalSpeed = 0.0;

    // Advanced movement tracking - FIXED: Changed to ConcurrentLinkedDeque for thread safety
    private final Deque<MovementSample> movementHistory = new ConcurrentLinkedDeque<>();
    private final int MAX_HISTORY = 40;

    // Special case timers
    private long lastTeleportTime = 0;
    private long lastDamageTime = 0;
    private long lastVelocityTime = 0;
    private long lastSpecialTime = 0;

    // Material sets for environment checking
    private static final Set<Material> LIQUID_MATERIALS = new HashSet<>();
    private static final Set<Material> CLIMBABLE_MATERIALS = new HashSet<>();
    private static final Set<Material> NON_SOLID_MATERIALS = new HashSet<>();

    static {
        // Setup liquid materials
        LIQUID_MATERIALS.add(Material.WATER);
        LIQUID_MATERIALS.add(Material.LAVA);

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
        NON_SOLID_MATERIALS.add(Material.WATER);
        NON_SOLID_MATERIALS.add(Material.LAVA);
        // Add more non-solid blocks as needed
    }

    public FlyB(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "FlyB", "Movement");
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
        lastHorizontalSpeed = horizontalDistance;

        // Analyze environment
        boolean clientOnGround = player.isOnGround(); // What the client reports
        boolean serverOnGround = isActuallyOnGround(player); // What the server verifies

        boolean inLiquid = isInLiquid(player);
        boolean onClimbable = isOnClimbable(player);
        boolean inVehicle = player.getVehicle() != null;
        boolean hasLevitation = player.hasPotionEffect(PotionEffectType.LEVITATION);
        boolean hasSlowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
        boolean nearGround = isNearGround(player);
        boolean nearCeiling = isNearCeiling(player);
        boolean inWeb = isInWeb(player);

        // Track movement sample
        addMovementSample(to, dx, dy, dz, horizontalDistance, clientOnGround, serverOnGround,
                inLiquid, onClimbable, inVehicle);

        // Skip checks for exempt conditions
        if (isRecentlyTeleported() ||
                isRecentlyDamaged() ||
                isRecentlyVelocity() ||
                isRecentlySpecial() ||
                inLiquid ||
                onClimbable ||
                inWeb ||
                hasLevitation) {

            updateState(to, clientOnGround, serverOnGround, inLiquid, inVehicle);
            return;
        }

        // Check for ground state spoofing
        detectGroundSpoofing(player, clientOnGround, serverOnGround, to.getY());

        // Update air time tracking - using server verification for accuracy
        if (serverOnGround) {
            serverVerifiedAirTicks = 0;
        } else {
            serverVerifiedAirTicks++;
        }

        // Update client-reported air time
        if (clientOnGround) {
            airTicks = 0;
            hoverSamples = 0;
            hoverStartTime = 0;
            balanceStartTime = 0;
        } else {
            airTicks++;
        }

        // Server-verified hover detection - independent of client ground claim
        if (!serverOnGround && !nearGround && !hasLevitation && !hasSlowFalling && serverVerifiedAirTicks > 5) {
            detectHovering(player, to.getY(), dy);
        } else {
            // Reset hover tracking
            hoverSamples = 0;
            hoverStartTime = 0;
        }

        // Server-verified glide detection - independent of client ground claim
        if (!serverOnGround && !nearGround && !hasSlowFalling && !player.isGliding() && serverVerifiedAirTicks > 8) {
            detectGliding(player, horizontalDistance, dy);
        } else {
            glideBuffer = Math.max(0, glideBuffer - BUFFER_DECREMENT);
        }

        // "Boat fly" and creative fly detection
        if (!serverOnGround && (wasInVehicle || isRecentlyInVehicle()) && !player.isGliding() && serverVerifiedAirTicks > 10) {
            detectCreativeFlyHacks(player, to.getY(), dy);
        } else {
            boatFlyBuffer = Math.max(0, boatFlyBuffer - BUFFER_DECREMENT);
        }

        // Detect sustained flight without justification - powerful detection method
        if (serverVerifiedAirTicks > MAX_AIR_TICKS && !hasLevitation && !hasSlowFalling
                && !player.isGliding() && !inLiquid && !onClimbable && !inWeb) {
            detectSustainedFlight(player, to.getY(), serverVerifiedAirTicks);
        }

        // Update state for next check
        updateState(to, clientOnGround, serverOnGround, inLiquid, inVehicle);
    }

    /**
     * Detect when client is spoofing ground status
     */
    private void detectGroundSpoofing(Player player, boolean clientOnGround, boolean serverOnGround,
                                      double currentY) {
        // Ground spoofing = client says on ground, but server says not on ground
        if (clientOnGround && !serverOnGround) {
            // Additional verification to reduce false positives
            boolean nearGroundBlocks = isNearGroundBlock(player);

            // Only count as violation if not near any possible ground
            if (!nearGroundBlocks) {
                groundSpoofViolations++;

                // Increment buffer based on violation count
                groundSpoofBuffer++;

                if (groundSpoofBuffer >= GROUND_SPOOF_BUFFER_THRESHOLD && groundSpoofViolations >= 3) {
                    String details = String.format(
                            "ground-spoof: client=true, server=false, violations=%d, y=%.2f",
                            groundSpoofViolations, currentY
                    );

                    flag(1.0, details);
                    groundSpoofBuffer = Math.max(0, groundSpoofBuffer - 3);
                }
            }
        } else {
            // Decay buffer and violations
            groundSpoofBuffer = Math.max(0, groundSpoofBuffer - BUFFER_DECREMENT);
            if (serverOnGround) {
                groundSpoofViolations = Math.max(0, groundSpoofViolations - 1);
            }
        }

        // If too many violations, start treating client ground as untrustworthy
        if (groundSpoofViolations >= MAX_GROUND_SPOOFING_VIOLATIONS) {
            // From now on, we'll rely more heavily on server-side ground checks
        }
    }

    /**
     * Detect sustained flight without proper justification
     */
    private void detectSustainedFlight(Player player, double currentY, int airTicks) {
        // This detects when a player has been in the air too long without legitimate means
        String details = String.format(
                "sustained-flight: air-ticks=%d, y=%.2f",
                airTicks, currentY
        );

        hoverBuffer += 2;

        if (hoverBuffer >= HOVER_BUFFER_THRESHOLD + 3) { // Higher threshold for sustained flight
            flag(1.0, details);
            hoverBuffer = Math.max(0, hoverBuffer - 5);
        }
    }

    /**
     * Determine if the player is ACTUALLY on the ground from server perspective
     */
    private boolean isActuallyOnGround(Player player) {
        Location loc = player.getLocation();
        double feetY = loc.getY();
        World world = loc.getWorld();

        // Check if there's a solid block directly below the player
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                Block block = world.getBlockAt(
                        (int) Math.floor(loc.getX() + x),
                        (int) Math.floor(feetY - GROUND_CHECK_DISTANCE),
                        (int) Math.floor(loc.getZ() + z)
                );

                if (block.getType().isSolid() && !NON_SOLID_MATERIALS.contains(block.getType())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a player is near any block that could be considered ground
     * More lenient than isActuallyOnGround for reducing false positives
     */
    private boolean isNearGroundBlock(Player player) {
        Location loc = player.getLocation();
        double feetY = loc.getY();
        World world = loc.getWorld();

        // Check within a slightly larger radius and distance
        for (double x = -0.4; x <= 0.4; x += 0.4) {
            for (double z = -0.4; z <= 0.4; z += 0.4) {
                for (double y = 0; y <= 0.5; y += 0.1) {
                    Block block = world.getBlockAt(
                            (int) Math.floor(loc.getX() + x),
                            (int) Math.floor(feetY - y),
                            (int) Math.floor(loc.getZ() + z)
                    );

                    if (block.getType().isSolid() && !NON_SOLID_MATERIALS.contains(block.getType())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Add a new movement sample to the history
     */
    private void addMovementSample(Location location, double dx, double dy, double dz,
                                   double horizontalDistance, boolean clientOnGround, boolean serverOnGround,
                                   boolean inLiquid, boolean onClimbable, boolean inVehicle) {

        MovementSample sample = new MovementSample(
                location.getX(), location.getY(), location.getZ(),
                dx, dy, dz, horizontalDistance,
                clientOnGround, serverOnGround, inLiquid, onClimbable, inVehicle,
                System.currentTimeMillis()
        );

        movementHistory.addLast(sample);

        while (movementHistory.size() > MAX_HISTORY) {
            movementHistory.removeFirst();
        }
    }

    /**
     * Detect hovering behavior (maintaining fixed height)
     */
    private void detectHovering(Player player, double currentY, double dy) {
        if (hoverSamples == 0) {
            baseHoverY = currentY;
            hoverStartTime = System.currentTimeMillis();
            hoverSamples = 1;
            return;
        }

        // Check if player is staying around the same height
        double yDifference = Math.abs(currentY - baseHoverY);

        if (yDifference < HOVER_DISTANCE_THRESHOLD) {
            hoverSamples++;

            // Basic hover detection - player maintains height for too long
            long hoverDuration = System.currentTimeMillis() - hoverStartTime;

            if (hoverDuration > HOVER_TIME_THRESHOLD && hoverSamples >= 8) {
                double fallExpectation = FALL_THRESHOLD * (hoverDuration / 1000.0);

                String details = String.format(
                        "hover-violation: y=%.2f, duration=%dms, samples=%d, expected-fall=%.2f",
                        currentY, hoverDuration, hoverSamples, fallExpectation
                );

                hoverBuffer += Math.max(1, hoverSamples / 5);

                if (hoverBuffer >= HOVER_BUFFER_THRESHOLD) {
                    flag(1.0, details);
                    hoverBuffer = Math.max(0, hoverBuffer - 4);

                    // Reset hover tracking
                    hoverSamples = 0;
                    hoverStartTime = System.currentTimeMillis();
                    baseHoverY = currentY;
                }
            }
        } else {
            // If significant vertical movement, adjust the base hover height
            baseHoverY = currentY;

            // Keep sampling but reduce the buffer slightly
            hoverBuffer = Math.max(0, hoverBuffer - BUFFER_DECREMENT);
        }

        // Detect subtle balance behavior (very small vertical movement)
        if (Math.abs(dy) < BALANCE_VELOCITY_THRESHOLD) {
            if (balanceStartTime == 0) {
                balanceStartTime = System.currentTimeMillis();
            } else {
                long balanceDuration = System.currentTimeMillis() - balanceStartTime;

                // Longer balance time is more suspicious
                if (balanceDuration > 1000 && serverVerifiedAirTicks > 10) {
                    String details = String.format(
                            "balance-violation: y=%.2f, dy=%.5f, duration=%dms, air-ticks=%d",
                            currentY, dy, balanceDuration, serverVerifiedAirTicks
                    );

                    hoverBuffer++;

                    if (hoverBuffer >= HOVER_BUFFER_THRESHOLD) {
                        flag(1.0, details);
                        hoverBuffer = Math.max(0, hoverBuffer - 3);
                        balanceStartTime = System.currentTimeMillis();
                    }
                }
            }
        } else {
            balanceStartTime = 0;
        }
    }

    /**
     * Detect unnatural gliding (without elytra)
     */
    private void detectGliding(Player player, double horizontalDistance, double dy) {
        // Skip if no horizontal movement
        if (horizontalDistance < 0.1) {
            return;
        }

        // Check for horizontal movement with minimal vertical drop
        if (dy > FALL_THRESHOLD && dy < 0) {
            // Calculate glide ratio (horizontal distance : vertical drop)
            double glideRatio = horizontalDistance / Math.abs(dy);

            // Natural glide ratio shouldn't exceed a certain value
            if (glideRatio > GLIDE_RATIO_MAX) {
                String details = String.format(
                        "glide-violation: ratio=%.2f, h-dist=%.2f, v-dist=%.5f",
                        glideRatio, horizontalDistance, dy
                );

                glideBuffer += (glideRatio > GLIDE_RATIO_MAX * 1.5) ? 2 : 1;

                if (glideBuffer >= GLIDE_BUFFER_THRESHOLD) {
                    flag(1.0, details);
                    glideBuffer = Math.max(0, glideBuffer - 3);
                }
            }
        }

        // Check for horizontal movement with near-zero vertical motion
        if (Math.abs(dy) < MINOR_FALL_THRESHOLD && horizontalDistance > 0.15) {
            String details = String.format(
                    "zero-fall-glide: h-dist=%.2f, v-dist=%.5f, air-ticks=%d",
                    horizontalDistance, dy, serverVerifiedAirTicks
            );

            glideBuffer += 2;

            if (glideBuffer >= GLIDE_BUFFER_THRESHOLD) {
                flag(1.0, details);
                glideBuffer = Math.max(0, glideBuffer - 3);
            }
        }

        // Also decay the buffer when movement is natural
        if (dy < FALL_THRESHOLD) {
            glideBuffer = Math.max(0, glideBuffer - BUFFER_DECREMENT);
        }
    }

    /**
     * Detect creative fly hacks (including "boat fly")
     */
    private void detectCreativeFlyHacks(Player player, double currentY, double dy) {
        // Detect upward movement after leaving vehicle
        if (dy > 0 && !wasReallyOnGround) {
            String details = String.format(
                    "vehicle-fly: y=%.2f, dy=%.3f, air-ticks=%d",
                    currentY, dy, serverVerifiedAirTicks
            );

            boatFlyBuffer += 2;

            if (boatFlyBuffer >= BOAT_FLY_BUFFER_THRESHOLD) {
                flag(1.0, details);
                boatFlyBuffer = Math.max(0, boatFlyBuffer - 3);
            }
        }

        // Detect maintaining height after leaving vehicle
        if (Math.abs(dy) < MINOR_FALL_THRESHOLD && serverVerifiedAirTicks > 15) {
            String details = String.format(
                    "vehicle-hover: y=%.2f, dy=%.5f, air-ticks=%d",
                    currentY, dy, serverVerifiedAirTicks
            );

            boatFlyBuffer++;

            if (boatFlyBuffer >= BOAT_FLY_BUFFER_THRESHOLD) {
                flag(1.0, details);
                boatFlyBuffer = Math.max(0, boatFlyBuffer - 3);
            }
        }
    }

    /**
     * Check if player was recently in a vehicle
     * FIXED: Added null checks and concurrent safety
     */
    private boolean isRecentlyInVehicle() {
        // Check if movement history is empty
        if (movementHistory.isEmpty()) {
            return false;
        }

        try {
            // Check the movement history safely
            long currentTime = System.currentTimeMillis();

            // Make a copy to avoid concurrent modification
            List<MovementSample> safeCopy = new ArrayList<>(movementHistory);

            for (MovementSample sample : safeCopy) {
                if (sample != null && currentTime - sample.timestamp < 3000 && sample.inVehicle) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Log the error but don't crash
            plugin.getLogger().warning("Error in isRecentlyInVehicle: " + e.getMessage());
            return false;
        }

        return false;
    }

    /**
     * Update player state
     */
    private void updateState(Location location, boolean clientOnGround, boolean serverOnGround,
                             boolean inLiquid, boolean inVehicle) {
        lastY = location.getY();
        wasOnGround = clientOnGround;
        wasReallyOnGround = serverOnGround;
        wasInLiquid = inLiquid;
        wasInVehicle = inVehicle;
        playerData.setLastLocation(location);
    }

    /**
     * Reset detection state when player becomes exempt
     */
    private void resetDetectionState() {
        hoverBuffer = 0;
        glideBuffer = 0;
        boatFlyBuffer = 0;
        groundSpoofBuffer = 0;
        airTicks = 0;
        serverVerifiedAirTicks = 0;
        groundSpoofViolations = 0;
        hoverSamples = 0;
        hoverStartTime = 0;
        balanceStartTime = 0;
    }

    /**
     * Check if player is exempt from fly checks
     */
    private boolean isExempt(Player player) {
        return player.isFlying() ||
                player.getAllowFlight() ||
                player.getGameMode() == GameMode.CREATIVE ||
                player.getGameMode() == GameMode.SPECTATOR ||
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
        Material material = player.getLocation().getBlock().getType();
        return CLIMBABLE_MATERIALS.contains(material);
    }

    /**
     * Check if player is in a web
     */
    private boolean isInWeb(Player player) {
        return player.getLocation().getBlock().getType() == Material.COBWEB;
    }

    /**
     * Check if player is near ground
     */
    private boolean isNearGround(Player player) {
        Location loc = player.getLocation();
        double feetY = loc.getY();
        World world = loc.getWorld();

        // Check within a small radius below the player
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                for (double y = 0; y <= 2; y += 0.5) {
                    Block block = world.getBlockAt(
                            (int) Math.floor(loc.getX() + x),
                            (int) Math.floor(feetY - y),
                            (int) Math.floor(loc.getZ() + z)
                    );

                    if (block.getType().isSolid() && !NON_SOLID_MATERIALS.contains(block.getType())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if player is near ceiling
     */
    private boolean isNearCeiling(Player player) {
        Location loc = player.getLocation();
        double headY = loc.getY() + 1.8; // Player height
        World world = loc.getWorld();

        // Check within a small radius above the player
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                for (double y = 0; y <= 2; y += 0.5) {
                    Block block = world.getBlockAt(
                            (int) Math.floor(loc.getX() + x),
                            (int) Math.floor(headY + y),
                            (int) Math.floor(loc.getZ() + z)
                    );

                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
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
     * Check if player was recently in a special condition
     */
    private boolean isRecentlySpecial() {
        return System.currentTimeMillis() - lastSpecialTime < SPECIAL_EXEMPT_TIME;
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

    /**
     * Called for other special conditions
     */
    public void onSpecialCondition() {
        lastSpecialTime = System.currentTimeMillis();
    }

    /**
     * Private class to store movement samples
     */
    private static class MovementSample {
        public final double x, y, z;
        public final double dx, dy, dz;
        public final double horizontalDistance;
        public final boolean clientOnGround; // What client reported
        public final boolean serverOnGround; // What server verified
        public final boolean inLiquid;
        public final boolean onClimbable;
        public final boolean inVehicle;
        public final long timestamp;

        public MovementSample(double x, double y, double z,
                              double dx, double dy, double dz,
                              double horizontalDistance, boolean clientOnGround, boolean serverOnGround,
                              boolean inLiquid, boolean onClimbable,
                              boolean inVehicle, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.horizontalDistance = horizontalDistance;
            this.clientOnGround = clientOnGround;
            this.serverOnGround = serverOnGround;
            this.inLiquid = inLiquid;
            this.onClimbable = onClimbable;
            this.inVehicle = inVehicle;
            this.timestamp = timestamp;
        }
    }
}