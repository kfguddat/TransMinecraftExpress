package com.kfguddat.transminecraftexpress;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public class LimiterEntry {
    // name is the key used in YAML and is a human-friendly identifier
    public final String name;
    // speedSpec is either the name of a speed-val (e.g. "high") or a numeric string like "1.2"
    public String speedSpec;
    public final String worldName;
    public final String direction; // "N", "S", "E", "W", or "ALL"
    public final int x, y, z;

    // Modified Constructor: Name is assumed to be set/overwritten later if needed, but we keep it final for immutable semantics locally.
    // However, if we need to rename, we create a new instance (as done in renameLimiter).
    public LimiterEntry(String name, String speedSpec, String worldName, int x, int y, int z, String direction) {
        this.name = name;
        this.speedSpec = speedSpec;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.direction = (direction == null || direction.isEmpty()) ? "ALL" : direction;
    }

    public void setSpeedSpec(String s) { this.speedSpec = s; }

    public String getName() { return name; }

    public Location getLocation(World world) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public void saveTo(ConfigurationSection section) {
        section.set("speed", speedSpec);
        section.set("world", worldName);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("direction", direction);
    }

    public static LimiterEntry fromConfig(ConfigurationSection section, String keyName) {
        String world = section.getString("world", "world");
        int x = section.getInt("x", 0);
        int y = section.getInt("y", 64);
        int z = section.getInt("z", 0);
        String direction = section.getString("direction", "ALL");
        String speed = null;
        if (section.isString("speed")) {
            speed = section.getString("speed");
        } else if (section.isString("speed-type")) {
            // legacy format: convert to speed spec (HIGH/SCENIC/STATION or numeric for CUSTOM)
            String type = section.getString("speed-type", "STATION");
            if ("CUSTOM".equalsIgnoreCase(type)) {
                double custom = section.getDouble("custom-speed", 0.0);
                speed = Double.toString(custom);
            } else {
                speed = type.toLowerCase();
            }
        }
        if (speed == null) speed = "";
        LimiterEntry l = new LimiterEntry(keyName, speed, world, x, y, z, direction);
        return l;
    }
}
