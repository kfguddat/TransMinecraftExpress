package com.kfguddat.transminecraftexpress;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public class StationEntry {
    public final String name;
    public final String worldName;
    public final int x, y, z;
    
    // Transient cache for runtime usage
    public transient int pathIndex = -1;

    public StationEntry(String name, String worldName, int x, int y, int z) {
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Location getLocation(World world) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public void saveTo(ConfigurationSection section) {
        section.set("world", worldName);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
    }

    public static StationEntry fromConfig(ConfigurationSection section, String keyName) {
        String name = keyName;
        String world = section.getString("world", "world");
        int x = section.getInt("x", 0);
        int y = section.getInt("y", 64);
        int z = section.getInt("z", 0);
        StationEntry s = new StationEntry(name, world, x, y, z);
        return s;
    }
}
