package fi.tj88888.quantumAC.log;

/**
 * Represents a violation log entry
 */
public class ViolationLog {

    private final String playerName;
    private final String checkName;
    private final String checkType;
    private final double vl;
    private final String details;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final int ping;
    private final double tps;
    private final long timestamp;

    public ViolationLog(String playerName, String checkName, String checkType, double vl, String details,
                        String world, double x, double y, double z, int ping, double tps) {
        this.playerName = playerName;
        this.checkName = checkName;
        this.checkType = checkType;
        this.vl = vl;
        this.details = details;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ping = ping;
        this.tps = tps;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the player name
     *
     * @return Player name
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Gets the check name
     *
     * @return Check name
     */
    public String getCheckName() {
        return checkName;
    }

    /**
     * Gets the check type
     *
     * @return Check type
     */
    public String getCheckType() {
        return checkType;
    }

    /**
     * Gets the violation level
     *
     * @return Violation level
     */
    public double getVl() {
        return vl;
    }

    /**
     * Gets the violation details
     *
     * @return Violation details
     */
    public String getDetails() {
        return details;
    }

    /**
     * Gets the world name
     *
     * @return World name
     */
    public String getWorld() {
        return world;
    }

    /**
     * Gets the X coordinate
     *
     * @return X coordinate
     */
    public double getX() {
        return x;
    }

    /**
     * Gets the Y coordinate
     *
     * @return Y coordinate
     */
    public double getY() {
        return y;
    }

    /**
     * Gets the Z coordinate
     *
     * @return Z coordinate
     */
    public double getZ() {
        return z;
    }

    /**
     * Gets the player's ping
     *
     * @return Player ping
     */
    public int getPing() {
        return ping;
    }

    /**
     * Gets the server TPS
     *
     * @return Server TPS
     */
    public double getTps() {
        return tps;
    }

    /**
     * Gets the violation timestamp
     *
     * @return Timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }
}