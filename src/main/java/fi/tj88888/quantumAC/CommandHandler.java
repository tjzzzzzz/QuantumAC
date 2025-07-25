package fi.tj88888.quantumAC;

import fi.tj88888.quantumAC.check.movement.rotation.RotationA;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.log.ViolationLog;
import fi.tj88888.quantumAC.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles plugin commands
 */
public class CommandHandler implements CommandExecutor, TabCompleter {

    private final QuantumAC plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public CommandHandler(QuantumAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("quantumac")) {
            return false;
        }

        if (args.length == 0) {
            sendInfo(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sendHelp(sender);
                break;

            case "reload":
                if (!sender.hasPermission("quantumac.reload")) {
                    sender.sendMessage(ChatUtil.colorize("&cYou don't have permission to use this command."));
                    return true;
                }

                plugin.getConfigManager().reloadConfigs();
                sender.sendMessage(ChatUtil.colorize("&aQuantumAC configurations reloaded successfully."));
                break;

            case "alerts":
                if (!sender.hasPermission("quantumac.alerts")) {
                    sender.sendMessage(ChatUtil.colorize("&cYou don't have permission to use this command."));
                    return true;
                }

                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatUtil.colorize("&cOnly players can toggle alerts."));
                    return true;
                }

                Player player = (Player) sender;
                UUID uuid = player.getUniqueId();

                plugin.getAlertManager().toggleAlerts(uuid);
                boolean enabled = plugin.getAlertManager().hasAlertsEnabled(uuid);

                sender.sendMessage(ChatUtil.colorize(
                        enabled ? "&aAlert notifications enabled." : "&cAlert notifications disabled."));
                break;

            case "history":
                if (!sender.hasPermission("quantumac.history")) {
                    sender.sendMessage(ChatUtil.colorize("&cYou don't have permission to use this command."));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(ChatUtil.colorize("&cUsage: /quantumac history <player> [limit]"));
                    return true;
                }

                String targetName = args[1];
                Player target = Bukkit.getPlayer(targetName);

                if (target == null) {
                    sender.sendMessage(ChatUtil.colorize("&cPlayer not found or not online."));
                    return true;
                }

                int limit = 10;
                if (args.length >= 3) {
                    try {
                        limit = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatUtil.colorize("&cInvalid limit number."));
                        return true;
                    }
                }

                showViolationHistory(sender, target, limit);
                break;

            case "stats":
                if (!sender.hasPermission("quantumac.stats")) {
                    sender.sendMessage(ChatUtil.colorize("&cYou don't have permission to use this command."));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(ChatUtil.colorize("&cUsage: /quantumac stats <player>"));
                    return true;
                }

                String playerName = args[1];
                Player targetPlayer = Bukkit.getPlayer(playerName);

                if (targetPlayer == null) {
                    sender.sendMessage(ChatUtil.colorize("&cPlayer not found or not online."));
                    return true;
                }

                showPlayerStats(sender, targetPlayer);
                break;

            case "verbose":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatUtil.colorize("&cOnly players can toggle verbose mode."));
                    return true;
                }

                if (!sender.hasPermission("quantumac.verbose")) {
                    sender.sendMessage(ChatUtil.colorize("&cYou don't have permission to use this command."));
                    return true;
                }

                player = (Player) sender;
                uuid = player.getUniqueId();

                plugin.getAlertManager().toggleVerbose(uuid);

                boolean verboseEnabled = plugin.getAlertManager().hasVerboseEnabled(uuid);
                sender.sendMessage(ChatUtil.colorize(
                        verboseEnabled ? "&aVerbose mode enabled. Alerts will show detailed information."
                                : "&cVerbose mode disabled. Alerts will now show minimal information."));
                break;

            case "debug":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatUtil.colorize("&cOnly players can toggle debug mode."));
                    return true;
                }

                if (!sender.hasPermission("quantumac.debug")) {
                    sender.sendMessage(ChatUtil.colorize("&cYou don't have permission to use this command."));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(ChatUtil.colorize("&cUsage: /quantumac debug <rotations>"));
                    return true;
                }

                switch (args[1].toLowerCase()) {
                    case "rotations": // Subcommand for rotations
                        Player rotationPlayer = (Player) sender;
                        PlayerData rotationPlayerData = plugin.getPlayerDataManager().getPlayerData(rotationPlayer.getUniqueId());

                        if (rotationPlayerData == null) {
                            sender.sendMessage(ChatUtil.colorize("&cPlayer data not found. Debug mode cannot be toggled."));
                            return true;
                        }

                        RotationA rotationCheck = plugin.getCheckManager().getCheck(rotationPlayer.getUniqueId(), RotationA.class); // Assuming you have a method to get the check
                        if (rotationCheck == null) {
                            sender.sendMessage(ChatUtil.colorize("&cRotation debug is not available."));
                            return true;
                        }

                        boolean isDebugEnabled = rotationCheck.toggleDebug(); // Assume this method toggles the flag and returns the new state
                        sender.sendMessage(ChatUtil.colorize(isDebugEnabled
                                ? "&aRotation debug mode enabled. Detailed rotation checks will be displayed."
                                : "&cRotation debug mode disabled."));
                        break;
                    case "packets": // Subcommand for tracking packets
                        Player packetPlayer = (Player) sender;
                        PlayerData packetPlayerData = plugin.getPlayerDataManager().getPlayerData(packetPlayer.getUniqueId());

                        if (packetPlayerData == null) {
                            sender.sendMessage(ChatUtil.colorize("&cPlayer data not found. Debug mode cannot be toggled."));
                            return true;
                        }

                        boolean isPacketDebugEnabled = packetPlayerData.togglePacketDebug();
                        sender.sendMessage(ChatUtil.colorize(isPacketDebugEnabled
                                ? "&aPacket debug mode enabled. You will see detailed packet information in real-time."
                                : "&cPacket debug mode disabled. Packet tracking stopped."));
                        break;


                    case "packetstats": // shows statistics from collected packets
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(ChatUtil.colorize("&cOnly players can use this command."));
                            return true;
                        }

                        Player statsPlayer = (Player) sender;
                        PlayerData statsPlayerData = plugin.getPlayerDataManager().getPlayerData(statsPlayer.getUniqueId());

                        if (statsPlayerData == null) {
                            sender.sendMessage(ChatUtil.colorize("&cPlayer data not found."));
                            return true;
                        }

                        if (!statsPlayerData.isPacketDebugEnabled()) {
                            sender.sendMessage(ChatUtil.colorize("&cPacket debug mode is not enabled. Use /quantumac debug packets to enable it."));
                            return true;
                        }

                        statsPlayerData.displayPacketDebug(sender);
                        break;

                    default:
                        sender.sendMessage(ChatUtil.colorize("&cUnknown debug option. Available: &7rotations."));
                        break;
                }
                break;

            default:
                sender.sendMessage(ChatUtil.colorize("&cUnknown command. Use /quantumac help for a list of commands."));
        }

        return true;
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage(ChatUtil.colorize("&7=== &bQuantumAC &7==="));
        sender.sendMessage(ChatUtil.colorize("&bVersion: &7" + plugin.getDescription().getVersion()));
        sender.sendMessage(ChatUtil.colorize("&bDeveloped by: &7tj88888"));
        sender.sendMessage(ChatUtil.colorize("&bActive players: &7" + plugin.getPlayerDataManager().getActivePlayerCount()));
        sender.sendMessage(ChatUtil.colorize("&bTPS: &7" + String.format("%.1f", plugin.getConfigManager().getCurrentTPS())));
        sender.sendMessage(ChatUtil.colorize("&7Use &b/quantumac help &7for commands."));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatUtil.colorize("&7=== &bQuantumAC Commands &7==="));
        sender.sendMessage(ChatUtil.colorize("&b/quantumac &7- Show plugin information"));
        sender.sendMessage(ChatUtil.colorize("&b/quantumac help &7- Show commands list"));
        sender.sendMessage(ChatUtil.colorize("&b/quantumac reload &7- Reload plugin configurations"));
        sender.sendMessage(ChatUtil.colorize("&b/quantumac alerts &7- Toggle violation alerts"));
        sender.sendMessage(ChatUtil.colorize("&b/quantumac history <player> [limit] &7- View player violation history"));
        sender.sendMessage(ChatUtil.colorize("&b/quantumac stats <player> &7- View player statistics"));
        sender.sendMessage(ChatUtil.colorize("&b/quantumac verbose &7- Toggle verbose alerts with detailed information"));

        if (sender.hasPermission("quantumac.debug")) {
            sender.sendMessage(ChatUtil.colorize("&b/quantumac debug rotations &7- Toggle rotation debug mode"));
            sender.sendMessage(ChatUtil.colorize("&b/quantumac debug packets &7- Toggle real-time packet display"));
            sender.sendMessage(ChatUtil.colorize("&b/quantumac debug packetstats &7- Show packet statistics summary"));
        }
    }

    private void showViolationHistory(CommandSender sender, Player target, int limit) {
        sender.sendMessage(ChatUtil.colorize("&7=== &b" + target.getName() + "'s Violation History &7==="));
        sender.sendMessage(ChatUtil.colorize("&7Loading history..."));

        plugin.getMongoManager().getPlayerViolations(target.getUniqueId(), limit)
                .thenAccept(violations -> {
                    if (violations.isEmpty()) {
                        sender.sendMessage(ChatUtil.colorize("&7No violations found."));
                        return;
                    }

                    for (ViolationLog log : violations) {
                        // Use getVl() instead of getViolationLevel()
                        String timestamp = dateFormat.format(new Date(log.getTimestamp()));
                        sender.sendMessage(ChatUtil.colorize(
                                "&7[" + timestamp + "] &b" + log.getCheckName() +
                                        " &7(" + log.getCheckType() + ") &7- VL: &b" +
                                        String.format("%.1f", log.getVl()) + " &7- &b" + log.getDetails()
                        ));
                    }
                });
    }

    private void showPlayerStats(CommandSender sender, Player target) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ChatUtil.colorize("&cNo data found for that player."));
            return;
        }

        sender.sendMessage(ChatUtil.colorize("&7=== &b" + target.getName() + "'s Stats &7==="));
        sender.sendMessage(ChatUtil.colorize("&bJoined: &7" + dateFormat.format(new Date(data.getJoinTime()))));
        sender.sendMessage(ChatUtil.colorize("&bPing: &7" + data.getAveragePing() + "ms"));
        sender.sendMessage(ChatUtil.colorize("&bTotal Violations: &7" + data.getTotalViolations()));

        // Add movement stats if available
        if (data.getMovementData() != null) {
            sender.sendMessage(ChatUtil.colorize("&7--- &bMovement Stats &7---"));
            sender.sendMessage(ChatUtil.colorize("&bSpeed: &7" +
                    String.format("%.2f", data.getMovementData().getDeltaXZ()) + " blocks/tick"));
            sender.sendMessage(ChatUtil.colorize("&bYaw: &7" +
                    String.format("%.1f", data.getMovementData().getYaw()) + "°"));
            sender.sendMessage(ChatUtil.colorize("&bPitch: &7" +
                    String.format("%.1f", data.getMovementData().getPitch()) + "°"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("quantumac")) {
            return null;
        }

        if (args.length == 1) {
            List<String> commands = new ArrayList<>(Arrays.asList("help", "reload", "alerts", "history", "stats", "verbose"));

            // Only add debug if they have permission
            if (sender.hasPermission("quantumac.debug")) {
                commands.add("debug");
            }

            return commands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("stats")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("debug")) {
                // Add debug subcommands
                return Arrays.asList("rotations", "packets", "packetstats").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}