package fi.tj88888.quantumAC.log;

import fi.tj88888.quantumAC.QuantumAC;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

public class LogManager {

    private final QuantumAC plugin;
    private final File logFolder;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat fileFormat;

    public LogManager(QuantumAC plugin) {
        this.plugin = plugin;
        this.logFolder = new File(plugin.getDataFolder(), "logs");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.fileFormat = new SimpleDateFormat("yyyy-MM-dd");

        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }
    }

    public void logViolation(ViolationLog violationLog) {
        CompletableFuture.runAsync(() -> {
            File logFile = getLogFile();

            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                writer.println(formatViolationLog(violationLog));
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to write to log file: " + e.getMessage());
                e.printStackTrace();
            }
        }, plugin.getPacketExecutor());
    }

    private File getLogFile() {
        String fileName = fileFormat.format(new Date()) + ".log";
        return new File(logFolder, fileName);
    }

    private String formatViolationLog(ViolationLog log) {
        return String.format(
                "[%s] %s failed %s (%s) VL: %.1f | Details: %s | Loc: %s (%.1f, %.1f, %.1f) | Ping: %d | TPS: %.1f",
                dateFormat.format(new Date(log.getTimestamp())),
                log.getPlayerName(),
                log.getCheckName(),
                log.getCheckType(),
                log.getViolationLevel(),
                log.getDetails(),
                log.getWorld(),
                log.getX(),
                log.getY(),
                log.getZ(),
                log.getPing(),
                log.getTps()
        );
    }

    public void logInfo(String message) {
        CompletableFuture.runAsync(() -> {
            File logFile = getLogFile();

            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                writer.println(String.format("[%s] [INFO] %s", dateFormat.format(new Date()), message));
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to write to log file: " + e.getMessage());
                e.printStackTrace();
            }
        }, plugin.getPacketExecutor());
    }

    public void logWarning(String message) {
        CompletableFuture.runAsync(() -> {
            File logFile = getLogFile();

            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                writer.println(String.format("[%s] [WARNING] %s", dateFormat.format(new Date()), message));
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to write to log file: " + e.getMessage());
                e.printStackTrace();
            }
        }, plugin.getPacketExecutor());
    }

    public void logError(String message, Throwable throwable) {
        CompletableFuture.runAsync(() -> {
            File logFile = getLogFile();

            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                writer.println(String.format("[%s] [ERROR] %s", dateFormat.format(new Date()), message));
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to write to log file: " + e.getMessage());
                e.printStackTrace();
            }
        }, plugin.getPacketExecutor());
    }
}