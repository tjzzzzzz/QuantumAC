package fi.tj88888.quantumAC.check;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.combat.*;
import fi.tj88888.quantumAC.check.combat.killaura.*;
import fi.tj88888.quantumAC.check.movement.*;
import fi.tj88888.quantumAC.check.movement.rotation.RotationA;
import fi.tj88888.quantumAC.check.packet.*;
import fi.tj88888.quantumAC.data.PlayerData;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

import java.util.*;

public class CheckManager {

    private final QuantumAC plugin;
    private final List<Class<? extends Check>> checkClasses;
    private final Map<UUID, Set<Check>> activeChecks;

    public CheckManager(QuantumAC plugin) {
        this.plugin = plugin;
        this.checkClasses = new ArrayList<>();
        this.activeChecks = new HashMap<>();

        // Register all checks here
        registerChecks();
    }

    private void registerChecks() {
        // Speed Checks
        //registerCheck(SpeedA.class); // Finished apart from speed pot calculations
        //registerCheck(SpeedB.class);

        // Rotation Checks
        //registerCheck(RotationA.class); // Finished
        // Fly Checks
        //registerCheck(FlyA.class);
        //registerCheck(FlyB.class);
        //registerCheck(FlyC.class);
        // Packet Checks
        //registerCheck(TimerA.class); // Look into false flags
        // Combat Checks

        // KillAura Checks
        registerCheck(KillAuraA.class);
        registerCheck(KillAuraB.class);
        registerCheck(KillAuraC.class);
        registerCheck(KillAuraD.class);
        registerCheck(KillAuraE.class);
        //registerCheck(KillAuraP.class);



    }

    public void registerCheck(Class<? extends Check> checkClass) {
        checkClasses.add(checkClass);
    }

    public void initializeChecks(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);

        if (data == null) {
            return;
        }

        Set<Check> checks = new HashSet<>();

        for (Class<? extends Check> checkClass : checkClasses) {
            try {
                Check check = checkClass.getDeclaredConstructor(QuantumAC.class, PlayerData.class)
                        .newInstance(plugin, data);
                checks.add(check);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize check " + checkClass.getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        activeChecks.put(uuid, checks);
    }

    public void removeChecks(UUID uuid) {
        activeChecks.remove(uuid);
    }

    public Set<Check> getChecks(UUID uuid) {
        return activeChecks.getOrDefault(uuid, new HashSet<>());
    }

    public void processPacket(Player player, PacketEvent event) {
        UUID uuid = player.getUniqueId();
        Set<Check> checks = getChecks(uuid);

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (data == null) {
            plugin.getLogger().severe("PlayerData is null for player: " + player.getName());
            return;
        }
        if (data.getMovementData() == null) {
            plugin.getLogger().severe("MovementData is null for player: " + player.getName());
            return;
        }

        for (Check check : checks) {
            if (check.isEnabled()) {
                check.processPacket(event);
            }
        }
    }

    public void processJoin(Player player) {
        initializeChecks(player);
    }

    public void processQuit(Player player) {
        removeChecks(player.getUniqueId());
    }

    @SuppressWarnings("unchecked")
    public <T extends Check> T getCheck(UUID uuid, Class<T> checkClass) {
        Set<Check> checks = getChecks(uuid); // Fetch all active checks for the player
        for (Check check : checks) {
            if (checkClass.isInstance(check)) {
                return (T) check; // Return the specific check if it matches the specified type
            }
        }
        return null; // Return null if no match is found
    }
}