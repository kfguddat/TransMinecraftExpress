package com.kfguddat.transminecraftexpress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class StationRegistry {
    public static final String LINE_TAG_PREFIX = "ice_line:";

    private final Map<String, LineSettings> lineSettings = new HashMap<>();
    private final Map<String, Map<String, Station>> stationsByLine = new HashMap<>();
    private final Map<String, Map<String, List<Station>>> stationsByLineWorld = new HashMap<>();

    public void reload(FileConfiguration config) {
        lineSettings.clear();
        stationsByLine.clear();
        stationsByLineWorld.clear();

        ConfigurationSection linesSection = config.getConfigurationSection("lines");
        if (linesSection != null) {
            for (String lineKey : linesSection.getKeys(false)) {
                String line = normalize(lineKey);
                lineSettings.put(line, LineSettings.fromConfig(linesSection.getConfigurationSection(lineKey)));
            }
        }

        if (!lineSettings.containsKey("default")) {
            lineSettings.put("default", LineSettings.DEFAULT);
        }

        ConfigurationSection stationsSection = config.getConfigurationSection("stations");
        if (stationsSection == null) {
            return;
        }

        for (String lineKey : stationsSection.getKeys(false)) {
            String line = normalize(lineKey);
            ConfigurationSection lineStations = stationsSection.getConfigurationSection(lineKey);
            if (lineStations == null) {
                continue;
            }
            for (String stationKey : lineStations.getKeys(false)) {
                ConfigurationSection stationSection = lineStations.getConfigurationSection(stationKey);
                if (stationSection == null) {
                    continue;
                }
                String worldName = stationSection.getString("world", "world");
                double x = stationSection.getDouble("x", 0.0D);
                double y = stationSection.getDouble("y", 64.0D);
                double z = stationSection.getDouble("z", 0.0D);
                Station station = new Station(line, stationKey, worldName, x, y, z);
                stationsByLine
                        .computeIfAbsent(line, key -> new HashMap<>())
                        .put(stationKey.toLowerCase(Locale.ROOT), station);
                stationsByLineWorld
                        .computeIfAbsent(line, key -> new HashMap<>())
                        .computeIfAbsent(worldName, key -> new ArrayList<>())
                        .add(station);
            }
        }
    }

    public LineSettings getLineSettings(String line) {
        return lineSettings.getOrDefault(normalize(line), LineSettings.DEFAULT);
    }

    public Set<String> getLines() {
        return Collections.unmodifiableSet(lineSettings.keySet());
    }

    public List<Station> getStationsForLine(String line) {
        Map<String, Station> stations = stationsByLine.get(normalize(line));
        if (stations == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(stations.values());
    }

    public Station getStation(String line, String stationName) {
        Map<String, Station> stations = stationsByLine.get(normalize(line));
        if (stations == null) {
            return null;
        }
        return stations.get(stationName.toLowerCase(Locale.ROOT));
    }

    public double distanceToNearestStation(String line, Location location, double maxDistance) {
        World world = location.getWorld();
        if (world == null) {
            return -1.0D;
        }

        Map<String, List<Station>> byWorld = stationsByLineWorld.get(normalize(line));
        if (byWorld == null) {
            return -1.0D;
        }

        List<Station> stations = byWorld.get(world.getName());
        if (stations == null || stations.isEmpty()) {
            return -1.0D;
        }

        double maxDistanceSquared = maxDistance * maxDistance;
        double bestSquared = -1.0D;

        for (Station station : stations) {
            Location stationLocation = station.toLocation(world);
            double distanceSquared = stationLocation.distanceSquared(location);
            if (distanceSquared > maxDistanceSquared) {
                continue;
            }
            if (bestSquared < 0.0D || distanceSquared < bestSquared) {
                bestSquared = distanceSquared;
            }
        }

        if (bestSquared < 0.0D) {
            return -1.0D;
        }
        return Math.sqrt(bestSquared);
    }

    public static String lineFromTags(Set<String> tags) {
        for (String tag : tags) {
            if (tag.startsWith(LINE_TAG_PREFIX)) {
                return normalize(tag.substring(LINE_TAG_PREFIX.length()));
            }
        }
        return "default";
    }

    public static String normalize(String line) {
        if (line == null || line.trim().isEmpty()) {
            return "default";
        }
        return line.trim().toLowerCase(Locale.ROOT);
    }
}
