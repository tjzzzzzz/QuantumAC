package fi.tj88888.quantumAC.util;

/**
 * Class to store and manage player movement data
 * Enhanced with better rotation tracking
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
    private float yaw;             // Current yaw (0-360)
    private float pitch;           // Current pitch (-90 to 90)
    private float lastYaw;         // Last yaw
    private float lastPitch;       // Last pitch
    private float deltaYaw;        // Change in yaw
    private float deltaPitch;      // Change in pitch
    private float rawDeltaYaw;     // Raw change before normalization
    private float[] recentYaws;    // Store recent yaws to detect patterns
    private float[] recentPitches; // Store recent pitches
    private int rotationIndex;     // Index for circular buffer

    // Sensitivity tracking
    private float minYawDelta = Float.MAX_VALUE;  // Minimum non-zero yaw delta
    private float maxYawDelta = 0;                // Maximum yaw delta

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
        this.rawDeltaYaw = 0;

        // Initialize rotation history arrays (store last 20 rotations)
        this.recentYaws = new float[20];
        this.recentPitches = new float[20];
        this.rotationIndex = 0;

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
     * Updates rotation data with improved detection for small changes
     *
     * @param yaw Yaw angle (0-360)
     * @param pitch Pitch angle (-90 to 90)
     */
    public void updateRotation(float yaw, float pitch) {
        // Normalize yaw to 0-360 range
        while (yaw < 0) yaw += 360;
        while (yaw >= 360) yaw -= 360;

        // Clamp pitch to -90 to 90 range
        pitch = Math.max(-90, Math.min(90, pitch));

        // Save previous values
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;

        // Store new values
        this.yaw = yaw;
        this.pitch = pitch;

        // Add to history arrays
        recentYaws[rotationIndex] = yaw;
        recentPitches[rotationIndex] = pitch;
        rotationIndex = (rotationIndex + 1) % recentYaws.length;

        // Calculate raw delta for analysis
        this.rawDeltaYaw = this.yaw - this.lastYaw;

        // Calculate deltas, handling wrap-around for yaw
        // For yaw, we need to handle the wrap-around at 0/360
        if (this.lastYaw != 0) { // Skip first calculation
            float delta = Math.abs(this.yaw - this.lastYaw);

            // Handle wrap-around (e.g. from 359 to 1 degrees)
            if (delta > 180) {
                delta = 360 - delta;
            }

            this.deltaYaw = delta;

            // Update min/max for sensitivity tracking
            if (delta > 0.001f && delta < minYawDelta) {
                minYawDelta = delta;
            }
            if (delta > maxYawDelta) {
                maxYawDelta = delta;
            }
        } else {
            this.deltaYaw = 0;
        }

        // For pitch, it's simpler
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

    public double getLastDeltaXZ() {
        return Math.sqrt(lastDeltaX * lastDeltaX + lastDeltaZ * lastDeltaZ);
    }

    public double getDeltaXZ() {
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
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

    /**
     * Gets raw (unnormalized) yaw delta, useful for detecting direction changes
     * @return Raw yaw change including sign
     */
    public float getRawDeltaYaw() {
        return rawDeltaYaw;
    }

    /**
     * Gets minimum detected non-zero yaw delta (for sensitivity detection)
     * @return Minimum meaningful yaw change
     */
    public float getMinYawDelta() {
        return minYawDelta == Float.MAX_VALUE ? 0 : minYawDelta;
    }

    /**
     * Gets maximum detected yaw delta
     * @return Maximum yaw change
     */
    public float getMaxYawDelta() {
        return maxYawDelta;
    }

    /**
     * Get a historical yaw value
     * @param stepsBack How many rotations back (0 = current)
     * @return The historical yaw value
     */
    public float getHistoricalYaw(int stepsBack) {
        if (stepsBack >= recentYaws.length) {
            stepsBack = recentYaws.length - 1;
        }
        int index = (rotationIndex - 1 - stepsBack + recentYaws.length) % recentYaws.length;
        return recentYaws[index];
    }

    /**
     * Get a historical pitch value
     * @param stepsBack How many rotations back (0 = current)
     * @return The historical pitch value
     */
    public float getHistoricalPitch(int stepsBack) {
        if (stepsBack >= recentPitches.length) {
            stepsBack = recentPitches.length - 1;
        }
        int index = (rotationIndex - 1 - stepsBack + recentPitches.length) % recentPitches.length;
        return recentPitches[index];
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

    /**
     * Detects if there's a pattern of consistent rotation deltas
     * Useful for detecting aimbot/aim assist
     *
     * @return true if suspicious pattern detected
     */
    public boolean hasConsistentRotationPattern() {
        // Need at least 5 rotation samples
        if (rotationIndex < 5) return false;

        int consistentCount = 0;
        float lastDelta = -1;

        // Check for consistent yaw changes
        for (int i = 1; i < Math.min(10, rotationIndex); i++) {
            int idx = (rotationIndex - i + recentYaws.length) % recentYaws.length;
            int prevIdx = (rotationIndex - i - 1 + recentYaws.length) % recentYaws.length;

            float currentYaw = recentYaws[idx];
            float prevYaw = recentYaws[prevIdx];

            float delta = Math.abs(currentYaw - prevYaw);
            if (delta > 180) delta = 360 - delta;

            // If this is our first measurement, store it
            if (lastDelta < 0) {
                lastDelta = delta;
                continue;
            }

            // Check if this delta is very similar to the last one
            if (Math.abs(delta - lastDelta) < 0.01) {
                consistentCount++;
            }

            lastDelta = delta;
        }

        // If we have multiple consistent deltas, flag it
        return consistentCount >= 3;
    }
}