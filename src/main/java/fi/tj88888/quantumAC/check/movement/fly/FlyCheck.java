package fi.tj88888.quantumAC.check.movement.fly;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.Check;
import fi.tj88888.quantumAC.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

/**
 * Base class for all fly checks.
 * Provides common functionality and helper methods for fly detection.
 */
public abstract class FlyCheck extends Check {

    // Exemption times
    protected static final long TELEPORT_EXEMPT_TIME = 3000; // 3 seconds
    protected static final long DAMAGE_EXEMPT_TIME = 1500; // 1.5 seconds
    protected static final long VELOCITY_EXEMPT_TIME = 2000; // 2 seconds
    protected static final long SPECIAL_EXEMPT_TIME = 1000; // 1 second

    // Special case timers
    protected long lastTeleportTime = 0;
    protected long lastDamageTime = 0;
    protected long lastVelocityTime = 0;
    protected long lastSpecialTime = 0;

    // Material sets for environment checking
    protected static final Set<Material> LIQUID_MATERIALS = new HashSet<>();
    protected static final Set<Material> CLIMBABLE_MATERIALS = new HashSet<>();
    protected static final Set<Material> NON_SOLID_MATERIALS = new HashSet<>();

    static {
        // Setup liquid materials
        LIQUID_MATERIALS.add(Material.WATER);
        LIQUID_MATERIALS.add(Material.LAVA);

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
        NON_SOLID_MATERIALS.add(Material.WATER);
        NON_SOLID_MATERIALS.add(Material.LAVA);
        // Add more non-solid blocks as needed
    }

    public FlyCheck(QuantumAC plugin, PlayerData playerData, String checkName) {
        super(plugin, playerData, checkName, "Movement");
    }

    /**
     * Common method to process fly-related packet checks
     */
    protected void processFlyPacket(PacketEvent event) {
        if (!isMovementPacket(event.getPacketType())) {
            return;
        }

        Player player = event.getPlayer();

        // Skip if player is exempt from checks
        if (isExempt(player)) {
            return;
        }
    }

    /**
     * Check if player is in an exempt state
     */
    protected boolean isExempt(Player player) {
        return player.isFlying() ||
               player.isInsideVehicle() ||
               player.isDead() ||
               player.getGameMode() == GameMode.CREATIVE ||
               player.getGameMode() == GameMode.SPECTATOR;
    }

    /**
     * Check if a packet is a movement packet
     */
    protected boolean isMovementPacket(PacketType type) {
        return type == PacketType.Play.Client.POSITION ||
               type == PacketType.Play.Client.POSITION_LOOK ||
               type == PacketType.Play.Client.LOOK ||
               type == PacketType.Play.Client.FLYING;
    }

    /**
     * Check if the player is in a liquid
     */
    protected boolean isInLiquid(Player player) {
        Block block = player.getLocation().getBlock();
        return LIQUID_MATERIALS.contains(block.getType());
    }

    /**
     * Check if the player is on a climbable block
     */
    protected boolean isOnClimbable(Player player) {
        Block block = player.getLocation().getBlock();
        return CLIMBABLE_MATERIALS.contains(block.getType());
    }

    /**
     * Check if the player is in or on a web
     */
    protected boolean isInWeb(Player player) {
        Location loc = player.getLocation();
        return loc.getBlock().getType() == Material.COBWEB ||
               loc.clone().add(0, 0.1, 0).getBlock().getType() == Material.COBWEB;
    }

    /**
     * Check if the player is near ground
     */
    protected boolean isNearGround(Player player) {
        Location loc = player.getLocation();
        double y = loc.getY();
        
        // Check block at feet and slightly below
        for (double offset = 0; offset <= 0.5; offset += 0.1) {
            Block block = loc.clone().subtract(0, offset, 0).getBlock();
            if (!NON_SOLID_MATERIALS.contains(block.getType())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if the player is near a ceiling
     */
    protected boolean isNearCeiling(Player player) {
        Location loc = player.getLocation();
        
        // Check block above head and slightly above
        for (double offset = 0; offset <= 0.5; offset += 0.1) {
            Block block = loc.clone().add(0, 1.8 + offset, 0).getBlock();
            if (!NON_SOLID_MATERIALS.contains(block.getType())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if player was recently teleported
     */
    protected boolean isRecentlyTeleported() {
        return (System.currentTimeMillis() - lastTeleportTime) < TELEPORT_EXEMPT_TIME;
    }

    /**
     * Check if player was recently damaged
     */
    protected boolean isRecentlyDamaged() {
        return (System.currentTimeMillis() - lastDamageTime) < DAMAGE_EXEMPT_TIME;
    }

    /**
     * Check if player was recently affected by velocity
     */
    protected boolean isRecentlyVelocity() {
        return (System.currentTimeMillis() - lastVelocityTime) < VELOCITY_EXEMPT_TIME;
    }

    /**
     * Check if player was recently in a special condition
     */
    protected boolean isRecentlySpecial() {
        return (System.currentTimeMillis() - lastSpecialTime) < SPECIAL_EXEMPT_TIME;
    }

    /**
     * Set player as recently teleported
     */
    public void onPlayerTeleport() {
        lastTeleportTime = System.currentTimeMillis();
    }

    /**
     * Set player as recently damaged
     */
    public void onPlayerDamage() {
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Set player as recently affected by velocity
     */
    public void onPlayerVelocity(Vector velocity) {
        lastVelocityTime = System.currentTimeMillis();
    }

    /**
     * Set player as recently in a special condition
     */
    public void onSpecialCondition() {
        lastSpecialTime = System.currentTimeMillis();
    }

    /**
     * Calculate a tolerance value based on player's ping and other conditions
     */
    protected double calculateTolerance(Player player) {
        double tolerance = 0.01; // Base tolerance
        
        // Add tolerance based on ping
        int ping = playerData.getPing();
        if (ping > 0) {
            tolerance += Math.min(0.1, ping / 500.0); // Max 0.1 additional tolerance for high ping
        }
        
        // Add tolerance for special conditions
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            tolerance += 0.05;
        }
        
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            tolerance += 0.05;
        }
        
        return tolerance;
    }
} 