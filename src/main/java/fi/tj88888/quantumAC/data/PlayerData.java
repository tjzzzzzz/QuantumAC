package fi.tj88888.quantumAC.data;

import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.util.MovementData;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerData {

    private final UUID uuid;
    private final String playerName;
    private long joinTime;
    private int averagePing;
    private int totalViolations;

    // Movement data
    private Location lastLocation;
    private Location lastSafeLocation;
    private MovementData movementData;

    // Check VL tracking
    private final Map<Class<? extends Check>, Double> violationLevels;

    // Packet tracking
    private long lastFlying;
    private long lastPosition;
    private long lastPositionLook;
    private long lastLook;
    private int flyingCount;
    private int positionCount;
    private int positionLookCount;
    private int lookCount;

    // Ping tracking
    private final int[] pingHistory;
    private int pingIndex;

    // Combat tracking
    private long lastAttack;
    private Integer lastAttackedEntity;
    private int attackCount;

    // Exemption tracking
    private final Map<String, Long> exemptionTimers;
    private boolean exempt;

    public PlayerData(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.joinTime = System.currentTimeMillis();
        this.averagePing = 0;
        this.totalViolations = 0;

        this.movementData = new MovementData();
        this.violationLevels = new ConcurrentHashMap<>();

        this.pingHistory = new int[20];
        this.pingIndex = 0;

        this.exemptionTimers = new HashMap<>();
        this.exempt = false;
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
    }

    public void incrementTotalViolations() {
        this.totalViolations++;
    }

    // Violation level methods
    public double getViolationLevel(Class<? extends Check> checkClass) {
        return violationLevels.getOrDefault(checkClass, 0.0);
    }

    public void setViolationLevel(Class<? extends Check> checkClass, double vl) {
        violationLevels.put(checkClass, vl);
    }

    public void incrementViolationLevel(Class<? extends Check> checkClass, double amount) {
        double currentVL = getViolationLevel(checkClass);
        violationLevels.put(checkClass, currentVL + amount);
        incrementTotalViolations();
    }

    public void decreaseViolationLevels() {
        // Decrease all violation levels over time
        violationLevels.replaceAll((check, vl) -> Math.max(0, vl - 0.1));
    }

    // Location methods
    public Location getLastLocation() {
        return lastLocation;
    }

    public void setLastLocation(Location lastLocation) {
        this.lastLocation = lastLocation;
    }

    public Location getLastSafeLocation() {
        return lastSafeLocation;
    }

    public void setLastSafeLocation(Location lastSafeLocation) {
        this.lastSafeLocation = lastSafeLocation;
    }

    public MovementData getMovementData() {
        return movementData;
    }

    // Packet timing methods
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

    // Combat methods
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
}