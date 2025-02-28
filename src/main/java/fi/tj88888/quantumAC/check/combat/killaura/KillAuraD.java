package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * KillAuraD
 * Detects abnormal combat sequence timing
 * Analyzes the packet sequence for attacks (arm animation followed by attack)
 */
public class KillAuraD extends Check {

    // Further reduced minimum time to avoid false positives
    // Aimbots can still be caught with extremely low values
    private static final long MIN_TIME_BETWEEN_SWING_AND_ATTACK = 3; // Reduced from 5ms

    // Increased maximum time to accommodate network variations
    private static final long MAX_TIME_BETWEEN_SWING_AND_ATTACK = 200; // Increased from 150ms

    // Adjusted consistency thresholds for extreme cases only
    private static final double CV_THRESHOLD = 0.05; // Stricter (was 0.07)
    private static final double RANGE_THRESHOLD = 2.0; // Stricter (was 3.0)

    // Significantly increased violation requirements
    private static final int MIN_NO_SWING_VIOLATIONS = 5; // Increased from 3
    private static final int MIN_TIMING_VIOLATIONS = 4; // Increased from previous value
    private static final int MIN_CONSISTENCY_VIOLATIONS = 5; // Increased from previous value

    // Violation tracking
    private int noSwingViolations = 0;
    private int timingViolations = 0;
    private int consistencyViolations = 0;
    private int consecutiveNoSwingAttacks = 0;
    private long lastViolationTime = 0;
    private long lastFlagTime = 0;
    private static final long VIOLATION_EXPIRY_TIME = 8000; // 8 seconds

    private final Deque<Long> recentSwings = new ArrayDeque<>();
    private final Deque<SequenceTimings> recentSequences = new ArrayDeque<>();

    private long lastSwingPacket = 0;
    private boolean awaitingAttack = false;

    public KillAuraD(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAura", "D");
    }

    @Override
    public void processPacket(PacketEvent event) {
        // Expire violations after some time
        checkViolationExpiry();

        long now = System.currentTimeMillis();

        // Track arm animation packets (swing)
        if (event.getPacketType() == PacketType.Play.Client.ARM_ANIMATION) {
            lastSwingPacket = now;
            recentSwings.add(now);

            // Reset consecutive no-swing counter
            consecutiveNoSwingAttacks = 0;

            // Keep only the last 20 swings (increased from 10)
            if (recentSwings.size() > 20) {
                recentSwings.pollFirst();
            }

            awaitingAttack = true;
        }

        // Check use entity packets (attack)
        else if (event.getPacketType() == PacketType.Play.Client.USE_ENTITY) {
            // Ensure it's an attack action and has data
            if (event.getPacket().getEnumEntityUseActions().size() > 0 &&
                    event.getPacket().getEnumEntityUseActions().read(0).getAction() == EnumWrappers.EntityUseAction.ATTACK) {

                // If we saw a recent swing packet
                if (awaitingAttack && lastSwingPacket > 0) {
                    long timeBetween = now - lastSwingPacket;

                    // Record this sequence timing
                    SequenceTimings sequence = new SequenceTimings(lastSwingPacket, now, timeBetween);
                    recentSequences.add(sequence);

                    // Keep only the last 20 sequences (increased from 15)
                    if (recentSequences.size() > 20) {
                        recentSequences.pollFirst();
                    }

                    // Only check suspicious timing with a ping-adjusted threshold
                    if (playerData.getAveragePing() < 100) {  // Skip for high ping players
                        checkTimingAnomalies(sequence, now);
                    }

                    // Check for consistently abnormal timings - with more data and only for low ping
                    if (recentSequences.size() >= 15 && playerData.getAveragePing() < 150) { // Increased threshold, only for low ping
                        checkConsistentPatterns(now);
                    }

                    awaitingAttack = false;
                }
                // Attack without a swing packet, but with more safeguards
                else if (!awaitingAttack || lastSwingPacket == 0 || (now - lastSwingPacket) > MAX_TIME_BETWEEN_SWING_AND_ATTACK) {
                    handleNoSwingAttack(now);
                }
            }
        }
    }

    /**
     * Handles attacks without swing packets
     */
    private void handleNoSwingAttack(long now) {
        // Skip for high ping players
        if (playerData.getAveragePing() > 150) {
            return;
        }

        // Increment counter for tracking consecutive issues
        consecutiveNoSwingAttacks++;

        // Only start counting violations after multiple consecutive occurrences
        if (consecutiveNoSwingAttacks >= 3) {
            noSwingViolations++;
            lastViolationTime = now;

            // Only flag after many violations and with a long cooldown
            if (noSwingViolations >= MIN_NO_SWING_VIOLATIONS && now - lastFlagTime > 20000) {
                flag(0.8, "Multiple attacks without swing packets (" + consecutiveNoSwingAttacks + " consecutive)");
                lastFlagTime = now;
                noSwingViolations = 0;
                consecutiveNoSwingAttacks = 0;
            }
        }
    }

    private void checkTimingAnomalies(SequenceTimings sequence, long now) {
        // Only flag for extremely suspicious timing (near-zero time between swing and attack)
        // This is physically impossible without cheating
        if (sequence.timeBetween < MIN_TIME_BETWEEN_SWING_AND_ATTACK) {
            timingViolations++;
            lastViolationTime = now;

            if (timingViolations >= MIN_TIMING_VIOLATIONS && now - lastFlagTime > 15000) {
                flag(0.8, "Multiple attacks with impossible timing (" + sequence.timeBetween + "ms)");
                lastFlagTime = now;
                timingViolations = 0;
            }
        }
    }

    private void checkConsistentPatterns(long now) {
        // Skip if we don't have enough data
        if (recentSequences.size() < 15) {
            return;
        }

        // Calculate statistics about swing-to-attack timing
        double total = 0;
        double min = Double.MAX_VALUE;
        double max = 0;

        for (SequenceTimings sequence : recentSequences) {
            total += sequence.timeBetween;
            min = Math.min(min, sequence.timeBetween);
            max = Math.max(max, sequence.timeBetween);
        }

        double avg = total / recentSequences.size();
        double range = max - min;

        // Calculate standard deviation
        double variance = 0;
        for (SequenceTimings sequence : recentSequences) {
            variance += Math.pow(sequence.timeBetween - avg, 2);
        }
        double stdDev = Math.sqrt(variance / recentSequences.size());

        // Calculate coefficient of variation (lower = more consistent)
        double cv = (avg > 0) ? stdDev / avg : 0;

        // Check for extremely machine-like consistency (near-zero variation)
        // Natural human input has variable timing; perfect consistency is a strong signal
        if (cv < CV_THRESHOLD && range < RANGE_THRESHOLD && recentSequences.size() >= 15) {
            consistencyViolations++;
            lastViolationTime = now;

            if (consistencyViolations >= MIN_CONSISTENCY_VIOLATIONS && now - lastFlagTime > 20000) {
                flag(0.9, "Extremely consistent attack timing - machine-like precision (CV: " +
                        String.format("%.4f", cv) + ", Range: " + String.format("%.1f", range) + "ms)");
                lastFlagTime = now;
                consistencyViolations = 0;
            }
        }
    }

    /**
     * Expires violation counters after some time without violations
     */
    private void checkViolationExpiry() {
        long now = System.currentTimeMillis();

        // If it's been too long since last violation, reduce counters
        if (now - lastViolationTime > VIOLATION_EXPIRY_TIME) {
            // Gradual decay instead of reset
            noSwingViolations = Math.max(0, noSwingViolations - 1);
            timingViolations = Math.max(0, timingViolations - 1);
            consistencyViolations = Math.max(0, consistencyViolations - 1);

            // Reset consecutive counter more aggressively
            if (consecutiveNoSwingAttacks > 0 && now - lastViolationTime > VIOLATION_EXPIRY_TIME / 2) {
                consecutiveNoSwingAttacks = 0;
            }
        }
    }

    private static class SequenceTimings {
        final long swingTime;
        final long attackTime;
        final long timeBetween;

        SequenceTimings(long swingTime, long attackTime, long timeBetween) {
            this.swingTime = swingTime;
            this.attackTime = attackTime;
            this.timeBetween = timeBetween;
        }
    }
}