package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;

/**
 * KillAuraC
 * Detects players attacking while blocking (AutoBlock)
 * This behavior is impossible in vanilla Minecraft
 */
public class KillAuraC extends Check {

    private boolean blocking = false;
    private boolean releasingBlock = false;

    private int autoBlockVL = 0;
    private static final int VL_THRESHOLD = 3;

    private long lastReset = 0;
    private static final long RESET_COOLDOWN = 5000; // 5 seconds

    public KillAuraC(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAura", "C");
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (event == null || event.getPacketType() == null) return;

        long now = System.currentTimeMillis();
        PacketType packetType = event.getPacketType();

        try {
            if (isMovementPacket(packetType)) {
                blocking = false;
                releasingBlock = false;
            }

            else if (packetType == PacketType.Play.Client.BLOCK_PLACE) {
                blocking = true;
            }

            else if (packetType == PacketType.Play.Client.BLOCK_DIG) {
                EnumWrappers.PlayerDigType digType = null;

                try {
                    if (event.getPacket().getPlayerDigTypes().size() > 0) {
                        digType = event.getPacket().getPlayerDigTypes().read(0);
                    }
                } catch (Exception ignored) {}

                if (digType == EnumWrappers.PlayerDigType.RELEASE_USE_ITEM) {
                    releasingBlock = true;
                }
            }

            else if (packetType == PacketType.Play.Client.USE_ENTITY &&
                    event.getPacket().getEnumEntityUseActions().size() > 0) {

                EnumWrappers.EntityUseAction action =
                        event.getPacket().getEnumEntityUseActions().read(0).getAction();

                if (action == EnumWrappers.EntityUseAction.ATTACK) {
                    long timeSinceJoin = System.currentTimeMillis() - playerData.getJoinTime();
                    if (timeSinceJoin < 3000) { // 3000ms = ~60 ticks
                        return;
                    }

                    if (blocking || releasingBlock) {
                        autoBlockVL++;

                        if (autoBlockVL >= VL_THRESHOLD) {
                            flag(1.0, "AutoBlocking detected (Block: " + blocking +
                                    ", Release: " + releasingBlock + ")");
                            autoBlockVL = 0;
                        }
                    }
                }
            }

            // Reset violation level periodically
            if (now - lastReset > RESET_COOLDOWN) {
                autoBlockVL = Math.max(0, autoBlockVL - 1);
                lastReset = now;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error in KillAuraC check: " + e.getMessage());
        }
    }

    private boolean isMovementPacket(PacketType packetType) {
        return packetType == PacketType.Play.Client.POSITION ||
                packetType == PacketType.Play.Client.LOOK ||
                packetType == PacketType.Play.Client.POSITION_LOOK ||
                packetType == PacketType.Play.Client.FLYING;
    }
}