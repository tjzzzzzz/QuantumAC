package fi.tj88888.quantumAC.check;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.movement.SpeedA;
import fi.tj88888.quantumAC.check.packet.TimerA;
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
        registerCheck(SpeedA.class);
        registerCheck(TimerA.class);

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
        if (data == null || data.isExempt()) {
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
}