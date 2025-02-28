package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * KillAuraA
 * Detects machine-like consistency in attack patterns
 * Flags suspiciously consistent clicking or unnaturally high CPS (clicks per second)
 */
public class KillAuraA extends Check {

    // Higher threshold to avoid flagging legitimate players who can click fast
    private static final int MAX_CPS_THRESHOLD = 32; // Further increased from 30

    // Made more lenient to require extreme consistency patterns that are impossible for humans
    private static final double CONSISTENCY_THRESHOLD = 0.99; // Increased from 0.985

    // Increased minimum click requirements for more accurate analysis
    private static final int MIN_CLICKS_FOR_ANALYSIS = 12; // Increased from 8

    // Extreme consistency threshold (physically impossible for humans)
    private static final double EXTREME_CONSISTENCY_CV = 0.03; // Added explicit threshold for CV

    // Violation tracking
    private double lastDeviation = 0.0;
    private int similarPatternCount = 0;
    private int consistencyViolations = 0;
    private int highCPSViolations = 0;
    private long lastViolationTime = 0;
    private long lastFlagTime = 0;

    // Track click consistency over time
    private final List<Double> recentCVValues = new ArrayList<>();
    private long lastHighCPSTime = 0;

    public KillAuraA(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAura", "A");
    }

    @Override
    public void processPacket(PacketEvent event) {
        // Cleanup old CV values
        if (recentCVValues.size() > 5) {
            recentCVValues.remove(0);
        }

        // Only process USE_ENTITY (attack) and ARM_ANIMATION (swing) packets
        if (event.getPacketType() == PacketType.Play.Client.USE_ENTITY) {
            // Ensure it's an attack action
            if (event.getPacket().getEnumEntityUseActions().size() > 0) {
                EnumWrappers.EntityUseAction action = event.getPacket().getEnumEntityUseActions().read(0).getAction();
                if (action == EnumWrappers.EntityUseAction.ATTACK) {
                    // Process attack action
                    long now = System.currentTimeMillis();

                    // Store attack data
                    playerData.setLastAttack(now);
                    if (event.getPacket().getIntegers().size() > 0) {
                        playerData.setLastAttackedEntity(event.getPacket().getIntegers().read(0));
                    }

                    checkAttackPattern(now);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Client.ARM_ANIMATION) {
            playerData.setLastArmAnimation(System.currentTimeMillis());

            // Check for extremely high CPS
            checkClickRate();
        }
    }

    private void checkAttackPattern(long now) {
        // Expire violations after some time
        if (now - lastViolationTime > 8000) {
            consistencyViolations = Math.max(0, consistencyViolations - 1);
            similarPatternCount = Math.max(0, similarPatternCount - 1);
        }

        // Get the recent arm animation timings - require more data points for more accuracy
        if (playerData.getRecentArmAnimations().size() < MIN_CLICKS_FOR_ANALYSIS) {
            return;
        }

        // Convert to array, filtering out nulls
        List<Long> filteredClicks = new ArrayList<>();
        for (Long click : playerData.getRecentArmAnimations()) {
            if (click != null) {
                filteredClicks.add(click);
            }
        }

        // If we don't have enough valid clicks after filtering, return
        if (filteredClicks.size() < MIN_CLICKS_FOR_ANALYSIS) {
            return;
        }

        // Sort to ensure chronological order
        Collections.sort(filteredClicks);
        Long[] clicks = filteredClicks.toArray(new Long[0]);

        // Calculate intervals between clicks
        double totalDiff = 0;
        double[] intervals = new double[clicks.length - 1];

        // Calculate intervals
        for (int i = 1; i < clicks.length; i++) {
            intervals[i-1] = clicks[i] - clicks[i-1];
            totalDiff += intervals[i-1];
        }

        // Guard against division by zero
        if (intervals.length == 0 || totalDiff == 0) {
            return;
        }

        double avgInterval = totalDiff / intervals.length;
        double variance = 0;

        // Calculate variance
        for (double interval : intervals) {
            variance += Math.pow(interval - avgInterval, 2);
        }

        variance /= intervals.length;
        double stdDeviation = Math.sqrt(variance);

        // Guard against division by zero
        if (avgInterval == 0) {
            return;
        }

        // Calculate coefficient of variation (lower = more consistent)
        double cv = stdDeviation / avgInterval;

        // Store for trend analysis
        recentCVValues.add(cv);

        // If we have previous data, check for very similar patterns
        if (lastDeviation > 0) {
            double deviationDiff = Math.abs(lastDeviation - cv);

            // Make pattern detection much stricter - extremely low CV and extremely consistent patterns
            if (deviationDiff < 0.015 && cv < 0.06) { // Stricter thresholds
                // Consider context - is the player in combat?
                long timeSinceLastAttack = now - playerData.getLastAttack();
                if (timeSinceLastAttack < 1000) { // In active combat
                    similarPatternCount++;

                    // Require more consistently suspicious patterns before flagging
                    if (similarPatternCount > 4) { // Increased from 3
                        // Only flag once per 15 seconds to prevent spamming
                        if (now - lastFlagTime > 15000) {
                            flag(1.0, "Extreme click consistency in combat (CV: " + String.format("%.3f", cv) + ")");
                            lastFlagTime = now;
                            lastViolationTime = now;
                            consistencyViolations++;
                        }
                        similarPatternCount = 0;
                    }
                } else {
                    // Less suspicious if not in combat (might be breaking blocks with an auto-clicker)
                    similarPatternCount = Math.max(0, similarPatternCount - 1);
                }
            } else {
                // Reset counter if pattern changes
                similarPatternCount = Math.max(0, similarPatternCount - 1);
            }
        }

        // Look for extreme consistency patterns - physically impossible for humans
        // This requires analysis of CV over several samples
        if (recentCVValues.size() >= 3) {
            boolean allExtreme = true;
            for (Double cvValue : recentCVValues) {
                if (cvValue > EXTREME_CONSISTENCY_CV) {
                    allExtreme = false;
                    break;
                }
            }

            if (allExtreme && clicks.length >= 15) { // Require more data for this check
                consistencyViolations += 2; // More serious violation
                lastViolationTime = now;

                if (consistencyViolations >= 3 && now - lastFlagTime > 20000) {
                    flag(1.0, "Physically impossible click consistency detected (CV consistently < " +
                            EXTREME_CONSISTENCY_CV + ")");
                    lastFlagTime = now;
                    consistencyViolations = 0;
                    recentCVValues.clear(); // Reset after flagging
                }
            }
        }

        // Machine-like patterns typically have very low variation
        // Only flag extremely consistent patterns that humans can't replicate
        if (cv < (1.0 - CONSISTENCY_THRESHOLD) && clicks.length >= 15) { // Higher click requirement
            // Check if CV is extremely low (physically impossible)
            if (cv < 0.02) { // Near-zero variation is impossible for humans
                consistencyViolations += 2;
                lastViolationTime = now;

                if (consistencyViolations >= 3 && now - lastFlagTime > 15000) {
                    flag(1.0, "Inhuman click consistency detected (CV: " + String.format("%.4f", cv) + ")");
                    lastFlagTime = now;
                    consistencyViolations = 0;
                }
            }
        }

        lastDeviation = cv;
    }

    /**
     * Checks for abnormal clicking patterns that might indicate KillAura or AutoClicker
     */
    private void checkClickRate() {
        long now = System.currentTimeMillis();
        double cps = playerData.getClicksPerSecond();

        // Debug logging (only at higher CPS values)
        if (cps > 22) {
            plugin.getLogger().info("Player " + playerData.getPlayerName() + " CPS: " + String.format("%.1f", cps));
        }

        // Only check if we have enough data to calculate CPS
        if (cps > 0) {
            // Track high CPS over time
            if (cps > MAX_CPS_THRESHOLD - 5) {
                if (lastHighCPSTime > 0 && now - lastHighCPSTime < 5000) {
                    // Consecutive high CPS periods
                    highCPSViolations++;
                    lastViolationTime = now;
                } else {
                    // Reset if there's a gap in high CPS
                    highCPSViolations = 1;
                }
                lastHighCPSTime = now;
            } else {
                // Decay violations when not seeing high CPS
                if (now - lastHighCPSTime > 8000) {
                    highCPSViolations = Math.max(0, highCPSViolations - 1);
                }
            }

            // Progressive flagging based on CPS ranges with rate limiting
            if (cps > MAX_CPS_THRESHOLD && now - lastFlagTime > 15000) {
                // Extremely high CPS - flag with high confidence
                flag(1.0, "Abnormally high CPS: " + String.format("%.1f", cps));
                lastFlagTime = now;
            } else if (cps > MAX_CPS_THRESHOLD - 3 && highCPSViolations >= 3 &&
                    playerData.hasConsistentClickPattern() && now - lastFlagTime > 15000) {
                // Sustained high CPS with consistent pattern
                flag(0.8, "Sustained high CPS with consistent pattern: " + String.format("%.1f", cps));
                lastFlagTime = now;
                highCPSViolations = 0;
            }
            // Removed lower CPS checks to avoid false positives
        }
    }
}