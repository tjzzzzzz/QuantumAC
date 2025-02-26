package fi.tj88888.quantumAC.util;

/**
 * Class to store and manage player movement data
 */
public class MovementData {

    // Position data
    private double lastX;
    private double lastY;
    private double lastZ;
    private double x;
    private double y;
    private double z;

    // Velocity and acceleration
    private double deltaX;
    private double deltaY;
    private double deltaZ;
    private double lastDeltaX;
    private double lastDeltaY;
    private double lastDeltaZ;
    private double acceleration;

    // Rotation data
    private float yaw;
    private float pitch;
    private float lastYaw;
    private float lastPitch;
    private float deltaYaw;
    private float deltaPitch;

    // Ground state
    private boolean onGround;
    private boolean wasOnGround;
    private long groundTime;
    private long airTime;

    // Jumping state
    private boolean jumping;
    private long jumpStart;

    // Block data
    private boolean insideBlock;
    private boolean onIce;
    private boolean onSlime;
    private boolean inLiquid;
    private boolean onStairs;
    private boolean onSlab;

    public MovementData() {
        this.lastX = 0;
        this.lastY = 0;
        this.lastZ = 0;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.deltaX = 0;
        this.deltaY = 0;
        this.deltaZ = 0;
        this.lastDeltaX = 0;
        this.lastDeltaY = 0;
        this.lastDeltaZ = 0;
        this.acceleration = 0;
        this.yaw = 0;
        this.pitch = 0;
        this.lastYaw = 0;
        this.lastPitch = 0;
        this.deltaYaw = 0;
        this.deltaPitch = 0;
        this.onGround = true;
        this.wasOnGround = true;
        this.groundTime = 0;
        this.airTime = 0;
        this.jumping = false;
        this.jumpStart = 0;
        this.insideBlock = false;
        this.onIce = false;
        this.onSlime = false;
        this.inLiquid = false;
        this.onStairs = false;
        this.onSlab = false;
    }

    /**
     * Updates position data
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public void updatePosition(double x, double y, double z) {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;

        this.x = x;
        this.y = y;
        this.z = z;

        this.lastDeltaX = this.deltaX;
        this.lastDeltaY = this.deltaY;
        this.lastDeltaZ = this.deltaZ;

        this.deltaX = this.x - this.lastX;
        this.deltaY = this.y - this.lastY;
        this.deltaZ = this.z - this.lastZ;

        // Calculate acceleration (change in speed)
        double lastSpeed = Math.sqrt(lastDeltaX * lastDeltaX + lastDeltaZ * lastDeltaZ);
        double currentSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        this.acceleration = currentSpeed - lastSpeed;
    }

    /**
     * Updates rotation data
     *
     * @param yaw Yaw angle
     * @param pitch Pitch angle
     */
    public void updateRotation(float yaw, float pitch) {
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;

        this.yaw = yaw;
        this.pitch = pitch;

        // Calculate deltas, handling wrap-around for yaw
        this.deltaYaw = Math.abs(this.yaw - this.lastYaw);
        if (this.deltaYaw > 180) {
            this.deltaYaw = 360 - this.deltaYaw;
        }

        this.deltaPitch = Math.abs(this.pitch - this.lastPitch);
    }

    /**
     * Updates ground state
     *
     * @param onGround Whether player is on ground
     */
    public void updateGroundState(boolean onGround) {
        this.wasOnGround = this.onGround;
        this.onGround = onGround;

        long now = System.currentTimeMillis();

        if (onGround && !wasOnGround) {
            // Just landed
            this.groundTime = now;
        } else if (!onGround && wasOnGround) {
            // Just left ground
            this.airTime = now;

            // Check if this is the start of a jump (positive Y velocity right after leaving ground)
            if (this.deltaY > 0) {
                this.jumping = true;
                this.jumpStart = now;
            }
        }

        // Reset jumping state if back on ground
        if (onGround) {
            this.jumping = false;
        }
    }

    /**
     * Updates block state data
     *
     * @param insideBlock Whether player is inside a block
     * @param onIce Whether player is on ice
     * @param onSlime Whether player is on slime
     * @param inLiquid Whether player is in liquid
     * @param onStairs Whether player is on stairs
     * @param onSlab Whether player is on a slab
     */
    public void updateBlockState(boolean insideBlock, boolean onIce, boolean onSlime,
                                 boolean inLiquid, boolean onStairs, boolean onSlab) {
        this.insideBlock = insideBlock;
        this.onIce = onIce;
        this.onSlime = onSlime;
        this.inLiquid = inLiquid;
        this.onStairs = onStairs;
        this.onSlab = onSlab;
    }

    // Getters for all properties
    public double getLastX() {
        return lastX;
    }

    public double getLastY() {
        return lastY;
    }

    public double getLastZ() {
        return lastZ;
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

    public double getDeltaX() {
        return deltaX;
    }

    public double getDeltaY() {
        return deltaY;
    }

    public double getDeltaZ() {
        return deltaZ;
    }

    public double getLastDeltaX() {
        return lastDeltaX;
    }

    public double getLastDeltaY() {
        return lastDeltaY;
    }

    public double getLastDeltaZ() {
        return lastDeltaZ;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getLastYaw() {
        return lastYaw;
    }

    public float getLastPitch() {
        return lastPitch;
    }

    public float getDeltaYaw() {
        return deltaYaw;
    }

    public float getDeltaPitch() {
        return deltaPitch;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public boolean wasOnGround() {
        return wasOnGround;
    }

    public long getGroundTime() {
        return groundTime;
    }

    public long getAirTime() {
        return airTime;
    }

    public boolean isJumping() {
        return jumping;
    }

    public long getJumpStart() {
        return jumpStart;
    }

    public boolean isInsideBlock() {
        return insideBlock;
    }

    public boolean isOnIce() {
        return onIce;
    }

    public boolean isOnSlime() {
        return onSlime;
    }

    public boolean isInLiquid() {
        return inLiquid;
    }

    public boolean isOnStairs() {
        return onStairs;
    }

    public boolean isOnSlab() {
        return onSlab;
    }

    /**
     * Calculates horizontal distance moved
     *
     * @return Horizontal distance
     */
    public double getHorizontalDistance() {
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    /**
     * Calculates 3D distance moved
     *
     * @return 3D distance
     */
    public double get3DDistance() {
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    /**
     * Gets time spent in air (in milliseconds)
     *
     * @return Time in air
     */
    public long getTimeInAir() {
        return onGround ? 0 : System.currentTimeMillis() - airTime;
    }

    /**
     * Gets time spent on ground (in milliseconds)
     *
     * @return Time on ground
     */
    public long getTimeOnGround() {
        return !onGround ? 0 : System.currentTimeMillis() - groundTime;
    }
}