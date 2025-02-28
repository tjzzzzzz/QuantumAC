package fi.tj88888.quantumAC.check.movement.rotation;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.util.ChatUtil;
import fi.tj88888.quantumAC.util.MovementData;
import org.bukkit.entity.Player;

/**
 * RotationA check - Zero-Drop Keep Sprint Detection
 * Focuses exclusively on detecting zero speed drops during sharp turns
 */
public class RotationA extends Check {

    // Only check significant rotations
    private static final float SHARP_TURN_THRESHOLD = 30.0F;
    private static final float VERY_SHARP_TURN_THRESHOLD = 45.0F;

    // Speed thresholds
    private static final double SPRINT_MIN_SPEED = 0.15;

    // Core detection: near-zero speed drops
    private static final double ZERO_DROP_THRESHOLD = 0.005;  // 0.5% or less = suspicious
    private static final double NEGATIVE_DROP_THRESHOLD = -0.001; // Actually gaining speed = very suspicious

    // Violation system - faster detection
    private int violations = 0;
    private static final int FLAG_THRESHOLD = 8; // Reduced from 10 for faster flagging
    private long lastViolationTime = 0;
    private static final long VIOLATION_DECAY_TIME = 40000; // 40 seconds

    // Consecutive detection - faster detection
    private int consecutiveSuspicious = 0;
    private static final int CONSECUTIVE_THRESHOLD = 2; // Reduced from 3 for faster detection
    private long lastSuspiciousTime = 0;
    private static final long CONSECUTIVE_RESET_TIME = 2000; // Reset after 2 seconds

    // Debug mode
    private boolean debugMode = false;

    public RotationA(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "RotationA", "Rotation");
    }

    public boolean toggleDebug() {
        this.debugMode = !this.debugMode;
        return this.debugMode;
    }

    @Override
    public void processPacket(PacketEvent event) {
        // Check packet type
        PacketType packetType = event.getPacketType();

        boolean isRotationPacket = PacketType.Play.Client.LOOK == packetType ||
                PacketType.Play.Client.POSITION_LOOK == packetType;

        if (!isRotationPacket) {
            return;
        }

        Player player = event.getPlayer();

        // Skip exempted players
        if (player.isInsideVehicle() || player.isFlying() || playerData.isExempt()) {
            return;
        }

        // Decay violations over time (very gradual)
        long now = System.currentTimeMillis();
        if (now - lastViolationTime > VIOLATION_DECAY_TIME && violations > 0) {
            violations--;
            lastViolationTime = now;
        }

        // Reset consecutive counter after timeout
        if (now - lastSuspiciousTime > CONSECUTIVE_RESET_TIME) {
            consecutiveSuspicious = 0;
        }

        // Get movement data
        MovementData data = playerData.getMovementData();
        if (data == null) {
            return;
        }

        // Skip initial or slow movements
        float deltaYaw = data.getDeltaYaw();
        double currentSpeed = data.getDeltaXZ();
        double previousSpeed = data.getLastDeltaXZ();

        if (data.getLastYaw() == 0 || currentSpeed < SPRINT_MIN_SPEED) {
            return;
        }

        // Skip conditions that affect movement
        if (data.isOnIce() || data.isOnSlime() || data.isInLiquid() ||
                data.isOnStairs() || data.isOnSlab() || data.isJumping()) {
            return;
        }

        // Calculate speed change (negative = speedup, positive = slowdown)
        double speedDiff = previousSpeed - currentSpeed;
        double speedDropRatio = speedDiff / previousSpeed;

        // Debug info - only shown in debug mode
        if (debugMode) {
            ChatUtil.sendDebug(player, "Keep Sprint Zero-Drop Check:");
            ChatUtil.sendDebug(player, String.format("  Yaw Delta: %.2f", deltaYaw));
            ChatUtil.sendDebug(player, String.format("  Speed: %.3f → %.3f (diff: %.4f, drop: %.2f%%)",
                    previousSpeed, currentSpeed, speedDiff, speedDropRatio * 100));
        }

        // Detect zero/negative drops only during significant turns
        boolean isSuspicious = false;
        int suspiciousLevel = 0;

        // Near-zero drop during sharp turn
        if (deltaYaw > SHARP_TURN_THRESHOLD && Math.abs(speedDropRatio) < ZERO_DROP_THRESHOLD) {
            isSuspicious = true;
            suspiciousLevel = 2; // Increased from 1 for faster detection

            // Extra suspicious if actually accelerating during a turn
            if (speedDropRatio < NEGATIVE_DROP_THRESHOLD) {
                suspiciousLevel = 3; // Increased from 2 for faster detection
            }

            // Very sharp turns should definitely cause speed loss
            if (deltaYaw > VERY_SHARP_TURN_THRESHOLD) {
                suspiciousLevel++; // Up to 4 now for very suspicious movements
            }

            // Debug info only shown in debug mode
            if (debugMode) {
                ChatUtil.sendDebug(player, String.format("  SUSPICIOUS: Zero drop (%.2f%%) during %.1f° turn",
                        speedDropRatio * 100, deltaYaw));
            }
        }

        // Only take action if suspicious
        if (isSuspicious) {
            consecutiveSuspicious++;
            lastSuspiciousTime = now;

            // Only add violations after consecutive detections to avoid false positives
            if (consecutiveSuspicious >= CONSECUTIVE_THRESHOLD) {
                violations += suspiciousLevel;

                // Debug violation messages only shown in debug mode
                if (debugMode) {
                    String vioMessage = String.format(
                            "Keep sprint detected: deltaYaw=%.2f, drop=%.2f%%, consecutive=%d, +%d vl",
                            deltaYaw, speedDropRatio * 100, consecutiveSuspicious, suspiciousLevel);
                    ChatUtil.sendWarning(player, vioMessage);
                }
            }
        }

        // Flag if threshold reached
        if (violations >= FLAG_THRESHOLD) {
            String details = String.format("deltaYaw=%.2f, speed=%.3f→%.3f, drop=%.2f%%",
                    deltaYaw, previousSpeed, currentSpeed, speedDropRatio * 100);
            flag(player, "Keep sprint - zero speed drop", details);
        }
    }
}