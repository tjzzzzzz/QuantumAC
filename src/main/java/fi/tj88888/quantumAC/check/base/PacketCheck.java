package fi.tj88888.quantumAC.check.base;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all packet-related checks
 * Provides common functionality for packet timing analysis
 */
public abstract class PacketCheck extends Check {

    // Common constants for packet checks
    protected static final long MAX_PACKET_INTERVAL = 100; // 100 milliseconds
    protected static final long MIN_PACKET_INTERVAL = 5;   // 5 milliseconds
    
    // Common state tracking
    protected final Map<PacketType, Long> lastPacketTimes = new HashMap<>();
    protected final Map<PacketType, Deque<Long>> packetTimingHistory = new HashMap<>();
    protected final int MAX_SAMPLES = 40;
    
    // Packet count tracking
    protected final Map<PacketType, Integer> packetCounts = new HashMap<>();
    protected long packetCountStartTime = System.currentTimeMillis();
    protected static final long PACKET_COUNT_INTERVAL = 1000; // 1 second

    public PacketCheck(QuantumAC plugin, PlayerData playerData, String checkName, String checkType) {
        super(plugin, playerData, checkName, checkType);
    }

    /**
     * Tracks packet timing for a specific packet type
     * 
     * @param event The packet event
     * @param type The packet type to track
     */
    protected void trackPacketTiming(PacketEvent event, PacketType type) {
        if (event.getPacketType() != type) return;
        
        long now = System.currentTimeMillis();
        
        // Initialize history queue if needed
        if (!packetTimingHistory.containsKey(type)) {
            packetTimingHistory.put(type, new ArrayDeque<>());
        }
        
        // Record packet time
        Deque<Long> history = packetTimingHistory.get(type);
        history.addLast(now);
        if (history.size() > MAX_SAMPLES) {
            history.removeFirst();
        }
        
        // Update last packet time
        lastPacketTimes.put(type, now);
        
        // Update packet count
        if (!packetCounts.containsKey(type)) {
            packetCounts.put(type, 0);
        }
        packetCounts.put(type, packetCounts.get(type) + 1);
        
        // Reset packet counts periodically
        if (now - packetCountStartTime > PACKET_COUNT_INTERVAL) {
            packetCountStartTime = now;
            packetCounts.clear();
        }
    }

    /**
     * Gets the time since the last packet of a specific type
     * 
     * @param type The packet type
     * @return Time in milliseconds, or -1 if no packet of that type has been received
     */
    protected long getTimeSinceLastPacket(PacketType type) {
        if (!lastPacketTimes.containsKey(type)) return -1;
        return System.currentTimeMillis() - lastPacketTimes.get(type);
    }

    /**
     * Gets the time between the last two packets of a specific type
     * 
     * @param type The packet type
     * @return Time in milliseconds, or -1 if not enough data
     */
    protected long getTimeBetweenLastPackets(PacketType type) {
        if (!packetTimingHistory.containsKey(type)) return -1;
        
        Deque<Long> history = packetTimingHistory.get(type);
        if (history.size() < 2) return -1;
        
        Long[] times = history.toArray(new Long[0]);
        return times[times.length - 1] - times[times.length - 2];
    }

    /**
     * Gets the average time between packets of a specific type
     * 
     * @param type The packet type
     * @return Average time in milliseconds, or -1 if not enough data
     */
    protected double getAverageTimeBetweenPackets(PacketType type) {
        if (!packetTimingHistory.containsKey(type)) return -1;
        
        Deque<Long> history = packetTimingHistory.get(type);
        if (history.size() < 2) return -1;
        
        Long[] times = history.toArray(new Long[0]);
        double sum = 0;
        int count = 0;
        
        for (int i = 1; i < times.length; i++) {
            sum += times[i] - times[i - 1];
            count++;
        }
        
        return sum / count;
    }

    /**
     * Gets the standard deviation of time between packets of a specific type
     * 
     * @param type The packet type
     * @return Standard deviation in milliseconds, or -1 if not enough data
     */
    protected double getStdDevTimeBetweenPackets(PacketType type) {
        if (!packetTimingHistory.containsKey(type)) return -1;
        
        Deque<Long> history = packetTimingHistory.get(type);
        if (history.size() < 3) return -1;
        
        Long[] times = history.toArray(new Long[0]);
        double avg = getAverageTimeBetweenPackets(type);
        double sum = 0;
        int count = 0;
        
        for (int i = 1; i < times.length; i++) {
            double diff = times[i] - times[i - 1] - avg;
            sum += diff * diff;
            count++;
        }
        
        return Math.sqrt(sum / count);
    }

    /**
     * Gets the packet rate (packets per second) for a specific type
     * 
     * @param type The packet type
     * @return Packets per second, or 0 if no packets of that type have been received
     */
    protected double getPacketRate(PacketType type) {
        if (!packetCounts.containsKey(type)) return 0;
        
        long now = System.currentTimeMillis();
        long interval = now - packetCountStartTime;
        
        if (interval <= 0) return 0;
        
        return packetCounts.get(type) * 1000.0 / interval;
    }

    /**
     * Checks if the packet rate exceeds a threshold
     * 
     * @param type The packet type
     * @param threshold The threshold in packets per second
     * @return True if the rate exceeds the threshold
     */
    protected boolean isPacketRateExceeded(PacketType type, double threshold) {
        return getPacketRate(type) > threshold;
    }

    /**
     * Checks if the packet timing is too consistent (potential timer hack)
     * 
     * @param type The packet type
     * @param maxDeviation Maximum allowed standard deviation as a percentage of the average
     * @return True if the timing is too consistent
     */
    protected boolean isPacketTimingTooConsistent(PacketType type, double maxDeviation) {
        double avg = getAverageTimeBetweenPackets(type);
        double stdDev = getStdDevTimeBetweenPackets(type);
        
        if (avg <= 0 || stdDev < 0) return false;
        
        return (stdDev / avg) < maxDeviation;
    }
} 