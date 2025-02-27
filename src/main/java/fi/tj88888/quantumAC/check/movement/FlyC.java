package fi.tj88888.quantumAC.check.movement;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.util.MovementData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * FlyC - Specialized in detecting algorithmic flight patterns and trajectories
 *
 * This check focuses on:
 * 1. Detecting unnatural flight trajectories and patterns
 * 2. Finding mathematical regularities in movement (step function, sine wave, etc.)
 * 3. Analyzing 3D movement consistency over time
 * 4. Detecting "phase" fly hacks (moving through blocks)
 */
public class FlyC extends Check {

    // Pattern detection constants
    private static final int PATTERN_BUFFER_THRESHOLD = 10;
    private static final int PHASE_BUFFER_THRESHOLD = 7;
    private static final int ARC_BUFFER_THRESHOLD = 8;
    private static final int BUFFER_DECREMENT = 1;

    // Pattern analysis settings
    private static final int MIN_TRAJECTORY_POINTS = 10;
    private static final int MAX_TRAJECTORY_POINTS = 40;
    private static final double STEP_PATTERN_THRESHOLD = 0.002; // Max variance for step pattern
    private static final double SINE_PATTERN_THRESHOLD = 0.05; // Max variance from sine curve
    private static final double REGULARITY_THRESHOLD = 0.98; // Regularity score threshold (0-1)
    private static final double PHASE_SPEED_THRESHOLD = 0.8; // Speed threshold for phase detection

    // Exemption times
    private static final long TELEPORT_EXEMPT_TIME = 3000; // 3 seconds
    private static final long DAMAGE_EXEMPT_TIME = 1500; // 1.5 seconds
    private static final long VELOCITY_EXEMPT_TIME = 2000; // 2 seconds

    // Pattern detection buffers
    private int algorithmicPatternBuffer = 0;
    private int phaseBuffer = 0;
    private int arcTrajectoryBuffer = 0;

    // Movement tracking
    private final List<TrajectoryPoint> trajectoryPoints = new ArrayList<>();
    private double lastPatternMatchScore = 0.0;
    private long lastPatternDetectionTime = 0;
    private int consecutivePatternMatches = 0;

    // State tracking
    private boolean wasOnGround = true;
    private boolean wasInLiquid = false;
    private boolean wasOnClimbable = false;
    private boolean wasInWeb = false;
    private int airTicks = 0;

    // Special case timers
    private long lastTeleportTime = 0;
    private long lastDamageTime = 0;
    private long lastVelocityTime = 0;

    // Material sets for environment checking
    private static final Set<Material> LIQUID_MATERIALS = new HashSet<>();
    private static final Set<Material> CLIMBABLE_MATERIALS = new HashSet<>();
    private static final Set<Material> NON_SOLID_MATERIALS = new HashSet<>();

    static {
        // Setup liquid materials
        LIQUID_MATERIALS.add(Material.WATER);
        LIQUID_MATERIALS.add(Material.LAVA);

        // Setup climbable materials
        CLIMBABLE_MATERIALS.add(Material.LADDER);
        CLIMBABLE_MATERIALS.add(Material.VINE);
        CLIMBABLE_MATERIALS.add(Material.SCAFFOLDING);
        CLIMBABLE_MATERIALS.add(Material.TWISTING_VINES);
        CLIMBABLE_MATERIALS.add(Material.WEEPING_VINES);

        // Setup non-solid materials
        NON_SOLID_MATERIALS.add(Material.AIR);
        NON_SOLID_MATERIALS.add(Material.CAVE_AIR);
        NON_SOLID_MATERIALS.add(Material.VOID_AIR);
        NON_SOLID_MATERIALS.add(Material.WATER);
        NON_SOLID_MATERIALS.add(Material.LAVA);
    }

    public FlyC(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "FlyC", "Movement");
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (!isMovementPacket(event.getPacketType())) {
            return;
        }

        Player player = event.getPlayer();

        // Skip if player is exempt from checks
        if (isExempt(player)) {
            resetDetectionState();
            return;
        }

        // Get locations for movement calculation
        Location from = playerData.getLastLocation();
        Location to = player.getLocation();

        if (from == null || !from.getWorld().equals(to.getWorld())) {
            playerData.setLastLocation(to);
            return;
        }

        // Calculate movement
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double distance3D = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Analyze environment
        boolean onGround = player.isOnGround();
        boolean inLiquid = isInLiquid(player);
        boolean onClimbable = isOnClimbable(player);
        boolean inWeb = isInWeb(player);
        boolean hasLevitation = player.hasPotionEffect(PotionEffectType.LEVITATION);
        boolean hasSlowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
        boolean nearGround = isNearGround(player);
        boolean nearCeiling = isNearCeiling(player);

        // Update air time tracking
        if (onGround) {
            airTicks = 0;
        } else {
            airTicks++;
        }

        // Skip checks for exempt conditions
        if (isRecentlyTeleported() ||
                isRecentlyDamaged() ||
                isRecentlyVelocity() ||
                inLiquid ||
                onClimbable ||
                inWeb ||
                hasLevitation) {

            updateState(to, onGround, inLiquid, onClimbable, inWeb);
            return;
        }

        // Add trajectory point
        if (!onGround && airTicks > 3) {
            addTrajectoryPoint(to, dx, dy, dz, horizontalDistance, distance3D, System.currentTimeMillis());
        } else if (onGround) {
            // Clear trajectory on ground touch
            trajectoryPoints.clear();
        }

        // Only run pattern analysis with enough trajectory points
        if (trajectoryPoints.size() >= MIN_TRAJECTORY_POINTS && airTicks > 5) {
            // Analyze movement patterns
            detectAlgorithmicPattern(player);

            // Detect arc trajectory violations
            detectArcTrajectoryViolations(player);
        }

        // Phase detection (moving through blocks)
        if (distance3D > PHASE_SPEED_THRESHOLD) {
            detectPhasing(player, from, to, distance3D);
        }

        // Update state for next check
        updateState(to, onGround, inLiquid, onClimbable, inWeb);
    }

    /**
     * Add a new trajectory point
     */
    private void addTrajectoryPoint(Location location, double dx, double dy, double dz,
                                    double horizontalDistance, double distance3D, long timestamp) {

        TrajectoryPoint point = new TrajectoryPoint(
                location.getX(), location.getY(), location.getZ(),
                dx, dy, dz, horizontalDistance, distance3D, timestamp
        );

        trajectoryPoints.add(point);

        // Maintain maximum size
        if (trajectoryPoints.size() > MAX_TRAJECTORY_POINTS) {
            trajectoryPoints.remove(0);
        }
    }

    /**
     * Detect algorithmic flying patterns
     */
    private void detectAlgorithmicPattern(Player player) {
        // Skip if not enough points
        if (trajectoryPoints.size() < MIN_TRAJECTORY_POINTS) {
            return;
        }

        // Get patterns to check
        double stepPatternScore = detectStepPattern();
        double sinePatternScore = detectSinePattern();
        double regularityScore = calculateRegularityScore();

        // Get the highest pattern match score
        double highestScore = Math.max(Math.max(stepPatternScore, sinePatternScore), regularityScore);
        lastPatternMatchScore = highestScore;

        // If we found a consistent pattern
        if (highestScore > REGULARITY_THRESHOLD) {
            // Determine which pattern type matched
            String patternType = "unknown";
            if (stepPatternScore > REGULARITY_THRESHOLD) {
                patternType = "step";
            } else if (sinePatternScore > REGULARITY_THRESHOLD) {
                patternType = "sine";
            } else if (regularityScore > REGULARITY_THRESHOLD) {
                patternType = "regular";
            }

            // Check for consecutive pattern matches
            long timeSinceLastDetection = System.currentTimeMillis() - lastPatternDetectionTime;
            if (timeSinceLastDetection < 2000) {
                consecutivePatternMatches++;
            } else {
                consecutivePatternMatches = 1;
            }

            lastPatternDetectionTime = System.currentTimeMillis();

            // High pattern match score is suspicious
            String details = String.format(
                    "algorithmic-pattern: type=%s, score=%.3f, consecutive=%d, air-ticks=%d",
                    patternType, highestScore, consecutivePatternMatches, airTicks
            );

            // Increase buffer based on consistency of pattern detection
            algorithmicPatternBuffer += Math.min(3, consecutivePatternMatches);

            if (algorithmicPatternBuffer >= PATTERN_BUFFER_THRESHOLD) {
                flag(1.0, details);
                algorithmicPatternBuffer = Math.max(0, algorithmicPatternBuffer - 4);
            }
        } else {
            // Decay buffer slowly
            algorithmicPatternBuffer = Math.max(0, algorithmicPatternBuffer - BUFFER_DECREMENT);

            // Reset consecutive matches
            if (System.currentTimeMillis() - lastPatternDetectionTime > 3000) {
                consecutivePatternMatches = 0;
            }
        }
    }

    /**
     * Detect violations of natural arc trajectories
     */
    private void detectArcTrajectoryViolations(Player player) {
        if (trajectoryPoints.size() < MIN_TRAJECTORY_POINTS) {
            return;
        }

        // In Minecraft, natural trajectory should be parabolic
        // Detect non-parabolic motion

        // Extract points for quadratic regression
        double[] x = new double[trajectoryPoints.size()];
        double[] y = new double[trajectoryPoints.size()];

        for (int i = 0; i < trajectoryPoints.size(); i++) {
            TrajectoryPoint p = trajectoryPoints.get(i);
            x[i] = i; // Use index as x-value for time progression
            y[i] = p.y; // y-coordinate
        }

        // Calculate parabolic fit using quadratic regression
        double[] coefficients = quadraticRegression(x, y);

        // If the coefficient is too small, this isn't a parabola
        if (Math.abs(coefficients[2]) < 0.0001) {
            String details = String.format(
                    "non-parabolic-motion: a=%.5f, points=%d, air-ticks=%d",
                    coefficients[2], trajectoryPoints.size(), airTicks
            );

            arcTrajectoryBuffer += 2;

            if (arcTrajectoryBuffer >= ARC_BUFFER_THRESHOLD) {
                flag(1.0, details);
                arcTrajectoryBuffer = Math.max(0, arcTrajectoryBuffer - 3);
            }
            return;
        }

        // Calculate mean squared error of the fit
        double mse = 0;
        for (int i = 0; i < x.length; i++) {
            double predicted = coefficients[0] + coefficients[1] * x[i] + coefficients[2] * x[i] * x[i];
            double error = y[i] - predicted;
            mse += error * error;
        }
        mse /= x.length;

        // Check if the MSE is too high (poor fit to parabola)
        if (mse > 0.05 && airTicks > 15) {
            String details = String.format(
                    "non-parabolic-fit: mse=%.5f, points=%d, air-ticks=%d",
                    mse, trajectoryPoints.size(), airTicks
            );

            arcTrajectoryBuffer += 2;

            if (arcTrajectoryBuffer >= ARC_BUFFER_THRESHOLD) {
                flag(1.0, details);
                arcTrajectoryBuffer = Math.max(0, arcTrajectoryBuffer - 3);
            }
        } else {
            // Decay buffer
            arcTrajectoryBuffer = Math.max(0, arcTrajectoryBuffer - BUFFER_DECREMENT);
        }
    }

    /**
     * Detect phasing through blocks
     */
    private void detectPhasing(Player player, Location from, Location to, double distance3D) {
        // Skip if recently teleported
        if (isRecentlyTeleported()) {
            return;
        }

        // Check if player moved through solid blocks
        List<Block> blocksBetween = getBlocksBetween(from, to);

        int solidBlockCount = 0;
        for (Block block : blocksBetween) {
            if (block.getType().isSolid() && !NON_SOLID_MATERIALS.contains(block.getType())) {
                solidBlockCount++;
            }
        }

        if (solidBlockCount > 0) {
            String details = String.format(
                    "phase-violation: solid-blocks=%d, distance=%.2f",
                    solidBlockCount, distance3D
            );

            phaseBuffer += Math.min(solidBlockCount, 3);

            if (phaseBuffer >= PHASE_BUFFER_THRESHOLD) {
                flag(1.0, details);
                phaseBuffer = Math.max(0, phaseBuffer - 2);
            }
        } else {
            // Decay buffer
            phaseBuffer = Math.max(0, phaseBuffer - BUFFER_DECREMENT);
        }
    }

    /**
     * Get blocks between two locations
     */
    private List<Block> getBlocksBetween(Location from, Location to) {
        List<Block> blocks = new ArrayList<>();

        // Get world
        World world = from.getWorld();

        // Create vector between points
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        direction.normalize();

        // Raytrace with small steps
        double step = 0.2;
        for (double d = 0; d <= length; d += step) {
            Vector position = from.toVector().add(direction.clone().multiply(d));
            Block block = world.getBlockAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());

            // Only add each block once
            if (!blocks.contains(block)) {
                blocks.add(block);
            }
        }

        return blocks;
    }

    /**
     * Detect step pattern (constant y-values)
     */
    private double detectStepPattern() {
        // Extract y-values
        List<Double> yValues = new ArrayList<>();
        for (TrajectoryPoint point : trajectoryPoints) {
            yValues.add(point.y);
        }

        // Calculate step pattern score (how flat the y-values are)
        double mean = calculateMean(yValues);
        double variance = calculateVariance(yValues, mean);

        // Perfect step pattern would have zero variance
        if (variance < STEP_PATTERN_THRESHOLD) {
            return 1.0;
        } else {
            return Math.max(0, 1.0 - (variance / STEP_PATTERN_THRESHOLD));
        }
    }

    /**
     * Detect sine pattern (smooth oscillation in height)
     */
    private double detectSinePattern() {
        int n = trajectoryPoints.size();
        if (n < 10) return 0.0;

        List<Double> yValues = new ArrayList<>();
        for (TrajectoryPoint point : trajectoryPoints) {
            yValues.add(point.y);
        }

        // Normalize values
        double min = Collections.min(yValues);
        double max = Collections.max(yValues);
        if (max - min < 0.1) return 0.0; // Not enough variation

        List<Double> normalizedY = new ArrayList<>();
        for (double y : yValues) {
            normalizedY.add((y - min) / (max - min));
        }

        // Try different frequencies
        double bestScore = 0.0;
        for (double freq = 1.0; freq <= 5.0; freq += 0.5) {
            double score = calculateSineMatchScore(normalizedY, freq);
            if (score > bestScore) {
                bestScore = score;
            }
        }

        return bestScore;
    }

    /**
     * Calculate how well a set of points matches a sine wave
     */
    private double calculateSineMatchScore(List<Double> values, double frequency) {
        int n = values.size();
        double error = 0.0;

        for (int i = 0; i < n; i++) {
            double x = (double) i / n * 2 * Math.PI * frequency;
            double expectedY = (Math.sin(x) + 1) / 2; // Normalized sine between 0 and 1
            error += Math.pow(values.get(i) - expectedY, 2);
        }

        double mse = error / n;
        if (mse > SINE_PATTERN_THRESHOLD) {
            return 0.0;
        } else {
            return 1.0 - (mse / SINE_PATTERN_THRESHOLD);
        }
    }

    /**
     * Calculate regularity score (consistency in movement)
     */
    private double calculateRegularityScore() {
        if (trajectoryPoints.size() < 10) {
            return 0.0;
        }

        // Calculate consistency in horizontal and vertical movement
        List<Double> horizontalDistances = new ArrayList<>();
        List<Double> verticalDistances = new ArrayList<>();
        List<Long> timeDiffs = new ArrayList<>();

        for (int i = 1; i < trajectoryPoints.size(); i++) {
            TrajectoryPoint current = trajectoryPoints.get(i);
            TrajectoryPoint prev = trajectoryPoints.get(i - 1);

            horizontalDistances.add(current.horizontalDistance);
            verticalDistances.add(current.dy);
            timeDiffs.add(current.timestamp - prev.timestamp);
        }

        // Calculate coefficient of variation (lower is more regular)
        // Use the renamed methods
        double cvHorizontal = calculateDoubleCV(horizontalDistances);
        double cvVertical = calculateDoubleCV(verticalDistances);
        double cvTime = calculateLongCV(timeDiffs);

        // Very consistent time intervals are suspicious
        double timeRegularity = 1.0 - Math.min(1.0, cvTime / 0.1);

        // Consistent horizontal movement with consistent timing is suspicious
        double movementRegularity = 1.0 - Math.min(1.0, cvHorizontal / 0.2);

        // Combine scores (weighted)
        return (timeRegularity * 0.4) + (movementRegularity * 0.6);
    }

    /**
     * Calculate coefficient of variation (standard deviation / mean)
     */
    private double calculateDoubleCV(List<Double> values) {
        double mean = calculateMean(values);
        if (mean == 0) return Double.MAX_VALUE;

        double variance = calculateVariance(values, mean);
        return Math.sqrt(variance) / mean;
    }

    /**
     * Calculate coefficient of variation for Long values
     */
    private double calculateLongCV(List<Long> values) {
        List<Double> doubleValues = new ArrayList<>();
        for (Long val : values) {
            doubleValues.add((double) val);
        }
        return calculateDoubleCV(doubleValues);
    }

    /**
     * Perform quadratic regression (fit a parabola)
     */
    private double[] quadraticRegression(double[] x, double[] y) {
        int n = x.length;

        // Calculate sums for the system of equations
        double sumX = 0, sumX2 = 0, sumX3 = 0, sumX4 = 0, sumY = 0, sumXY = 0, sumX2Y = 0;

        for (int i = 0; i < n; i++) {
            double xi = x[i];
            double yi = y[i];
            double xi2 = xi * xi;

            sumX += xi;
            sumX2 += xi2;
            sumX3 += xi2 * xi;
            sumX4 += xi2 * xi2;
            sumY += yi;
            sumXY += xi * yi;
            sumX2Y += xi2 * yi;
        }

        // System of equations: a0*n + a1*sumX + a2*sumX2 = sumY
        //                     a0*sumX + a1*sumX2 + a2*sumX3 = sumXY
        //                     a0*sumX2 + a1*sumX3 + a2*sumX4 = sumX2Y

        // Cramers rule to solve the system
        double d = n * (sumX2 * sumX4 - sumX3 * sumX3) -
                sumX * (sumX * sumX4 - sumX3 * sumX2) +
                sumX2 * (sumX * sumX3 - sumX2 * sumX2);

        double d1 = sumY * (sumX2 * sumX4 - sumX3 * sumX3) -
                sumX * (sumXY * sumX4 - sumX3 * sumX2Y) +
                sumX2 * (sumXY * sumX3 - sumX2 * sumX2Y);

        double d2 = n * (sumXY * sumX4 - sumX3 * sumX2Y) -
                sumY * (sumX * sumX4 - sumX3 * sumX2) +
                sumX2 * (sumX * sumX2Y - sumX2 * sumXY);

        double d3 = n * (sumX2 * sumX2Y - sumXY * sumX3) -
                sumX * (sumX * sumX2Y - sumXY * sumX2) +
                sumY * (sumX * sumX3 - sumX2 * sumX2);

        // Coefficients
        double a = d3 / d; // Quadratic coefficient
        double b = d2 / d; // Linear coefficient
        double c = d1 / d; // Constant term

        return new double[] {c, b, a};
    }

    /**
     * Calculate mean of a list of values
     */
    private double calculateMean(List<Double> values) {
        double sum = 0;
        for (double val : values) {
            sum += val;
        }
        return sum / values.size();
    }

    /**
     * Calculate variance of a list of values
     */
    private double calculateVariance(List<Double> values, double mean) {
        double sum = 0;
        for (double val : values) {
            sum += Math.pow(val - mean, 2);
        }
        return sum / values.size();
    }

    /**
     * Update player state
     */
    private void updateState(Location location, boolean onGround, boolean inLiquid,
                             boolean onClimbable, boolean inWeb) {
        wasOnGround = onGround;
        wasInLiquid = inLiquid;
        wasOnClimbable = onClimbable;
        wasInWeb = inWeb;
        playerData.setLastLocation(location);
    }

    /**
     * Reset detection state when player becomes exempt
     */
    private void resetDetectionState() {
        algorithmicPatternBuffer = 0;
        phaseBuffer = 0;
        arcTrajectoryBuffer = 0;
        airTicks = 0;
        trajectoryPoints.clear();
        consecutivePatternMatches = 0;
    }

    /**
     * Check if player is exempt from fly checks
     */
    private boolean isExempt(Player player) {
        return player.isFlying() ||
                player.getAllowFlight() ||
                player.getGameMode() == GameMode.CREATIVE ||
                player.getGameMode() == GameMode.SPECTATOR ||
                player.getVehicle() != null ||
                player.isGliding() || // Has elytra deployed
                player.isRiptiding() || // Using trident with riptide
                playerData.isExempt();
    }

    /**
     * Check if a packet is a movement packet
     */
    private boolean isMovementPacket(PacketType type) {
        return type == PacketType.Play.Client.POSITION ||
                type == PacketType.Play.Client.POSITION_LOOK ||
                type == PacketType.Play.Client.LOOK ||
                type == PacketType.Play.Client.FLYING;
    }

    /**
     * Check if player is in a liquid
     */
    private boolean isInLiquid(Player player) {
        Material material = player.getLocation().getBlock().getType();
        return LIQUID_MATERIALS.contains(material);
    }

    /**
     * Check if player is on a climbable block
     */
    private boolean isOnClimbable(Player player) {
        Material material = player.getLocation().getBlock().getType();
        return CLIMBABLE_MATERIALS.contains(material);
    }

    /**
     * Check if player is in a web
     */
    private boolean isInWeb(Player player) {
        return player.getLocation().getBlock().getType() == Material.COBWEB;
    }

    /**
     * Check if player is near ground
     */
    private boolean isNearGround(Player player) {
        Location loc = player.getLocation();
        double feetY = loc.getY();
        World world = loc.getWorld();

        // Check within a small radius below the player
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                for (double y = 0; y <= 2; y += 0.5) {
                    Block block = world.getBlockAt(
                            (int) Math.floor(loc.getX() + x),
                            (int) Math.floor(feetY - y),
                            (int) Math.floor(loc.getZ() + z)
                    );

                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if player is near ceiling
     */
    private boolean isNearCeiling(Player player) {
        Location loc = player.getLocation();
        double headY = loc.getY() + 1.8; // Player height
        World world = loc.getWorld();

        // Check within a small radius above the player
        for (double x = -0.3; x <= 0.3; x += 0.3) {
            for (double z = -0.3; z <= 0.3; z += 0.3) {
                for (double y = 0; y <= 2; y += 0.5) {
                    Block block = world.getBlockAt(
                            (int) Math.floor(loc.getX() + x),
                            (int) Math.floor(headY + y),
                            (int) Math.floor(loc.getZ() + z)
                    );

                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if player was recently teleported
     */
    private boolean isRecentlyTeleported() {
        return System.currentTimeMillis() - lastTeleportTime < TELEPORT_EXEMPT_TIME;
    }

    /**
     * Check if player was recently damaged
     */
    private boolean isRecentlyDamaged() {
        return System.currentTimeMillis() - lastDamageTime < DAMAGE_EXEMPT_TIME;
    }

    /**
     * Check if player recently received velocity
     */
    private boolean isRecentlyVelocity() {
        return System.currentTimeMillis() - lastVelocityTime < VELOCITY_EXEMPT_TIME;
    }

    /**
     * Called when player teleports
     */
    public void onPlayerTeleport() {
        lastTeleportTime = System.currentTimeMillis();
        resetDetectionState();
    }

    /**
     * Called when player takes damage
     */
    public void onPlayerDamage() {
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Called when player receives velocity
     */
    public void onPlayerVelocity(Vector velocity) {
        lastVelocityTime = System.currentTimeMillis();
    }

    /**
     * Private class to store trajectory points
     */
    private static class TrajectoryPoint {
        public final double x, y, z;
        public final double dx, dy, dz;
        public final double horizontalDistance;
        public final double distance3D;
        public final long timestamp;

        public TrajectoryPoint(double x, double y, double z,
                               double dx, double dy, double dz,
                               double horizontalDistance, double distance3D,
                               long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.horizontalDistance = horizontalDistance;
            this.distance3D = distance3D;
            this.timestamp = timestamp;
        }
    }
}