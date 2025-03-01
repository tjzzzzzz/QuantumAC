package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.util.MovementData;
import org.bukkit.entity.Player;

/**
 * KillAuraD
 * Detects players who maintain sprint speed when attacking
 * Vanilla clients should slow down when hitting entities
 */
public class KillAuraD extends Check {

    // Detection thresholds
    private double threshold = 0.0;
    private int hits = 0;
    private double lastDeltaXZ = 0.0;
    private double deltaXZ = 0.0;
    private boolean isSprinting = false;
    // Removed the flag cooldown variables since we're not using them anymore

    // Movement tracking
    private double[] recentSpeeds = new double[5]; // Track recent speeds
    private int speedIndex = 0;
    private boolean speedArrayFilled = false;

    // Attack timing
    private long lastAttackTime = 0;

    public KillAuraD(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAura", "D");
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (event == null || event.getPacketType() == null) return;

        long now = System.currentTimeMillis();
        PacketType packetType = event.getPacketType();

        try {
            // Handle movement packets
            if (isMovementPacket(packetType)) {
                // Skip check for new players
                long timeSinceJoin = now - playerData.getJoinTime();
                if (timeSinceJoin < 3000) { // 3000ms = ~60 ticks
                    threshold = 0;
                    return;
                }

                // Get player and movement data
                Player player = playerData.getPlayer();
                if (player == null) return;

                // Update movement values
                MovementData movementData = playerData.getMovementData();
                MovementData previousMovementData = playerData.getPreviousMovementData();

                // Calculate movement delta (speed)
                lastDeltaXZ = deltaXZ;

                // Calculate current XZ movement (horizontal speed)
                double dx = movementData.getX() - previousMovementData.getX();
                double dz = movementData.getZ() - previousMovementData.getZ();
                deltaXZ = Math.sqrt(dx * dx + dz * dz);

                // Update speed history
                recentSpeeds[speedIndex] = deltaXZ;
                speedIndex = (speedIndex + 1) % recentSpeeds.length;
                if (speedIndex == 0) {
                    speedArrayFilled = true;
                }

                // Check if player is sprinting (approximation based on speed)
                double baseSpeed = getBaseSpeed(player);
                isSprinting = deltaXZ > baseSpeed * 0.8;

                long timeSinceLastAttack = now - playerData.getLastAttack();

                // Only check if we've actually attacked recently (within 500ms)
                if (hits > 0 && hits <= 3 && timeSinceLastAttack < 500) {
                    if (isSprinting && movementData.isOnGround()) {  // Only check when on ground
                        // Calculate expected slowdown based on speed
                        double expectedSlowdown = baseSpeed * 0.6;
                        double actualSlowdown = getSpeedDifference();

                        // Check for very low slowdowns
                        if (actualSlowdown < expectedSlowdown * 0.2 && isConsistentMovement() &&
                                deltaXZ > baseSpeed * 0.8) {
                            // Build threshold for low slowdown detection
                            threshold += 0.75;

                            // Only flag on high threshold - removed cooldown check
                            if (threshold > 15) {
                                // Calculate VL based on severity
                                double vlIncrement = 1.0 + (threshold / 20.0);

                                // Using the deprecated method that works correctly for VL tracking
                                flag(vlIncrement, "very low slowdown=" + String.format("%.5f", actualSlowdown) +
                                        ", expected=" + String.format("%.3f", expectedSlowdown) +
                                        ", vl=" + String.format("%.1f", vlIncrement));

                                // Reduce threshold a bit after flagging
                                threshold = Math.max(0, threshold - 2);
                            }
                        }
                        else {
                            // Normal slowdown, decrease threshold
                            threshold = Math.max(0, threshold - 2.0);
                        }
                    } else {
                        // Not sprinting or not on ground, decrease threshold
                        threshold = Math.max(0, threshold - 1.5);
                    }
                } else if (timeSinceLastAttack >= 500) {
                    // Reset hits counter if it's been too long since the last attack
                    hits = 0;

                    // Rapidly decrease threshold between combat encounters
                    if (timeSinceLastAttack > 2000) { // 2 seconds without combat
                        threshold = Math.max(0, threshold - 3.0);
                    } else {
                        threshold = Math.max(0, threshold - 1.0);
                    }
                }

                // Reset hit counter after several movement packets
                if (hits > 8) {
                    hits = 0;
                    threshold = Math.max(0, threshold - 2.0);
                }

                // Always gradually decrease threshold over time
                if (threshold > 0) {
                    threshold = Math.max(0, threshold - 0.1);
                }

                // Save last attack time for timing calculations
                if (playerData.getLastAttack() > lastAttackTime) {
                    lastAttackTime = playerData.getLastAttack();
                }
            }

            // Handle attack packets
            else if (packetType == PacketType.Play.Client.USE_ENTITY &&
                    event.getPacket().getEnumEntityUseActions().size() > 0) {

                EnumWrappers.EntityUseAction action =
                        event.getPacket().getEnumEntityUseActions().read(0).getAction();

                // Reset hit counter on new attack
                if (action == EnumWrappers.EntityUseAction.ATTACK) {
                    hits = 0;

                    // Get the entity ID and start monitoring
                    if (event.getPacket().getIntegers().size() > 0) {
                        int entityId = event.getPacket().getIntegers().read(0);
                        playerData.setLastAttackedEntity(entityId);

                        // Start monitoring for all attacks
                        hits = 1;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error in KillAuraD check: " + e.getMessage());
        }
    }

    /**
     * Helper method to determine if a packet is a movement packet
     */
    private boolean isMovementPacket(PacketType packetType) {
        return packetType == PacketType.Play.Client.POSITION ||
                packetType == PacketType.Play.Client.LOOK ||
                packetType == PacketType.Play.Client.POSITION_LOOK ||
                packetType == PacketType.Play.Client.FLYING;
    }

    /**
     * Calculate base walking speed for a player
     */
    private double getBaseSpeed(Player player) {
        double baseSpeed = 0.13; // Default base speed

        try {
            // Get speed from player attribute
            baseSpeed = player.getWalkSpeed() * 0.3; // Convert to blocks per tick
        } catch (Exception ignored) {}

        return baseSpeed;
    }

    /**
     * Checks if player movement has been consistent enough to analyze
     */
    private boolean isConsistentMovement() {
        if (!speedArrayFilled) return false;

        // Need at least some movement in the history
        int movingPackets = 0;
        for (double speed : recentSpeeds) {
            if (speed > 0.05) movingPackets++;
        }

        return movingPackets >= 3;
    }

    /**
     * Calculate the speed difference after attack
     */
    private double getSpeedDifference() {
        if (!speedArrayFilled) return 0;

        // Find max speed before attack
        double maxSpeed = 0;
        for (double speed : recentSpeeds) {
            if (speed > maxSpeed) maxSpeed = speed;
        }

        // Calculate difference between max speed and current
        return maxSpeed - deltaXZ;
    }
}