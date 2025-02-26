package fi.tj88888.quantumAC.listener;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.check.packet.TimerA;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.Set;
import java.util.UUID;

public class ConnectionListener implements Listener {

    private final QuantumAC plugin;

    public ConnectionListener(QuantumAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Create player data
        plugin.getPlayerDataManager().createPlayerData(player);

        // Initialize checks
        plugin.getCheckManager().initializeChecks(player);

        // Call TimerA join method
        getTimerACheck(player).onPlayerJoin();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Remove player data
        plugin.getPlayerDataManager().removePlayerData(uuid);

        // Remove checks
        plugin.getCheckManager().removeChecks(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Notify TimerA check
        getTimerACheck(player).onPlayerTeleport();
        getSpeedACheck(player).onPlayerTeleport();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // Notify TimerA check
        getTimerACheck(player).onWorldChange();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        // Notify SpeedA check about the damage (to prevent false positives)
        getSpeedACheck(player).onPlayerDamage();
    }

    // Helper method to get TimerA check for a player
    private TimerA getTimerACheck(Player player) {
        Set<Check> checks = plugin.getCheckManager().getChecks(player.getUniqueId());
        for (Check check : checks) {
            if (check instanceof TimerA) {
                return (TimerA) check;
            }
        }

        // If check not found, create new instance (shouldn't happen normally)
        return new TimerA(plugin, plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()));
    }

    // Helper method to get SpeedA check for a player
    private fi.tj88888.quantumAC.check.movement.SpeedA getSpeedACheck(Player player) {
        Set<Check> checks = plugin.getCheckManager().getChecks(player.getUniqueId());
        for (Check check : checks) {
            if (check instanceof fi.tj88888.quantumAC.check.movement.SpeedA) {
                return (fi.tj88888.quantumAC.check.movement.SpeedA) check;
            }
        }

        // If check not found, create new instance (shouldn't happen normally)
        return new fi.tj88888.quantumAC.check.movement.SpeedA(plugin,
                plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()));
    }
}