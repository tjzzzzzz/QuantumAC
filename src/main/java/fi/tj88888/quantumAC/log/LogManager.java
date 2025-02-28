package fi.tj88888.quantumAC.log;

import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.log.ViolationLog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Handles file logging of violations and other events
 */
public class LogManager {

    private final QuantumAC plugin;
    private final File logFolder;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    // Async logging queue
    private final BlockingQueue<ViolationLog> logQueue = new LinkedBlockingQueue<>();
    private final Thread logThread;
    private boolean running = true;

    public LogManager(QuantumAC plugin) {
        this.plugin = plugin;

        // Create logs directory if it doesn't exist
        logFolder = new File(plugin.getDataFolder(), "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }

        // Start async logging thread
        logThread = new Thread(this::processLogQueue);
        logThread.setName("QuantumAC-LogManager");
        logThread.setDaemon(true);
        logThread.start();
    }

    /**
     * Log a violation to file if file logging is enabled
     *
     * @param log The violation to log
     */
    public void logViolation(ViolationLog log) {
        // Add to queue for async processing
        logQueue.add(log);
    }

    /**
     * Process the log queue in a separate thread
     */
    private void processLogQueue() {
        while (running) {
            try {
                ViolationLog log = logQueue.take();
                if (log != null) {
                    writeLogToFile(log);
                }
            } catch (InterruptedException e) {
                plugin.getLogger().warning("Log thread interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                plugin.getLogger().severe("Error in log thread: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Write a violation log to the appropriate file
     *
     * @param log The violation to log
     */
    private void writeLogToFile(ViolationLog log) {
        if (!plugin.getConfigManager().getConfig().getBoolean("violations.log-to-file", false)) {
            return;
        }

        String date = dateFormat.format(new Date(log.getTimestamp()));
        File logFile = new File(logFolder, date + ".log");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            // Format: [Time] Player failed Check (Type) VL: x.x | Details | World (x, y, z) | Ping: x | TPS: x.x
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(timeFormat.format(new Date(log.getTimestamp()))).append("] ");
            sb.append(log.getPlayerName()).append(" failed ");
            sb.append(log.getCheckName()).append(" (").append(log.getCheckType()).append(") ");
            sb.append("VL: ").append(String.format("%.1f", log.getVl())).append(" | ");
            sb.append("Details: ").append(log.getDetails()).append(" | ");
            sb.append("World: ").append(log.getWorld()).append(" (");
            sb.append(String.format("%.1f", log.getX())).append(", ");
            sb.append(String.format("%.1f", log.getY())).append(", ");
            sb.append(String.format("%.1f", log.getZ())).append(") | ");
            sb.append("Ping: ").append(log.getPing()).append("ms | ");
            sb.append("TPS: ").append(String.format("%.1f", log.getTps()));

            writer.write(sb.toString());
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().severe("Error writing to log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Shutdown the logging thread
     */
    public void shutdown() {
        running = false;
        logThread.interrupt();
    }
}