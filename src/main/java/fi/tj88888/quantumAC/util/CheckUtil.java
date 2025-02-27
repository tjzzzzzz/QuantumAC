package fi.tj88888.quantumAC.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class CheckUtil {

    /**
     * Checks if a player is in liquid (water or lava).
     */
    public static boolean isInLiquid(Player player) {
        Material material = player.getLocation().getBlock().getType();
        return material == Material.WATER || material == Material.LAVA;
    }

    /**
     * Checks if a player is on ice.
     */
    public static boolean isOnIce(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return below.getBlock().getType() == Material.ICE;
    }

    /**
     * Checks if a player is on packed ice.
     */
    public static boolean isOnPackedIce(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return below.getBlock().getType() == Material.PACKED_ICE;
    }

    /**
     * Checks if a player is on blue ice.
     */
    public static boolean isOnBlueIce(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return below.getBlock().getType() == Material.BLUE_ICE;
    }

    /**
     * Checks if a player is on slime blocks.
     */
    public static boolean isOnSlime(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return below.getBlock().getType() == Material.SLIME_BLOCK;
    }

    /**
     * Checks if a player is on soul sand.
     */
    public static boolean isOnSoulSand(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return below.getBlock().getType() == Material.SOUL_SAND;
    }

    /**
     * Checks if a player is on stairs.
     */
    public static boolean isOnStairs(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        Material material = below.getBlock().getType();
        return material.name().contains("STAIRS");
    }

    /**
     * Checks if a player is on a slab.
     */
    public static boolean isOnSlab(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        Material material = below.getBlock().getType();
        return material.name().contains("SLAB");
    }

    public static boolean isCloseToClimbable(Player player) {
        Location current = player.getLocation();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Material blockType = current.clone().add(x, y, z).getBlock().getType();
                    if (blockType == Material.LADDER || blockType == Material.VINE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isCloseToBlock(Player player, Material material) {
        Location current = player.getLocation();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (current.clone().add(x, y, z).getBlock().getType() == material) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isInMaterial(Player player, Material material) {
        Location location = player.getLocation();
        Material blockType = location.getBlock().getType();
        return blockType == material;
    }

}