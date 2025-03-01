package fi.tj88888.quantumAC.check.movement.fly.components;

import fi.tj88888.quantumAC.check.ViolationData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Component to detect player phasing/noclipping through blocks.
 * This checks if a player's movement path intersects with solid blocks.
 */
public class PhaseDetectionComponent {

    // Detection constants
    private static final double PHASE_SPEED_THRESHOLD = 0.8; // Speed threshold for phase detection
    private static final int PHASE_BUFFER_THRESHOLD = 7;
    private static final int BUFFER_DECREMENT = 1;
    
    // Material sets for block checking
    private static final Set<Material> NON_SOLID_MATERIALS = new HashSet<>();
    
    static {
        NON_SOLID_MATERIALS.add(Material.AIR);
        NON_SOLID_MATERIALS.add(Material.CAVE_AIR);
        NON_SOLID_MATERIALS.add(Material.VOID_AIR);
        NON_SOLID_MATERIALS.add(Material.WATER);
        NON_SOLID_MATERIALS.add(Material.LAVA);
        // Add other non-solid materials as needed
    }

    // State tracking
    private int phaseBuffer = 0;
    private int phaseVL = 0;
    private int consecutiveDetections = 0;
    private long lastFlag = 0;
    
    /**
     * Checks for phase/noclip violations
     * 
     * @param player The player to check
     * @param from The player's previous location
     * @param to The player's current location
     * @param distance3D The 3D movement distance
     * @param tolerance Additional tolerance to apply to threshold
     * @return ViolationData if a violation was detected, null otherwise
     */
    public ViolationData checkPhasing(Player player, Location from, Location to, 
                                    double distance3D, double tolerance) {
        // Skip if movement is too slow to be phasing
        double adjustedThreshold = PHASE_SPEED_THRESHOLD - tolerance;
        if (distance3D < adjustedThreshold) {
            phaseBuffer = Math.max(0, phaseBuffer - BUFFER_DECREMENT);
            return null;
        }
        
        // Get blocks between the two locations
        List<Block> blocksBetween = getBlocksBetween(from, to);
        
        // Count solid blocks in path
        int solidBlocksInPath = 0;
        for (Block block : blocksBetween) {
            if (!NON_SOLID_MATERIALS.contains(block.getType()) && !block.isPassable()) {
                solidBlocksInPath++;
            }
        }
        
        // Check if the player moved through solid blocks
        if (solidBlocksInPath > 0) {
            phaseBuffer++;
            
            // Only flag if buffer threshold is reached
            if (phaseBuffer >= PHASE_BUFFER_THRESHOLD) {
                // Reset buffer partially after flagging
                phaseBuffer = Math.max(0, phaseBuffer - 2);
                
                // Update tracking variables
                phaseVL++;
                lastFlag = System.currentTimeMillis();
                consecutiveDetections++;
                
                // Create violation data with detailed information
                return new ViolationData(
                    String.format(
                        "phase: speed=%.2f, solid-blocks=%d, distance=%.2f, consecutive=%d",
                        distance3D, solidBlocksInPath, from.distance(to), consecutiveDetections
                    ),
                    phaseVL
                );
            }
        } else {
            // No phasing detected, decrease buffer
            phaseBuffer = Math.max(0, phaseBuffer - BUFFER_DECREMENT);
        }
        
        return null;
    }
    
    /**
     * Get blocks between two locations
     */
    private List<Block> getBlocksBetween(Location from, Location to) {
        List<Block> blocks = new ArrayList<>();
        
        // Validate locations are in same world
        if (from.getWorld() != to.getWorld()) {
            return blocks;
        }
        
        World world = from.getWorld();
        
        // Calculate vector components
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        
        // Calculate total distance
        double distance = from.distance(to);
        
        // Prevent division by zero
        if (distance < 0.01) {
            return blocks;
        }
        
        // Calculate direction vector
        double dirX = dx / distance;
        double dirY = dy / distance;
        double dirZ = dz / distance;
        
        // Sample points along path
        double increment = 0.2; // Sample every 0.2 blocks
        int samples = (int) Math.ceil(distance / increment);
        
        for (int i = 0; i < samples; i++) {
            double progress = i * increment;
            if (progress > distance) {
                progress = distance;
            }
            
            // Calculate point along path
            double x = from.getX() + dirX * progress;
            double y = from.getY() + dirY * progress;
            double z = from.getZ() + dirZ * progress;
            
            // Get block at point
            Block block = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            
            // Add if not already in list
            if (!blocks.contains(block)) {
                blocks.add(block);
            }
        }
        
        return blocks;
    }
    
    /**
     * Resets the phase detection state
     */
    public void reset() {
        phaseBuffer = 0;
        phaseVL = 0;
        consecutiveDetections = 0;
        lastFlag = 0;
    }
} 