package fi.tj88888.quantumAC.listener;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.check.movement.*;
import fi.tj88888.quantumAC.check.movement.fly.FlyA;
import fi.tj88888.quantumAC.check.movement.fly.FlyB;
import fi.tj88888.quantumAC.check.movement.fly.FlyC;
import fi.tj88888.quantumAC.check.packet.TimerA;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;

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

        // Notify checks
        getTimerACheck(player).onPlayerTeleport();
        getSpeedACheck(player).onPlayerTeleport();
        getSpeedBCheck(player).onPlayerTeleport();
        getFlyACheck(player).onPlayerTeleport();
        getFlyBCheck(player).onPlayerTeleport();
        getFlyCCheck(player).onPlayerTeleport();

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

        // Notify checks about the damage (to prevent false positives)
        // Add null checks to prevent NullPointerExceptions
        SpeedA speedA = getSpeedACheck(player);
        if (speedA != null) speedA.onPlayerDamage();
        
        SpeedB speedB = getSpeedBCheck(player);
        if (speedB != null) speedB.onPlayerDamage();
        
        FlyA flyA = getFlyACheck(player);
        if (flyA != null) flyA.onPlayerDamage();
        
        FlyB flyB = getFlyBCheck(player);
        if (flyB != null) flyB.onPlayerDamage();
        
        FlyC flyC = getFlyCCheck(player);
        if (flyC != null) flyC.onPlayerDamage();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Vector velocity = event.getVelocity();

        // Notify checks about the velocity change
        // Add null checks to prevent NullPointerExceptions
        FlyA flyA = getFlyACheck(player);
        if (flyA != null) flyA.onPlayerVelocity(velocity);
        
        FlyB flyB = getFlyBCheck(player);
        if (flyB != null) flyB.onPlayerVelocity(velocity);
        
        FlyC flyC = getFlyCCheck(player);
        if (flyC != null) flyC.onPlayerVelocity(velocity);
        
        SpeedA speedA = getSpeedACheck(player);
        if (speedA != null) speedA.onPlayerVelocity(velocity);
        
        SpeedB speedB = getSpeedBCheck(player);
        if (speedB != null) speedB.onPlayerVelocity(velocity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Notify checks when the player respawns

    }


    // Helper method to get TimerA check for a player
    private TimerA getTimerACheck(Player player) {
        return plugin.getCheckManager().getCheck(player.getUniqueId(), TimerA.class);
    }

    private SpeedA getSpeedACheck(Player player) {
        return plugin.getCheckManager().getCheck(player.getUniqueId(), SpeedA.class);
    }

    private SpeedB getSpeedBCheck(Player player) {
        return plugin.getCheckManager().getCheck(player.getUniqueId(), SpeedB.class);
    }

    // Helper method to get FlyA check for a player
    private FlyA getFlyACheck(Player player) {
        return plugin.getCheckManager().getCheck(player.getUniqueId(), FlyA.class);
    }

    private FlyB getFlyBCheck(Player player) {
        return plugin.getCheckManager().getCheck(player.getUniqueId(), FlyB.class);
    }

    private FlyC getFlyCCheck(Player player) {
        return plugin.getCheckManager().getCheck(player.getUniqueId(), FlyC.class);
    }


}