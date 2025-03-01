package fi.tj88888.quantumAC.check.base;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Base class for all combat-related checks
 * Provides common functionality for combat analysis
 */
public abstract class CombatCheck extends Check {

    // Common constants for combat checks
    protected static final long MAX_ATTACK_INTERVAL = 2000; // 2 seconds
    protected static final long MIN_ATTACK_INTERVAL = 50;   // 50 milliseconds
    
    // Common state tracking
    protected long lastAttackTime = 0;
    protected long lastArmAnimationTime = 0;
    protected Integer lastAttackedEntityId = null;
    protected final Deque<Long> recentAttackTimes = new ArrayDeque<>();
    protected final Deque<Long> recentArmAnimations = new ArrayDeque<>();
    protected final int MAX_SAMPLES = 20;

    public CombatCheck(QuantumAC plugin, PlayerData playerData, String checkName, String checkType) {
        super(plugin, playerData, checkName, checkType);
    }

    /**
     * Processes common combat packet handling
     * 
     * @param event The packet event
     * @return True if the packet was a combat-related packet
     */
    protected boolean processCombatPacket(PacketEvent event) {
        if (event == null || event.getPacketType() == null) return false;

        long now = System.currentTimeMillis();
        PacketType packetType = event.getPacketType();
        boolean processed = false;

        try {
            // Handle arm animation packets
            if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
                lastArmAnimationTime = now;
                playerData.setLastArmAnimation(now);
                
                // Update history
                recentArmAnimations.addLast(now);
                if (recentArmAnimations.size() > MAX_SAMPLES) {
                    recentArmAnimations.removeFirst();
                }
                
                processed = true;
            }
            // Handle USE_ENTITY (attack) packets
            else if (packetType == PacketType.Play.Client.USE_ENTITY &&
                    event.getPacket().getEnumEntityUseActions().size() > 0) {

                EnumWrappers.EntityUseAction action =
                        event.getPacket().getEnumEntityUseActions().read(0).getAction();

                // Only process attack actions
                if (action == EnumWrappers.EntityUseAction.ATTACK) {
                    if (event.getPacket().getIntegers().size() > 0) {
                        lastAttackedEntityId = event.getPacket().getIntegers().read(0);
                        playerData.setLastAttackedEntity(lastAttackedEntityId);
                    }

                    lastAttackTime = now;
                    playerData.setLastAttack(now);
                    
                    // Update history
                    recentAttackTimes.addLast(now);
                    if (recentAttackTimes.size() > MAX_SAMPLES) {
                        recentAttackTimes.removeFirst();
                    }
                    
                    processed = true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error in CombatCheck processing: " + e.getMessage());
        }
        
        return processed;
    }

    /**
     * Gets the time difference between the last arm animation and attack
     * 
     * @return Time difference in milliseconds
     */
    protected long getTimeBetweenSwingAndAttack() {
        return lastAttackTime - lastArmAnimationTime;
    }

    /**
     * Gets the time difference between the last two attacks
     * 
     * @return Time difference in milliseconds, or -1 if not enough data
     */
    protected long getTimeBetweenAttacks() {
        if (recentAttackTimes.size() < 2) return -1;
        
        Long[] attacks = recentAttackTimes.toArray(new Long[0]);
        return attacks[attacks.length - 1] - attacks[attacks.length - 2];
    }

    /**
     * Gets the average time between recent attacks
     * 
     * @return Average time in milliseconds, or -1 if not enough data
     */
    protected double getAverageTimeBetweenAttacks() {
        if (recentAttackTimes.size() < 2) return -1;
        
        Long[] attacks = recentAttackTimes.toArray(new Long[0]);
        double sum = 0;
        int count = 0;
        
        for (int i = 1; i < attacks.length; i++) {
            sum += attacks[i] - attacks[i - 1];
            count++;
        }
        
        return sum / count;
    }

    /**
     * Gets the standard deviation of time between attacks
     * 
     * @return Standard deviation in milliseconds, or -1 if not enough data
     */
    protected double getStdDevTimeBetweenAttacks() {
        if (recentAttackTimes.size() < 3) return -1;
        
        Long[] attacks = recentAttackTimes.toArray(new Long[0]);
        double avg = getAverageTimeBetweenAttacks();
        double sum = 0;
        int count = 0;
        
        for (int i = 1; i < attacks.length; i++) {
            double diff = attacks[i] - attacks[i - 1] - avg;
            sum += diff * diff;
            count++;
        }
        
        return Math.sqrt(sum / count);
    }

    /**
     * Gets the attacked entity if it exists in the world
     * 
     * @param player The attacking player
     * @return The attacked entity, or null if not found
     */
    protected Entity getAttackedEntity(Player player) {
        if (lastAttackedEntityId == null) return null;
        
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity.getEntityId() == lastAttackedEntityId) {
                return entity;
            }
        }
        
        return null;
    }

    /**
     * Checks if the packet is a movement packet
     * 
     * @param type The packet type
     * @return True if it's a movement packet
     */
    protected boolean isMovementPacket(PacketType type) {
        return type == PacketType.Play.Client.POSITION ||
               type == PacketType.Play.Client.POSITION_LOOK ||
               type == PacketType.Play.Client.LOOK ||
               type == PacketType.Play.Client.FLYING;
    }
} 