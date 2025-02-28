package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KillAuraE
 * Detects abnormal hit/miss ratio and suspicious hit selection
 * Analyzes accuracy and prioritization in combat
 */
public class KillAuraE extends Check {

    // Increased accuracy threshold to reduce false positives for skilled players
    private static final double MIN_SUSPICIOUS_ACCURACY = 0.95; // Increased from 0.9
    private static final int MIN_ATTACKS_FOR_ANALYSIS = 30; // Increased from 20
    private static final double HEALTH_PRIORITY_THRESHOLD = 0.85; // Increased from 0.8

    // Add violation tracking
    private int accuracyViolations = 0;
    private int healthTargetingViolations = 0;
    private int rangeViolations = 0;
    private int wallHackViolations = 0;
    private long lastViolationTime = 0;
    private long lastFlagTime = 0;
    private static final int MIN_VIOLATIONS_BEFORE_FLAG = 2;
    private static final long VIOLATION_EXPIRY_TIME = 10000; // 10 seconds

    // Count of arm animations (potential attacks)
    private int totalSwings = 0;

    // Count of successful attacks
    private int successfulHits = 0;

    // Track low health targeting patterns
    private final Map<Integer, Double> entityHealthAtAttack = new HashMap<>();

    // Thread-safe cache for entity data
    private final ConcurrentHashMap<Integer, EntityData> entityCache = new ConcurrentHashMap<>();

    public KillAuraE(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAura", "E");

        // Schedule regular entity caching on the main thread
        Bukkit.getScheduler().runTaskTimer(plugin, this::cacheEntityData, 1L, 5L);
    }

    /**
     * Caches entity data from the main thread
     * This includes position, health, and line of sight data
     */
    private void cacheEntityData() {
        Player player = playerData.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Clean old cache entries if too many
        if (entityCache.size() > 50) {
            entityCache.clear();
        }

        // Cache nearby entities for later async use
        for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
            Location entityLoc = entity.getLocation();

            // More accurate line of sight checking that considers more edge cases
            boolean hasLineOfSight = hasLineOfSight(player, entity);

            double health = -1;
            double maxHealth = -1;

            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                health = living.getHealth();
                maxHealth = living.getMaxHealth();
            }

            entityCache.put(entity.getEntityId(), new EntityData(
                    entity.getType(),
                    entityLoc.getX(),
                    entityLoc.getY(),
                    entityLoc.getZ(),
                    entity.getHeight(),
                    entity.getWidth(),
                    hasLineOfSight,
                    health,
                    maxHealth,
                    System.currentTimeMillis()
            ));
        }
    }

    /**
     * Better line of sight check that considers edge cases and gaps
     */
    private boolean hasLineOfSight(Player player, Entity target) {
        // Basic line of sight check
        boolean directLineOfSight = player.hasLineOfSight(target);

        // If there's a direct line of sight, no need for further checks
        if (directLineOfSight) {
            return true;
        }

        // For targets very close to the player, the line of sight check may fail
        // due to how Minecraft handles collision boxes
        if (player.getLocation().distance(target.getLocation()) < 2.0) {
            return true;
        }

        // Check if there are gaps in walls that might allow attacks
        Location playerEye = player.getEyeLocation();
        Location targetCenter = target.getLocation().add(0, target.getHeight() / 2, 0);

        // Generate ray from player to target
        double dx = targetCenter.getX() - playerEye.getX();
        double dy = targetCenter.getY() - playerEye.getY();
        double dz = targetCenter.getZ() - playerEye.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Normalize direction vector
        dx /= distance;
        dy /= distance;
        dz /= distance;

        // Step along the ray checking for non-solid blocks
        for (double d = 0; d < distance; d += 0.5) {
            Location checkLoc = playerEye.clone().add(dx * d, dy * d, dz * d);
            Block block = checkLoc.getBlock();

            // If we find a non-solid block along the way, there might be a gap
            if (!block.getType().isSolid() ||
                    block.getType() == Material.GLASS ||
                    block.getType().toString().contains("FENCE")) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void processPacket(PacketEvent event) {
        // Expire violations after some time
        checkViolationExpiry();

        Player player = playerData.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) {
            return; // Skip creative mode where hit detection doesn't apply normally
        }

        // Track arm animations as potential attacks
        if (event.getPacketType() == PacketType.Play.Client.ARM_ANIMATION) {
            totalSwings++;

            // Reset counters periodically to avoid stale data
            if (totalSwings > 200) { // Increased from 100
                totalSwings = 0;
                successfulHits = 0;
            }
        }

        // Track actual entity hits
        else if (event.getPacketType() == PacketType.Play.Client.USE_ENTITY) {
            // Ensure it's an attack action and packet has data
            if (event.getPacket().getEnumEntityUseActions().size() == 0 ||
                    event.getPacket().getEnumEntityUseActions().read(0).getAction() != EnumWrappers.EntityUseAction.ATTACK ||
                    event.getPacket().getIntegers().size() == 0) {
                return;
            }

            int entityId = event.getPacket().getIntegers().read(0);

            // Look for the entity in our cache
            EntityData targetData = entityCache.get(entityId);
            if (targetData == null || System.currentTimeMillis() - targetData.timestamp > 500) {
                return; // Skip if no cached data or data is too old
            }

            // Increment hits counter
            successfulHits++;

            // Check hit/miss ratio periodically
            checkAccuracy();

            // Store health data if available
            if (targetData.health > 0 && targetData.maxHealth > 0) {
                double healthPercent = targetData.health / targetData.maxHealth;
                entityHealthAtAttack.put(entityId, healthPercent);

                // Check for health-based target prioritization - but only with enough data
                if (entityHealthAtAttack.size() >= 8) { // Increased from 5
                    checkHealthBasedPriority();
                }
            }

            // Check for invalid attack range with entity-specific adjustment
            checkAttackRange(player, targetData);

            // Check for attacking through walls/obstacles
            checkLineOfSight(player, targetData);
        }
    }

    private void checkAccuracy() {
        // Only analyze if we have a meaningful sample size - increased requirement
        if (totalSwings < MIN_ATTACKS_FOR_ANALYSIS || successfulHits < 15) {
            return;
        }

        // Calculate hit ratio
        double hitRatio = (double) successfulHits / totalSwings;

        // Extremely high accuracy is suspicious but only with a lot of data
        if (hitRatio > MIN_SUSPICIOUS_ACCURACY) {
            accuracyViolations++;
            lastViolationTime = System.currentTimeMillis();

            if (accuracyViolations >= MIN_VIOLATIONS_BEFORE_FLAG &&
                    System.currentTimeMillis() - lastFlagTime > 20000) { // Longer cooldown
                flag(0.8, "Suspiciously high hit ratio over extended period: " +
                        String.format("%.1f%%", hitRatio * 100) + " (" + successfulHits + "/" + totalSwings + ")");
                lastFlagTime = System.currentTimeMillis();

                // Reset counters after flagging
                totalSwings = 0;
                successfulHits = 0;
            }
        }
    }

    private void checkHealthBasedPriority() {
        // Count low health targets
        int lowHealthTargets = 0;
        for (double health : entityHealthAtAttack.values()) {
            if (health < 0.3) { // 30% health or below
                lowHealthTargets++;
            }
        }

        double lowHealthRatio = (double) lowHealthTargets / entityHealthAtAttack.size();

        // Check for suspicious prioritization of low health targets with stricter threshold
        if (lowHealthRatio > HEALTH_PRIORITY_THRESHOLD && entityHealthAtAttack.size() >= 10) {
            healthTargetingViolations++;
            lastViolationTime = System.currentTimeMillis();

            if (healthTargetingViolations >= MIN_VIOLATIONS_BEFORE_FLAG &&
                    System.currentTimeMillis() - lastFlagTime > 15000) {
                flag(0.7, "Suspicious low-health targeting pattern: " +
                        String.format("%.1f%%", lowHealthRatio * 100) + " low-health targets (" +
                        lowHealthTargets + "/" + entityHealthAtAttack.size() + ")");
                lastFlagTime = System.currentTimeMillis();

                // Clear the map
                entityHealthAtAttack.clear();
            }
        }

        // Prevent unlimited growth
        if (entityHealthAtAttack.size() > 30) { // Increased from 20
            entityHealthAtAttack.clear();
        }
    }

    private void checkAttackRange(Player player, EntityData targetData) {
        Location playerLoc = player.getLocation();
        double distanceSquared = distanceSquared(
                playerLoc.getX(), playerLoc.getY(), playerLoc.getZ(),
                targetData.x, targetData.y, targetData.z
        );

        // Entity-specific range calculations based on type and size
        double maxRangeSquared = getMaxRangeForEntity(targetData);

        // Check for attack range violations with buffer
        if (distanceSquared > maxRangeSquared * 1.1) { // 10% buffer for lag compensation
            rangeViolations++;
            lastViolationTime = System.currentTimeMillis();

            if (rangeViolations >= MIN_VIOLATIONS_BEFORE_FLAG &&
                    System.currentTimeMillis() - lastFlagTime > 10000) {
                double actualDistance = Math.sqrt(distanceSquared);
                flag(0.85, "Multiple attacks beyond maximum range: " +
                        String.format("%.2f", actualDistance) + " blocks for entity type " + targetData.type);
                lastFlagTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * Calculate maximum range squared for different entity types
     */
    private double getMaxRangeForEntity(EntityData targetData) {
        // Base range is around 3-4 blocks squared
        double baseRange = 4.5 * 4.5; // Slightly increased from 4.0

        // Adjust for entity width/height
        double entitySize = targetData.width * targetData.height;

        // Special entity types get adjusted ranges
        if (targetData.type == EntityType.ENDER_DRAGON ||
                targetData.type == EntityType.GHAST ||
                targetData.type == EntityType.PHANTOM) {
            return 6.0 * 6.0; // Increased from 5.0 for larger entities
        } else if (targetData.type == EntityType.SLIME ||
                targetData.type == EntityType.MAGMA_CUBE) {
            // These can vary in size, so we use the entity dimensions
            return Math.max(baseRange, (3.0 + entitySize) * (3.0 + entitySize));
        } else if (targetData.type == EntityType.HORSE ||
                targetData.type == EntityType.DONKEY ||
                targetData.type == EntityType.MULE ||
                targetData.type == EntityType.RAVAGER) {
            return 5.0 * 5.0; // Larger mobs
        }

        // For very small entities, use a slightly larger range
        if (entitySize < 0.5) {
            return baseRange * 1.1;
        }

        return baseRange;
    }

    private void checkLineOfSight(Player player, EntityData targetData) {
        // Skip if creative mode
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Check if the player has line of sight to the target (from cached data)
        if (!targetData.hasLineOfSight) {
            // Get locations
            Location eyeLocation = player.getEyeLocation();
            double distance = distance(
                    eyeLocation.getX(), eyeLocation.getY(), eyeLocation.getZ(),
                    targetData.x, targetData.y + (targetData.height / 2), targetData.z
            );

            // Skip if very close to prevent false positives due to collision boxes
            if (distance <= 2.0) {
                return;
            }

            // Flag if no line of sight and not very close
            wallHackViolations++;
            lastViolationTime = System.currentTimeMillis();

            if (wallHackViolations >= MIN_VIOLATIONS_BEFORE_FLAG + 1 && // Higher threshold for wallhack detection
                    System.currentTimeMillis() - lastFlagTime > 10000) {
                flag(0.9, "Multiple attacks through obstacles - no line of sight at " +
                        String.format("%.1f", distance) + " blocks");
                lastFlagTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * Expires violation counters after some time without violations
     */
    private void checkViolationExpiry() {
        long now = System.currentTimeMillis();

        // If it's been too long since last violation, reset counters
        if (now - lastViolationTime > VIOLATION_EXPIRY_TIME) {
            accuracyViolations = 0;
            healthTargetingViolations = 0;
            rangeViolations = 0;
            wallHackViolations = 0;
        }
    }

    /**
     * Calculate distance squared between two points (more efficient)
     */
    private double distanceSquared(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2);
    }

    /**
     * Calculate 3D distance between two points
     */
    private double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt(distanceSquared(x1, y1, z1, x2, y2, z2));
    }

    /**
     * Class to store cached entity data
     */
    private static class EntityData {
        final EntityType type;
        final double x;
        final double y;
        final double z;
        final double height;
        final double width;
        final boolean hasLineOfSight;
        final double health;
        final double maxHealth;
        final long timestamp;

        EntityData(EntityType type, double x, double y, double z, double height, double width,
                   boolean hasLineOfSight, double health, double maxHealth, long timestamp) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.height = height;
            this.width = width;
            this.hasLineOfSight = hasLineOfSight;
            this.health = health;
            this.maxHealth = maxHealth;
            this.timestamp = timestamp;
        }
    }
}