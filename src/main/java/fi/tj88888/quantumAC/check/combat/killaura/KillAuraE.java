package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.ViolationData;
import fi.tj88888.quantumAC.check.combat.killaura.components.DeadPlayerActionComponent;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * KillAuraE - Detects players who send USE_ENTITY and attack packets while dead.
 * In vanilla Minecraft, players should not be able to attack when dead.
 * This has been refactored to use the DeadPlayerActionComponent.
 */
public class KillAuraE extends KillAuraCheck {

    // Component for detecting dead player actions
    private final DeadPlayerActionComponent deadPlayerActionComponent;
    
    public KillAuraE(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAuraE");
        this.deadPlayerActionComponent = new DeadPlayerActionComponent();
    }

    @Override
    public void processPacket(PacketEvent event) {
        try {
            // Call the parent class method to process common KillAura checks
            super.processKillAuraPacket(event);
            
            Player player = event.getPlayer();
            PacketType packetType = event.getPacketType();
            
            // Check if player is dead
            boolean isDead = player.isDead();
            
            // Process USE_ENTITY packets (attacks)
            if (packetType == PacketType.Play.Client.USE_ENTITY) {
                processUseEntityPacket(player, event, isDead);
            }
            
            // Process ARM_ANIMATION packets (swing)
            else if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
                processArmAnimationPacket(player, isDead);
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error in KillAuraE for player " + event.getPlayer().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Process USE_ENTITY packets to check for dead player attacks
     */
    private void processUseEntityPacket(Player player, PacketEvent event, boolean isDead) {
        // Extract attack action from packet
        boolean isAttackAction = false;
        
        if (event.getPacket().getEnumEntityUseActions().size() > 0) {
            EnumWrappers.EntityUseAction action = 
                event.getPacket().getEnumEntityUseActions().read(0).getAction();
                
            isAttackAction = (action == EnumWrappers.EntityUseAction.ATTACK);
        }
        
        // Check for dead player USE_ENTITY violation using the component
        ViolationData violationData = deadPlayerActionComponent.checkDeadUseEntity(
            player, isDead, isAttackAction
        );
        
        // Flag if violation detected
        if (violationData != null) {
            // Use the correct flag method with violation level as a double
            flag((double) violationData.getViolationLevel(), violationData.getDetails());
            onViolation();
        }
    }
    
    /**
     * Process ARM_ANIMATION packets to check for dead player arm swings
     */
    private void processArmAnimationPacket(Player player, boolean isDead) {
        // Get current time
        long currentTime = System.currentTimeMillis();
        
        // Check for dead player ARM_ANIMATION violation using the component
        ViolationData violationData = deadPlayerActionComponent.checkDeadArmAnimation(
            player, isDead, currentTime
        );
        
        // Flag if violation detected
        if (violationData != null) {
            // Use the correct flag method with violation level as a double
            flag((double) violationData.getViolationLevel(), violationData.getDetails());
            onViolation();
        }
    }
    
    public void onViolation() {
        // Called when a violation is detected and logged
    }
    
    public void reset() {
        // Reset component state
        deadPlayerActionComponent.reset();
    }
}