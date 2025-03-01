package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.combat.killaura.components.AttackRateComponent;
import fi.tj88888.quantumAC.check.combat.killaura.components.AttackPatternComponent;
import fi.tj88888.quantumAC.data.PlayerData;

/**
 * KillAuraC - Detects suspicious attack rates and patterns
 * This can indicate auto-clickers, macros, or other combat assistance tools
 */
public class KillAuraC extends KillAuraCheck {

    // Components for attack analysis
    private final AttackRateComponent attackRateComponent;
    private final AttackPatternComponent attackPatternComponent;

    public KillAuraC(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "C");
        this.attackRateComponent = new AttackRateComponent();
        this.attackPatternComponent = new AttackPatternComponent();
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (event == null || event.getPacketType() == null) return;

        // Process the packet using the base handler
        boolean processed = processKillAuraPacket(event);
        if (!processed) return;

        // Get the most recent attack time
        long now = System.currentTimeMillis();
        long attackTime = playerData.getLastAttack();
        
        // Only check if this was an attack packet
        if (attackTime == now) {
            // Check for suspicious attack rate
            String rateViolation = attackRateComponent.checkAttackRate(attackTime);
            if (rateViolation != null) {
                flag(1.0, rateViolation);
                return; // Don't check pattern if rate already flagged
            }
            
            // Check for suspicious attack pattern
            String patternViolation = attackPatternComponent.checkAttackPattern(attackTime);
            if (patternViolation != null) {
                flag(1.0, patternViolation);
            }
        }
    }
    
    public void reset() {
        attackRateComponent.reset();
        attackPatternComponent.reset();
    }
} 