package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ConcurrentHashMap;

/**
 * KillAuraB
 * Detects suspicious rotation behavior during combat
 * Checks for abnormal head movements when attacking
 */
public class KillAuraB extends Check {

    // Increased thresholds to account for legitimate flick shots
    private static final double MAX_PRE_ATTACK_YAW_RATE = 52.0;  // Increased from 40.0
    private static final double MAX_PRE_ATTACK_PITCH_RATE = 38.0; // Increased from 30.0

    // Decreased minimum time to avoid false positives from legitimate play
    private static final double MIN_POST_ROTATION_TIME = 50.0; // Further reduced from 80.0

    // More lenient perfect aim thresholds for less false positives
    private static final double PERFECT_AIM_YAW_THRESHOLD = 2.0; // Changed from 0.5
    private static final double PERFECT_AIM_PITCH_THRESHOLD = 2.0; // Changed from 0.5

    // Added minimum required violation count before flagging
    private static final int MIN_VIOLATIONS_BEFORE_FLAG = 2;
    private static final long VIOLATION_EXPIRY_TIME = 5000; // 5 seconds

    // Thread-safe storage of data for async processing
    private final ConcurrentHashMap<Integer, EntityPositionData> recentEntityPositions = new ConcurrentHashMap<>();

    private long lastRotationTime = 0;
    private float lastYaw = 0;
    private float lastPitch = 0;

    // Violation tracking
    private int fastRotationViolations = 0;
    private int perfectAimViolations = 0;
    private int attackOutsideViewViolations = 0;
    private int postRotationViolations = 0;
    private long lastViolationTime = 0;
    private long lastFlagTime = 0;

    public KillAuraB(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAura", "B");

        // Schedule a repeating task to cache entity positions on the main thread
        Bukkit.getScheduler().runTaskTimer(plugin, this::cacheEntityPositions, 1L, 5L); // Run every 5 ticks
    }

    /**
     * Caches entity positions from the main thread for later async use
     * This runs on the main server thread, avoiding the AsyncCatcher error
     */
    private void cacheEntityPositions() {
        Player player = playerData.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Clear old data periodically
        if (recentEntityPositions.size() > 50) {
            recentEntityPositions.clear();
        }

        // Only get entities within a reasonable range (16 blocks)
        for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
            Location entityLoc = entity.getLocation();
            recentEntityPositions.put(entity.getEntityId(), new EntityPositionData(
                    entity.getType(),
                    entityLoc.getX(),
                    entityLoc.getY(),
                    entityLoc.getZ(),
                    entity.getHeight(),
                    System.currentTimeMillis()
            ));
        }
    }

    /**
     * Process rotation data from packets
     */
    private void processRotation(PacketEvent event, float yaw, float pitch) {
        // Calculate rotation change
        float yawDelta = Math.abs(yaw - lastYaw);
        float pitchDelta = Math.abs(pitch - lastPitch);

        // Adjust for angle wrapping
        if (yawDelta > 180) {
            yawDelta = 360 - yawDelta;
        }

        // Update rotation data in MovementData
        playerData.getMovementData().updateRotation(yaw, pitch);

        // Only count MAJOR rotations for post-rotation attack checks
        // Significantly increased threshold to only count large flicks, not small adjustments
        if (yawDelta > 20.0 || pitchDelta > 15.0) {  // Much higher threshold
            lastRotationTime = System.currentTimeMillis();
        }

        lastYaw = yaw;
        lastPitch = pitch;
    }

    @Override
    public void processPacket(PacketEvent event) {
        // Expire violations after some time
        checkViolationExpiry();

        // Track rotation packets
        if (event.getPacketType() == PacketType.Play.Client.LOOK ||
                event.getPacketType() == PacketType.Play.Client.POSITION_LOOK) {

            float yaw = event.getPacket().getFloat().read(0);
            float pitch = event.getPacket().getFloat().read(1);
            processRotation(event, yaw, pitch);
        }

        // Detect attacks
        else if (event.getPacketType() == PacketType.Play.Client.USE_ENTITY) {
            // Ensure it's an attack and packet has data
            if (event.getPacket().getEnumEntityUseActions().size() > 0 &&
                    event.getPacket().getEnumEntityUseActions().read(0).getAction() == EnumWrappers.EntityUseAction.ATTACK) {

                long now = System.currentTimeMillis();

                // Get entity being attacked
                if (event.getPacket().getIntegers().size() > 0) {
                    int entityId = event.getPacket().getIntegers().read(0);

                    // Check if the entity data is in our cache
                    EntityPositionData targetData = recentEntityPositions.get(entityId);
                    if (targetData != null && (now - targetData.timestamp) < 500) { // Only use fresh data
                        // Analyze the attack rotation using cached entity data
                        analyzeAttackRotation(targetData, now);
                    }
                }

                // Modified post-rotation attack check
                checkPostRotationAttack(now);
            }
        }
    }

    /**
     * Improved check for attacks happening too soon after large rotations
     * This separates the logic and adds more context awareness
     */
    private void checkPostRotationAttack(long now) {
        // Skip this check for high-ping players (adds leniency for lag)
        if (playerData.getAveragePing() > 150) {
            return;
        }

        // Only run check if we have a valid major rotation time
        if (lastRotationTime <= 0) {
            return;
        }

        long timeSinceRotation = now - lastRotationTime;

        // Reduced time threshold to only catch extremely fast aimbot/aim assist
        // 50ms is much lower than previous 80ms
        if (timeSinceRotation < MIN_POST_ROTATION_TIME) {
            // Get actual rotation values for context
            double yawRate = playerData.getMovementData().getDeltaYaw();
            double pitchRate = playerData.getMovementData().getDeltaPitch();

            // Require both a recent major rotation AND fast rotation rate
            // This combination is very unlikely in legitimate play
            if (yawRate > 30.0 || pitchRate > 20.0) {
                // Increment violation counter
                postRotationViolations++;
                lastViolationTime = now;

                // Higher threshold than before (3 instead of 2)
                if (postRotationViolations >= 3 && now - lastFlagTime > 15000) {
                    flag(0.8, "Suspicious attack pattern: " + timeSinceRotation +
                            "ms after major rotation (rate: Y=" + String.format("%.1f", yawRate) +
                            ", P=" + String.format("%.1f", pitchRate) + ")");
                    lastFlagTime = now;
                    postRotationViolations = 0; // Reset after flagging
                }
            }
        }
    }

    private void analyzeAttackRotation(EntityPositionData targetData, long now) {
        Player player = playerData.getPlayer();
        if (player == null) return;

        // This method now uses cached entity position data instead of direct entity access

        // Get player's current look data
        Location playerLocation = player.getLocation();
        Vector playerPos = playerLocation.toVector();

        // Create target position vector from cached data
        Vector targetPos = new Vector(
                targetData.x,
                targetData.y + (targetData.height / 2), // Aim at center of target
                targetData.z
        );

        // Calculate ideal look direction to target
        Vector toTarget = targetPos.subtract(playerPos);

        // Calculate ideal yaw and pitch to hit the target
        double distanceXZ = Math.sqrt(toTarget.getX() * toTarget.getX() + toTarget.getZ() * toTarget.getZ());
        double idealYaw = Math.toDegrees(Math.atan2(-toTarget.getX(), toTarget.getZ()));
        double idealPitch = Math.toDegrees(-Math.atan2(toTarget.getY(), distanceXZ));

        // Fix yaw to Minecraft's coordinate system
        if (idealYaw < 0) {
            idealYaw += 360.0;
        }

        // Calculate the difference between actual and ideal angles
        double yawDiff = Math.abs(playerLocation.getYaw() - idealYaw);
        double pitchDiff = Math.abs(playerLocation.getPitch() - idealPitch);

        // Normalize yaw difference
        if (yawDiff > 180) {
            yawDiff = 360 - yawDiff;
        }

        // Analyze pre-attack rotation rate
        double yawRate = playerData.getMovementData().getDeltaYaw();
        double pitchRate = playerData.getMovementData().getDeltaPitch();

        // Check for abnormally fast rotations right before an attack
        if (yawRate > MAX_PRE_ATTACK_YAW_RATE || pitchRate > MAX_PRE_ATTACK_PITCH_RATE) {
            // Increment violation counter instead of flagging immediately
            fastRotationViolations++;
            lastViolationTime = now;

            // Only flag if we've seen multiple violations and not flagged recently
            if (fastRotationViolations >= MIN_VIOLATIONS_BEFORE_FLAG && now - lastFlagTime > 10000) {
                flag(0.8, "Multiple abnormal rotations before attack - Yaw: " + String.format("%.1f", yawRate) +
                        ", Pitch: " + String.format("%.1f", pitchRate));
                lastFlagTime = now;
                fastRotationViolations = 0; // Reset after flagging
            }
        }

        // Flag suspiciously perfect aim with more lenient thresholds
        if (yawDiff < PERFECT_AIM_YAW_THRESHOLD && pitchDiff < PERFECT_AIM_PITCH_THRESHOLD) {
            perfectAimViolations++;
            lastViolationTime = now;

            // Only flag after multiple violations of extremely precise aim
            if (perfectAimViolations >= MIN_VIOLATIONS_BEFORE_FLAG + 1 && now - lastFlagTime > 10000) { // Higher threshold for this
                flag(0.7, "Multiple instances of abnormally accurate aim - Latest Yaw diff: " +
                        String.format("%.2f", yawDiff) + ", Pitch diff: " + String.format("%.2f", pitchDiff));
                lastFlagTime = now;
                perfectAimViolations = 0; // Reset after flagging
            }
        }

        // Check for attacking behind without looking - this is extremely suspicious
        if (yawDiff > 100) { // Increased slightly from 90 degrees
            attackOutsideViewViolations++;
            lastViolationTime = now;

            // Still flag this more readily since it's very suspicious
            if (attackOutsideViewViolations >= MIN_VIOLATIONS_BEFORE_FLAG && now - lastFlagTime > 10000) {
                flag(0.9, "Multiple attacks outside view - Latest Yaw diff: " + String.format("%.1f", yawDiff));
                lastFlagTime = now;
                attackOutsideViewViolations = 0; // Reset after flagging
            }
        }
    }

    /**
     * More aggressive violation expiry to prevent false flags during combat
     */
    private void checkViolationExpiry() {
        long now = System.currentTimeMillis();

        // If it's been too long since last violation, reset counters
        if (now - lastViolationTime > VIOLATION_EXPIRY_TIME) {
            fastRotationViolations = 0;
            perfectAimViolations = 0;
            attackOutsideViewViolations = 0;
            postRotationViolations = Math.max(0, postRotationViolations - 1); // Decay instead of reset
        }

        // Additional decay for postRotationViolations - these should decay faster
        // during combat to prevent false positives
        if (now - lastViolationTime > 2000) { // 2 second decay
            postRotationViolations = Math.max(0, postRotationViolations - 1);
        }
    }

    /**
     * Simple class to store cached entity position data
     */
    private static class EntityPositionData {
        final org.bukkit.entity.EntityType type;
        final double x;
        final double y;
        final double z;
        final double height;
        final long timestamp;

        EntityPositionData(org.bukkit.entity.EntityType type, double x, double y, double z, double height, long timestamp) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.height = height;
            this.timestamp = timestamp;
        }
    }
}