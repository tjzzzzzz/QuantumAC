package fi.tj88888.quantumAC.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.data.PlayerDataManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Handles incoming packet processing for the anti-cheat system
 * Optimized for performance with movement data handling
 */
public class PacketListener {

    private final QuantumAC plugin;

    // Cache to reduce repeated block checks
    private final ConcurrentMap<String, BlockStateCache> blockStateCache = new ConcurrentHashMap<>();
    private static final long BLOCK_CACHE_EXPIRY = TimeUnit.SECONDS.toMillis(5);

    // Last packet times for rate limiting movement processing
    private final ConcurrentMap<UUID, Long> lastMovementUpdate = new ConcurrentHashMap<>();
    private static final long MOVEMENT_UPDATE_THROTTLE = 10; // ms between updates

    public PacketListener(QuantumAC plugin) {
        this.plugin = plugin;

        // Schedule periodic cache cleanup
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                this::cleanupBlockCache, 20 * 30, 20 * 30); // Run every 30 seconds
    }

    public void onPacketReceive(PacketEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (playerData == null) return;

        // Update packet timing data
        long now = System.currentTimeMillis();
        PacketType packetType = event.getPacketType();

        if (playerData.isPacketDebugEnabled()) {
            // Extract more specific information for certain packet types
            String packetInfo = packetType.name();

            // For flying packets, add ground state information
            if (packetType == PacketType.Play.Client.FLYING ||
                    packetType == PacketType.Play.Client.POSITION ||
                    packetType == PacketType.Play.Client.POSITION_LOOK ||
                    packetType == PacketType.Play.Client.LOOK) {
                try {
                    boolean onGround = event.getPacket().getBooleans().read(0);
                    packetInfo += onGround ? "[GROUND]" : "[AIR]";
                } catch (Exception e) {
                    // Ignore exceptions from packet reading
                }
            }

            // For use entity, add the action type
            else if (packetType == PacketType.Play.Client.USE_ENTITY) {
                try {
                    if (event.getPacket().getEnumEntityUseActions().size() > 0) {
                        EnumWrappers.EntityUseAction action = event.getPacket().getEnumEntityUseActions().read(0).getAction();
                        packetInfo += "[" + action.name() + "]";
                    }
                } catch (Exception e) {
                    // Ignore exceptions from packet reading
                }
            }

            // For block dig, add the action type
            else if (packetType == PacketType.Play.Client.BLOCK_DIG) {
                try {
                    EnumWrappers.PlayerDigType digType = event.getPacket().getPlayerDigTypes().read(0);
                    packetInfo += "[" + digType.name() + "]";
                } catch (Exception e) {
                    // Ignore exceptions from packet reading
                }
            }

            // For entity action, add the action type
            else if (packetType == PacketType.Play.Client.ENTITY_ACTION) {
                try {
                    EnumWrappers.PlayerAction action = event.getPacket().getPlayerActions().read(0);
                    packetInfo += "[" + action.name() + "]";
                } catch (Exception e) {
                    // Ignore exceptions from packet reading
                }
            }

            // For window click, add slot information
            else if (packetType == PacketType.Play.Client.WINDOW_CLICK) {
                try {
                    int slot = event.getPacket().getIntegers().read(1);
                    packetInfo += "[SLOT:" + slot + "]";
                } catch (Exception e) {
                    // Ignore exceptions from packet reading
                }
            }

            // For custom payload, add channel information
            else if (packetType == PacketType.Play.Client.CUSTOM_PAYLOAD) {
                try {
                    String channel = event.getPacket().getStrings().read(0);
                    packetInfo += "[" + channel + "]";
                } catch (Exception e) {
                    // Ignore exceptions from packet reading
                }
            }

            // Store packet for history and display real-time information
            playerData.recordPacketDebug(packetInfo, now);
            playerData.displayRealtimePacketInfo(packetInfo, now);
        }

        try {
            // Handle movement packets with optimized processing
            if (packetType == PacketType.Play.Client.POSITION ||
                    packetType == PacketType.Play.Client.POSITION_LOOK ||
                    packetType == PacketType.Play.Client.FLYING) {

                handleMovementPacket(event, player, playerData, now);

            } else if (packetType == PacketType.Play.Client.LOOK) {
                // Just update rotation data for look packets
                playerData.setLastLook(now);

                // Only extract rotation data if we should process it (rate limiting)
                if (shouldProcessMovement(uuid, now)) {
                    try {
                        float yaw = event.getPacket().getFloat().read(0);
                        float pitch = event.getPacket().getFloat().read(1);
                        boolean onGround = event.getPacket().getBooleans().read(0);

                        // Get current position from player data
                        Location loc = playerData.getLastLocation();
                        if (loc != null) {
                            // Update movement data with new rotation but same position
                            plugin.getPlayerDataManager().updatePlayerMovement(
                                    uuid, loc.getX(), loc.getY(), loc.getZ(),
                                    yaw, pitch, onGround
                            );
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "Error processing LOOK packet for " + player.getName(), e);
                    }
                }
            }
            // Handle combat packets
            else if (packetType == PacketType.Play.Client.USE_ENTITY) {
                handleUseEntityPacket(event, playerData, now);
            } else if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
                playerData.setLastArmAnimation(now);
            }
            // Handle inventory packets
            else if (packetType == PacketType.Play.Client.WINDOW_CLICK) {
                playerData.setLastInventoryAction(now);
            } else if (packetType == PacketType.Play.Client.CLOSE_WINDOW) {
                playerData.setLastInventoryClose(now);
            }
            // Handle miscellaneous packets
            else if (packetType == PacketType.Play.Client.ABILITIES) {
                playerData.setLastAbilitiesPacket(now);
            } else if (packetType == PacketType.Play.Client.BLOCK_DIG) {
                playerData.setLastBlockDig(now);
            } else if (packetType == PacketType.Play.Client.BLOCK_PLACE) {
                playerData.setLastBlockPlace(now);
            }
            // Additional packet tracking for entity action, transaction, keep alive etc.
            else if (packetType == PacketType.Play.Client.ENTITY_ACTION) {
                if (playerData.isPacketDebugEnabled()) {
                    playerData.setLastEntityAction(now);
                }
            }
            else if (packetType == PacketType.Play.Client.TRANSACTION) {
                if (playerData.isPacketDebugEnabled()) {
                    playerData.setLastTransaction(now);
                }
            }
            else if (packetType == PacketType.Play.Client.KEEP_ALIVE) {
                if (playerData.isPacketDebugEnabled()) {
                    playerData.setLastKeepAlive(now);
                }
            }
            else if (packetType == PacketType.Play.Client.CUSTOM_PAYLOAD) {
                if (playerData.isPacketDebugEnabled()) {
                    playerData.setLastCustomPayload(now);
                }
            }

            // Update ping
            playerData.updatePing(player.getPing());

            // Process packet through all checks
            plugin.getCheckManager().processPacket(player, event);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Error processing packet for " + player.getName(), e);
        }
    }

    /**
     * Handle movement packet processing with performance optimizations
     */
    private void handleMovementPacket(PacketEvent event, Player player, PlayerData playerData, long now) {
        UUID uuid = player.getUniqueId();
        PacketType packetType = event.getPacketType();

        // Update basic timing data
        if (packetType == PacketType.Play.Client.FLYING) {
            playerData.setLastFlying(now);
        } else if (packetType == PacketType.Play.Client.POSITION) {
            playerData.setLastPosition(now);
        } else if (packetType == PacketType.Play.Client.POSITION_LOOK) {
            playerData.setLastPositionLook(now);
        }

        // Update player location in playerData
        Location location = player.getLocation();
        playerData.setLastLocation(location.clone());

        // Rate limit full movement updates to avoid excessive processing
        if (shouldProcessMovement(uuid, now)) {
            try {
                // For position and position_look packets, extract the coordinates
                if (packetType == PacketType.Play.Client.POSITION ||
                        packetType == PacketType.Play.Client.POSITION_LOOK) {

                    double x = event.getPacket().getDoubles().read(0);
                    double y = event.getPacket().getDoubles().read(1);
                    double z = event.getPacket().getDoubles().read(2);

                    float yaw = 0, pitch = 0;
                    if (packetType == PacketType.Play.Client.POSITION_LOOK) {
                        yaw = event.getPacket().getFloat().read(0);
                        pitch = event.getPacket().getFloat().read(1);
                    } else {
                        // For position-only packets, keep the current rotation
                        yaw = location.getYaw();
                        pitch = location.getPitch();
                    }

                    boolean onGround = event.getPacket().getBooleans().read(0);

                    // Update all movement data in PlayerDataManager (centralized handling)
                    PlayerDataManager dataManager = plugin.getPlayerDataManager();
                    dataManager.updatePlayerMovement(uuid, x, y, z, yaw, pitch, onGround);

                    // Update safe location if player is on ground and not in invalid position
                    if (onGround && !player.isFlying() && !player.isInsideVehicle()) {
                        playerData.setLastSafeLocation(location.clone());
                    }

                    // Handle block state updates on a slight delay to reduce performance impact
                    if (now % 5 == 0) { // Only check every 5ms (approximately)
                        updateBlockState(player, playerData, x, y, z);
                    }
                }

                lastMovementUpdate.put(uuid, now);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error processing movement data for " + player.getName(), e);
            }
        }
    }

    /**
     * Determine if we should process a movement update based on rate limiting
     */
    private boolean shouldProcessMovement(UUID uuid, long now) {
        Long last = lastMovementUpdate.get(uuid);
        if (last == null) return true;
        return (now - last) >= MOVEMENT_UPDATE_THROTTLE;
    }

    /**
     * Update block state information with caching for performance
     */
    private void updateBlockState(Player player, PlayerData playerData, double x, double y, double z) {
        UUID uuid = player.getUniqueId();
        World world = player.getWorld();

        // Create a cache key for this position
        String cacheKey = world.getName() + ":" +
                Math.floor(x) + ":" +
                Math.floor(y) + ":" +
                Math.floor(z);

        // Check if we have a recent cache for this position
        BlockStateCache cache = blockStateCache.get(cacheKey);
        long now = System.currentTimeMillis();

        if (cache != null && (now - cache.timestamp < BLOCK_CACHE_EXPIRY)) {
            // Use cached values if recent enough
            plugin.getPlayerDataManager().updatePlayerBlockState(
                    uuid,
                    cache.insideBlock,
                    cache.onIce,
                    cache.onSlime,
                    cache.inLiquid,
                    cache.onStairs,
                    cache.onSlab
            );
            return;
        }

        // If no cache or expired, compute block states
        try {
            // Get blocks at player's feet and body
            Block atFeet = world.getBlockAt((int)Math.floor(x),
                    (int)Math.floor(y - 0.2),
                    (int)Math.floor(z));

            Block atBody = world.getBlockAt((int)Math.floor(x),
                    (int)Math.floor(y + 0.8),
                    (int)Math.floor(z));

            // Check various block states
            boolean insideBlock = !atBody.getType().isTransparent() && atBody.getType() != Material.AIR;
            boolean onIce = atFeet.getType() == Material.ICE || atFeet.getType() == Material.PACKED_ICE;
            boolean onSlime = atFeet.getType() == Material.SLIME_BLOCK;
            boolean inLiquid = atFeet.isLiquid() || atBody.isLiquid();
            boolean onStairs = atFeet.getType().name().contains("STAIRS");
            boolean onSlab = atFeet.getType().name().contains("SLAB") ||
                    atFeet.getType().name().contains("STEP");

            // Update movement data with block state
            plugin.getPlayerDataManager().updatePlayerBlockState(
                    uuid, insideBlock, onIce, onSlime, inLiquid, onStairs, onSlab);

            // Cache the results
            BlockStateCache newCache = new BlockStateCache(
                    now, insideBlock, onIce, onSlime, inLiquid, onStairs, onSlab);
            blockStateCache.put(cacheKey, newCache);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Error updating block state for " + player.getName(), e);
        }
    }

    /**
     * Handle USE_ENTITY packet processing
     */
    private void handleUseEntityPacket(PacketEvent event, PlayerData playerData, long now) {
        try {
            if (event.getPacket().getEnumEntityUseActions().size() > 0) {
                EnumWrappers.EntityUseAction action = event.getPacket().getEnumEntityUseActions().read(0).getAction();

                if (action == EnumWrappers.EntityUseAction.ATTACK) {
                    playerData.setLastAttack(now);
                    if (event.getPacket().getIntegers().size() > 0) {
                        playerData.setLastAttackedEntity(event.getPacket().getIntegers().read(0));
                    }
                } else if (action == EnumWrappers.EntityUseAction.INTERACT
                        || action == EnumWrappers.EntityUseAction.INTERACT_AT) {
                    playerData.setLastInteract(now);
                    if (event.getPacket().getIntegers().size() > 0) {
                        playerData.setLastInteractedEntity(event.getPacket().getIntegers().read(0));
                    }
                }
            }
        } catch (Exception e) {
            // Some clients might send malformed USE_ENTITY packets
            plugin.getLogger().warning("Error processing USE_ENTITY packet: " + e.getMessage());
        }
    }

    /**
     * Cleanup old block state cache entries
     */
    private void cleanupBlockCache() {
        long now = System.currentTimeMillis();
        blockStateCache.entrySet().removeIf(entry ->
                now - entry.getValue().timestamp > BLOCK_CACHE_EXPIRY);
    }



    /**
     * Helper class to cache block state information
     */
    private static class BlockStateCache {
        final long timestamp;
        final boolean insideBlock;
        final boolean onIce;
        final boolean onSlime;
        final boolean inLiquid;
        final boolean onStairs;
        final boolean onSlab;

        BlockStateCache(long timestamp, boolean insideBlock, boolean onIce,
                        boolean onSlime, boolean inLiquid, boolean onStairs, boolean onSlab) {
            this.timestamp = timestamp;
            this.insideBlock = insideBlock;
            this.onIce = onIce;
            this.onSlime = onSlime;
            this.inLiquid = inLiquid;
            this.onStairs = onStairs;
            this.onSlab = onSlab;
        }
    }
}