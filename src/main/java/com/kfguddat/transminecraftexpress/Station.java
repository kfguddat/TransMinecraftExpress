package com.kfguddat.transminecraftexpress;

import org.bukkit.Location;
import org.bukkit.World;

public final class Station {
    private final String line;
    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;

    public Station(String line, String name, String worldName, double x, double y, double z) {
        this.line = line;
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getLine() {
        return line;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }
}
