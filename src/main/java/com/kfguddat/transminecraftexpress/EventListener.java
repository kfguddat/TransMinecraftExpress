package com.kfguddat.transminecraftexpress;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Sign;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

public class EventListener implements Listener {
    private final TransMinecraftExpress plugin;
    private final NetworkManager network;
    private final NamespacedKey lineKey;
    private final NamespacedKey indexKey;
    private final NamespacedKey limitKey;

    public EventListener(TransMinecraftExpress plugin, NetworkManager network) {
        this.plugin = plugin;
        this.network = network;
        this.lineKey = new NamespacedKey(plugin, "line");
        this.indexKey = new NamespacedKey(plugin, "node_index");
        this.limitKey = new NamespacedKey(plugin, "speed_limit");
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (!event.getLine(0).equalsIgnoreCase("[TMX]")) return;
        if (!event.getPlayer().hasPermission("tmx.admin")) {
            event.setLine(0, "§c[No Perms]");
            return;
        }

        String lineName = event.getLine(1);
        if (lineName == null || lineName.isEmpty()) {
            event.setLine(0, "§c[Error]");
            event.setLine(1, "No Line Name");
            return;
        }

        String stationName = event.getLine(2);
        Line line = network.getLine(lineName);
        String lastStop = "";
        if (line != null && !line.stations.isEmpty()) {
            boolean circular = false;
            if (line.startPoint != null && line.endPoint != null) {
                if (line.startPoint.world.equals(line.endPoint.world) &&
                    line.startPoint.x == line.endPoint.x &&
                    line.startPoint.y == line.endPoint.y &&
                    line.startPoint.z == line.endPoint.z) {
                    circular = true;
                }
            }
            // For circular lines, show the start station; for linear lines, show the last station
            if (circular) {
                lastStop = line.stations.get(0).name;
            } else {
                lastStop = line.stations.get(line.stations.size() - 1).name;
            }
        }

        // Determine target pixel width based on sign type
        int targetWidth = 90; // Default Oak Sign width (roughly)
        // Check for HangingSign (1.20+)
        try {
            if (event.getBlock().getState() instanceof org.bukkit.block.HangingSign) {
                 targetWidth = 60; // Narrower capacity due to large font/smaller board? 
                 // Actually hanging signs usually have less capacity? User says "too much whitespace", so less width logic needed.
            }
        } catch (NoClassDefFoundError | Exception ignored) {
            // Pre-1.20 support fallback
        }

        // Format lines with configured prefixes and right-aligned content
        event.setLine(0, formatSignLine(network.getSignPrefixLine1(), lineName, targetWidth));
        event.setLine(1, formatSignLine(network.getSignPrefixLine2(), stationName == null ? "" : stationName, targetWidth));
        event.setLine(2, formatSignLine(network.getSignPrefixLine3(), lastStop == null ? "" : lastStop, targetWidth));
        // line 3 left as whatever the player typed (custom text)

        if (line == null) {
            event.getPlayer().sendMessage("§cWarning: Line '" + lineName + "' does not exist (yet).");
        } else if (stationName != null && !stationName.isEmpty()) {
            boolean found = false;
            for (StationEntry s : line.stations) if (s.name.equalsIgnoreCase(stationName)) { found = true; break; }
            if (!found) event.getPlayer().sendMessage("§cWarning: Station '" + stationName + "' not found on line '" + lineName + "'.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        if (event.getClickedBlock().getState() instanceof Sign) {
            Sign sign = (Sign) event.getClickedBlock().getState();
            String first = sign.getLine(0);
            if (first != null) {
                String stripped = ChatColor.stripColor(first);
                for (java.util.Map.Entry<String, Line> e : network.getLines().entrySet()) {
                    Line line = e.getValue();
                    if (stripped.toLowerCase().endsWith(line.name.toLowerCase())) {
                        event.setCancelled(true); // prevent sign editor opening
                        handleStationSign(event.getPlayer(), sign, line.name);
                        return;
                    }
                }
            }
        }
    }

    private void handleStationSign(Player player, Sign sign, String lineName) {
        String rawLine = sign.getLine(1);
        String text = ChatColor.stripColor(rawLine);

        // Remove prefix if configured and present
        String configPrefix = network.getSignPrefixLine2();
        if (configPrefix != null && !configPrefix.isEmpty()) {
            String p = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', configPrefix));
            if (!p.isEmpty() && text.startsWith(p)) {
                text = text.substring(p.length());
            }
        }
        
        String stationName = text.trim();

        Line line = network.getLine(lineName);
        if (line == null) {
            player.sendMessage("§cError: Line '" + lineName + "' not defined.");
            return;
        }

        if (stationName == null || stationName.isEmpty()) {
            player.sendMessage("§cError: Station name missing on sign (line 2).");
            return;
        }

        StationEntry targetStation = null;
        int stationIndex = -1;
        for (int i = 0; i < line.stations.size(); i++) {
            StationEntry s = line.stations.get(i);
            if (s.name.equalsIgnoreCase(stationName)) {
                targetStation = s;
                stationIndex = i;
                break;
            }
        }
        if (targetStation == null) {
            // Attempt to resolve by checking all stations to see if the sign's text ENDS with the station name.
            // This is robust against prefix changes (e.g. old prefix "Line:" vs new prefix "§lStn:")
            for (int i = 0; i < line.stations.size(); i++) {
                StationEntry s = line.stations.get(i);
                if (text.trim().endsWith(s.name) || text.trim().equalsIgnoreCase(s.name)) {
                    targetStation = s;
                    stationIndex = i;
                    stationName = s.name; // Logic update: use the canonical name
                    break;
                }
            }
        }
        
        if (targetStation == null) {
            player.sendMessage("§cError: Station '" + stationName + "' not found on line.");
            return;
        }
        
        // Update sign layout if clicked (fixes outdated signs)
        // Determine target pixel width based on sign type
        int targetWidth = 90; 
        try {
            if (sign.getBlock().getState() instanceof org.bukkit.block.HangingSign) {
                 targetWidth = 60; 
            }
        } catch (NoClassDefFoundError | Exception ignored) {}

        String lastStop = "";
        if (!line.stations.isEmpty()) {
            boolean circular = false;
            if (line.startPoint != null && line.endPoint != null) {
                if (line.startPoint.world.equals(line.endPoint.world) &&
                    line.startPoint.x == line.endPoint.x &&
                    line.startPoint.y == line.endPoint.y &&
                    line.startPoint.z == line.endPoint.z) {
                    circular = true;
                }
            }
            // For circular lines, show the start station; for linear lines, show the last station
            if (circular) {
                lastStop = line.stations.get(0).name;
            } else {
                lastStop = line.stations.get(line.stations.size() - 1).name;
            }
        }
        
        String newL0 = formatSignLine(network.getSignPrefixLine1(), line.name, targetWidth);
        String newL1 = formatSignLine(network.getSignPrefixLine2(), targetStation.name, targetWidth); // Use canonical station name
        String newL2 = formatSignLine(network.getSignPrefixLine3(), lastStop, targetWidth);
        
        boolean changed = false;
        if (!sign.getLine(0).equals(newL0)) { sign.setLine(0, newL0); changed = true; }
        if (!sign.getLine(1).equals(newL1)) { sign.setLine(1, newL1); changed = true; }
        if (!sign.getLine(2).equals(newL2)) { sign.setLine(2, newL2); changed = true; }
        
        if (changed) {
            sign.update();
        }

        Location spawnLoc = targetStation.getLocation(player.getWorld());
        Minecart cart = (Minecart) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.MINECART);

        cart.getPersistentDataContainer().set(lineKey, PersistentDataType.STRING, lineName);
        int nextIndex = stationIndex + 1;
        if (nextIndex >= line.stations.size()) nextIndex = line.stations.size();
        cart.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, nextIndex);
        
        // Set initial speed limit to "station" speedval
        Double stationSpeed = network.getSpeedVal("station");
        if (stationSpeed == null) stationSpeed = 0.3; // fallback 6 m/s
        
        // Convert blocks/sec to blocks/tick
        cart.getPersistentDataContainer().set(limitKey, PersistentDataType.DOUBLE, stationSpeed / 20.0);

        cart.addPassenger(player);
    }
    
    @EventHandler
    public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
        if (!network.isCollision()) {
             Vehicle v = event.getVehicle();
             if (v instanceof Minecart && v.getPersistentDataContainer().has(lineKey, PersistentDataType.STRING)) {
                 event.setCancelled(true);
                 event.setCollisionCancelled(true); // Ensure collision physics are disabled
             }
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        Vehicle v = event.getVehicle();
        if (v instanceof Minecart) {
            if (v.getPersistentDataContainer().has(lineKey, PersistentDataType.STRING)) {
                Bukkit.getScheduler().runTask(plugin, v::remove);
            }
        }
    }

    // Build a sign line with `prefix` on the left and `content` right-aligned
    private String formatSignLine(String prefix, String content, int targetWidth) {
        if (prefix == null) prefix = "";
        if (content == null) content = "";
        
        String visiblePrefix = ChatColor.stripColor(prefix);
        String visibleContent = ChatColor.stripColor(content);
        
        int pWidth = getPixelWidth(visiblePrefix);
        int cWidth = getPixelWidth(visibleContent);
        int spWidth = getPixelWidth(" ");
        
        int remaining = targetWidth - pWidth - cWidth;
        int spaces = 0;
        
        if (remaining > 0) {
            spaces = remaining / spWidth;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        for (int i = 0; i < spaces; i++) sb.append(' ');
        sb.append(content);
        
        return sb.toString();
    }
    
    // Estimates pixel width of a string using default Minecraft font logic
    private int getPixelWidth(String s) {
        int w = 0;
        boolean bold = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Handle color codes check (standard format §x)
            if (c == '§') {
                if (i + 1 < s.length()) {
                     char code = s.charAt(i+1);
                     if (code == 'l' || code == 'L') bold = true;
                     else if (code == 'r' || code == 'R') bold = false; // Reset? Usually clears
                     else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || (code >= 'A' && code <= 'F')) bold = false; // Color resets bold
                     i++;
                }
                continue;
            }
            
            int charW = 6; // Default
            if (c == ' ' || c == 't' || c == 'I' || c == '[' || c == ']') charW = 4;
            else if (c == 'f' || c == 'k') charW = 5;
            else if (c == 'l' || c == '\'') charW = 3;
            else if (c == '!' || c == '|' || c == '.' || c == ',' || c == ':' || c == ';' || c == 'i') charW = 2;
            else if (c == '@' || c == '~') charW = 7;
            
            if (bold) charW++;
            
            w += charW;
        }
        return w;
    }
}
