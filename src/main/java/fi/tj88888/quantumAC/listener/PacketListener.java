package fi.tj88888.quantumAC.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PacketListener {

    private final QuantumAC plugin;

    public PacketListener(QuantumAC plugin) {
        this.plugin = plugin;
    }

    public void onPacketReceive(PacketEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player == null) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData == null) return;

        // Update packet timing data
        long now = System.currentTimeMillis();
        PacketType packetType = event.getPacketType();

        // Handle different packet types
        if (packetType == PacketType.Play.Client.FLYING) {
            playerData.setLastFlying(now);
        } else if (packetType == PacketType.Play.Client.POSITION) {
            playerData.setLastPosition(now);
            updatePlayerLocation(player, playerData);
        } else if (packetType == PacketType.Play.Client.POSITION_LOOK) {
            playerData.setLastPositionLook(now);
            updatePlayerLocation(player, playerData);
        } else if (packetType == PacketType.Play.Client.LOOK) {
            playerData.setLastLook(now);
        } else if (packetType == PacketType.Play.Client.USE_ENTITY) {
            EnumWrappers.EntityUseAction action = event.getPacket().getEntityUseActions().read(0);
            if (action == EnumWrappers.EntityUseAction.ATTACK) {
                playerData.setLastAttack(now);
                playerData.setLastAttackedEntity(event.getPacket().getIntegers().read(0));
            }
        }

        // Update ping
        playerData.updatePing(player.getPing());

        // Process packet through all checks
        plugin.getCheckManager().processPacket(player, event);
    }

    private void updatePlayerLocation(Player player, PlayerData playerData) {
        Location location = player.getLocation();
        playerData.setLastLocation(location.clone());

        // Update safe location if player is on ground and not in invalid position
        if (player.isOnGround() && !player.isFlying() && !player.isInsideVehicle()) {
            playerData.setLastSafeLocation(location.clone());
        }
    }
}