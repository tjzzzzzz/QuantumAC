package fi.tj88888.quantumAC.check.combat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KillAuraP - Simple Packet Order Violation Check
 *
 * Detects KillAura by checking for incorrect packet sequences
 */
public class KillAuraP extends Check {
    // Concurrent tracking to ensure thread safety
    private final Map<UUID, PacketSequence> playerPacketSequences = new ConcurrentHashMap<>();

    public KillAuraP(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "KillAuraP", "PacketOrder");
    }

    @Override
    public void processPacket(PacketEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PacketType packetType = event.getPacketType();

        // Ensure we have a tracker for this player
        PacketSequence sequence = playerPacketSequences.computeIfAbsent(
                playerId, k -> new PacketSequence()
        );

        // Track packet types
        if (packetType == PacketType.Play.Client.LOOK ||
                packetType == PacketType.Play.Client.POSITION_LOOK) {
            sequence.recordRotationPacket();
        }

        // Check for attack packet
        if (packetType == PacketType.Play.Client.USE_ENTITY) {
            // Verify it's an attack packet
            if (event.getPacket().getEnumEntityUseActions().read(0)
                    .getAction() == EnumWrappers.EntityUseAction.ATTACK) {

                // Check for packet order violation
                if (sequence.checkAttackPacketOrder()) {
                    // Flag for incorrect packet sequence
                    flag(0.8, "Impossible attack packet sequence detected");
                }
            }
        }
    }

    /**
     * Internal class to track packet sequence for a player
     */
    private static class PacketSequence {
        // Packet sequence states
        private boolean hadRotationPacket = false;
        private boolean hadAttackPacket = false;
        private long lastRotationTime = 0;
        private long lastAttackTime = 0;

        /**
         * Record a rotation packet
         */
        public void recordRotationPacket() {
            hadRotationPacket = true;
            lastRotationTime = System.currentTimeMillis();
        }

        /**
         * Check if attack packet order is valid
         * @return true if packet order is suspicious
         */
        public boolean checkAttackPacketOrder() {
            long currentTime = System.currentTimeMillis();

            // Suspicious scenario 1: Attack before any rotation
            if (!hadRotationPacket) {
                return true;
            }

            // Suspicious scenario 2: Multiple attacks without intermediate rotation
            if (hadAttackPacket && currentTime - lastAttackTime < 50) {
                return true;
            }

            // Suspicious scenario 3: Attack sent before rotation
            if (lastAttackTime > lastRotationTime) {
                return true;
            }

            // Update attack state
            hadAttackPacket = true;
            lastAttackTime = currentTime;

            return false;
        }
    }
}