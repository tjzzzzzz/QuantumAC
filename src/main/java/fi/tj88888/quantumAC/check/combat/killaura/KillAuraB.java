package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;


/**
 * KillAuraB
 * Detects if a player sends swing packets too late after attacks
 * Legit clients send arm swing before or very shortly after attacks
 */
public class KillAuraB extends Check {
    private static final long MAX_TIME_BETWEEN_ATTACK_SWING = 100; // ms
    private static final long RESET_VIOLATION_TIME = 10000; // ms

    private long lastAttack = 0;
    private int lateSwingVL = 0;
    private long lastFlag = 0;
    private int consecutiveDetections = 0;

    public KillAuraB(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAura", "B");
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (event == null || event.getPacketType() == null) return;

        long now = System.currentTimeMillis();
        PacketType packetType = event.getPacketType();

        try {
            if (packetType == PacketType.Play.Client.USE_ENTITY &&
                    event.getPacket().getEnumEntityUseActions().size() > 0) {

                EnumWrappers.EntityUseAction action =
                        event.getPacket().getEnumEntityUseActions().read(0).getAction();

                if (action == EnumWrappers.EntityUseAction.ATTACK) {
                    if (event.getPacket().getIntegers().size() > 0) {
                        playerData.setLastAttackedEntity(event.getPacket().getIntegers().read(0));
                    }

                    lastAttack = now;
                    playerData.setLastAttack(now);
                }
            }
            else if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
                playerData.setLastArmAnimation(now);
                checkLateSwing(now);
            }

            if (now - lastFlag > RESET_VIOLATION_TIME) {
                lateSwingVL = Math.max(0, lateSwingVL - 1);
                consecutiveDetections = 0;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error in KillAuraB check: " + e.getMessage());
        }
    }

    /**
     * Checks if the swing packet was sent too late after attack
     */
    private void checkLateSwing(long swingTime) {
        if (lastAttack == 0) return;

        int ping = Math.max(playerData.getAveragePing(), 50);
        long timeDiff = swingTime - lastAttack;
        long maxAllowedTime = MAX_TIME_BETWEEN_ATTACK_SWING + ping;

        if (timeDiff > maxAllowedTime) {
            consecutiveDetections++;

            if (consecutiveDetections >= 2) {
                lateSwingVL++;

                if (lateSwingVL >= 2) {
                    flag(1.0, "Late swing: " + timeDiff + "ms after attack (max allowed: " + maxAllowedTime + "ms)");
                    lastFlag = swingTime;
                    lateSwingVL = 0;
                    consecutiveDetections = 0;
                }
            }
        } else {
            consecutiveDetections = 0;
        }
    }

}