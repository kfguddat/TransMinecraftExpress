package com.kfguddat.transminecraftexpress;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class NetworkManager {
    private final Map<String, Line> lines = new java.util.LinkedHashMap<>();
    private java.io.File dataFolder;
    // Global settings (defaults)
    private double accel = 0.05;
    private double decel = 0.08;
    private double scanSpeed = 5.0; // blocks per second
    private double trainSpacing = 1.5;
    private boolean trainsEnabled = true;
    private boolean animalTrainsEnabled = true;
    private boolean suffocationProtection = false;
    private boolean showNextStationBar = true;
    private boolean collision = true; // Default to collision enabled

    // named speed values (blocks per second) — order is preserved so first entry is default
    private final java.util.LinkedHashMap<String, Double> speedVals = new java.util.LinkedHashMap<>();
    // Sign prefix strings (prepended to left side of sign lines 1-3)
    private String signPrefixLine1 = "§b"; // default keeps old coloring for line 1
    private String signPrefixLine2 = "";
    private String signPrefixLine3 = "";

    public void setDataFolder(java.io.File f) {
        this.dataFolder = f;
    }

    public void reload(FileConfiguration config) {
        lines.clear();
        ConfigurationSection linesSec = config.getConfigurationSection("lines");
        if (linesSec != null) {
            for (String key : linesSec.getKeys(false)) {
                // key might be lowercase if saved that way, but Line.fromConfig uses key as name.
                // We should check if the config section has a stored name or trust the key.
                // If we want to support capitalization, we rely on Line.fromConfig to maybe read a "properName" field?
                // Or we just assume the key IS the name (which YAML handles as case-sensitive usually? No wait).
                // YAML keys are case sensitive in Spigot API usually, but if we forced lowercase on save...
                // Let's modify Line.fromConfig to accept the key as the name, but we need to ensure saving preserves it.
                // For now, assume key is the name.
                lines.put(key.toLowerCase(), Line.fromConfig(key, linesSec.getConfigurationSection(key)));
            }
        }
        
        // Load paths if available
        if (dataFolder != null) {
            for (Line line : lines.values()) {
                loadPath(line);
                recalculateLineDistances(line, null);
            }
        }

        // Load global settings
        this.accel = config.getDouble("accel", this.accel);
        this.decel = config.getDouble("decel", this.decel);
        this.scanSpeed = config.getDouble("scan-speed", this.scanSpeed);
        this.trainSpacing = config.getDouble("trainspacing", this.trainSpacing);
        this.trainsEnabled = config.getBoolean("trains", this.trainsEnabled);
        this.animalTrainsEnabled = config.getBoolean("animaltrains", this.animalTrainsEnabled);
        this.suffocationProtection = config.getBoolean("suffocation", this.suffocationProtection);
        this.showNextStationBar = config.getBoolean("nextstationbar", this.showNextStationBar);
        this.collision = config.getBoolean("collision", this.collision);
        // load speed-vals map
        this.speedVals.clear();
        ConfigurationSection sv = config.getConfigurationSection("speed-vals");
        if (sv != null) {
            for (String key : sv.getKeys(false)) {
                double v = sv.getDouble(key, 0.0);
                this.speedVals.put(key, v);
            }
        } else {
            // defaults
            this.speedVals.put("high", 1.6);
            this.speedVals.put("scenic", 0.6);
            this.speedVals.put("station", 0.3);
        }
        this.signPrefixLine1 = config.getString("sign-prefix.line1", this.signPrefixLine1);
        this.signPrefixLine2 = config.getString("sign-prefix.line2", this.signPrefixLine2);
        this.signPrefixLine3 = config.getString("sign-prefix.line3", this.signPrefixLine3);
    }

    public double getAccel() { return accel; }
    public double getDecel() { return decel; }
    public double getScanSpeed() { return scanSpeed; }
    public double getTrainSpacing() { return trainSpacing; }
    public boolean isTrainsEnabled() { return trainsEnabled; }
    public boolean isAnimalTrainsEnabled() { return animalTrainsEnabled; }
    public boolean isSuffocationProtection() { return suffocationProtection; }
    public void setAccel(double v) { this.accel = v; }
    public void setDecel(double v) { this.decel = v; }
    public void setScanSpeed(double v) { this.scanSpeed = v; }
    public void setTrainSpacing(double v) { this.trainSpacing = v; }
    public void setTrainsEnabled(boolean v) { this.trainsEnabled = v; }
    public void setAnimalTrainsEnabled(boolean v) { this.animalTrainsEnabled = v; }
    public void setSuffocationProtection(boolean v) { this.suffocationProtection = v; }
    public boolean isShowNextStationBar() { return showNextStationBar; }
    
    public boolean isCollision() { return collision; }
    public void setCollision(boolean v) { this.collision = v; }
    public void setShowNextStationBar(boolean v) { this.showNextStationBar = v; }

    // Speed-vals map access
    public java.util.Map<String, Double> getSpeedVals() { return java.util.Collections.unmodifiableMap(speedVals); }
    public Double getSpeedVal(String name) { return speedVals.get(name); }
    public void setSpeedVal(String name, double value) { speedVals.put(name, value); }
    public Double removeSpeedVal(String name) { return speedVals.remove(name); }
    // default speed (fallback) is the first entry in the map
    public double getDefaultSpeedVal() {
        if (speedVals.isEmpty()) return 1.6;
        return speedVals.values().iterator().next();
    }

    public String getSignPrefixLine1() { return signPrefixLine1; }
    public String getSignPrefixLine2() { return signPrefixLine2; }
    public String getSignPrefixLine3() { return signPrefixLine3; }

    public void setSignPrefixLine1(String s) { this.signPrefixLine1 = s == null ? "" : s; }
    public void setSignPrefixLine2(String s) { this.signPrefixLine2 = s == null ? "" : s; }
    public void setSignPrefixLine3(String s) { this.signPrefixLine3 = s == null ? "" : s; }

    public Line getLine(String name) {
        return lines.get(name.toLowerCase());
    }

    public Line getOrCreateLine(String name) {
        // Store with lowercase key for lookup, but Line object keeps original case name
        return lines.computeIfAbsent(name.toLowerCase(), k -> new Line(name));
    }
    
    public Map<String, Line> getLines() {
        return lines;
    }

    public boolean moveLine(String name, boolean up) {
        String key = name.toLowerCase();
        if (!lines.containsKey(key)) return false;
        java.util.List<String> keys = new java.util.ArrayList<>(lines.keySet());
        int idx = keys.indexOf(key);
        if (idx == -1) return false;
        int newIdx = up ? idx - 1 : idx + 1;
        if (newIdx < 0 || newIdx >= keys.size()) return false;
        keys.remove(idx);
        keys.add(newIdx, key);
        java.util.LinkedHashMap<String, Line> reordered = new java.util.LinkedHashMap<>();
        for (String k : keys) reordered.put(k, lines.get(k));
        lines.clear();
        lines.putAll(reordered);
        return true;
    }

    public boolean renameLine(String oldName, String newName) {
        String oldKey = oldName.toLowerCase();
        String newKey = newName.toLowerCase();
        
        if (!lines.containsKey(oldKey)) return false;
        if (lines.containsKey(newKey)) return false; // Name taken
        
        // Preserve order
        java.util.List<String> keys = new java.util.ArrayList<>(lines.keySet());
        int index = keys.indexOf(oldKey);
        if (index == -1) return false;
        
        Line oldLine = lines.get(oldKey);
        Line newLine = new Line(newName);
        
        // Copy Properties
        newLine.hexColor = oldLine.hexColor;
        newLine.barColor = oldLine.barColor;
        newLine.startPoint = oldLine.startPoint;
        newLine.endPoint = oldLine.endPoint;
        newLine.stations.addAll(oldLine.stations);
        newLine.limiters.addAll(oldLine.limiters);
        newLine.verifiedPath.addAll(oldLine.verifiedPath);
        
        // Path file rename
        if (dataFolder != null) {
            java.io.File pathsDir = new java.io.File(dataFolder, "paths");
            java.io.File oldFile = new java.io.File(pathsDir, oldLine.name + ".bin");
            java.io.File newFile = new java.io.File(pathsDir, newLine.name + ".bin");
            if (oldFile.exists()) {
                oldFile.renameTo(newFile);
            }
        }
        
        // Rebuild map with order
        keys.set(index, newKey);
        java.util.Map<String, Line> newMap = new java.util.LinkedHashMap<>();
        
        java.util.Map<String, Line> oldMap = new java.util.HashMap<>(lines);
        lines.clear();

        for (String k : keys) {
            if (k.equals(newKey)) {
                lines.put(k, newLine);
            } else {
                lines.put(k, oldMap.get(k));
            }
        }
        
        return true;
    }
    
    public boolean renameStation(Line line, String oldName, String newName) {
         for (int i = 0; i < line.stations.size(); i++) {
             StationEntry s = line.stations.get(i);
             // Allow case insensitive match for finding, but ideally exact
             if (s.name.equalsIgnoreCase(oldName)) {
                 // Check collision within line
                 for (StationEntry other : line.stations) if (other != s && other.name.equalsIgnoreCase(newName)) return false;
                 
                 StationEntry newStation = new StationEntry(newName, s.worldName, s.x, s.y, s.z);
                 // Preserve path index
                 newStation.pathIndex = s.pathIndex; 
                 line.stations.set(i, newStation);
                 return true;
             }
         }
         return false;
    }

    public boolean renameLimiter(Line line, String oldName, String newName) {
         for (int i = 0; i < line.limiters.size(); i++) {
             LimiterEntry l = line.limiters.get(i);
             if (l.name.equalsIgnoreCase(oldName)) {
                 for (LimiterEntry other : line.limiters) if (other != l && other.name.equalsIgnoreCase(newName)) return false;
                 // Since LimiterEntry has public fields but final name, recreate
                 LimiterEntry newLimiter = new LimiterEntry(newName, l.speedSpec, l.worldName, l.x, l.y, l.z, l.direction);
                 line.limiters.set(i, newLimiter);
                 return true;
             }
         }
         return false;
    }

    public void savePath(Line line) {
        if (dataFolder == null) return;
        java.io.File pathsDir = new java.io.File(dataFolder, "paths");
        if (!pathsDir.exists()) pathsDir.mkdirs();
        java.io.File file = new java.io.File(pathsDir, line.name + ".bin");
        try (java.io.DataOutputStream dos = new java.io.DataOutputStream(new java.io.FileOutputStream(file))) {
            dos.writeInt(line.verifiedPath.size());
            for (org.bukkit.util.Vector v : line.verifiedPath) {
                dos.writeInt(v.getBlockX());
                dos.writeInt(v.getBlockY());
                dos.writeInt(v.getBlockZ());
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    public void loadPath(Line line) {
        if (dataFolder == null) return;
        java.io.File file = new java.io.File(new java.io.File(dataFolder, "paths"), line.name + ".bin");
        if (!file.exists()) {
            line.verifiedPath.clear();
            return;
        }
        try (java.io.DataInputStream dis = new java.io.DataInputStream(new java.io.FileInputStream(file))) {
            int count = dis.readInt();
            line.verifiedPath.clear();
            for (int i = 0; i < count; i++) {
                int x = dis.readInt();
                int y = dis.readInt();
                int z = dis.readInt();
                line.verifiedPath.add(new org.bukkit.util.Vector(x, y, z));
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
    
    public void recalculateLineDistances(Line line, org.bukkit.command.CommandSender sender) {
        if (line.verifiedPath.isEmpty()) {
            if (sender != null) sender.sendMessage("§eWarning: No scanned path available for line '" + line.name + "'. Cannot update order.");
            return;
        }
        
        // Collect all control points for sorting
        java.util.List<CP> points = new java.util.ArrayList<>();
        
        // Match stations
        for (StationEntry s : line.stations) {
            int idx = findPathIndex(line.verifiedPath, s.x, s.y, s.z);
            s.pathIndex = idx; // Update transient cache
            if (idx == -1) {
                if (sender != null) sender.sendMessage("§cWarning: Station '" + s.name + "' is not on the scanned track.");
            } else {
                points.add(new CP(s, idx));
            }
        }
        
        // Match limiters
        for (LimiterEntry l : line.limiters) {
            int idx = findPathIndex(line.verifiedPath, l.x, l.y, l.z);
            if (idx == -1) {
                if (sender != null) sender.sendMessage("§cWarning: Limiter '" + l.name + "' is not on the scanned track.");
            } else {
                points.add(new CP(l, idx));
            }
        }
        
        java.util.Collections.sort(points);
        
        // Re-sort the line lists based on path order
        final java.util.List<CP> finalPoints = points;
        line.stations.sort((a, b) -> {
            int idxA = findCPIndex(finalPoints, a);
            int idxB = findCPIndex(finalPoints, b);
            return Integer.compare(idxA, idxB);
        });
        line.limiters.sort((a, b) -> {
            int idxA = findCPIndex(finalPoints, a);
            int idxB = findCPIndex(finalPoints, b);
            return Integer.compare(idxA, idxB);
        });
        
        if (sender != null) sender.sendMessage("§aStation and Limiter order updated based on track path for line '" + line.name + "'.");
        
        autoRenameLimiters(line);
    }

    private void autoRenameLimiters(Line line) {
        // Collect all nodes with their path index
        // We know they are sorted in valid order now (stations and limiters list are path-ordered)
        // But we need to interleave them to know which station precedes which limiter
        
        class Node implements Comparable<Node> {
            final boolean isStation;
            final Object obj;
            final int pIndex;
            Node(boolean s, Object o, int p) { isStation=s; obj=o; pIndex=p; }
            @Override public int compareTo(Node o) { return Integer.compare(pIndex, o.pIndex); }
        }
        
        java.util.List<Node> nodes = new java.util.ArrayList<>();
        for (StationEntry s : line.stations) {
            int idx = findPathIndex(line.verifiedPath, s.x, s.y, s.z);
            if (idx != -1) nodes.add(new Node(true, s, idx));
        }
        for (LimiterEntry l : line.limiters) {
            int idx = findPathIndex(line.verifiedPath, l.x, l.y, l.z);
            if (idx != -1) nodes.add(new Node(false, l, idx));
        }
        java.util.Collections.sort(nodes);
        
        String lastStation = "Start";
        int counter = 1;
        
        java.util.Map<LimiterEntry, LimiterEntry> swaps = new java.util.HashMap<>();
        
        for (Node n : nodes) {
            if (n.isStation) {
                lastStation = ((StationEntry)n.obj).name;
                counter = 1;
            } else {
                LimiterEntry l = (LimiterEntry)n.obj;
                String newName = lastStation + "_" + counter;
                if (!l.name.equals(newName)) {
                    swaps.put(l, new LimiterEntry(newName, l.speedSpec, l.worldName, l.x, l.y, l.z, l.direction));
                }
                counter++;
            }
        }
        
        for (int i=0; i<line.limiters.size(); i++) {
            LimiterEntry old = line.limiters.get(i);
            if (swaps.containsKey(old)) {
                line.limiters.set(i, swaps.get(old));
            }
        }
    }

    // Helper classes and methods for calculation
    
    private static class CP implements Comparable<CP> {
        final Object node;
        int pIndex;
        CP(Object n, int p) { node = n; pIndex = p; }
        @Override public int compareTo(CP o) { return Integer.compare(pIndex, o.pIndex); }
    }
    
    private int findCPIndex(java.util.List<CP> points, Object node) {
        for (CP cp : points) if (cp.node == node) return cp.pIndex;
        return Integer.MAX_VALUE; 
    }

    private int findPathIndex(java.util.List<org.bukkit.util.Vector> path, int x, int y, int z) {
        for (int i = 0; i < path.size(); i++) {
             org.bukkit.util.Vector v = path.get(i);
             // Exact match
             if (v.getBlockX() == x && v.getBlockY() == y && v.getBlockZ() == z) return i;
             // Check with tolerance (e.g. 1 block up/down/radius) in case user clicked slightly off
             if (Math.abs(v.getBlockX() - x) <= 1 && Math.abs(v.getBlockY() - y) <= 2 && Math.abs(v.getBlockZ() - z) <= 1) {
                 return i; // Approximate match
             }
        }
        return -1;
    }

}
