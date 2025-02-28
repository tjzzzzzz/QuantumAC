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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KillAuraC
 * Detects multi-target attacks with suspicious patterns
 * Looks for switching between targets too quickly or in an unnatural pattern
 */
public class KillAuraC extends Check {

    // Further reduced to catch only extreme cases
    private static final long MIN_TARGET_SWITCH_TIME = 100; // Further reduced from 150ms

    // Entity types that should be excluded from fast switch detection
    // (these often spawn in groups and legitimate players switch between them rapidly)
    private static final EntityType[] EXCLUDED_ENTITY_TYPES = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.CREEPER, EntityType.SILVERFISH, EntityType.ENDERMITE,
            EntityType.BAT, EntityType.VEX, EntityType.SLIME, EntityType.MAGMA_CUBE
    };

    // Increased to avoid false positives in crowded combat
    private static final int MAX_TARGETS_PERIOD = 5; // Increased from 4
    private static final long TARGET_PERIOD = 1500; // Increased from 1000ms for more leniency

    // Added more lenient requirement for alternating patterns
    private static final int MIN_ALTERNATIONS_FOR_FLAG = 5; // Increased from 4

    // Add tracking variables for violations with higher thresholds
    private int fastSwitchViolations = 0;
    private int unrealisticSwitchViolations = 0;
    private int multiTargetViolations = 0;
    private long lastViolationTime = 0;
    private long lastFlagTime = 0;
    private static final int MIN_VIOLATIONS_BEFORE_FLAG = 3; // Increased from 2
    private static final long VIOLATION_EXPIRY_TIME = 8000; // Increased from 5000ms

    private final Queue<TargetInfo> recentTargets = new LinkedList<>();
    private final Map<Integer, Long> lastAttackByEntity = new HashMap<>();
    private int lastTarget = -1;

    // Track distances to help with context
    private double lastSwitchDistance = 0;
    private int similarDistanceSwitches = 0;

    // Thread-safe cache for entity positions
    private final ConcurrentHashMap<Integer, EntityInfo> entityPositionCache = new ConcurrentHashMap<>();

    public KillAuraC(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAura", "C");

        // Schedule entity position caching (run every 5 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, this::cacheEntityPositions, 1L, 5L);
    }

    /**
     * Cache entity positions from the main thread for async use
     */
    private void cacheEntityPositions() {
        Player player = playerData.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Clean up old entries
        if (entityPositionCache.size() > 50) {
            entityPositionCache.clear();
        }

        // Cache nearby entities for distance calculations (within 20 blocks radius)
        for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
            Location loc = entity.getLocation();
            entityPositionCache.put(entity.getEntityId(), new EntityInfo(
                    entity.getType(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    System.currentTimeMillis()
            ));
        }
    }

    /**
     * Check if entity type should be excluded from fast switch detection
     */
    private boolean isExcludedEntityType(EntityType type) {
        for (EntityType excluded : EXCLUDED_ENTITY_TYPES) {
            if (type == excluded) return true;
        }
        return false;
    }

    @Override
    public void processPacket(PacketEvent event) {
        // Expire violations after some time
        checkViolationExpiry();

        if (event.getPacketType() != PacketType.Play.Client.USE_ENTITY) {
            return;
        }

        // Ensure it's an attack action and packet has data
        if (event.getPacket().getEnumEntityUseActions().size() == 0 ||
                event.getPacket().getEnumEntityUseActions().read(0).getAction() != EnumWrappers.EntityUseAction.ATTACK) {
            return;
        }

        // Ensure packet has entity ID
        if (event.getPacket().getIntegers().size() == 0) {
            return;
        }

        // Handle the attack
        int entityId = event.getPacket().getIntegers().read(0);
        long now = System.currentTimeMillis();

        // Skip if attacking the same entity
        if (entityId == lastTarget) {
            return;
        }

        // Get cached entity info if available
        EntityInfo targetEntity = entityPositionCache.get(entityId);
        EntityInfo lastEntity = lastTarget != -1 ? entityPositionCache.get(lastTarget) : null;

        // If we had a previous target, check for fast switching
        if (lastTarget != -1) {
            long lastAttackTime = playerData.getLastAttack();
            long timeSinceLastAttack = now - lastAttackTime;

            // Skip fast switch check in certain situations
            boolean skipFastSwitchCheck = false;

            // Skip for high ping players
            if (playerData.getAveragePing() > 120) {
                skipFastSwitchCheck = true;
            }

            // Skip for excluded entity types (common mob types often fought in groups)
            if (targetEntity != null && lastEntity != null) {
                if (isExcludedEntityType(targetEntity.type) && isExcludedEntityType(lastEntity.type)) {
                    skipFastSwitchCheck = true;
                }
            }

            // Check for switching targets too quickly (only if not skipped)
            if (!skipFastSwitchCheck && timeSinceLastAttack < MIN_TARGET_SWITCH_TIME) {
                // Increment violation counter with context awareness
                if (timeSinceLastAttack < 50) {  // Extremely fast switches are more suspicious
                    fastSwitchViolations += 2;
                } else {
                    fastSwitchViolations++;
                }

                lastViolationTime = now;

                if (fastSwitchViolations >= MIN_VIOLATIONS_BEFORE_FLAG && now - lastFlagTime > 15000) {
                    flag(0.7, "Multiple extreme cases of fast target switching (latest: " +
                            timeSinceLastAttack + "ms)");
                    lastFlagTime = now;
                    fastSwitchViolations = 0;
                }
            }

            // Check for distance-based patterns between targets
            analyzeTargetDistancePatterns(targetEntity, lastEntity, now);
        }

        // Record this target
        lastTarget = entityId;
        lastAttackByEntity.put(entityId, now);

        // Add to recent targets queue
        recentTargets.add(new TargetInfo(entityId, now));

        // Remove old targets outside our time window
        while (!recentTargets.isEmpty() && recentTargets.peek().time < now - TARGET_PERIOD) {
            recentTargets.poll();
        }

        // Check for attacking multiple targets in a short period
        checkMultiTargetAttacks(now);

        // Check target distribution for machine-like patterns
        checkTargetDistribution(now);
    }

    /**
     * Analyzes patterns in the distances between consecutively attacked entities
     */
    private void analyzeTargetDistancePatterns(EntityInfo targetEntity, EntityInfo lastEntity, long now) {
        if (targetEntity == null || lastEntity == null) {
            return;
        }

        double distanceBetweenTargets = distance(
                targetEntity.x, targetEntity.y, targetEntity.z,
                lastEntity.x, lastEntity.y, lastEntity.z
        );

        // Very far apart targets need analysis with player position context
        if (distanceBetweenTargets > 7.0) { // Increased from 6.0
            Player player = playerData.getPlayer();
            if (player != null) {
                Location playerLoc = player.getLocation();
                double distToLastTarget = distance(
                        playerLoc.getX(), playerLoc.getY(), playerLoc.getZ(),
                        lastEntity.x, lastEntity.y, lastEntity.z
                );
                double distToNewTarget = distance(
                        playerLoc.getX(), playerLoc.getY(), playerLoc.getZ(),
                        targetEntity.x, targetEntity.y, targetEntity.z
                );

                // Check for unrealistic target switching - higher thresholds and context
                if (distToLastTarget > 4.5 && distToNewTarget > 4.5 && // Increased from 4.0
                        Math.abs(distToLastTarget - distToNewTarget) < 1.0) { // Targets at similar distances

                    unrealisticSwitchViolations++;
                    lastViolationTime = now;

                    if (unrealisticSwitchViolations >= MIN_VIOLATIONS_BEFORE_FLAG + 1 && // Higher threshold
                            now - lastFlagTime > 15000) {
                        flag(0.8, "Multiple unrealistic target switches between distant entities: " +
                                String.format("%.1f", distanceBetweenTargets) + " blocks apart");
                        lastFlagTime = now;
                        unrealisticSwitchViolations = 0;
                    }
                }
            }
        }

        // Look for suspiciously similar distances between target switches
        // (a common pattern in some aimbots that cycle between targets)
        if (lastSwitchDistance > 0) {
            double distanceDifference = Math.abs(distanceBetweenTargets - lastSwitchDistance);

            // If distances are very similar
            if (distanceDifference < 0.3 && distanceBetweenTargets > 2.0) {
                similarDistanceSwitches++;

                if (similarDistanceSwitches >= 4 && now - lastFlagTime > 20000) {
                    flag(0.7, "Suspicious pattern of identical distance target switches: " +
                            String.format("%.2f", distanceBetweenTargets) + " blocks");
                    lastFlagTime = now;
                    similarDistanceSwitches = 0;
                }
            } else {
                // Reset or decay counter
                similarDistanceSwitches = Math.max(0, similarDistanceSwitches - 1);
            }
        }

        lastSwitchDistance = distanceBetweenTargets;
    }

    /**
     * Checks for attacking multiple different targets in a short period
     */
    private void checkMultiTargetAttacks(long now) {
        // Only run check if we have enough targets
        if (recentTargets.size() <= MAX_TARGETS_PERIOD) {
            return;
        }

        // Count unique entities
        Map<Integer, Integer> targetCounts = new HashMap<>();
        for (TargetInfo info : recentTargets) {
            targetCounts.put(info.entityId, targetCounts.getOrDefault(info.entityId, 0) + 1);
        }

        // Check both enough unique targets and enough total attacks
        if (targetCounts.size() >= MAX_TARGETS_PERIOD && recentTargets.size() >= MAX_TARGETS_PERIOD + 3) {
            // Check if the targets are distributed evenly (suspicious pattern)
            boolean evenDistribution = true;
            int expectedCount = recentTargets.size() / targetCounts.size();

            for (int count : targetCounts.values()) {
                if (Math.abs(count - expectedCount) > 1) {
                    evenDistribution = false;
                    break;
                }
            }

            // Perfect distribution is more suspicious
            if (evenDistribution) {
                multiTargetViolations += 2;
            } else {
                multiTargetViolations++;
            }

            lastViolationTime = now;

            if (multiTargetViolations >= MIN_VIOLATIONS_BEFORE_FLAG + 1 && now - lastFlagTime > 15000) {
                String distribution = evenDistribution ? " with suspicious even distribution" : "";
                flag(0.75, "Multiple instances of rapid multi-target attacks: " +
                        targetCounts.size() + " targets in " + (TARGET_PERIOD / 1000.0) + " seconds" + distribution);
                lastFlagTime = now;
                multiTargetViolations = 0;
            }
        }
    }

    private void checkTargetDistribution(long now) {
        // Need at least a few targets to analyze
        if (recentTargets.size() < 10) { // Increased from 8
            return;
        }

        // Count alternating pattern (switching back and forth)
        int alternatingCount = 0;
        Integer prev = null;
        Integer prevPrev = null;

        for (TargetInfo info : recentTargets) {
            if (prev != null && prevPrev != null) {
                // Check if we're cycling between the same two targets repeatedly
                if (info.entityId == prevPrev && info.entityId != prev) {
                    alternatingCount++;
                }
            }

            prevPrev = prev;
            prev = info.entityId;
        }

        // If we see a strong alternating pattern (A-B-A-B-A-B-A-B-A)
        // Increased required alternations to reduce false positives
        if (alternatingCount >= MIN_ALTERNATIONS_FOR_FLAG) {
            if (now - lastFlagTime > 20000) { // Longer cooldown for this flag
                flag(0.7, "Suspicious alternating target pattern detected: " + alternatingCount + " alternations");
                lastFlagTime = now;
            }
        }
    }

    /**
     * Expires violation counters after some time without violations
     */
    private void checkViolationExpiry() {
        long now = System.currentTimeMillis();

        // If it's been too long since last violation, decay counters
        if (now - lastViolationTime > VIOLATION_EXPIRY_TIME) {
            fastSwitchViolations = Math.max(0, fastSwitchViolations - 1);
            unrealisticSwitchViolations = Math.max(0, unrealisticSwitchViolations - 1);
            multiTargetViolations = Math.max(0, multiTargetViolations - 1);
            similarDistanceSwitches = Math.max(0, similarDistanceSwitches - 1);
        }

        // Faster decay for fast switch violations
        if (now - lastViolationTime > VIOLATION_EXPIRY_TIME / 2) {
            fastSwitchViolations = Math.max(0, fastSwitchViolations - 1);
        }
    }

    /**
     * Calculate 3D distance between two points
     */
    private double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt(
                Math.pow(x2 - x1, 2) +
                        Math.pow(y2 - y1, 2) +
                        Math.pow(z2 - z1, 2)
        );
    }

    /**
     * Class to store target info for pattern analysis
     */
    private static class TargetInfo {
        final int entityId;
        final long time;

        TargetInfo(int entityId, long time) {
            this.entityId = entityId;
            this.time = time;
        }
    }

    /**
     * Class to store cached entity position data
     */
    private static class EntityInfo {
        final org.bukkit.entity.EntityType type;
        final double x;
        final double y;
        final double z;
        final long timestamp;

        EntityInfo(org.bukkit.entity.EntityType type, double x, double y, double z, long timestamp) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }
    }
}