package com.kfguddat.transminecraftexpress;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

public final class TmxCommand implements CommandExecutor {
    private final TransMinecraftExpress plugin;
    private final StationRegistry registry;

    public TmxCommand(TransMinecraftExpress plugin, StationRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tmx.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        switch (root) {
            case "reload":
                handleReload(sender);
                return true;
            case "station":
                handleStation(sender, args);
                return true;
            case "line":
                handleLine(sender, args);
                return true;
            case "cart":
                handleCart(sender, args);
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        registry.reload(plugin.getConfig());
        sender.sendMessage("TransMinecraftExpress config reloaded.");
    }

    private void handleStation(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /tmx station <add|remove|list> <line> [name]");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String line = StationRegistry.normalize(args[2]);
        switch (action) {
            case "add":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Only players can add stations.");
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage("Usage: /tmx station add <line> <name>");
                    return;
                }
                addStation((Player) sender, line, args[3]);
                return;
            case "remove":
                if (args.length < 4) {
                    sender.sendMessage("Usage: /tmx station remove <line> <name>");
                    return;
                }
                removeStation(sender, line, args[3]);
                return;
            case "list":
                listStations(sender, line);
                return;
            default:
                sender.sendMessage("Usage: /tmx station <add|remove|list> <line> [name]");
        }
    }

    private void addStation(Player player, String line, String name) {
        String stationKey = name.toLowerCase(Locale.ROOT);
        FileConfiguration config = plugin.getConfig();
        String path = "stations." + line + "." + stationKey;
        if (config.contains(path)) {
            player.sendMessage("Station already exists: " + stationKey);
            return;
        }

        Location location = player.getLocation();
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        plugin.saveConfig();
        registry.reload(config);
        player.sendMessage("Station added on line " + line + ": " + stationKey);
    }

    private void removeStation(CommandSender sender, String line, String name) {
        String stationKey = name.toLowerCase(Locale.ROOT);
        FileConfiguration config = plugin.getConfig();
        String path = "stations." + line + "." + stationKey;
        if (!config.contains(path)) {
            sender.sendMessage("No station found named " + stationKey + " on line " + line);
            return;
        }
        config.set(path, null);
        plugin.saveConfig();
        registry.reload(config);
        sender.sendMessage("Station removed from line " + line + ": " + stationKey);
    }

    private void listStations(CommandSender sender, String line) {
        List<Station> stations = registry.getStationsForLine(line);
        if (stations.isEmpty()) {
            sender.sendMessage("No stations for line " + line);
            return;
        }
        String list = stations.stream()
                .map(Station::getName)
                .sorted()
                .collect(Collectors.joining(", "));
        sender.sendMessage("Stations for line " + line + ": " + list);
    }

    private void handleLine(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /tmx line <set|info|list> <line> [key] [value]");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            listLines(sender);
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("Usage: /tmx line <set|info|list> <line> [key] [value]");
            return;
        }

        String line = StationRegistry.normalize(args[2]);
        switch (action) {
            case "info":
                sendLineInfo(sender, line);
                return;
            case "set":
                if (args.length < 5) {
                    sender.sendMessage("Usage: /tmx line set <line> <key> <value>");
                    return;
                }
                setLineValue(sender, line, args[3], args[4]);
                return;
            default:
                sender.sendMessage("Usage: /tmx line <set|info|list> <line> [key] [value]");
        }
    }

    private void listLines(CommandSender sender) {
        Set<String> lines = registry.getLines();
        if (lines.isEmpty()) {
            sender.sendMessage("No lines configured.");
            return;
        }
        String list = lines.stream().sorted().collect(Collectors.joining(", "));
        sender.sendMessage("Lines: " + list);
    }

    private void sendLineInfo(CommandSender sender, String line) {
        LineSettings settings = registry.getLineSettings(line);
        sender.sendMessage("Line " + line + " settings:");
        sender.sendMessage(" target-speed=" + settings.targetSpeed);
        sender.sendMessage(" approach-speed=" + settings.approachSpeed);
        sender.sendMessage(" accel-per-tick=" + settings.accelPerTick);
        sender.sendMessage(" decel-per-tick=" + settings.decelPerTick);
        sender.sendMessage(" approach-radius=" + settings.approachRadius);
        sender.sendMessage(" stop-radius=" + settings.stopRadius);
    }

    private void setLineValue(CommandSender sender, String line, String key, String value) {
        Double parsedValue = parseDouble(value);
        if (parsedValue == null) {
            sender.sendMessage("Value must be a number: " + value);
            return;
        }

        String normalizedKey = normalizeLineKey(key);
        if (normalizedKey == null) {
            sender.sendMessage("Unknown key. Use one of: target-speed, approach-speed, accel-per-tick, decel-per-tick, approach-radius, stop-radius");
            return;
        }

        FileConfiguration config = plugin.getConfig();
        String path = "lines." + line + "." + normalizedKey;
        config.set(path, parsedValue);
        plugin.saveConfig();
        registry.reload(config);
        sender.sendMessage("Updated line " + line + " " + normalizedKey + " to " + parsedValue);
    }

    private String normalizeLineKey(String key) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "target-speed":
            case "approach-speed":
            case "accel-per-tick":
            case "decel-per-tick":
            case "approach-radius":
            case "stop-radius":
                return key.toLowerCase(Locale.ROOT);
            default:
                return null;
        }
    }

    private void handleCart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can assign line tags to carts.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /ice cart setline <line>");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!action.equals("setline")) {
            sender.sendMessage("Usage: /ice cart setline <line>");
            return;
        }

        String line = StationRegistry.normalize(args[2]);
        Player player = (Player) sender;
        Minecart cart = findNearestMinecart(player.getLocation(), 5.0D);
        if (cart == null) {
            sender.sendMessage("No minecart found within 5 blocks.");
            return;
        }

        for (String tag : cart.getScoreboardTags()) {
            if (tag.startsWith(StationRegistry.LINE_TAG_PREFIX)) {
                cart.removeScoreboardTag(tag);
            }
        }
        cart.addScoreboardTag(StationRegistry.LINE_TAG_PREFIX + line);
        sender.sendMessage("Minecart line set to " + line);
    }

    private Minecart findNearestMinecart(Location location, double radius) {
        List<Minecart> carts = location.getWorld().getEntitiesByClass(Minecart.class).stream()
                .filter(cart -> cart.getLocation().distanceSquared(location) <= radius * radius)
                .sorted(Comparator.comparingDouble(cart -> cart.getLocation().distanceSquared(location)))
                .collect(Collectors.toList());
        if (carts.isEmpty()) {
            return null;
        }
        return carts.get(0);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/ice reload");
        sender.sendMessage("/ice line list");
        sender.sendMessage("/ice line info <line>");
        sender.sendMessage("/ice line set <line> <key> <value>");
        sender.sendMessage("/ice station list <line>");
        sender.sendMessage("/ice station add <line> <name>");
        sender.sendMessage("/ice station remove <line> <name>");
        sender.sendMessage("/ice cart setline <line>");
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
