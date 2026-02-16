package com.kfguddat.transminecraftexpress;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class EventListener implements Listener {
    private final TransMinecraftExpress plugin;
    private final NetworkManager network;
    private final NamespacedKey lineKey;
    private final NamespacedKey indexKey;
    private final NamespacedKey limitKey;
    private final NamespacedKey trainLeaderKey;
    private final java.util.Map<java.util.UUID, AnimalTrainSession> animalTrainSessions = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> disconnectingPlayers = new java.util.HashSet<>();

    private static class LeashLink {
        final java.util.UUID entityId;
        final java.util.UUID holderId;
        final boolean holderIsPlayer;

        LeashLink(java.util.UUID entityId, java.util.UUID holderId, boolean holderIsPlayer) {
            this.entityId = entityId;
            this.holderId = holderId;
            this.holderIsPlayer = holderIsPlayer;
        }
    }

    private static class AnimalTrainSession {
        final java.util.UUID rootCartId;
        final java.util.List<LeashLink> links;

        AnimalTrainSession(java.util.UUID rootCartId, java.util.List<LeashLink> links) {
            this.rootCartId = rootCartId;
            this.links = links;
        }
    }

    private void status(Player player, String message) {
        if (player == null || message == null) return;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    public EventListener(TransMinecraftExpress plugin, NetworkManager network) {
        this.plugin = plugin;
        this.network = network;
        this.lineKey = new NamespacedKey(plugin, "line");
        this.indexKey = new NamespacedKey(plugin, "node_index");
        this.limitKey = new NamespacedKey(plugin, "speed_limit");
        this.trainLeaderKey = new NamespacedKey(plugin, "train_leader");
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
            status(event.getPlayer(), "§cWarning: Line '" + lineName + "' does not exist (yet).");
        } else if (stationName != null && !stationName.isEmpty()) {
            boolean found = false;
            for (StationEntry s : line.stations) if (s.name.equalsIgnoreCase(stationName)) { found = true; break; }
            if (!found) status(event.getPlayer(), "§cWarning: Station '" + stationName + "' not found on line '" + lineName + "'.");
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
    

    // Unified Entity Interaction for Link/Follow Logic
    @EventHandler
    public void onEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (!network.isTrainsEnabled()) return;
        
        // Debug - Force Message
        // event.getPlayer().sendMessage("Debug: Entity Clicked " + event.getRightClicked().getType());
        
        if (event.getRightClicked() instanceof Minecart) {
             // Check hand to prevent double firing
             if (event.getHand() != EquipmentSlot.HAND) return;
             
             Player player = event.getPlayer();
             Minecart clicked = (Minecart) event.getRightClicked();
             
             // player.sendMessage("Debug: Checking TMX data...");
             
             if (clicked.getPersistentDataContainer().has(lineKey, PersistentDataType.STRING)) {
                
                // player.sendMessage("Debug: TMX Valid.");

                // Prevent sitting on occupied carts (optional, but good for trains)
                if (!clicked.getPassengers().isEmpty()) {
                    
                    if (clicked.getPassengers().contains(player)) return;
                    event.setCancelled(true);
                    
                    // Allow creating a follower cart if player is not riding anything
                    if (player.getVehicle() == null) {
                         createFollowerCart(player, clicked);
                    } else {
                        status(player, "§cYou are already in a vehicle!");
                    }
                } else {
                    // Empty cart - Normal ride
                }
            } else {
                 // player.sendMessage("Debug: Not TMX.");
            }
        }
    }
    
    private void createFollowerCart(Player player, Minecart leader) {
        Minecart attachTo = findTrainTail(leader);

        String lineKeyStr = leader.getPersistentDataContainer().get(lineKey, PersistentDataType.STRING);
        if (lineKeyStr == null) {
            status(player, "§cError: No line data on leader.");
            return;
        }
        
        Line line = network.getLine(lineKeyStr);
        if (line == null) {
               status(player, "§cError: Line not found in network.");
             return;
        }
        
        Integer leaderIdx = attachTo.getPersistentDataContainer().get(new NamespacedKey(plugin, "path_index"), PersistentDataType.INTEGER);
        if (leaderIdx == null) leaderIdx = 0;
        
        // Use path logic to spawn cart behind
        int spawnIdx = leaderIdx;
        double targetDist = network.getTrainSpacing();
        double currentDist = 0;
        
        if (!line.verifiedPath.isEmpty()) {
            // Scan backwards from leader's position
            for (int i = leaderIdx - 1; i >= 0; i--) {
                org.bukkit.util.Vector p1 = line.verifiedPath.get(i);
                org.bukkit.util.Vector p2 = line.verifiedPath.get(i+1);
                currentDist += p1.distance(p2);
                spawnIdx = i;
                if (currentDist >= targetDist) break;
            }
            
            // Circular wrap logic
             if (currentDist < targetDist && line.startPoint != null && line.endPoint != null) {
                 if (line.startPoint.world.equals(line.endPoint.world) &&
                     line.startPoint.x == line.endPoint.x &&
                     line.startPoint.y == line.endPoint.y &&
                     line.startPoint.z == line.endPoint.z) {
                         int last = line.verifiedPath.size() - 2;
                         for (int i = last; i >= 0; i--) {
                             if (i+1 >= line.verifiedPath.size()) continue;
                             org.bukkit.util.Vector p1 = line.verifiedPath.get(i);
                             org.bukkit.util.Vector p2 = line.verifiedPath.get(i+1);
                             currentDist += p1.distance(p2);
                             spawnIdx = i;
                             if (currentDist >= targetDist) break;
                         }
                     }
            }
        }
        
        // Calculate Spawn Location
           Location spawnLoc = attachTo.getLocation();
        if (!line.verifiedPath.isEmpty() && spawnIdx >= 0 && spawnIdx < line.verifiedPath.size()) {
             org.bukkit.util.Vector v = line.verifiedPath.get(spawnIdx);
               spawnLoc = new Location(attachTo.getWorld(), v.getX() + 0.5, v.getY(), v.getZ() + 0.5);
             if (spawnIdx + 1 < line.verifiedPath.size()) {
                 org.bukkit.util.Vector next = line.verifiedPath.get(spawnIdx+1);
                 spawnLoc.setDirection(next.clone().subtract(v));
             } else if (spawnIdx > 0) {
                 // Last point look at previous?
                 org.bukkit.util.Vector prev = line.verifiedPath.get(spawnIdx-1);
                 spawnLoc.setDirection(v.clone().subtract(prev));
             }
        } else {
             // Fallback
               spawnLoc.add(attachTo.getLocation().getDirection().multiply(-2));
        }

        Minecart follower = (Minecart) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.MINECART);
        follower.getPersistentDataContainer().set(lineKey, PersistentDataType.STRING, lineKeyStr);
        follower.getPersistentDataContainer().set(new NamespacedKey(plugin, "path_index"), PersistentDataType.INTEGER, spawnIdx);
        follower.getPersistentDataContainer().set(trainLeaderKey, PersistentDataType.STRING, attachTo.getUniqueId().toString());
        
        // Copy target station info from leader
        if (leader.getPersistentDataContainer().has(indexKey, PersistentDataType.INTEGER)) {
             follower.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, 
                leader.getPersistentDataContainer().get(indexKey, PersistentDataType.INTEGER));
        }

        if (leader.getPersistentDataContainer().has(limitKey, PersistentDataType.DOUBLE)) {
            follower.getPersistentDataContainer().set(limitKey, PersistentDataType.DOUBLE,
            leader.getPersistentDataContainer().get(limitKey, PersistentDataType.DOUBLE));
        }
        
        follower.addPassenger(player);
        status(player, "§aJoined train!");

        tryCreateAnimalTrain(player, follower, line, lineKeyStr);
    }

    private Minecart findTrainTail(Minecart start) {
        Minecart current = start;
        java.util.Set<java.util.UUID> visited = new java.util.HashSet<>();

        while (current != null && visited.add(current.getUniqueId())) {
            Minecart child = null;
            for (Minecart cart : current.getWorld().getEntitiesByClass(Minecart.class)) {
                if (!cart.getPersistentDataContainer().has(lineKey, PersistentDataType.STRING)) continue;
                String leaderId = cart.getPersistentDataContainer().get(trainLeaderKey, PersistentDataType.STRING);
                if (leaderId != null && leaderId.equals(current.getUniqueId().toString())) {
                    child = cart;
                    break;
                }
            }

            if (child == null) return current;
            current = child;
        }

        return start;
    }

    private Minecart spawnFollowerForTrain(Minecart leader, Line line, String lineKeyStr) {
        Integer leaderIdx = leader.getPersistentDataContainer().get(new NamespacedKey(plugin, "path_index"), PersistentDataType.INTEGER);
        if (leaderIdx == null) leaderIdx = 0;

        int spawnIdx = leaderIdx;
        double targetDist = Math.max(0.5, network.getTrainSpacing());
        double currentDist = 0;

        if (!line.verifiedPath.isEmpty()) {
            for (int i = leaderIdx - 1; i >= 0; i--) {
                org.bukkit.util.Vector p1 = line.verifiedPath.get(i);
                org.bukkit.util.Vector p2 = line.verifiedPath.get(i + 1);
                currentDist += p1.distance(p2);
                spawnIdx = i;
                if (currentDist >= targetDist) break;
            }

            if (currentDist < targetDist && line.startPoint != null && line.endPoint != null) {
                if (line.startPoint.world.equals(line.endPoint.world) &&
                    line.startPoint.x == line.endPoint.x &&
                    line.startPoint.y == line.endPoint.y &&
                    line.startPoint.z == line.endPoint.z) {
                    int last = line.verifiedPath.size() - 2;
                    for (int i = last; i >= 0; i--) {
                        if (i + 1 >= line.verifiedPath.size()) continue;
                        org.bukkit.util.Vector p1 = line.verifiedPath.get(i);
                        org.bukkit.util.Vector p2 = line.verifiedPath.get(i + 1);
                        currentDist += p1.distance(p2);
                        spawnIdx = i;
                        if (currentDist >= targetDist) break;
                    }
                }
            }
        }

        Location spawnLoc = leader.getLocation();
        if (!line.verifiedPath.isEmpty() && spawnIdx >= 0 && spawnIdx < line.verifiedPath.size()) {
            org.bukkit.util.Vector v = line.verifiedPath.get(spawnIdx);
            spawnLoc = new Location(leader.getWorld(), v.getX() + 0.5, v.getY(), v.getZ() + 0.5);
            if (spawnIdx + 1 < line.verifiedPath.size()) {
                org.bukkit.util.Vector next = line.verifiedPath.get(spawnIdx + 1);
                spawnLoc.setDirection(next.clone().subtract(v));
            } else if (spawnIdx > 0) {
                org.bukkit.util.Vector prev = line.verifiedPath.get(spawnIdx - 1);
                spawnLoc.setDirection(v.clone().subtract(prev));
            }
        } else {
            spawnLoc.add(leader.getLocation().getDirection().multiply(-2));
        }

        Minecart follower = (Minecart) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.MINECART);
        follower.getPersistentDataContainer().set(lineKey, PersistentDataType.STRING, lineKeyStr);
        follower.getPersistentDataContainer().set(new NamespacedKey(plugin, "path_index"), PersistentDataType.INTEGER, spawnIdx);
        follower.getPersistentDataContainer().set(trainLeaderKey, PersistentDataType.STRING, leader.getUniqueId().toString());

        if (leader.getPersistentDataContainer().has(indexKey, PersistentDataType.INTEGER)) {
            follower.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER,
                leader.getPersistentDataContainer().get(indexKey, PersistentDataType.INTEGER));
        }
        if (leader.getPersistentDataContainer().has(limitKey, PersistentDataType.DOUBLE)) {
            follower.getPersistentDataContainer().set(limitKey, PersistentDataType.DOUBLE,
                leader.getPersistentDataContainer().get(limitKey, PersistentDataType.DOUBLE));
        }

        return follower;
    }

    private java.util.List<LeashLink> collectLeashChain(Player player) {
        java.util.Map<java.util.UUID, LivingEntity> allLeashed = new java.util.HashMap<>();
        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
            if (!entity.isLeashed()) continue;
            allLeashed.put(entity.getUniqueId(), entity);
        }

        java.util.List<LeashLink> out = new java.util.ArrayList<>();
        java.util.Set<java.util.UUID> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<LivingEntity> queue = new java.util.ArrayDeque<>();

        for (LivingEntity entity : allLeashed.values()) {
            try {
                Entity holder = entity.getLeashHolder();
                if (holder instanceof Player && holder.getUniqueId().equals(player.getUniqueId())) {
                    queue.add(entity);
                }
            } catch (IllegalStateException ignored) {
            }
        }

        while (!queue.isEmpty()) {
            LivingEntity current = queue.poll();
            if (!visited.add(current.getUniqueId())) continue;

            try {
                Entity holder = current.getLeashHolder();
                if (holder != null) {
                    boolean holderIsPlayer = holder instanceof Player;
                    out.add(new LeashLink(current.getUniqueId(), holder.getUniqueId(), holderIsPlayer));
                }
            } catch (IllegalStateException ignored) {
            }

            for (LivingEntity candidate : allLeashed.values()) {
                if (visited.contains(candidate.getUniqueId())) continue;
                try {
                    Entity holder = candidate.getLeashHolder();
                    if (holder != null && holder.getUniqueId().equals(current.getUniqueId())) {
                        queue.add(candidate);
                    }
                } catch (IllegalStateException ignored) {
                }
            }
        }

        return out;
    }

    private void tryCreateAnimalTrain(Player player, Minecart rootCart, Line line, String lineKeyStr) {
        if (!network.isAnimalTrainsEnabled()) return;

        java.util.List<LeashLink> chain = collectLeashChain(player);
        if (chain.isEmpty()) return;

        Minecart leader = rootCart;
        java.util.List<LeashLink> placed = new java.util.ArrayList<>();

        for (LeashLink link : chain) {
            Entity e = Bukkit.getEntity(link.entityId);
            if (!(e instanceof LivingEntity)) continue;
            LivingEntity animal = (LivingEntity) e;
            animal.setLeashHolder(null);
            Minecart animalCart = spawnFollowerForTrain(leader, line, lineKeyStr);
            animalCart.addPassenger(animal);
            leader = animalCart;
            placed.add(link);
        }

        animalTrainSessions.put(player.getUniqueId(), new AnimalTrainSession(rootCart.getUniqueId(), placed));
        status(player, "§aAnimal train created (" + placed.size() + ").");
    }

    private void handleTrainLeaderExit(Minecart leader) {
        String leaderId = leader.getUniqueId().toString();
        
        java.util.List<Minecart> nearby = leader.getNearbyEntities(20, 10, 20).stream()
            .filter(e -> e instanceof Minecart)
            .map(e -> (Minecart)e)
            .collect(java.util.stream.Collectors.toList());
            
        NamespacedKey leaderKey = new NamespacedKey(plugin, "train_leader");
        
        for (Minecart follower : nearby) {
            String targetId = follower.getPersistentDataContainer().get(leaderKey, PersistentDataType.STRING);
            if (targetId != null && targetId.equals(leaderId)) {
                // Found the follower. Promote it.
                promoteFollower(follower, leader);
            }
        }
    }
    
    private void promoteFollower(Minecart follower, Minecart oldLeader) {
        follower.getPersistentDataContainer().remove(new NamespacedKey(plugin, "train_leader"));
        for (org.bukkit.entity.Entity passenger : follower.getPassengers()) {
            if (passenger instanceof Player) {
                status((Player) passenger, "§eThe conductor left. You are now the conductor!");
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
            status(player, "§cError: Line '" + lineName + "' not defined.");
            return;
        }

        if (stationName == null || stationName.isEmpty()) {
            status(player, "§cError: Station name missing on sign (line 2).");
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
            status(player, "§cError: Station '" + stationName + "' not found on line.");
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

        if (targetStation.pathIndex >= 0) {
            cart.getPersistentDataContainer().set(new NamespacedKey(plugin, "path_index"), PersistentDataType.INTEGER, targetStation.pathIndex);
        }

        cart.addPassenger(player);

        tryCreateAnimalTrain(player, cart, line, lineName);
    }
    
    @EventHandler
    public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
        Vehicle v = event.getVehicle();
        if (!(v instanceof Minecart)) return;

        Minecart vehicleCart = (Minecart) v;
        boolean vehicleIsTmx = vehicleCart.getPersistentDataContainer().has(lineKey, PersistentDataType.STRING);
        if (!vehicleIsTmx) return;

        // Always prevent TMX minecarts from pushing other TMX minecarts.
        if (event.getEntity() instanceof Minecart) {
            Minecart other = (Minecart) event.getEntity();
            if (other.getPersistentDataContainer().has(lineKey, PersistentDataType.STRING)) {
                event.setCancelled(true);
                event.setCollisionCancelled(true);
                return;
            }
        }

        // Keep existing global collision toggle for non-TMX-minecart entities.
        if (!network.isCollision()) {
            event.setCancelled(true);
            event.setCollisionCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (network.isSuffocationProtection()) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) return;
        if (!(event.getEntity() instanceof Animals)) return;

        Entity entity = event.getEntity();
        if (entity.getVehicle() instanceof Minecart) {
            Minecart cart = (Minecart) entity.getVehicle();
            if (cart.getPersistentDataContainer().has(lineKey, PersistentDataType.STRING)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        Vehicle v = event.getVehicle();
        if (v instanceof Minecart) {
            if (v.getPersistentDataContainer().has(lineKey, PersistentDataType.STRING)) {
                if (event.getExited() instanceof Player) {
                    Player exited = (Player) event.getExited();

                    // On disconnect, keep non-animal riders in their cart for rejoin.
                    if (disconnectingPlayers.contains(exited.getUniqueId()) && !animalTrainSessions.containsKey(exited.getUniqueId())) {
                        return;
                    }

                    handleAnimalTrainOwnerExit(exited, (Minecart) v);
                }
                // Check if this cart is a leader of a train
                handleTrainLeaderExit((Minecart) v);
                
                Bukkit.getScheduler().runTask(plugin, v::remove);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        disconnectingPlayers.add(player.getUniqueId());

        if (animalTrainSessions.containsKey(player.getUniqueId()) && player.getVehicle() instanceof Minecart) {
            Minecart cart = (Minecart) player.getVehicle();
            if (cart.getPersistentDataContainer().has(lineKey, PersistentDataType.STRING)) {
                handleAnimalTrainOwnerExit(player, cart);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> disconnectingPlayers.remove(player.getUniqueId()), 40L);
    }

    private void handleAnimalTrainOwnerExit(Player player, Minecart exitedCart) {
        if (!network.isAnimalTrainsEnabled()) return;

        AnimalTrainSession session = animalTrainSessions.get(player.getUniqueId());
        if (session == null) return;
        if (!session.rootCartId.equals(exitedCart.getUniqueId())) return;

        java.util.Map<java.util.UUID, LivingEntity> released = new java.util.HashMap<>();
        int idx = 0;

        for (LeashLink link : session.links) {
            Entity entity = Bukkit.getEntity(link.entityId);
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity animal = (LivingEntity) entity;

            if (animal.getVehicle() instanceof Minecart) {
                Minecart c = (Minecart) animal.getVehicle();
                animal.leaveVehicle();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (c.isValid() && c.getPassengers().isEmpty()) c.remove();
                });
            }

            Location dropLoc = findSafeDropLocation(player, idx++);
            animal.teleport(dropLoc);
            released.put(animal.getUniqueId(), animal);
        }

        // Restore leash links (player links first, then entity-to-entity links).
        for (LeashLink link : session.links) {
            LivingEntity child = released.get(link.entityId);
            if (child == null) continue;
            if (link.holderIsPlayer) {
                child.setLeashHolder(player);
            }
        }
        for (LeashLink link : session.links) {
            if (link.holderIsPlayer) continue;
            LivingEntity child = released.get(link.entityId);
            Entity holder = released.get(link.holderId);
            if (child == null) continue;
            if (holder instanceof LivingEntity) {
                child.setLeashHolder(holder);
            } else {
                child.setLeashHolder(player);
            }
        }

        animalTrainSessions.remove(player.getUniqueId());
        status(player, "§eAnimal leads reattached.");
    }

    private Location findSafeDropLocation(Player player, int index) {
        Location base = player.getLocation().clone();
        org.bukkit.util.Vector backward = base.getDirection().clone().setY(0);
        if (backward.lengthSquared() < 0.0001) backward = new org.bukkit.util.Vector(0, 0, 1);
        backward.normalize().multiply(-1.0);
        org.bukkit.util.Vector side = new org.bukkit.util.Vector(-backward.getZ(), 0, backward.getX());

        // Spread animals in rows behind the player to avoid suffocation.
        int row = index / 3;
        int col = index % 3;
        double lateral = (col - 1) * 1.6; // -1.6, 0, +1.6
        double back = 1.8 + row * 1.7;

        Location candidate = base.clone()
            .add(backward.clone().multiply(back))
            .add(side.clone().multiply(lateral));

        for (int dy = 1; dy >= -2; dy--) {
            Location test = candidate.clone().add(0, dy, 0);
            if (test.getBlock().isPassable() && test.clone().add(0, 1, 0).getBlock().isPassable()) {
                return test;
            }
        }

        return candidate;
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
