package fi.tj88888.quantumAC.log;

import java.util.UUID;

public class ViolationLog {

    private final UUID playerUuid;
    private final String playerName;
    private final String checkName;
    private final String checkType;
    private final double violationLevel;
    private final String details;
    private final long timestamp;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final int ping;
    private final double tps;

    public ViolationLog(UUID playerUuid, String playerName, String checkName, String checkType,
                        double violationLevel, String details, long timestamp, String world,
                        double x, double y, double z, int ping, double tps) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.checkName = checkName;
        this.checkType = checkType;
        this.violationLevel = violationLevel;
        this.details = details;
        this.timestamp = timestamp;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ping = ping;
        this.tps = tps;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getCheckName() {
        return checkName;
    }

    public String getCheckType() {
        return checkType;
    }

    public double getViolationLevel() {
        return violationLevel;
    }

    public String getDetails() {
        return details;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getPing() {
        return ping;
    }

    public double getTps() {
        return tps;
    }

    @Override
    public String toString() {
        return String.format(
                "%s failed %s (VL: %.1f) - %s",
                playerName,
                checkName,
                violationLevel,
                details
        );
    }
}