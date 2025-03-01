package fi.tj88888.quantumAC.check.base;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.util.MovementData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

/**
 * Base class for all movement-related checks
 * Provides common functionality for movement analysis
 */
public abstract class MovementCheck extends Check {

    // Common material sets for movement checks
    protected static final Set<Material> CLIMBABLE_MATERIALS = new HashSet<>();
    protected static final Set<Material> NON_SOLID_MATERIALS = new HashSet<>();
    protected static final Set<Material> LIQUID_MATERIALS = new HashSet<>();
    protected static final Set<Material> SPECIAL_BLOCKS = new HashSet<>();
    protected static final Set<Material> BOUNCE_BLOCKS = new HashSet<>();

    // Common exemption timers
    protected static final long TELEPORT_EXEMPT_TIME = 4000;
    protected static final long DAMAGE_EXEMPT_TIME = 2000;
    protected static final long VELOCITY_EXEMPT_TIME = 2500;
    protected static final long SPECIAL_BLOCK_EXEMPT_TIME = 1000;
    protected static final long GROUND_EXIT_EXEMPT_TIME = 500;

    // Initialize material sets
    static {
        // Setup climbable materials
        CLIMBABLE_MATERIALS.add(Material.LADDER);
        CLIMBABLE_MATERIALS.add(Material.VINE);
        CLIMBABLE_MATERIALS.add(Material.SCAFFOLDING);
        CLIMBABLE_MATERIALS.add(Material.TWISTING_VINES);
        CLIMBABLE_MATERIALS.add(Material.WEEPING_VINES);

        // Setup non-solid materials
        NON_SOLID_MATERIALS.add(Material.AIR);
        NON_SOLID_MATERIALS.add(Material.CAVE_AIR);
        NON_SOLID_MATERIALS.add(Material.VOID_AIR);

        // Setup liquid materials
        LIQUID_MATERIALS.add(Material.WATER);
        LIQUID_MATERIALS.add(Material.LAVA);

        // Special blocks that affect movement
        SPECIAL_BLOCKS.add(Material.SLIME_BLOCK);
        SPECIAL_BLOCKS.add(Material.HONEY_BLOCK);
        SPECIAL_BLOCKS.add(Material.COBWEB);
        SPECIAL_BLOCKS.add(Material.SOUL_SAND);

        // Blocks that can cause bouncing
        BOUNCE_BLOCKS.add(Material.SLIME_BLOCK);
        // Bed materials are handled in isOnBounceBlock method
        BOUNCE_BLOCKS.add(Material.PISTON);
        BOUNCE_BLOCKS.add(Material.STICKY_PISTON);
    }

    // Common state tracking
    protected long lastTeleportTime = 0;
    protected long lastDamageTime = 0;
    protected long lastVelocityTime = 0;
    protected long lastSpecialBlockTime = 0;
    protected long lastGroundExitTime = 0;
    protected long lastBounceTime = 0;

    public MovementCheck(QuantumAC plugin, PlayerData playerData, String checkName, String checkType) {
        super(plugin, playerData, checkName, checkType);
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

    /**
     * Checks if the player is in a liquid (water or lava)
     * 
     * @param player The player to check
     * @return True if the player is in a liquid
     */
    protected boolean isInLiquid(Player player) {
        Block block = player.getLocation().getBlock();
        return LIQUID_MATERIALS.contains(block.getType());
    }

    /**
     * Checks if the player is on a climbable block
     * 
     * @param player The player to check
     * @return True if the player is on a climbable block
     */
    protected boolean isOnClimbable(Player player) {
        Location loc = player.getLocation();
        Block block = loc.getBlock();
        
        if (CLIMBABLE_MATERIALS.contains(block.getType())) {
            return true;
        }
        
        // Check block below for scaffolding
        Block blockBelow = loc.clone().subtract(0, 0.1, 0).getBlock();
        return blockBelow.getType() == Material.SCAFFOLDING;
    }

    /**
     * Checks if the player is in a web
     * 
     * @param player The player to check
     * @return True if the player is in a web
     */
    protected boolean isInWeb(Player player) {
        Block block = player.getLocation().getBlock();
        return block.getType() == Material.COBWEB;
    }

    /**
     * Checks if the player is on a special block that affects movement
     * 
     * @param player The player to check
     * @return True if the player is on a special block
     */
    protected boolean isOnSpecialBlock(Player player) {
        Location loc = player.getLocation();
        Block block = loc.clone().subtract(0, 0.1, 0).getBlock();
        
        if (SPECIAL_BLOCKS.contains(block.getType())) {
            lastSpecialBlockTime = System.currentTimeMillis();
            return true;
        }
        
        return false;
    }

    /**
     * Checks if the player is on a bounce block
     * 
     * @param player The player to check
     * @return True if the player is on a bounce block
     */
    protected boolean isOnBounceBlock(Player player) {
        Location loc = player.getLocation();
        Block block = loc.clone().subtract(0, 0.1, 0).getBlock();
        
        if (BOUNCE_BLOCKS.contains(block.getType()) || block.getType().name().endsWith("_BED")) {
            lastBounceTime = System.currentTimeMillis();
            return true;
        }
        
        return false;
    }

    /**
     * Checks if the player is near the ground
     * 
     * @param player The player to check
     * @return True if the player is near the ground
     */
    protected boolean isNearGround(Player player) {
        Location loc = player.getLocation();
        double y = loc.getY();
        World world = loc.getWorld();
        
        // Check a few blocks below the player
        for (double i = 0; i <= 2; i += 0.5) {
            Block block = new Location(world, loc.getX(), y - i, loc.getZ()).getBlock();
            if (!NON_SOLID_MATERIALS.contains(block.getType()) && 
                !LIQUID_MATERIALS.contains(block.getType())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Checks if the player is near a ceiling
     * 
     * @param player The player to check
     * @return True if the player is near a ceiling
     */
    protected boolean isNearCeiling(Player player) {
        Location loc = player.getLocation();
        double y = loc.getY() + 2; // Player height is ~1.8
        World world = loc.getWorld();
        
        // Check a few blocks above the player
        for (double i = 0; i <= 0.5; i += 0.1) {
            Block block = new Location(world, loc.getX(), y + i, loc.getZ()).getBlock();
            if (!NON_SOLID_MATERIALS.contains(block.getType())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Checks if the player has recently teleported
     * 
     * @return True if the player has recently teleported
     */
    protected boolean isRecentlyTeleported() {
        return System.currentTimeMillis() - lastTeleportTime < TELEPORT_EXEMPT_TIME;
    }

    /**
     * Checks if the player has recently taken damage
     * 
     * @return True if the player has recently taken damage
     */
    protected boolean isRecentlyDamaged() {
        return System.currentTimeMillis() - lastDamageTime < DAMAGE_EXEMPT_TIME;
    }

    /**
     * Checks if the player has recently received velocity
     * 
     * @return True if the player has recently received velocity
     */
    protected boolean isRecentlyVelocity() {
        return System.currentTimeMillis() - lastVelocityTime < VELOCITY_EXEMPT_TIME;
    }

    /**
     * Checks if the player has recently been on a special block
     * 
     * @return True if the player has recently been on a special block
     */
    protected boolean isRecentlyOnSpecialBlock() {
        return System.currentTimeMillis() - lastSpecialBlockTime < SPECIAL_BLOCK_EXEMPT_TIME;
    }

    /**
     * Checks if the player has recently left the ground
     * 
     * @return True if the player has recently left the ground
     */
    protected boolean isRecentlyLeftGround() {
        return System.currentTimeMillis() - lastGroundExitTime < GROUND_EXIT_EXEMPT_TIME;
    }

    /**
     * Checks if the player has recently bounced
     * 
     * @return True if the player has recently bounced
     */
    protected boolean isRecentlyBounced() {
        return System.currentTimeMillis() - lastBounceTime < VELOCITY_EXEMPT_TIME;
    }

    /**
     * Called when a player teleports
     */
    public void onPlayerTeleport() {
        lastTeleportTime = System.currentTimeMillis();
    }

    /**
     * Called when a player takes damage
     */
    public void onPlayerDamage() {
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Called when a player receives velocity
     * 
     * @param velocity The velocity vector
     */
    public void onPlayerVelocity(Vector velocity) {
        lastVelocityTime = System.currentTimeMillis();
    }

    /**
     * Gets the jump boost level of a player
     * 
     * @param player The player to check
     * @return The jump boost level (0 if none)
     */
    protected int getJumpBoostLevel(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.JUMP_BOOST)) {
                return effect.getAmplifier() + 1;
            }
        }
        return 0;
    }

    /**
     * Gets the slow falling effect status
     * 
     * @param player The player to check
     * @return True if the player has slow falling
     */
    protected boolean hasSlowFalling(Player player) {
        return player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
    }

    /**
     * Checks if the player is exempt from movement checks
     * 
     * @param player The player to check
     * @return True if the player is exempt
     */
    protected boolean isExempt(Player player) {
        return player.getAllowFlight() || 
               player.isInsideVehicle() || 
               isRecentlyTeleported() || 
               isRecentlyDamaged() || 
               isRecentlyVelocity() || 
               isRecentlyOnSpecialBlock() ||
               isRecentlyBounced() ||
               isInLiquid(player) ||
               isInWeb(player) ||
               isOnClimbable(player);
    }
} 