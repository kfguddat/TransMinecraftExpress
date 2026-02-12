package com.kfguddat.transminecraftexpress;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.boss.BarColor;

public class Line {
    public final String name;
    public final List<StationEntry> stations = new ArrayList<>();
    public final List<LimiterEntry> limiters = new ArrayList<>();
    public final List<org.bukkit.util.Vector> verifiedPath = new ArrayList<>();
    public StartPoint startPoint;
    public EndPoint endPoint;
    public String hexColor = "#FFFFFF"; // Default white
    public org.bukkit.boss.BarColor barColor = org.bukkit.boss.BarColor.WHITE;
    
    public static class StartPoint {
        public final String world;
        public final int x, y, z;
        public final String dir;
        public StartPoint(String world, int x, int y, int z, String dir) {
            this.world = world;
            this.x = x; this.y = y; this.z = z; this.dir = dir;
        }
    }

    public static class EndPoint {
        public final String world;
        public final int x, y, z;
        public final String dir;
        public EndPoint(String world, int x, int y, int z, String dir) {
            this.world = world;
            this.x = x; this.y = y; this.z = z; this.dir = dir;
        }
    }
    
    // Line-specific data only; global speed settings moved to NetworkManager

    public Line(String name) {
        this.name = name;
    }
    
    public net.md_5.bungee.api.ChatColor getChatColor() {
        try {
            return net.md_5.bungee.api.ChatColor.of(hexColor);
        } catch (Exception e) {
            return net.md_5.bungee.api.ChatColor.WHITE;
        }
    }

    public void saveTo(ConfigurationSection section) {
        section.set("color", hexColor);
        
        // global speed settings are not saved per-line anymore
        
        if (startPoint != null) {
            ConfigurationSection startSec = section.createSection("start");
            startSec.set("world", startPoint.world);
            startSec.set("x", startPoint.x);
            startSec.set("y", startPoint.y);
            startSec.set("z", startPoint.z);
            startSec.set("dir", startPoint.dir);
        }

        if (endPoint != null) {
            ConfigurationSection endSec = section.createSection("end");
            endSec.set("world", endPoint.world);
            endSec.set("x", endPoint.x);
            endSec.set("y", endPoint.y);
            endSec.set("z", endPoint.z);
            endSec.set("dir", endPoint.dir);
        }
        
        // Save stations using station name as key so order is determined by YAML order
        section.set("stations", null);
        ConfigurationSection stationsSec = section.createSection("stations");
        for (StationEntry s : stations) {
            stationsSec.createSection(s.name);
            s.saveTo(stationsSec.getConfigurationSection(s.name));
        }

        // Save limiters using limiter name as key
        section.set("limiters", null);
        ConfigurationSection limitersSec = section.createSection("limiters");
        for (LimiterEntry l : limiters) {
            String key = l.getName();
            limitersSec.createSection(key);
            l.saveTo(limitersSec.getConfigurationSection(key));
        }
    }

    public static Line fromConfig(String name, ConfigurationSection section) {
        Line line = new Line(name);

        String c = section.getString("color");
        if (c != null && !c.isEmpty()) {
            line.hexColor = c;
            line.barColor = matchBarColor(c);
        }

        ConfigurationSection startSec = section.getConfigurationSection("start");
        if (startSec != null) {
            line.startPoint = new StartPoint(
                startSec.getString("world", "world"),
                startSec.getInt("x"),
                startSec.getInt("y"),
                startSec.getInt("z"),
                startSec.getString("dir", "N")
            );
        }

        ConfigurationSection endSec = section.getConfigurationSection("end");
        if (endSec != null) {
            line.endPoint = new EndPoint(
                endSec.getString("world", "world"),
                endSec.getInt("x"),
                endSec.getInt("y"),
                endSec.getInt("z"),
                endSec.getString("dir", "N")
            );
        }

        ConfigurationSection stationsSec = section.getConfigurationSection("stations");
        if (stationsSec != null) {
            for (String key : stationsSec.getKeys(false)) {
                ConfigurationSection ssec = stationsSec.getConfigurationSection(key);
                if (ssec != null) {
                    line.stations.add(StationEntry.fromConfig(ssec, key));
                }
            }
        }

        ConfigurationSection limitersSec = section.getConfigurationSection("limiters");
        if (limitersSec != null) {
            for (String key : limitersSec.getKeys(false)) {
                ConfigurationSection lsec = limitersSec.getConfigurationSection(key);
                if (lsec != null) {
                    line.limiters.add(LimiterEntry.fromConfig(lsec, key));
                }
            }
        }
        return line;
    }
    
    private static BarColor matchBarColor(String hex) {
        if (hex == null || hex.isEmpty()) return BarColor.WHITE;
        try {
            if (!hex.startsWith("#")) {
                // simple names
                try { return BarColor.valueOf(hex.toUpperCase()); } catch(Exception e) {}
                return BarColor.WHITE;
            }
            int rgb = Integer.parseInt(hex.substring(1), 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            
            double min = Double.MAX_VALUE;
            BarColor best = BarColor.WHITE;
            
            // Map of standard BarColors
            java.util.Map<BarColor, int[]> map = new java.util.HashMap<>();
            map.put(BarColor.BLUE, new int[]{0, 0, 255});
            map.put(BarColor.GREEN, new int[]{0, 255, 0});
            map.put(BarColor.PINK, new int[]{255, 105, 180});
            map.put(BarColor.PURPLE, new int[]{128, 0, 128});
            map.put(BarColor.RED, new int[]{255, 0, 0});
            map.put(BarColor.WHITE, new int[]{255, 255, 255});
            map.put(BarColor.YELLOW, new int[]{255, 255, 0});
            
            for (java.util.Map.Entry<BarColor, int[]> e : map.entrySet()) {
                int[] v = e.getValue();
                double d = Math.pow(r - v[0], 2) + Math.pow(g - v[1], 2) + Math.pow(b - v[2], 2);
                if (d < min) { min = d; best = e.getKey(); }
            }
            return best;
        } catch (Exception e) {
            return BarColor.WHITE;
        }
    }
}
