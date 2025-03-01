package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.ViolationData;
import fi.tj88888.quantumAC.check.combat.killaura.components.SprintSpeedComponent;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * KillAuraD - Detects players using "keep sprint" cheats that maintain almost full sprint speed when attacking.
 * In vanilla Minecraft, players should slow down by approximately 60% when hitting entities.
 * This check uses proven threshold-based detection to identify cheats that implement very minimal slowdown (0.0001-0.05).
 */
public class KillAuraD extends KillAuraCheck {

    // Component for detecting sprint speed violations
    private final SprintSpeedComponent sprintSpeedComponent;
    
    // Movement tracking
    private double lastSpeed = 0.0;
    private Location lastLocation = null;
    private long lastAttackTime = 0;
    
    public KillAuraD(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAuraD");
        this.sprintSpeedComponent = new SprintSpeedComponent();
    }

    @Override
    public void processPacket(PacketEvent event) {
        try {
            // Call the parent class method to process common KillAura checks
            super.processKillAuraPacket(event);
            
            Player player = event.getPlayer();
            PacketType packetType = event.getPacketType();
            
            // Skip if player is exempt or in invalid state
            if (isExempt(player)) {
                return;
            }
            
            // Handle movement packets to track player speed
            if (isMovementPacket(packetType)) {
                processMovement(player);
            }
            
            // Handle attack packets
            else if (packetType == PacketType.Play.Client.USE_ENTITY) {
                processAttack(player);
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error in KillAuraD for player " + event.getPlayer().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process movement packets to track player speed
     */
    private void processMovement(Player player) {
        // Skip for new players
        long timeSinceJoin = System.currentTimeMillis() - playerData.getJoinTime();
        if (timeSinceJoin < 3000) { // 3 seconds after joining
            return;
        }
        
        Location currentLocation = player.getLocation();
        
        // Skip if location hasn't been initialized
        if (lastLocation == null || !lastLocation.getWorld().equals(currentLocation.getWorld())) {
            lastLocation = currentLocation;
            return;
        }
        
        // Calculate horizontal movement speed
        double dx = currentLocation.getX() - lastLocation.getX();
        double dz = currentLocation.getZ() - lastLocation.getZ();
        double currentSpeed = Math.sqrt(dx * dx + dz * dz);
        
        // Calculate base speed based on player attributes
        double baseSpeed = calculateBaseSpeed(player);
        
        // Get player status
        boolean sprinting = player.isSprinting();
        
        // Calculate tolerance based on ping and potion effects
        double tolerance = calculateTolerance(player);
        
        // Check for keep sprint violations using the component
        ViolationData violationData = sprintSpeedComponent.checkSprintSpeed(
            player, currentSpeed, baseSpeed, sprinting, lastAttackTime, tolerance
        );
        
        // Flag if violation detected
        if (violationData != null) {
            String details = String.format("%s [ping=%d, threshold=%.1f]", 
                violationData.getDetails(), 
                playerData.getPing(),
                sprintSpeedComponent.getThreshold());
                
            flag(player, details, violationData.getViolationLevel());
            onViolation();
        }
        
        // Update last known values
        lastSpeed = currentSpeed;
        lastLocation = currentLocation;
    }
    
    /**
     * Process attack packets to check for keep sprint violations
     */
    private void processAttack(Player player) {
        // Update attack time
        lastAttackTime = System.currentTimeMillis();
    }
    
    /**
     * Calculate player's base speed based on effects and attributes
     */
    private double calculateBaseSpeed(Player player) {
        double baseSpeed = 0.13; // Base walk speed
        
        // Apply sprinting multiplier
        if (player.isSprinting()) {
            baseSpeed *= 1.3; // 30% increase when sprinting
        }
        
        // Apply speed effect
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            baseSpeed *= 1.0 + (0.2 * amplifier); // 20% increase per level
        }
        
        // Apply slowness effect
        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier() + 1;
            baseSpeed *= 1.0 - (0.15 * amplifier); // 15% decrease per level
        }
        
        return baseSpeed;
    }
    
    /**
     * Calculate tolerance based on ping and other factors
     */
    private double calculateTolerance(Player player) {
        // Base tolerance
        double tolerance = 0.01;
        
        // Add tolerance based on ping
        int ping = playerData.getPing();
        if (ping > 0) {
            tolerance += Math.min(0.05, ping / 800.0); // Max 0.05 additional tolerance for high ping
        }
        
        return tolerance;
    }
    
    /**
     * Check if player is in an exempt state
     */
    private boolean isExempt(Player player) {
        return player.isFlying() || 
               player.isInsideVehicle() || 
               player.isDead() || 
               player.getGameMode().name().contains("SPECTATOR") ||
               player.getGameMode().name().contains("CREATIVE");
    }
    
    /**
     * Check if a packet is a movement packet
     */
    protected boolean isMovementPacket(PacketType type) {
        return type == PacketType.Play.Client.POSITION || 
               type == PacketType.Play.Client.POSITION_LOOK || 
               type == PacketType.Play.Client.LOOK ||
               type == PacketType.Play.Client.FLYING;
    }
    
    public void onViolation() {
        // Called when a violation is detected and logged
    }
    
    public void reset() {
        // Reset tracking variables
        lastSpeed = 0.0;
        lastLocation = null;
        lastAttackTime = 0;
        
        // Reset component state
        sprintSpeedComponent.reset();
    }
}