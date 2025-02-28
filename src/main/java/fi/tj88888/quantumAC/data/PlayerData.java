package fi.tj88888.quantumAC.data;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.util.MovementData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PlayerData {

    private final UUID uuid;
    private final String playerName;
    private long joinTime;
    private int averagePing;
    private int totalViolations;

    // Movement data with thread safety
    private final ReentrantReadWriteLock movementLock = new ReentrantReadWriteLock();
    private Location lastLocation;
    private Location lastSafeLocation;
    private final MovementData movementData;
    private final MovementData previousMovementData; // Keep a backup of previous movement state

    // Check VL tracking
    private final Map<Class<? extends Check>, Double> violationLevels;

    // Basic packet tracking
    private long lastFlying;
    private long lastPosition;
    private long lastPositionLook;
    private long lastLook;
    private int flyingCount;
    private int positionCount;
    private int positionLookCount;
    private int lookCount;

    // Enhanced packet tracking
    private long lastArmAnimation;
    private long lastInventoryAction;
    private long lastInventoryClose;
    private long lastBlockDig;
    private long lastBlockPlace;
    private long lastAbilitiesPacket;
    private long lastCustomPayload;
    private long lastTransaction;
    private long lastKeepAlive;

    // Count tracking for enhanced packets
    private int armAnimationCount;
    private int inventoryActionCount;
    private int blockDigCount;
    private int blockPlaceCount;

    // Recent action timing tracking for analysis
    private final Deque<Long> recentArmAnimations = new ArrayDeque<>();
    private final Deque<Long> recentInventoryActions = new ArrayDeque<>();

    // Ping tracking
    private final int[] pingHistory;
    private int pingIndex;

    // Combat tracking
    private long lastAttack;
    private Integer lastAttackedEntity;
    private int attackCount;

    // Enhanced combat tracking
    private long lastInteract;
    private Integer lastInteractedEntity;
    private int interactCount;

    // Exemption tracking
    private final Map<String, Long> exemptionTimers;
    private boolean exempt;

    // Performance optimization fields
    private double serverTPS = 20.0;
    private int cachedNearbyEntityCount = 0;
    private long lastEntityCountUpdate = 0;
    private static final long ENTITY_COUNT_UPDATE_INTERVAL = 5000; // 5 seconds

    // Buffer to reduce database writes
    private boolean dataChanged = false;
    private long lastDataSave = 0;
    private static final long DATA_SAVE_INTERVAL = 60000; // 1 minute

    public PlayerData(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.joinTime = System.currentTimeMillis();
        this.averagePing = 0;
        this.totalViolations = 0;

        // Always initialize MovementData
        this.movementData = new MovementData();
        this.previousMovementData = new MovementData(); // Initialize backup
        this.violationLevels = new ConcurrentHashMap<>();

        this.pingHistory = new int[20];
        this.pingIndex = 0;

        this.exemptionTimers = new HashMap<>();
        this.exempt = false;

        this.lastDataSave = System.currentTimeMillis();
    }

    // Basic getters and setters
    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(long joinTime) {
        this.joinTime = joinTime;
        this.dataChanged = true;
    }

    public int getAveragePing() {
        return averagePing;
    }

    public void setAveragePing(int averagePing) {
        this.averagePing = averagePing;
    }

    public int getTotalViolations() {
        return totalViolations;
    }

    public void setTotalViolations(int totalViolations) {
        this.totalViolations = totalViolations;
        this.dataChanged = true;
    }

    public void incrementTotalViolations() {
        this.totalViolations++;
        this.dataChanged = true;
    }

    // Violation level methods
    public double getViolationLevel(Class<? extends Check> checkClass) {
        return violationLevels.getOrDefault(checkClass, 0.0);
    }

    public void setViolationLevel(Class<? extends Check> checkClass, double vl) {
        violationLevels.put(checkClass, vl);
        this.dataChanged = true;
    }

    public void incrementViolationLevel(Class<? extends Check> checkClass, double amount) {
        double currentVL = getViolationLevel(checkClass);
        violationLevels.put(checkClass, currentVL + amount);
        incrementTotalViolations();
        this.dataChanged = true;
    }

    public void decreaseViolationLevels() {
        // Decrease all violation levels over time
        boolean changed = false;
        for (Map.Entry<Class<? extends Check>, Double> entry : violationLevels.entrySet()) {
            if (entry.getValue() > 0) {
                violationLevels.put(entry.getKey(), Math.max(0, entry.getValue() - 0.1));
                changed = true;
            }
        }
        if (changed) {
            this.dataChanged = true;
        }
    }

    // Location methods with thread safety
    public Location getLastLocation() {
        movementLock.readLock().lock();
        try {
            return lastLocation != null ? lastLocation.clone() : null;
        } finally {
            movementLock.readLock().unlock();
        }
    }

    public void setLastLocation(Location lastLocation) {
        movementLock.writeLock().lock();
        try {
            this.lastLocation = lastLocation;
        } finally {
            movementLock.writeLock().unlock();
        }
    }

    public Location getLastSafeLocation() {
        movementLock.readLock().lock();
        try {
            return lastSafeLocation != null ? lastSafeLocation.clone() : null;
        } finally {
            movementLock.readLock().unlock();
        }
    }

    public void setLastSafeLocation(Location lastSafeLocation) {
        movementLock.writeLock().lock();
        try {
            this.lastSafeLocation = lastSafeLocation;
        } finally {
            movementLock.writeLock().unlock();
        }
    }

    public MovementData getMovementData() {
        movementLock.readLock().lock();
        try {
            return movementData;
        } finally {
            movementLock.readLock().unlock();
        }
    }

    /**
     * Updates both current and previous movement data
     * Call this before making any changes to movement data
     */
    public void prepareMovementDataUpdate() {
        movementLock.writeLock().lock();
        try {
            // Backup current movement data to previous
            copyMovementData(movementData, previousMovementData);
        } finally {
            movementLock.writeLock().unlock();
        }
    }

    /**
     * Gets the previous movement data state
     * Useful for detecting rapid changes or rollbacks
     */
    public MovementData getPreviousMovementData() {
        movementLock.readLock().lock();
        try {
            return previousMovementData;
        } finally {
            movementLock.readLock().unlock();
        }
    }

    /**
     * Copy all movement data from one object to another
     */
    private void copyMovementData(MovementData source, MovementData target) {
        // This method would copy all relevant fields
        // Implementation depends on MovementData structure
        // Example (implement all fields as needed):
        target.updatePosition(source.getX(), source.getY(), source.getZ());
        target.updateRotation(source.getYaw(), source.getPitch());
        target.updateGroundState(source.isOnGround());
        target.updateBlockState(
                source.isInsideBlock(),
                source.isOnIce(),
                source.isOnSlime(),
                source.isInLiquid(),
                source.isOnStairs(),
                source.isOnSlab()
        );
    }

    // Basic packet timing methods
    public long getLastFlying() {
        return lastFlying;
    }

    public void setLastFlying(long lastFlying) {
        this.lastFlying = lastFlying;
        this.flyingCount++;
    }

    public long getLastPosition() {
        return lastPosition;
    }

    public void setLastPosition(long lastPosition) {
        this.lastPosition = lastPosition;
        this.positionCount++;
    }

    public long getLastPositionLook() {
        return lastPositionLook;
    }

    public void setLastPositionLook(long lastPositionLook) {
        this.lastPositionLook = lastPositionLook;
        this.positionLookCount++;
    }

    public long getLastLook() {
        return lastLook;
    }

    public void setLastLook(long lastLook) {
        this.lastLook = lastLook;
        this.lookCount++;
    }

    public int getFlyingCount() {
        return flyingCount;
    }

    public int getPositionCount() {
        return positionCount;
    }

    public int getPositionLookCount() {
        return positionLookCount;
    }

    public int getLookCount() {
        return lookCount;
    }

    // Enhanced packet tracking methods

    // Arm animation
    public long getLastArmAnimation() {
        return lastArmAnimation;
    }

    /**
     * Records an arm animation event for combat analysis
     *
     * @param time the timestamp of the arm animation
     */
    public void setLastArmAnimation(long time) {
        // Skip duplicate timestamps that are too close together
        if (!recentArmAnimations.isEmpty() && recentArmAnimations.peekLast() != null) {
            long lastTime = recentArmAnimations.peekLast();
            // If timestamps are identical or unrealistically close (< 10ms), ignore
            if (time - lastTime < 10) {
                return;
            }
        }

        this.lastArmAnimation = time;
        this.armAnimationCount++;

        // Store for click pattern analysis
        recentArmAnimations.add(time);

        // Maintain max size of queue
        while (recentArmAnimations.size() > 30) {
            recentArmAnimations.pollFirst();
        }

        // Remove clicks older than 3 seconds to focus on recent activity
        long now = System.currentTimeMillis();
        while (!recentArmAnimations.isEmpty() && recentArmAnimations.peekFirst() != null) {
            long oldest = recentArmAnimations.peekFirst();
            if (now - oldest > 3000) {
                recentArmAnimations.pollFirst();
            } else {
                break;
            }
        }
    }

    public int getArmAnimationCount() {
        return armAnimationCount;
    }

    public Deque<Long> getRecentArmAnimations() {
        return recentArmAnimations;
    }

    // Inventory actions
    public long getLastInventoryAction() {
        return lastInventoryAction;
    }

    public void setLastInventoryAction(long time) {
        this.lastInventoryAction = time;
        this.inventoryActionCount++;

        // Store for inventory action pattern analysis
        recentInventoryActions.add(time);
        while (recentInventoryActions.size() > 20) {
            recentInventoryActions.pollFirst();
        }
    }

    public int getInventoryActionCount() {
        return inventoryActionCount;
    }

    public Deque<Long> getRecentInventoryActions() {
        return recentInventoryActions;
    }

    public long getLastInventoryClose() {
        return lastInventoryClose;
    }

    public void setLastInventoryClose(long time) {
        this.lastInventoryClose = time;
    }

    // Block interactions
    public long getLastBlockDig() {
        return lastBlockDig;
    }

    public void setLastBlockDig(long time) {
        this.lastBlockDig = time;
        this.blockDigCount++;
    }

    public int getBlockDigCount() {
        return blockDigCount;
    }

    public long getLastBlockPlace() {
        return lastBlockPlace;
    }

    public void setLastBlockPlace(long time) {
        this.lastBlockPlace = time;
        this.blockPlaceCount++;
    }

    public int getBlockPlaceCount() {
        return blockPlaceCount;
    }

    // Ability packets
    public long getLastAbilitiesPacket() {
        return lastAbilitiesPacket;
    }

    public void setLastAbilitiesPacket(long time) {
        this.lastAbilitiesPacket = time;
    }

    // Custom payload
    public long getLastCustomPayload() {
        return lastCustomPayload;
    }

    public void setLastCustomPayload(long time) {
        this.lastCustomPayload = time;
    }

    // Transaction packets
    public long getLastTransaction() {
        return lastTransaction;
    }

    public void setLastTransaction(long time) {
        this.lastTransaction = time;
    }

    // Keep alive packets
    public long getLastKeepAlive() {
        return lastKeepAlive;
    }

    public void setLastKeepAlive(long time) {
        this.lastKeepAlive = time;
    }

    // Ping methods
    public void updatePing(int ping) {
        pingHistory[pingIndex] = ping;
        pingIndex = (pingIndex + 1) % pingHistory.length;

        // Calculate average ping
        int sum = 0;
        int count = 0;
        for (int p : pingHistory) {
            if (p > 0) {
                sum += p;
                count++;
            }
        }
        this.averagePing = count > 0 ? sum / count : 0;
    }

    // Basic combat methods
    public long getLastAttack() {
        return lastAttack;
    }

    public void setLastAttack(long lastAttack) {
        this.lastAttack = lastAttack;
        this.attackCount++;
    }

    public Integer getLastAttackedEntity() {
        return lastAttackedEntity;
    }

    public void setLastAttackedEntity(Integer lastAttackedEntity) {
        this.lastAttackedEntity = lastAttackedEntity;
    }

    public int getAttackCount() {
        return attackCount;
    }

    // Enhanced combat methods
    public long getLastInteract() {
        return lastInteract;
    }

    public void setLastInteract(long time) {
        this.lastInteract = time;
        this.interactCount++;
    }

    public Integer getLastInteractedEntity() {
        return lastInteractedEntity;
    }

    public void setLastInteractedEntity(Integer entityId) {
        this.lastInteractedEntity = entityId;
    }

    public int getInteractCount() {
        return interactCount;
    }

    // Exemption methods
    public void setExempt(String reason, long duration) {
        exemptionTimers.put(reason, System.currentTimeMillis() + duration);
        exempt = true;
    }

    public void removeExempt(String reason) {
        exemptionTimers.remove(reason);
        updateExemptStatus();
    }

    public boolean isExempt() {
        updateExemptStatus();
        return exempt;
    }

    private void updateExemptStatus() {
        long now = System.currentTimeMillis();
        exemptionTimers.entrySet().removeIf(entry -> entry.getValue() <= now);
        exempt = !exemptionTimers.isEmpty();
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(this.uuid); // Retrieve the Player object by UUID
    }

    /**
     * Calculates the clicks per second based on recent arm animations
     * Uses a sliding window approach for more accurate CPS measurement
     *
     * @return clicks per second or 0 if not enough data
     */
    public double getClicksPerSecond() {
        // Need at least 2 clicks to calculate CPS
        if (recentArmAnimations.size() < 2) {
            return 0;
        }

        // Filter out null values and sort timestamps
        List<Long> validClicks = new ArrayList<>();
        for (Long click : recentArmAnimations) {
            if (click != null) {
                validClicks.add(click);
            }
        }

        // Not enough valid clicks
        if (validClicks.size() < 2) {
            return 0;
        }

        // Sort to ensure chronological order
        Collections.sort(validClicks);

        // Current time
        long now = System.currentTimeMillis();

        // Calculate CPS using a 1-second window
        // Count how many clicks happened in the last second
        int recentClicks = 0;
        for (int i = validClicks.size() - 1; i >= 0; i--) {
            if (now - validClicks.get(i) <= 1000) {
                recentClicks++;
            } else {
                break; // No need to check older clicks
            }
        }

        // If we have recent clicks, return the count (which is already clicks per second)
        if (recentClicks > 0) {
            return recentClicks;
        }

        // Fallback to traditional calculation if no clicks in the last second
        long newest = validClicks.get(validClicks.size() - 1);
        long oldest = validClicks.get(0);
        long timeSpan = newest - oldest;

        // Prevent division by zero
        if (timeSpan <= 0) {
            return 0;
        }

        // Calculate CPS
        return (validClicks.size() - 1) * 1000.0 / timeSpan;
    }

    /**
     * Checks if the click timing is suspiciously consistent
     * Used for machine-like clicking detection
     *
     * @return true if clicks appear machine-generated
     */
    public boolean hasConsistentClickPattern() {
        if (recentArmAnimations.size() < 5) {
            return false;
        }

        // Filter out null values and sort timestamps
        List<Long> validClicks = new ArrayList<>();
        for (Long click : recentArmAnimations) {
            if (click != null) {
                validClicks.add(click);
            }
        }

        // Not enough valid clicks
        if (validClicks.size() < 5) {
            return false;
        }

        // Sort to ensure chronological order
        Collections.sort(validClicks);

        // Calculate intervals between clicks
        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < validClicks.size(); i++) {
            intervals.add((double)(validClicks.get(i) - validClicks.get(i-1)));
        }

        // Calculate average interval
        double sum = 0;
        for (double interval : intervals) {
            sum += interval;
        }
        double avgInterval = sum / intervals.size();

        // Calculate standard deviation
        double sumSquaredDiff = 0;
        for (double interval : intervals) {
            sumSquaredDiff += Math.pow(interval - avgInterval, 2);
        }
        double stdDev = Math.sqrt(sumSquaredDiff / intervals.size());

        // Calculate coefficient of variation (CV)
        double cv = (avgInterval > 0) ? stdDev / avgInterval : 0;

        // For human clicking, CV is typically > 0.1
        // For machine clicking, CV is typically < 0.05
        return cv < 0.08 && avgInterval < 100;
    }

    /**
     * Get the current server TPS (Ticks Per Second)
     * Used for contextual adjustments to detection thresholds
     *
     * @return Current server TPS
     */
    public double getServerTPS() {
        // Get TPS from the server via the plugin's config manager
        Player player = getPlayer();
        if (player != null && player.isOnline()) {
            QuantumAC plugin = QuantumAC.getInstance();
            if (plugin != null) {
                this.serverTPS = plugin.getConfigManager().getCurrentTPS();
            }
        }
        return this.serverTPS;
    }

    /**
     * Updates the cached entity count on the main thread
     * Should be called periodically from the main server thread
     */
    public void updateNearbyEntityCount() {
        long now = System.currentTimeMillis();
        // Only update if interval has passed
        if (now - lastEntityCountUpdate < ENTITY_COUNT_UPDATE_INTERVAL) {
            return;
        }

        Player player = getPlayer();
        if (player != null && player.isOnline()) {
            try {
                // This must run on the main thread
                this.cachedNearbyEntityCount = player.getNearbyEntities(16.0, 16.0, 16.0).size();
                this.lastEntityCountUpdate = now;
            } catch (Exception e) {
                // Fail silently - will keep using the last valid count
            }
        }
    }

    /**
     * Get the count of nearby entities
     * Uses cached value to avoid async access issues
     *
     * @return Count of nearby entities
     */
    public int getNearbyEntityCount() {
        // Just return the cached value
        // This is safe to call from async threads
        return this.cachedNearbyEntityCount;
    }

    /**
     * Set the server TPS manually
     * Useful for testing and simulations
     *
     * @param tps TPS value to set
     */
    public void setServerTPS(double tps) {
        this.serverTPS = Math.min(Math.max(tps, 0.0), 20.0); // Clamp between 0-20
    }

    /**
     * Check if data should be saved based on changes and time interval
     *
     * @return true if data should be saved
     */
    public boolean shouldSaveData() {
        long now = System.currentTimeMillis();
        return dataChanged && (now - lastDataSave > DATA_SAVE_INTERVAL);
    }

    /**
     * Mark data as saved to reset flags
     */
    public void markDataSaved() {
        this.dataChanged = false;
        this.lastDataSave = System.currentTimeMillis();
    }
}