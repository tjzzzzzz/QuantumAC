package fi.tj88888.quantumAC.check.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

public class TimerA extends Check {

    // Constants for packet frequency analysis
    private static final double MAX_PACKETS_PER_SECOND = 22.0; // Vanilla client sends ~20 packets/sec
    private static final double MIN_PACKETS_PER_SECOND = 15.0; // Allow for some packet loss
    private static final long SAMPLE_SIZE_MS = 3000;          // 3 second window for analysis
    private static final long MIN_SAMPLES = 40;               // Minimum packet samples for analysis
    private static final long MAX_TIME_DIFF = 100;            // Maximum time between packets (ms)

    // Buffer settings
    private static final int BUFFER_THRESHOLD = 8;            // Violations needed before flagging
    private static final int BUFFER_DECREMENT = 1;            // How much buffer decreases per legit packet
    private static final int RATIO_VIOLATION_BUFFER = 4;      // Buffer for ratio-based violations

    // Balance periods (help prevent false positives)
    private static final long JOIN_EXEMPT_DURATION = 5000;    // Exempt period after join (ms)
    private static final long TELEPORT_EXEMPT_DURATION = 3000; // Exempt period after teleport (ms)
    private static final long WORLD_CHANGE_EXEMPT_DURATION = 5000; // Exempt after world change (ms)

    // Packet timing tracking
    private final Deque<Long> packetTimestamps = new ArrayDeque<>();
    private long lastPacketTime = 0;
    private long joinTime = 0;
    private long lastTeleportTime = 0;
    private long lastWorldChangeTime = 0;
    private String lastWorld = "";

    // Violation tracking
    private int buffer = 0;
    private int fastPacketStreak = 0;
    private int slowPacketStreak = 0;
    private int ratioBuffer = 0;

    // Stats
    private double currentTps = 20.0;
    private double lowestTps = 20.0;
    private double highestTps = 20.0;
    private double avgTimeDiff = 50.0;

    public TimerA(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "TimerA", "Packet");
        this.joinTime = System.currentTimeMillis();
    }

    @Override
    public void processPacket(PacketEvent event) {
        // Only analyze flying packets (movement-related)
        if (!isMovementPacket(event.getPacketType())) {
            return;
        }

        Player player = event.getPlayer();

        // Skip if player is exempt
        if (isExempt(player)) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // First packet handling
        if (lastPacketTime == 0) {
            lastPacketTime = currentTime;
            return;
        }

        // Calculate time between packets
        long timeDiff = currentTime - lastPacketTime;

        // Skip extremely delayed packets (likely server lag)
        if (timeDiff > 1000) {
            lastPacketTime = currentTime;
            return;
        }

        // Record packet timing
        packetTimestamps.add(currentTime);

        // Keep only recent packets for analysis
        while (!packetTimestamps.isEmpty() &&
                currentTime - packetTimestamps.peek() > SAMPLE_SIZE_MS) {
            packetTimestamps.poll();
        }

        // Only analyze when we have enough data
        if (packetTimestamps.size() >= MIN_SAMPLES) {
            analyzePacketTiming(player, timeDiff);
        }

        // Update for next packet
        lastPacketTime = currentTime;
    }

    /**
     * Analyzes packet timing to detect timer hacks
     */
    private void analyzePacketTiming(Player player, long currentTimeDiff) {
        // Calculate current packets per second
        double packetsPerSecond = calculatePacketsPerSecond();

        // Calculate average time between packets
        double averageTimeDiff = calculateAverageTimeDiff();
        avgTimeDiff = averageTimeDiff;

        // Calculate TPS (client-side)
        double calculatedTps = 1000.0 / averageTimeDiff;
        currentTps = calculatedTps;

        // Update statistics
        if (calculatedTps > highestTps) highestTps = calculatedTps;
        if (calculatedTps < lowestTps) lowestTps = calculatedTps;

        // Violation checks - Multiple detection methods

        // 1. Check for abnormally high packet frequency
        if (packetsPerSecond > MAX_PACKETS_PER_SECOND) {
            fastPacketStreak++;
            slowPacketStreak = 0;

            // Increasing buffer based on severity
            double overAmount = packetsPerSecond - MAX_PACKETS_PER_SECOND;
            buffer += Math.max(1, (int)(overAmount / 2.0));

            // Flag more severe violations directly
            if (fastPacketStreak >= 3 && packetsPerSecond > MAX_PACKETS_PER_SECOND + 5) {
                String details = formatFastDetails(packetsPerSecond, calculatedTps, averageTimeDiff);
                flag(Math.min(3.0, (packetsPerSecond - MAX_PACKETS_PER_SECOND) / 3.0), details);

                // Reset after flagging
                buffer = Math.max(0, buffer - 3);
                fastPacketStreak = 0;
            }
        }
        // 2. Check for abnormally low packet frequency (timer slowdown)
        else if (packetsPerSecond < MIN_PACKETS_PER_SECOND && !isServerLagging()) {
            slowPacketStreak++;
            fastPacketStreak = 0;

            // Only flag for consistent slow patterns to avoid false positives
            if (slowPacketStreak >= 5) {
                String details = formatSlowDetails(packetsPerSecond, calculatedTps, averageTimeDiff);
                flag(1.0, details);
                slowPacketStreak = 0;
            }
        }
        // Normal packet rate
        else {
            fastPacketStreak = Math.max(0, fastPacketStreak - 1);
            slowPacketStreak = Math.max(0, slowPacketStreak - 1);
            buffer = Math.max(0, buffer - BUFFER_DECREMENT);
        }

        // 3. Check for suspiciously consistent packet timing (machine-like precision)
        analyzePacketConsistency();

        // Flag based on buffer threshold
        if (buffer >= BUFFER_THRESHOLD) {
            String details = formatDetails(packetsPerSecond, calculatedTps, averageTimeDiff);
            flag(1.0, details);
            buffer = Math.max(0, buffer - 3);
        }
    }

    /**
     * Analyzes consistency between packets (machine-like timing is suspicious)
     */
    private void analyzePacketConsistency() {
        // We need enough samples for pattern analysis
        if (packetTimestamps.size() < 30) {
            return;
        }

        // Convert queue to array for easier analysis
        Long[] packetTimes = packetTimestamps.toArray(new Long[0]);
        int diffCount = 0;
        double totalVariance = 0;

        // Calculate variance in packet timing
        for (int i = 1; i < packetTimes.length; i++) {
            long diff = packetTimes[i] - packetTimes[i-1];
            if (diff > 0 && diff < MAX_TIME_DIFF) {
                totalVariance += Math.abs(diff - avgTimeDiff);
                diffCount++;
            }
        }

        // Average deviation from the mean
        double averageVariance = diffCount > 0 ? totalVariance / diffCount : 0;

        // Suspiciously consistent timing is a strong indicator of a timer hack
        // Human players will have natural variance, hacks often have machine-like precision
        if (averageVariance < 1.0 && diffCount >= 20) {
            ratioBuffer += 2;

            if (ratioBuffer >= RATIO_VIOLATION_BUFFER) {
                String details = String.format(
                        "timer-consistency: variance=%.3f, tps=%.2f, diffs=%d",
                        averageVariance, currentTps, diffCount
                );
                flag(2.0, details);
                ratioBuffer = 0;
            }
        } else {
            ratioBuffer = Math.max(0, ratioBuffer - 1);
        }
    }

    /**
     * Calculate packets per second based on timestamps
     */
    private double calculatePacketsPerSecond() {
        long currentTime = System.currentTimeMillis();
        long oldestPacketTime = packetTimestamps.peek();
        double timeRange = (currentTime - oldestPacketTime) / 1000.0;

        // Avoid division by zero
        if (timeRange <= 0) return 20.0;

        return packetTimestamps.size() / timeRange;
    }

    /**
     * Calculate average time between packets
     */
    private double calculateAverageTimeDiff() {
        Long[] times = packetTimestamps.toArray(new Long[0]);
        if (times.length < 2) return 50.0;

        double totalDiff = 0;
        int count = 0;

        for (int i = 1; i < times.length; i++) {
            long diff = times[i] - times[i-1];

            // Filter out unusually large values (likely server hiccups)
            if (diff > 0 && diff < MAX_TIME_DIFF) {
                totalDiff += diff;
                count++;
            }
        }

        return count > 0 ? totalDiff / count : 50.0;
    }

    /**
     * Format violation details
     */
    private String formatDetails(double packetsPerSecond, double calculatedTps, double averageTimeDiff) {
        return String.format(
                "packets/s=%.2f, tps=%.2f, avg-diff=%.2fms, " +
                        "lowest-tps=%.2f, highest-tps=%.2f, buffer=%d",
                packetsPerSecond, calculatedTps, averageTimeDiff,
                lowestTps, highestTps, buffer
        );
    }

    /**
     * Format fast timer violation details
     */
    private String formatFastDetails(double packetsPerSecond, double calculatedTps, double averageTimeDiff) {
        return String.format(
                "fast-timer: packets/s=%.2f, tps=%.2f, avg-diff=%.2fms, streak=%d",
                packetsPerSecond, calculatedTps, averageTimeDiff, fastPacketStreak
        );
    }

    /**
     * Format slow timer violation details
     */
    private String formatSlowDetails(double packetsPerSecond, double calculatedTps, double averageTimeDiff) {
        return String.format(
                "slow-timer: packets/s=%.2f, tps=%.2f, avg-diff=%.2fms, streak=%d",
                packetsPerSecond, calculatedTps, averageTimeDiff, slowPacketStreak
        );
    }

    /**
     * Check if player is exempt from checks
     */
    private boolean isExempt(Player player) {
        // Basic exemptions
        if (player.isFlying() ||
                player.getAllowFlight() ||
                playerData.isExempt()) {
            return true;
        }

        // Time-based exemptions
        long currentTime = System.currentTimeMillis();

        // Exempt after join
        if (currentTime - joinTime < JOIN_EXEMPT_DURATION) {
            return true;
        }

        // Exempt after teleport
        if (currentTime - lastTeleportTime < TELEPORT_EXEMPT_DURATION) {
            return true;
        }

        // Exempt after world change
        if (currentTime - lastWorldChangeTime < WORLD_CHANGE_EXEMPT_DURATION) {
            return true;
        }

        // Check world change
        String currentWorld = player.getWorld().getName();
        if (!currentWorld.equals(lastWorld)) {
            lastWorld = currentWorld;
            lastWorldChangeTime = currentTime;
            return true;
        }

        return false;
    }

    /**
     * Check for server lag (to reduce false positives)
     */
    private boolean isServerLagging() {
        // Get server TPS from config manager (should be implemented elsewhere)
        double serverTps = plugin.getConfigManager().getCurrentTPS();
        return serverTps < 19.0; // If server TPS is below 19, consider it lagging
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
     * Called when player teleports (to prevent false positives)
     */
    public void onPlayerTeleport() {
        lastTeleportTime = System.currentTimeMillis();
        // Clear packet history after teleport
        packetTimestamps.clear();
        lastPacketTime = 0;
    }

    /**
     * Called when player changes worlds
     */
    public void onWorldChange() {
        lastWorldChangeTime = System.currentTimeMillis();
        // Clear packet history after world change
        packetTimestamps.clear();
        lastPacketTime = 0;
    }

    /**
     * Reset join time (when player rejoins)
     */
    public void onPlayerJoin() {
        joinTime = System.currentTimeMillis();
        // Clear packet history on join
        packetTimestamps.clear();
        lastPacketTime = 0;
        buffer = 0;
        fastPacketStreak = 0;
        slowPacketStreak = 0;
        ratioBuffer = 0;
    }
}