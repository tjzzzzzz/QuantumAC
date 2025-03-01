package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;

/**
 * KillAuraA - Detects if a player attacks too long after swinging their arm
 * This can indicate modified client attack timing or packet manipulation
 */
public class KillAuraA extends Check {
    // Detection constants
    private static final long MAX_TIME_BETWEEN_SWING_ATTACK = 350; // ms
    private static final long RESET_VIOLATION_TIME = 10000; // ms

    // State tracking
    private long lastArmAnimation = 0;
    private int lateAttackVL = 0;
    private long lastFlag = 0;
    private int consecutiveDetections = 0;

    public KillAuraA(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAura", "A");
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (event == null || event.getPacketType() == null) return;

        long now = System.currentTimeMillis();
        PacketType packetType = event.getPacketType();

        try {
            // Handle arm animation packets
            if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
                lastArmAnimation = now;
                playerData.setLastArmAnimation(now);
            }
            // Handle USE_ENTITY (attack) packets
            else if (packetType == PacketType.Play.Client.USE_ENTITY &&
                    event.getPacket().getEnumEntityUseActions().size() > 0) {

                EnumWrappers.EntityUseAction action =
                        event.getPacket().getEnumEntityUseActions().read(0).getAction();

                // Only process attack actions
                if (action == EnumWrappers.EntityUseAction.ATTACK) {
                    if (event.getPacket().getIntegers().size() > 0) {
                        playerData.setLastAttackedEntity(event.getPacket().getIntegers().read(0));
                    }

                    playerData.setLastAttack(now);
                    checkLateAttack(now);
                }
            }

            // Reset violations after time
            if (now - lastFlag > RESET_VIOLATION_TIME) {
                lateAttackVL = Math.max(0, lateAttackVL - 1);
                consecutiveDetections = 0;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error in KillAuraA check: " + e.getMessage());
        }
    }

    /**
     * Checks if the attack packet was sent too late after arm animation
     */
    private void checkLateAttack(long attackTime) {
        // Get the most recent arm animation time
        long mostRecentArmAnim = Math.max(lastArmAnimation, playerData.getLastArmAnimation());

        // Skip check if no arm animation was detected yet
        if (mostRecentArmAnim == 0) return;

        // Calculate time difference with ping compensation
        int ping = Math.max(playerData.getAveragePing(), 100);
        long timeDiff = attackTime - mostRecentArmAnim;
        long maxAllowedTime = MAX_TIME_BETWEEN_SWING_ATTACK + ping;

        // Check if the attack was sent too late
        if (timeDiff > maxAllowedTime) {
            consecutiveDetections++;

            // Require multiple consecutive detections before flagging
            if (consecutiveDetections >= 3) {
                lateAttackVL++;

                if (lateAttackVL >= 3) {
                    flag(1.0, "Late attack: " + timeDiff + "ms after swing (max allowed: " + maxAllowedTime + "ms)");
                    lastFlag = attackTime;
                    lateAttackVL = 0;
                    consecutiveDetections = 0;
                }
            }
        } else {
            // Valid pattern, reset consecutive detection
            consecutiveDetections = 0;
        }
    }
}