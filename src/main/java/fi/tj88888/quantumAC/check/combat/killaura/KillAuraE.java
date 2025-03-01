package fi.tj88888.quantumAC.check.combat.killaura;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * KillAuraE
 * Detects players who send USE_ENTITY and attack packets while dead
 * Vanilla clients should not be able to attack while dead
 */
public class KillAuraE extends Check {
    // Track if player sent USE_ENTITY recently
    private boolean sentUseEntity = false;

    // Time of last USE_ENTITY packet
    private long lastUseEntityTime = 0;

    // Time window (ms) to consider consecutive packets
    private static final long PACKET_WINDOW = 500;

    public KillAuraE(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAura", "E");
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (event == null || event.getPacketType() == null) return;

        PacketType packetType = event.getPacketType();
        Player player = playerData.getPlayer();

        if (player == null) return;

        long currentTime = System.currentTimeMillis();

        try {
            // Check for USE_ENTITY packet
            if (packetType == PacketType.Play.Client.USE_ENTITY) {
                // Check if player is dead
                if (player.isDead() || player.getHealth() <= 0) {
                    // If player is dead, capture that they sent a USE_ENTITY packet
                    sentUseEntity = true;
                    lastUseEntityTime = currentTime;

                    // Check if the USE_ENTITY is an attack action
                    if (event.getPacket().getEnumEntityUseActions().size() > 0) {
                        EnumWrappers.EntityUseAction action =
                                event.getPacket().getEnumEntityUseActions().read(0).getAction();

                        if (action == EnumWrappers.EntityUseAction.ATTACK) {
                            // This is already an attack, flag immediately
                            flag(1.0, "Dead player attacking entity");
                            sentUseEntity = false; // Reset flag
                        }
                    }
                } else {
                    // Player is alive, reset flag
                    sentUseEntity = false;
                }
            }
            // Check for other attack packets
            else if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
                // Only consider arm animations that happen shortly after a USE_ENTITY
                if (sentUseEntity && (currentTime - lastUseEntityTime) < PACKET_WINDOW) {
                    if (player.isDead() || player.getHealth() <= 0) {
                        // Flag for sending attack sequence while dead
                        flag(1.0, "Dead player sending attack sequence");
                        sentUseEntity = false; // Reset flag
                    }
                }
            }

            // Reset the sentUseEntity flag if too much time has passed
            if (sentUseEntity && (currentTime - lastUseEntityTime) > PACKET_WINDOW) {
                sentUseEntity = false;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error in KillAuraE check: " + e.getMessage());
        }
    }
}