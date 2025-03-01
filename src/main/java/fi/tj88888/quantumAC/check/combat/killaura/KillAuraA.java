package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.combat.killaura.components.LateAttackComponent;
import fi.tj88888.quantumAC.data.PlayerData;

/**
 * KillAuraA - Detects if a player attacks too long after swinging their arm
 * This can indicate modified client attack timing or packet manipulation
 */
public class KillAuraA extends KillAuraCheck {

    // Component for late attack detection
    private final LateAttackComponent lateAttackComponent;

    public KillAuraA(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "A");
        this.lateAttackComponent = new LateAttackComponent();
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (event == null || event.getPacketType() == null) return;

        // Process the packet using the base handler
        boolean processed = processKillAuraPacket(event);
        if (!processed) return;

        // Get the most recent arm animation and attack times
        long now = System.currentTimeMillis();
        long attackTime = playerData.getLastAttack();
        long armAnimTime = playerData.getLastArmAnimation();
        
        // Skip if we haven't seen both events yet
        if (attackTime == 0 || armAnimTime == 0) return;
        
        // Only check if this was an attack packet
        if (attackTime == now) {
            // Check for late attack using the component
            String violation = lateAttackComponent.checkLateAttack(
                attackTime, armAnimTime, playerData.getAveragePing());
                
            if (violation != null) {
                flag(1.0, violation);
            }
        }
    }
}