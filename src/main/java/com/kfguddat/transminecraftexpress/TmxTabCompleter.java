package com.kfguddat.transminecraftexpress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class TmxTabCompleter implements TabCompleter {
    private final NetworkManager network;

    public TmxTabCompleter(NetworkManager network) {
        this.network = network;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return Collections.emptyList();
        String a0 = args[0].toLowerCase(Locale.ROOT);
        
        // Level 1
        if (args.length == 1) {
            return filter(args[0], "scan", "reload", "create", "delete", "list", "manage", "set", "rename");
        }
        
        // /tmx scan <line>
        if (a0.equals("scan")) {
            if (args.length == 2) return filter(args[1], network.getLines().keySet().toArray(new String[0]));
            return Collections.emptyList();
        }
        
        // /tmx set ...
        if (a0.equals("set")) {
            if (args.length == 2) return filter(args[1], "accel", "decel", "scanspeed", "speedval", "signprefix", "collision", "end", "nextstationbar", "linecolor");
            
            String a1 = args[1].toLowerCase(Locale.ROOT);
            if (a1.equals("collision") && args.length == 3) {
                 return filter(args[2], "true", "false");
            }
            if (a1.equals("nextstationbar") && args.length == 3) {
                 return filter(args[2], "true", "false");
            }
            if (a1.equals("speedval")) {
                if (args.length == 3) {
                    // suggest existing speedval names to overwrite
                    return filter(args[2], network.getSpeedVals().keySet().toArray(new String[0]));
                }
                return Collections.emptyList();
            }
            if (a1.equals("signprefix")) {
                if (args.length == 3) {
                    return filter(args[2], "1", "2", "3");
                }
                return Collections.emptyList();
            }
            if (a1.equals("linecolor") && args.length == 3) {
                return filter(args[2], network.getLines().keySet().toArray(new String[0]));
            }
            if (a1.equals("end") && args.length == 3) {
                return filter(args[2], network.getLines().keySet().toArray(new String[0]));
            }
            return Collections.emptyList();
        }
        
        // /tmx create ...
        if (a0.equals("create")) {
            if (args.length == 2) return filter(args[1], "line", "station", "limit", "start", "end");
            String sub = args[1].toLowerCase(Locale.ROOT);
            
            // create line <name>
            
            // create start/end <line>
            if (sub.equals("start") || sub.equals("end")) {
                if (args.length == 3) return filter(args[2], network.getLines().keySet().toArray(new String[0]));
            }

            // create station <line> <name> [true|false]
            if (sub.equals("station")) {
                if (args.length == 3) return filter(args[2], network.getLines().keySet().toArray(new String[0]));
                if (args.length == 5) return filter(args[4], "true", "false");
            }
            
            // create limit <line> <speed>
            if (sub.equals("limit") || sub.equals("limiter")) {
                if (args.length == 3) return filter(args[2], network.getLines().keySet().toArray(new String[0]));
                if (args.length == 4) return filter(args[3], network.getSpeedVals().keySet().toArray(new String[0]));
            }
        }

        // /tmx rename ...
        if (a0.equals("rename")) {
            if (args.length == 2) return filter(args[1], "line", "station", "limit");
            String sub = args[1].toLowerCase(Locale.ROOT);
            
            // rename line <oldname>
            if (sub.equals("line") && args.length == 3) {
                 return filter(args[2], network.getLines().keySet().toArray(new String[0]));
            }
            
            // rename station <line> <station> <new>
            if (sub.equals("station")) {
                if (args.length == 3) return filter(args[2], network.getLines().keySet().toArray(new String[0]));
                if (args.length == 4) {
                    Line ln = network.getLine(args[2]);
                    if (ln == null) return Collections.emptyList();
                    return filter(args[3], ln.stations.stream().map(s -> s.name).toArray(String[]::new));
                }
            }
            
             // rename limit <line> <limiter> <new>
            if (sub.equals("limit") || sub.equals("limiter")) {
                if (args.length == 3) return filter(args[2], network.getLines().keySet().toArray(new String[0]));
                if (args.length == 4) {
                    Line ln = network.getLine(args[2]);
                    if (ln == null) return Collections.emptyList();
                    return filter(args[3], ln.limiters.stream().map(l -> l.getName()).toArray(String[]::new));
                }
            }
        }

        // /tmx delete ...
        if (a0.equals("delete")) {
            if (args.length == 2) return filter(args[1], "line", "station", "limit", "speedval");
            String sub = args[1].toLowerCase(Locale.ROOT);
            
            // delete line <name>
            if (sub.equals("line") && args.length == 3) {
                return filter(args[2], network.getLines().keySet().toArray(new String[0]));
            }
            
            // delete speedval <name>
            if (sub.equals("speedval") && args.length == 3) {
                return filter(args[2], network.getSpeedVals().keySet().toArray(new String[0]));
            }

            // delete station <line> <name>
            if (sub.equals("station") && args.length == 3) {
                return filter(args[2], network.getLines().keySet().toArray(new String[0]));
            }
            if (sub.equals("station") && args.length == 4) {
                Line ln = network.getLine(args[2]);
                if (ln == null) return Collections.emptyList();
                return filter(args[3], ln.stations.stream().map(s -> s.name).toArray(String[]::new));
            }
            
            // delete limit <line> <name>
            if ((sub.equals("limit") || sub.equals("limiter")) && args.length == 3) {
                return filter(args[2], network.getLines().keySet().toArray(new String[0]));
            }
            if ((sub.equals("limit") || sub.equals("limiter")) && args.length == 4) {
                 Line ln = network.getLine(args[2]);
                 if (ln == null) return Collections.emptyList();
                 return filter(args[3], ln.limiters.stream().map(l -> l.getName()).toArray(String[]::new));
            }
        }

        // /tmx manage ...
        if (a0.equals("manage")) {
            if (args.length == 2) return filter(args[1], "line", "station", "move");
            String sub = args[1].toLowerCase(Locale.ROOT);
            
            // manage line [name]
            if (sub.equals("line") && args.length == 3) {
                 return filter(args[2], network.getLines().keySet().toArray(new String[0]));
            }
            
            // manage station <line>
            if (sub.equals("station") && args.length == 3) {
                return filter(args[2], network.getLines().keySet().toArray(new String[0]));
            }
            
            // manage move ...
            if (sub.equals("move")) {
                if (args.length == 3) return filter(args[2], "line", "station", "limit");
                String type = args[2].toLowerCase(Locale.ROOT);
                // manage move line <name> <up|down>
                if (type.equals("line")) {
                    if (args.length == 4) return filter(args[3], network.getLines().keySet().toArray(new String[0]));
                    if (args.length == 5) return filter(args[4], "up", "down");
                }
                // manage move station <line> <station> <up|down>
                if (type.equals("station")) {
                    if (args.length == 4) return filter(args[3], network.getLines().keySet().toArray(new String[0]));
                    if (args.length == 5) {
                         Line ln = network.getLine(args[3]);
                         if (ln == null) return Collections.emptyList();
                         return filter(args[4], ln.stations.stream().map(s -> s.name).toArray(String[]::new));
                    }
                    if (args.length == 6) return filter(args[5], "up", "down");
                }
                // manage move limit <line> <limiter> <up|down>
                 if (type.equals("limit")) {
                    if (args.length == 4) return filter(args[3], network.getLines().keySet().toArray(new String[0]));
                    if (args.length == 5) {
                         Line ln = network.getLine(args[3]);
                         if (ln == null) return Collections.emptyList();
                         return filter(args[4], ln.limiters.stream().map(l -> l.getName()).toArray(String[]::new));
                    }
                    if (args.length == 6) return filter(args[5], "up", "down");
                }
            }
        }

        // /tmx list [line]
        if (a0.equals("list")) {
            if (args.length == 2) return filter(args[1], network.getLines().keySet().toArray(new String[0]));
        }

        return Collections.emptyList();
    }

    private List<String> filter(String prefix, String... candidates) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase(Locale.ROOT).startsWith(p)) out.add(c);
        }
        return out;
    }
}
