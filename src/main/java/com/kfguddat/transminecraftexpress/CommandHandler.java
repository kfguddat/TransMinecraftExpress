package com.kfguddat.transminecraftexpress;

import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.ChatColor;

import org.bukkit.block.BlockFace; // Import needed

public class CommandHandler implements CommandExecutor {
    private final TransMinecraftExpress plugin;
    private final NetworkManager network;

    public CommandHandler(TransMinecraftExpress plugin, NetworkManager network) {
        this.plugin = plugin;
        this.network = network;
    }

    private void showManageLines(CommandSender sender) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            for (String name : network.getLines().keySet()) {
                TextComponent tc = new TextComponent("§b" + name + " ");
                TextComponent up = new TextComponent("§a▲");
                up.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx manage move line " + name + " up"));
                TextComponent down = new TextComponent(" §a▼");
                down.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx manage move line " + name + " down"));
                TextComponent del = new TextComponent(" §c✖");
                del.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx delete line " + name));
                TextComponent ren = new TextComponent(" §e✎");
                ren.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tmx rename line " + name + " "));
                tc.addExtra(up);
                tc.addExtra(down);
                tc.addExtra(del);
                tc.addExtra(ren);
                p.spigot().sendMessage(tc);
            }
        } else {
            for (String name : network.getLines().keySet()) sender.sendMessage(name);
        }
    }

    private void showManageLine(String lineName, CommandSender sender) {
        Line line = network.getLine(lineName);
        if (line == null) { sender.sendMessage("§cLine not found."); return; }
        sender.sendMessage("§aManage Line: " + lineName);
        sender.sendMessage("Stations:");
        for (StationEntry s : line.stations) {
            if (sender instanceof Player) {
                TextComponent tc = new TextComponent("§e" + s.name + " ");
                TextComponent up = new TextComponent("§a▲");
                up.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx manage move station " + lineName + " " + s.name + " up"));
                TextComponent down = new TextComponent(" §a▼");
                down.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx manage move station " + lineName + " " + s.name + " down"));
                TextComponent del = new TextComponent(" §c✖");
                del.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx delete station " + lineName + " " + s.name));
                TextComponent ren = new TextComponent(" §e✎");
                ren.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tmx rename station " + lineName + " " + s.name + " "));
                tc.addExtra(up);
                tc.addExtra(down);
                tc.addExtra(del);
                tc.addExtra(ren);
                ((Player) sender).spigot().sendMessage(tc);
            } else {
                sender.sendMessage(s.name + " @ " + s.x + "," + s.y + "," + s.z);
            }
        }
        sender.sendMessage("Limiters:");
        for (LimiterEntry l : line.limiters) {
            if (sender instanceof Player) {
                TextComponent tc = new TextComponent("§6" + l.getName() + " (" + l.speedSpec + ") ");
                TextComponent up = new TextComponent("§a▲");
                up.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx manage move limit " + lineName + " " + l.getName() + " up"));
                TextComponent down = new TextComponent(" §a▼");
                down.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx manage move limit " + lineName + " " + l.getName() + " down"));
                TextComponent del = new TextComponent(" §c✖");
                del.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx delete limit " + lineName + " " + l.getName()));
                TextComponent ren = new TextComponent(" §e✎");
                ren.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tmx rename limit " + lineName + " " + l.getName() + " "));
                tc.addExtra(up);
                tc.addExtra(down);
                tc.addExtra(del);
                tc.addExtra(ren);
                ((Player) sender).spigot().sendMessage(tc);
            } else {
                sender.sendMessage(l.getName() + " (" + l.speedSpec + ") @ " + l.x + "," + l.y + "," + l.z);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tmx.admin")) return true;

        if (args.length == 0) return false;

        String action = args[0].toLowerCase(Locale.ROOT);

        // /tmx scan <line>
        if (action.equals("scan")) {
            if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
            if (args.length < 2) { sender.sendMessage("Usage: /tmx scan <line>"); return true; }
            String lineName = args[1];
            Line line = network.getLine(lineName);
            if (line == null) { sender.sendMessage("§cLine not found."); return true; }
            if (line.stations.isEmpty() && line.startPoint == null) { sender.sendMessage("§cLine has no stations or start point."); return true; }
            
            Player p = (Player) sender;
            Location spawnLoc;
            
            if (line.startPoint != null) {
                if (!line.startPoint.world.equals(p.getWorld().getName())) {
                     sender.sendMessage("§cStart point is in world " + line.startPoint.world);
                     return true;
                }
                spawnLoc = new Location(p.getWorld(), line.startPoint.x + 0.5, line.startPoint.y, line.startPoint.z + 0.5);
                // set yaw from direction (inverted to fix opposite direction bug)
                float yaw = 180; 
                if ("N".equals(line.startPoint.dir)) yaw = 0;       // N -> 0 (South)
                else if ("S".equals(line.startPoint.dir)) yaw = 180; // S -> 180 (North)
                else if ("E".equals(line.startPoint.dir)) yaw = 90;  // E -> 90 (West)
                else if ("W".equals(line.startPoint.dir)) yaw = -90; // W -> -90 (East)
                spawnLoc.setYaw(yaw);
            } else {
                StationEntry start = line.stations.get(0);
                if (!start.worldName.equals(p.getWorld().getName())) {
                     sender.sendMessage("§cFirst station is in world " + start.worldName);
                     return true;
                }
                spawnLoc = start.getLocation(p.getWorld());
            }

            // Clear existing path before scan
            line.verifiedPath.clear();
            
            // Explicitly record the start point to ensure index 0 matches the start station
            org.bukkit.util.Vector startVec = new org.bukkit.util.Vector(spawnLoc.getBlockX(), spawnLoc.getBlockY(), spawnLoc.getBlockZ());
            line.verifiedPath.add(startVec);
            
            org.bukkit.entity.Minecart cart = p.getWorld().spawn(spawnLoc, org.bukkit.entity.Minecart.class);
            
            // Start Direction logic
            if (line.startPoint != null) {
                org.bukkit.util.Vector v = new org.bukkit.util.Vector(0,0,0);
                String d = line.startPoint.dir; // N, S, E, W
                // Coordinate system: N=-Z, S=+Z, E=+X, W=-X
                if ("N".equals(d)) v.setZ(-1);
                else if ("S".equals(d)) v.setZ(1);
                else if ("E".equals(d)) v.setX(1);
                else if ("W".equals(d)) v.setX(-1);
                
                // Set pure velocity vector. The Controller will normalize this.
                cart.setVelocity(v.multiply(0.1));
                
                // Visual Yaw: N(180), S(0), E(-90), W(90)
                float yaw = 0; 
                if ("N".equals(d)) yaw = 180;        
                else if ("S".equals(d)) yaw = 0;     
                else if ("E".equals(d)) yaw = -90;  
                else if ("W".equals(d)) yaw = 90; 
                
                Location l = cart.getLocation();
                l.setYaw(yaw);
                cart.teleport(l);
            }

            org.bukkit.NamespacedKey lineKey = new org.bukkit.NamespacedKey(plugin, "line");
            org.bukkit.NamespacedKey scanKey = new org.bukkit.NamespacedKey(plugin, "scanning");
            
            cart.getPersistentDataContainer().set(lineKey, PersistentDataType.STRING, line.name);
            cart.getPersistentDataContainer().set(scanKey, PersistentDataType.BYTE, (byte) 1);
            
            cart.addPassenger(p);
            sender.sendMessage("§aScan started for line '" + line.name + "'. Do not exit the cart.");
            return true;
        }
        
        // /tmx rename <line|station|limit> ...
        if (action.equals("rename")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /tmx rename <line|station|limit> ...");
                return true;
            }
            String what = args[1].toLowerCase(Locale.ROOT);
            
            if (what.equals("line")) {
                if (args.length < 4) { sender.sendMessage("Usage: /tmx rename line <oldName> <newName>"); return true; }
                String oldName = args[2];
                String newName = args[3];
                // Check if old exists
                Line oldLine = network.getLine(oldName);
                if (oldLine == null) { sender.sendMessage("§cLine '" + oldName + "' not found."); return true; }
                
                if (network.renameLine(oldName, newName)) {
                    plugin.saveLines();
                    sender.sendMessage("§aLine renamed from '" + oldLine.name + "' to '" + newName + "'.");
                } else {
                    sender.sendMessage("§cRename failed. (Name '" + newName + "' taken?)");
                }
                return true;
            }
            if (what.equals("station")) {
                if (args.length < 5) { sender.sendMessage("Usage: /tmx rename station <line> <oldName> <newName>"); return true; }
                String lineName = args[2];
                String oldName = args[3];
                String newName = args[4];
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine '" + lineName + "' not found."); return true; }
                
                if (network.renameStation(line, oldName, newName)) {
                    plugin.saveLines();
                    sender.sendMessage("§aStation renamed to '" + newName + "'.");
                } else {
                    sender.sendMessage("§cRename failed. (Station not found or name taken?)");
                }
                return true;
            }
            if (what.equals("limit") || what.equals("limiter")) {
                if (args.length < 5) { sender.sendMessage("Usage: /tmx rename limit <line> <oldName> <newName>"); return true; }
                String lineName = args[2];
                String oldName = args[3];
                String newName = args[4];
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine '" + lineName + "' not found."); return true; }
                
                if (network.renameLimiter(line, oldName, newName)) {
                    plugin.saveLines();
                    sender.sendMessage("§aLimiter renamed to '" + newName + "'.");
                } else {
                    sender.sendMessage("§cRename failed. (Limiter not found or name taken?)");
                }
                return true;
            }
            sender.sendMessage("Unknown rename target. Use line/station/limit.");
            return true;
        }

        // /tmx reload
        if (action.equals("reload")) {
            plugin.reloadConfig();
            network.reload(plugin.getConfig());
            sender.sendMessage("§aConfig reloaded.");
            return true;
        }

        // /tmx create ...
        if (action.equals("create")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /tmx create <line|station|limit|start|end> ...");
                return true;
            }
            String what = args[1].toLowerCase(Locale.ROOT);
            if (what.equals("line")) {
                if (args.length < 4) {
                    sender.sendMessage("Usage: /tmx create line [name] [hexColor]");
                    return true;
                }
                String name = args[2];
                String hex = args[3];
                Line l = network.getOrCreateLine(name);
                l.hexColor = hex;
                // Reload config to parse color to barColor fully, or rely on save logic
                plugin.saveLines();
                network.reload(plugin.getConfig());
                sender.sendMessage("§aLine '" + name + "' created/updated with color " + hex + ".");
                return true;
            }
            
            if (what.equals("start")) {
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                if (args.length < 3) { sender.sendMessage("Usage: /tmx create start [line]"); return true; }
                String lineName = args[2];
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine not found."); return true; }

                Player p = (Player) sender;
                Location loc = p.getLocation();
                BlockFace face = p.getFacing();
                String d = "N";
                switch (face) {
                    case NORTH: d = "N"; break;
                    case SOUTH: d = "S"; break;
                    case EAST: d = "E"; break;
                    case WEST: d = "W"; break;
                    default: d = "N"; break;
                }

                line.startPoint = new Line.StartPoint(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), d);
                plugin.saveLines();
                sender.sendMessage("§aStart point set for line '" + line.name + "' at current location (Dir: " + d + ").");
                return true;
            }

            if (what.equals("end")) {
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                if (args.length < 3) { sender.sendMessage("Usage: /tmx create end [line]"); return true; }
                String lineName = args[2];
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine not found."); return true; }

                Player p = (Player) sender;
                Location loc = p.getLocation();
                BlockFace face = p.getFacing();
                String d = "N";
                switch (face) {
                    case NORTH: d = "N"; break;
                    case SOUTH: d = "S"; break;
                    case EAST: d = "E"; break;
                    case WEST: d = "W"; break;
                    default: d = "N"; break;
                }

                line.endPoint = new Line.EndPoint(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), d);
                plugin.saveLines();
                sender.sendMessage("§aEnd point set for line '" + line.name + "' at current location (Dir: " + d + ").");
                return true;
            }

            if (what.equals("station")) {
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                // New args: /tmx create station <line> <name> [isStart?]
                if (args.length < 4) { sender.sendMessage("Usage: /tmx create station [line] [stationName] <isStart?>"); return true; }
                String lineName = args[2];
                String stationName = args[3];
                boolean isStart = args.length >= 5 && (args[4].equalsIgnoreCase("true") || args[4].equalsIgnoreCase("yes") || args[4].equalsIgnoreCase("start"));
                
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine not found."); return true; }

                Player p = (Player) sender;
                Location loc = p.getLocation();
                StationEntry s = new StationEntry(stationName, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                
                // Simply add; sorting happens via scan
                line.stations.add(s);
                sender.sendMessage("§aStation '" + stationName + "' added to line '" + lineName + "'. Run /tmx scan to update order.");
                
                if (isStart) {
                    BlockFace face = p.getFacing();
                    String d = "N";
                    switch (face) {
                        case NORTH: d = "N"; break;
                        case SOUTH: d = "S"; break;
                        case EAST: d = "E"; break;
                        case WEST: d = "W"; break;
                        default: d = "N"; break;
                    }
                    line.startPoint = new Line.StartPoint(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), d);
                    sender.sendMessage("§aSet as Start Point (Direction: " + d + ")");
                }

                network.recalculateLineDistances(line, sender); // Will also sort if path exists
                plugin.saveLines();
                return true;
            }

            if (what.equals("limit") || what.equals("limiter")) {
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                // New args: /tmx create limit <line> <speed> (Name removed)
                if (args.length < 4) { sender.sendMessage("Usage: /tmx create limit [line] [speed|speedName]"); return true; }
                String lineName = args[2];
                String speedSpec = args[3];

                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine not found."); return true; }
                
                // Disallow if no stations exist
                if (line.stations.isEmpty()) { 
                    sender.sendMessage("§cCannot create limiter: Line has no stations. Create stations first so limiters can be named automatically."); 
                    return true; 
                }
                
                Player p = (Player) sender;
                Location loc = p.getLocation();
                
                // Temporary name, will be updated by auto-rename during recalculateLineDistances
                // We'll use a unique temp name to avoid collisions until then
                String tempName = "Pending_" + System.currentTimeMillis();
                
                LimiterEntry le = new LimiterEntry(tempName, speedSpec.toLowerCase(), loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                
                line.limiters.add(le);
                
                network.recalculateLineDistances(line, sender);
                plugin.saveLines();
                
                // Find and report the NEW name
                // Since recalculateLineDistances possibly replaced the object entirely, we loop to find matching coords
                String finalName = "Unknown";
                for (LimiterEntry l : line.limiters) {
                    if (l.x == le.x && l.y == le.y && l.z == le.z) {
                        finalName = l.getName();
                        break;
                    }
                }
                
                sender.sendMessage("§aLimiter created: " + finalName + " (" + speedSpec + ")");
                return true;
            }

            sender.sendMessage("Unknown create target. Use line/station/limit/start/end.");
            return true;
        }

        // /tmx delete ...
        if (action.equals("delete")) {
            if (args.length < 2) { sender.sendMessage("Usage: /tmx delete <line|station|limit> ..."); return true; }
            String what = args[1].toLowerCase(Locale.ROOT);
            if (what.equals("line")) {
                if (args.length < 3) { sender.sendMessage("Usage: /tmx delete line [name]"); return true; }
                String name = args[2];
                Map<String, Line> map = network.getLines();
                if (map.remove(name.toLowerCase()) != null) {
                    plugin.saveLines();
                    sender.sendMessage("§aLine '" + name + "' deleted.");
                    showManageLines(sender);
                } else sender.sendMessage("§cLine not found.");
                return true;
            }
            if (what.equals("station")) {
                if (args.length < 4) { sender.sendMessage("Usage: /tmx delete station [line] [stationName]"); return true; }
                String lineName = args[2];
                String stationName = args[3];
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine not found."); return true; }
                int rem = -1;
                for (int i = 0; i < line.stations.size(); i++) { if (line.stations.get(i).name.equalsIgnoreCase(stationName)) { rem = i; break; } }
                if (rem == -1) { sender.sendMessage("§cStation not found."); return true; }
                network.recalculateLineDistances(line, sender);
                line.stations.remove(rem);
                plugin.saveLines();
                sender.sendMessage("§aStation removed.");
                showManageLine(lineName, sender);
                return true;
            }
            if (what.equals("speedval")) {
                if (args.length < 3) { sender.sendMessage("Usage: /tmx delete speedval [name]"); return true; }
                String name = args[2].toLowerCase(Locale.ROOT);
                if (name.equals("station")) {
                    sender.sendMessage("§cCannot delete mandatory 'station' speedval.");
                    return true;
                }
                Double orig = network.getSpeedVal(name);
                if (orig == null) { sender.sendMessage("§cSpeedval not found."); return true; }
                // Replace usages in all limiters with numeric value
                for (Line ln : network.getLines().values()) {
                    for (LimiterEntry le : ln.limiters) {
                        if (le.speedSpec != null && le.speedSpec.equalsIgnoreCase(name)) {
                            le.setSpeedSpec(Double.toString(orig));
                        }
                    }
                }
                network.removeSpeedVal(name);
                plugin.saveLines();
                sender.sendMessage("§aRemoved speedval '" + name + "' and replaced usages with " + orig);
                return true;
            }
            if (what.equals("limit") || what.equals("limiter")) {
                if (args.length < 4) { sender.sendMessage("Usage: /tmx delete limit [line] [indexOrName]"); return true; }
                String lineName = args[2];
                String name = args[3];
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine not found."); return true; }
                int rem = -1;
                for (int i = 0; i < line.limiters.size(); i++) { if (line.limiters.get(i).getName().equalsIgnoreCase(name)) { rem = i; break; } }
                if (rem == -1) { sender.sendMessage("§cLimiter not found."); return true; }
                network.recalculateLineDistances(line, sender);
                line.limiters.remove(rem);
                plugin.saveLines();
                sender.sendMessage("§aLimiter removed.");
                showManageLine(lineName, sender);
                return true;
            }
            sender.sendMessage("Unknown delete target. Use line/station/limit.");
            return true;
        }

        // /tmx list [line]
        if (action.equals("list")) {
            if (args.length == 1) {
                // list lines clickable
                for (String name : network.getLines().keySet()) {
                    if (sender instanceof Player) {
                        TextComponent tc = new TextComponent("§b" + name);
                        tc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx list " + name));
                        ((Player) sender).spigot().sendMessage(tc);
                    } else {
                        sender.sendMessage(name);
                    }
                }
                return true;
            } else {
                String lineName = args[1];
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine not found."); return true; }
                sender.sendMessage("§aLine: " + lineName);
                sender.sendMessage("Stations:");
                for (StationEntry s : line.stations) {
                    sender.sendMessage(" - " + s.name + " @ " + s.x + "," + s.y + "," + s.z);
                }
                sender.sendMessage("Limiters:");
                for (LimiterEntry l : line.limiters) {
                    sender.sendMessage(" - " + l.getName() + " (" + l.speedSpec + ") @ " + l.x + "," + l.y + "," + l.z);
                }
                return true;
            }
        }

        // /tmx set <key> <value>
        if (action.equals("set")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /tmx set <accel|decel|scanspeed|speedval> ...");
                return true;
            }
            String key = args[1].toLowerCase(Locale.ROOT);
            if (key.equals("accel") || key.equals("decel") || key.equals("scanspeed")) {
                if (args.length < 3) { sender.sendMessage("Usage: /tmx set " + key + " <value>"); return true; }
                double val;
                try { val = Double.parseDouble(args[2]); } catch (Exception e) { sender.sendMessage("§cInvalid number."); return true; }
                if (key.equals("accel")) network.setAccel(val);
                else if (key.equals("decel")) network.setDecel(val);
                else network.setScanSpeed(val);
                plugin.saveLines();
                sender.sendMessage("§aSet " + key + " = " + val);
                return true;
            }
            if (key.equals("collision")) {
                if (args.length < 3) { sender.sendMessage("Usage: /tmx set collision <true|false>"); return true; }
                boolean v = Boolean.parseBoolean(args[2]);
                network.setCollision(v);
                plugin.saveLines();
                sender.sendMessage("§aSet collision = " + v);
                return true;
            }
            if (key.equals("nextstationbar")) {
                if (args.length < 3) { sender.sendMessage("Usage: /tmx set nextstationbar <true|false>"); return true; }
                boolean v = Boolean.parseBoolean(args[2]);
                network.setShowNextStationBar(v);
                plugin.saveLines();
                sender.sendMessage("§aSet nextstationbar = " + v);
                return true;
            }
            if (key.equals("speedval")) {
                if (args.length < 4) { sender.sendMessage("Usage: /tmx set speedval <name> <value>"); return true; }
                String name = args[2].toLowerCase(Locale.ROOT);
                double val;
                try { val = Double.parseDouble(args[3]); } catch (Exception e) { sender.sendMessage("§cInvalid number."); return true; }
                network.setSpeedVal(name, val);
                plugin.saveLines();
                sender.sendMessage("§aSet speedval '" + name + "' = " + val);
                return true;
            }
            if (key.equals("signprefix")) {
                // Usage: /tmx set signprefix <line1|line2|line3> <text>
                if (args.length < 4) { sender.sendMessage("Usage: /tmx set signprefix <1|2|3> <text>"); return true; }
                int lineNum;
                try { lineNum = Integer.parseInt(args[2]); } catch (NumberFormatException e) { sender.sendMessage("§cInvalid line number. Use 1, 2, or 3."); return true; }
                
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < args.length; i++) sb.append(args[i]).append(" ");
                String text = ChatColor.translateAlternateColorCodes('&', sb.toString().trim());
                
                if (lineNum == 1) network.setSignPrefixLine1(text);
                else if (lineNum == 2) network.setSignPrefixLine2(text);
                else if (lineNum == 3) network.setSignPrefixLine3(text);
                else { sender.sendMessage("§cLine number must be 1, 2 or 3."); return true; }
                
                plugin.saveLines();
                sender.sendMessage("§aSet sign prefix line " + lineNum + " to: '" + text + "§a'");
                return true;
            }
            if (key.equals("linecolor")) {
                if (args.length < 4) { sender.sendMessage("Usage: /tmx set linecolor <line> <hex>"); return true; }
                String lineName = args[2];
                String hex = args[3];
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine not found."); return true; }
                
                line.hexColor = hex;
                // Force update bar color logic locally or via helper
                // Reflection is tricky but we added matchBarColor as private...
                // Actually I should have made it public or updated it on set. 
                // For now, I'll rely on reloadConfig() to refresh barColor or I need to expose the setter.
                // Since I modified Line.java to have public fields, I can just reload it or copy the logic.
                // I'll reload config to be safe/easy, OR I can just access the field.
                // But wait, matchBarColor is private. I'll make it public? No, I can't easily edit Line again just for that without another tool call.
                // I will just saveLines() and then reload(). Or I can duplicate the simple logic since it is just visual update.
                plugin.saveLines(); // saves to disk
                network.reload(plugin.getConfig()); // reloads from disk (parsing hex to BarColor)
                
                sender.sendMessage("§aSet color for line '" + line.name + "' to " + hex);
                return true;
            }
            if (key.equals("end")) {
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                if (args.length < 3) { sender.sendMessage("Usage: /tmx set end <line>"); return true; }
                String lineName = args[2];
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine not found."); return true; }
                Player p = (Player) sender;
                Location loc = p.getLocation();
                BlockFace face = p.getFacing();
                String d = "N";
                switch (face) {
                    case NORTH: d = "N"; break;
                    case SOUTH: d = "S"; break;
                    case EAST: d = "E"; break;
                    case WEST: d = "W"; break;
                    default: d = "N"; break;
                }
                line.endPoint = new Line.EndPoint(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), d);
                plugin.saveLines();
                sender.sendMessage("§aEnd point set for line '" + line.name + "' at current location (Dir: " + d + ").");
                return true;
            }
            sender.sendMessage("§cUnknown set key. Allowed: accel, decel, scanspeed, speedval, signprefix, nextstationbar, linecolor, end, collision");
            return true;
        }

        // /tmx manage ...
        if (action.equals("manage")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /tmx manage <line|station>");
                return true;
            }
            String what = args[1].toLowerCase(Locale.ROOT);
            if (what.equals("line")) {
                if (args.length == 2) {
                    showManageLines(sender);
                    return true;
                }
                // show a specific line's managed entries
                if (args.length == 3) {
                    showManageLine(args[2], sender);
                    return true;
                }
                return true;
            }
            if (what.equals("station")) {
                if (args.length == 2) { sender.sendMessage("Usage: /tmx manage station <line>"); return true; }
                String lineName = args[2];
                Line line = network.getLine(lineName);
                if (line == null) { sender.sendMessage("§cLine not found."); return true; }
                if (args.length == 3) {
                    for (StationEntry s : line.stations) {
                        if (sender instanceof Player) {
                            TextComponent tc = new TextComponent("§e" + s.name + " ");
                            TextComponent up = new TextComponent("§a▲");
                            up.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx manage move station " + lineName + " " + s.name + " up"));
                            TextComponent down = new TextComponent(" §a▼");
                            down.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx manage move station " + lineName + " " + s.name + " down"));
                            TextComponent del = new TextComponent(" §c✖");
                            del.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tmx delete station " + lineName + " " + s.name));
                            tc.addExtra(up);
                            tc.addExtra(down);
                            tc.addExtra(del);
                            ((Player) sender).spigot().sendMessage(tc);
                        } else {
                            sender.sendMessage(s.name + " @ " + s.x + "," + s.y + "," + s.z);
                        }
                    }
                    return true;
                }
                // move station: /tmx manage move station <line> <station> <up|down>
                if (args.length >= 6 && args[2].equalsIgnoreCase("move") && args[3].equalsIgnoreCase("station")) {
                    // args: manage move station <line> <station> <up|down>
                    String lineArg = args[4];
                    String stationName = args[5];
                    String dir = args.length >= 7 ? args[6] : "";
                    Line ln = network.getLine(lineArg);
                    if (ln == null) { sender.sendMessage("§cLine not found."); return true; }
                    int idx = -1;
                    for (int i = 0; i < ln.stations.size(); i++) if (ln.stations.get(i).name.equalsIgnoreCase(stationName)) { idx = i; break; }
                    if (idx == -1) { sender.sendMessage("§cStation not found."); return true; }
                    if (dir.equalsIgnoreCase("up")) {
                        if (idx <= 0) { sender.sendMessage("§eAlready first."); return true; }
                        java.util.Collections.swap(ln.stations, idx, idx - 1);
                        plugin.saveLines();
                        sender.sendMessage("§aStation moved up.");
                        return true;
                    } else if (dir.equalsIgnoreCase("down")) {
                        if (idx >= ln.stations.size() - 1) { sender.sendMessage("§eAlready last."); return true; }
                        java.util.Collections.swap(ln.stations, idx, idx + 1);
                        plugin.saveLines();
                        sender.sendMessage("§aStation moved down.");
                        return true;
                    } else {
                        sender.sendMessage("Usage: /tmx manage move station <line> <station> <up|down>");
                        return true;
                    }
                }
                // move limiter: /tmx manage move limit <line> <limiter> <up|down>
                if (args.length >= 6 && args[2].equalsIgnoreCase("move") && args[3].equalsIgnoreCase("limit")) {
                    String lineArg = args[4];
                    String limiterName = args[5];
                    String dir = args.length >= 7 ? args[6] : "";
                    Line ln = network.getLine(lineArg);
                    if (ln == null) { sender.sendMessage("§cLine not found."); return true; }
                    int idx = -1;
                    for (int i = 0; i < ln.limiters.size(); i++) if (ln.limiters.get(i).getName().equalsIgnoreCase(limiterName)) { idx = i; break; }
                    if (idx == -1) { sender.sendMessage("§cLimiter not found."); return true; }
                    if (dir.equalsIgnoreCase("up")) {
                        if (idx <= 0) { sender.sendMessage("§eAlready first."); return true; }
                        java.util.Collections.swap(ln.limiters, idx, idx - 1);
                        plugin.saveLines();
                        sender.sendMessage("§aLimiter moved up.");
                        return true;
                    } else if (dir.equalsIgnoreCase("down")) {
                        if (idx >= ln.limiters.size() - 1) { sender.sendMessage("§eAlready last."); return true; }
                        java.util.Collections.swap(ln.limiters, idx, idx + 1);
                        plugin.saveLines();
                        sender.sendMessage("§aLimiter moved down.");
                        return true;
                    } else {
                        sender.sendMessage("Usage: /tmx manage move limit <line> <limiter> <up|down>");
                        return true;
                    }
                }
            }
            // support move line command: /tmx manage move line <name> <up|down>
            if (args.length >= 5 && args[1].equalsIgnoreCase("move") && args[2].equalsIgnoreCase("line")) {
                String name = args[3];
                String dir = args.length >= 5 ? args[4] : "";
                boolean up = dir.equalsIgnoreCase("up");
                boolean moved = network.moveLine(name, up);
                if (moved) { plugin.saveLines(); sender.sendMessage("§aLine moved."); }
                else sender.sendMessage("§cLine move failed or already at boundary.");
                return true;
            }
            return true;
        }

        return false;
    }
}
