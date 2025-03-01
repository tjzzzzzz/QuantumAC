package fi.tj88888.quantumAC.check.movement.fly;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.ViolationData;
import fi.tj88888.quantumAC.check.movement.fly.components.GroundSpoofingComponent;
import fi.tj88888.quantumAC.check.movement.fly.components.GlideDetectionComponent;
import fi.tj88888.quantumAC.check.movement.fly.components.HoverDetectionComponent;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Deque;

/**
 * FlyB - Specialized in detecting hovering, gliding, and "boat fly" hacks
 * This has been refactored to use the component-based approach.
 */
public class FlyB extends FlyCheck {

    // Components for different detection types
    private final HoverDetectionComponent hoverDetectionComponent;
    private final GlideDetectionComponent glideDetectionComponent;
    private final GroundSpoofingComponent groundSpoofingComponent;

    // Physics constants
    private static final double BALANCE_VELOCITY_THRESHOLD = 0.005; // Threshold for "balanced" vertical velocity
    
    // Detection constants
    private static final int BOAT_FLY_BUFFER_THRESHOLD = 7;
    private static final int MAX_AIR_TICKS = 40; // Max allowed server-verified air ticks
    private static final int BUFFER_DECREMENT = 1;

    // Boat fly detection
    private int boatFlyBuffer = 0;
    private int boatFlyVL = 0;
    
    // Player state tracking
    private double lastY = 0.0;
    private double lastYVelocity = 0.0;
    private boolean wasOnGround = true;
    private boolean wasReallyOnGround = true; // Server-verified ground state
    private int airTicks = 0;
    private int serverVerifiedAirTicks = 0; // Server-verified air time tracking
    private long balanceStartTime = 0;
    private boolean wasInLiquid = false;
    private boolean wasInVehicle = false;
    private double lastHorizontalSpeed = 0.0;

    // Advanced movement tracking
    private final Deque<MovementSample> movementHistory = new ConcurrentLinkedDeque<>();
    private final int MAX_HISTORY = 40;

    public FlyB(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "FlyB");
        this.hoverDetectionComponent = new HoverDetectionComponent();
        this.glideDetectionComponent = new GlideDetectionComponent();
        this.groundSpoofingComponent = new GroundSpoofingComponent();
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

        // Calculate tolerance based on ping and conditions
        double tolerance = calculateTolerance(player);
        
        // Check for ground state spoofing using component
        ViolationData groundSpoofData = groundSpoofingComponent.checkGroundSpoofing(
            player, clientOnGround, serverOnGround, isNearGroundBlock(player), to.getY(), tolerance
        );
        
        if (groundSpoofData != null) {
            flag(player, groundSpoofData.getDetails(), groundSpoofData.getViolationLevel());
        }

        // Update air time tracking - using server verification for accuracy
        if (serverOnGround) {
            serverVerifiedAirTicks = 0;
        } else {
            serverVerifiedAirTicks++;
        }

        // Update client-reported air time
        if (clientOnGround) {
            airTicks = 0;
        } else {
            airTicks++;
        }

        // Server-verified hover detection - independent of client ground claim
        if (!serverOnGround && !nearGround && !hasLevitation && !hasSlowFalling && serverVerifiedAirTicks > 5) {
            ViolationData hoverData = hoverDetectionComponent.checkHovering(
                player, to.getY(), dy, tolerance
            );
            
            if (hoverData != null) {
                flag(player, hoverData.getDetails(), hoverData.getViolationLevel());
            }
        }

        // Server-verified glide detection - independent of client ground claim
        if (!serverOnGround && !nearGround && !hasSlowFalling && !player.isGliding() && serverVerifiedAirTicks > 8) {
            ViolationData glideData = glideDetectionComponent.checkGliding(
                player, horizontalDistance, dy, tolerance
            );
            
            if (glideData != null) {
                flag(player, glideData.getDetails(), glideData.getViolationLevel());
            }
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
     * Detect sustained flight
     */
    private void detectSustainedFlight(Player player, double currentY, int airTicks) {
        String details = String.format(
                "sustained-flight: air-ticks=%d, y=%.2f, spoofs=%d",
                airTicks, currentY, groundSpoofingComponent.getGroundSpoofViolations()
        );
        
        // This is a serious violation - flag immediately
        flag(player, details, 10);
    }

    /**
     * Detect creative-style flying
     */
    private void detectCreativeFlyHacks(Player player, double currentY, double dy) {
        // Boat fly detection focuses on minimal vertical movement similar to creative mode flight
        if (Math.abs(dy) < BALANCE_VELOCITY_THRESHOLD && serverVerifiedAirTicks > 20) {
            boatFlyBuffer++;
            
            if (boatFlyBuffer >= BOAT_FLY_BUFFER_THRESHOLD) {
                boatFlyBuffer = 0;
                boatFlyVL++;
                
                String details = String.format(
                        "boat-fly: dy=%.5f, air-ticks=%d, y=%.2f",
                        dy, serverVerifiedAirTicks, currentY
                );
                
                flag(player, details, boatFlyVL);
            }
        } else {
            boatFlyBuffer = Math.max(0, boatFlyBuffer - BUFFER_DECREMENT);
        }
    }

    /**
     * Server-verified ground detection
     */
    private boolean isActuallyOnGround(Player player) {
        Location loc = player.getLocation();
        
        // Check a small distance below the player
        for (double offset = 0; offset <= 0.1; offset += 0.01) {
            if (!isPassable(loc.clone().subtract(0, offset, 0))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a location contains a passable block
     */
    private boolean isPassable(Location location) {
        return location.getBlock().isPassable();
    }

    /**
     * Check if player is near any ground-like blocks
     */
    private boolean isNearGroundBlock(Player player) {
        Location loc = player.getLocation();
        double playerWidth = 0.3; // Approximate player width/2
        
        // Check in a box below the player for any blocks
        for (double x = -playerWidth; x <= playerWidth; x += playerWidth) {
            for (double z = -playerWidth; z <= playerWidth; z += playerWidth) {
                Location checkLoc = loc.clone().add(x, -0.1, z);
                if (!isPassable(checkLoc)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Add a movement sample to history
     */
    private void addMovementSample(Location location, double dx, double dy, double dz,
                                   double horizontalDistance, boolean clientOnGround, boolean serverOnGround,
                                   boolean inLiquid, boolean onClimbable, boolean inVehicle) {
        MovementSample sample = new MovementSample(
                location.getX(), location.getY(), location.getZ(),
                dx, dy, dz, horizontalDistance,
                clientOnGround, serverOnGround,
                inLiquid, onClimbable, inVehicle,
                System.currentTimeMillis()
        );
        
        movementHistory.addLast(sample);
        
        if (movementHistory.size() > MAX_HISTORY) {
            movementHistory.removeFirst();
        }
    }

    /**
     * Check if player was recently in a vehicle
     */
    private boolean isRecentlyInVehicle() {
        final long VEHICLE_EXIT_GRACE_TIME = 500; // 0.5 seconds
        
        if (movementHistory.size() < 2) {
            return false;
        }
        
        for (MovementSample sample : movementHistory) {
            if (sample.inVehicle && (System.currentTimeMillis() - sample.timestamp) < VEHICLE_EXIT_GRACE_TIME) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Update state for next check
     */
    private void updateState(Location location, boolean clientOnGround, boolean serverOnGround,
                             boolean inLiquid, boolean inVehicle) {
        lastY = location.getY();
        wasOnGround = clientOnGround;
        wasReallyOnGround = serverOnGround;
        wasInLiquid = inLiquid;
        wasInVehicle = inVehicle;
    }

    /**
     * Reset all detection state
     */
    private void resetDetectionState() {
        hoverDetectionComponent.reset();
        glideDetectionComponent.reset();
        groundSpoofingComponent.reset();
        
        boatFlyBuffer = 0;
        boatFlyVL = 0;
        
        airTicks = 0;
        serverVerifiedAirTicks = 0;
        wasOnGround = true;
        wasReallyOnGround = true;
        wasInLiquid = false;
        wasInVehicle = false;
        balanceStartTime = 0;
    }

    public void reset() {
        resetDetectionState();
    }

    /**
     * Class to store movement samples
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