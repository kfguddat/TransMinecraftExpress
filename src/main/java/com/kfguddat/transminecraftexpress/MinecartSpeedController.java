
package com.kfguddat.transminecraftexpress;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;

public class MinecartSpeedController {
    private final TransMinecraftExpress plugin;
    private final NetworkManager network;
    private final NamespacedKey lineKey;
    private final NamespacedKey indexKey;
    private final NamespacedKey scanKey;
    private final NamespacedKey pathIndexKey;
    private final NamespacedKey limitKey;
    private final NamespacedKey speedKey;
    
    private final java.util.Map<java.util.UUID, StuckInfo> stuckMap = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, ScanState> scanStates = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, BossBar> cartBossBars = new java.util.HashMap<>();

    private static class StuckInfo {
        Vector lastPos;
        int stuckTicks;
        StuckInfo(Vector p) { lastPos = p; stuckTicks = 0; }
    }
    
    private static class ScanState {
        Vector lastPos;
        int stoppedTicks;
        ScanState(Vector p) { lastPos = p; stoppedTicks = 0; }
    }
    
    private BukkitRunnable task;

    public MinecartSpeedController(TransMinecraftExpress plugin, NetworkManager network) {
        this.plugin = plugin;
        this.network = network;
        this.lineKey = new NamespacedKey(plugin, "line");
        this.indexKey = new NamespacedKey(plugin, "node_index");
        this.scanKey = new NamespacedKey(plugin, "scanning");
        this.pathIndexKey = new NamespacedKey(plugin, "path_index");
        this.limitKey = new NamespacedKey(plugin, "speed_limit");
        this.speedKey = new NamespacedKey(plugin, "current_speed");
    }

    public void start() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (BossBar bar : cartBossBars.values()) bar.removeAll();
        cartBossBars.clear();
    }

    private void tick() {
        java.util.Set<java.util.UUID> currentCarts = new java.util.HashSet<>();
        
        for (World world : Bukkit.getWorlds()) {
            for (Minecart cart : world.getEntitiesByClass(Minecart.class)) {
                if (cart.getPersistentDataContainer().has(lineKey, PersistentDataType.STRING)) {
                    updateCart(cart);
                    currentCarts.add(cart.getUniqueId());
                }
            }
        }
        
        // Cleanup boss bars for removed carts
        cartBossBars.entrySet().removeIf(entry -> {
             if (!currentCarts.contains(entry.getKey())) {
                 entry.getValue().removeAll();
                 return true;
             }
             return false;
        });
    }

    private void updateCart(Minecart cart) {
        if (cart.getPassengers().isEmpty()) {
            cart.remove();
            return;
        }

        String lineName = cart.getPersistentDataContainer().get(lineKey, PersistentDataType.STRING);
        Line line = network.getLine(lineName);
        if (line == null) return;

        // Check scanning mode
        if (cart.getPersistentDataContainer().has(scanKey, PersistentDataType.BYTE)) {
            handleScanning(cart, line);
            return;
        }

        Integer targetIndex = cart.getPersistentDataContainer().get(indexKey, PersistentDataType.INTEGER);
        if (targetIndex == null) targetIndex = 0;

        // If we've reached or passed the last station index
        if (targetIndex >= line.stations.size()) {
            // Check for Circular Line Loop
            boolean circular = false;
            if (line.startPoint != null && line.endPoint != null) {
                // Check if start and end match exactly (location and direction)
                if (line.startPoint.world.equals(line.endPoint.world) &&
                    line.startPoint.x == line.endPoint.x &&
                    line.startPoint.y == line.endPoint.y &&
                    line.startPoint.z == line.endPoint.z &&
                    line.startPoint.dir.equalsIgnoreCase(line.endPoint.dir)) {
                    circular = true;
                }
            }
            
            if (circular) {
                // Loop: Reset indices to 0 (Start)
                cart.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, 0); // node_index -> 0
                // Do NOT reset path_index here either, to prevent snap-back if physically at end
                targetIndex = 0;
            } else {
                // End: Eject and Remove
                try { cart.eject(); } catch (Throwable t) { }
                cart.remove();
                return;
            }
        }

        // Target is the station at targetIndex
        StationEntry station = line.stations.get(targetIndex);
        if (!station.worldName.equals(cart.getWorld().getName())) return;

        double distSq = station.getLocation(cart.getWorld()).distanceSquared(cart.getLocation());

        // Arrival Check
        if (distSq < 4.0) { // 2 block radius
            int next = targetIndex + 1;
            
             // Check if this is the End of Line logic
            if (next >= line.stations.size()) {
                 boolean circular = false;
                 if (line.startPoint != null && line.endPoint != null) {
                    if (line.startPoint.world.equals(line.endPoint.world) &&
                        line.startPoint.x == line.endPoint.x &&
                        line.startPoint.y == line.endPoint.y &&
                        line.startPoint.z == line.endPoint.z &&
                        line.startPoint.dir.equalsIgnoreCase(line.endPoint.dir)) {
                        circular = true;
                    }
                 }
                 
                 if (circular) {
                    // Loop: Reset index to 0 (Start)
                    cart.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, 0);
                    // Do not force path index to 0; let geometry logic handle the jump
                    targetIndex = 0;
                } else {
                    // End: Eject and Remove
                    try { cart.eject(); } catch (Throwable t) { }
                    cart.remove();
                    return;
                }
            } else {
                 // Advance to next station
                 cart.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, next);
                 targetIndex = next;
            }
            
            // Re-fetch station if targetIndex changed
            station = line.stations.get(targetIndex);
            if (!station.worldName.equals(cart.getWorld().getName())) return;
        }

        Double currentLimit = null;
        if (cart.getPersistentDataContainer().has(limitKey, PersistentDataType.DOUBLE)) {
            currentLimit = cart.getPersistentDataContainer().get(limitKey, PersistentDataType.DOUBLE);
        }
        
        // If not set, initialize with default or station speed? 
        // Logic: if no limit set, default to network default.
        if (currentLimit == null) {
            currentLimit = network.getDefaultSpeedVal();
        }

        // Check for limiters at current position
        int cx = cart.getLocation().getBlockX();
        int cy = cart.getLocation().getBlockY();
        int cz = cart.getLocation().getBlockZ();
        
        boolean limitUpdated = false;
        for (LimiterEntry lim : line.limiters) {
            if (!lim.worldName.equals(cart.getWorld().getName())) continue;
            // Check radius? Block exact match is safer for limiters to avoid flickering
            if (lim.x == cx && lim.y == cy && lim.z == cz) {
                double limSpeed = toPerTick(limiterSpeedFrom(lim));
                currentLimit = limSpeed;
                limitUpdated = true;
                break;
            }
        }
        
        if (limitUpdated) {
            cart.getPersistentDataContainer().set(limitKey, PersistentDataType.DOUBLE, currentLimit);
        }
        
        double currentTargetSpeed = currentLimit;

        // Compute desired direction
        Vector currentPos = cart.getLocation().toVector();
        Vector targetPos = station.getLocation(cart.getWorld()).toVector();
        
        // --- Path Logic vs Auto Logic ---
        Vector guideDir = null;
        if (!line.verifiedPath.isEmpty()) {
            // Updated to handle both steering and correction
            guideDir = getPathDirection(cart, line);
            
            // Check for drifting/derailment
            Integer idx = cart.getPersistentDataContainer().get(pathIndexKey, PersistentDataType.INTEGER);
            if (idx != null) {
                // To avoid "Snap to Grid" (teleporting to node centers), we calculate distance to the TRACK SEGMENT.
                // We check the segments before and after the current closest node to find the true rail line.
                Vector pBest = line.verifiedPath.get(idx).clone().add(new Vector(0.5, 0, 0.5));
                
                Vector closestOnTrack = pBest;
                double minDistSq = Double.MAX_VALUE;
                
                // Segment 1: Previous -> Best
                if (idx > 0) {
                    Vector pPrev = line.verifiedPath.get(idx - 1).clone().add(new Vector(0.5, 0, 0.5));
                    Vector pt = getClosestPointOnSegment(pPrev, pBest, currentPos);
                    double d = pt.distanceSquared(currentPos);
                    if (d < minDistSq) { minDistSq = d; closestOnTrack = pt; }
                }
                
                // Segment 2: Best -> Next
                if (idx < line.verifiedPath.size() - 1) {
                    Vector pNext = line.verifiedPath.get(idx + 1).clone().add(new Vector(0.5, 0, 0.5));
                    Vector pt = getClosestPointOnSegment(pBest, pNext, currentPos);
                    double d = pt.distanceSquared(currentPos);
                    if (d < minDistSq) { minDistSq = d; closestOnTrack = pt; }
                }

                 // If clearly off-track (2m radius from LINE), trigger Reset
                 // Increased tolerance to 3m (9.0 sq) for corners/jumps
                 if (minDistSq > 9.0) {
                      StuckInfo info = stuckMap.computeIfAbsent(cart.getUniqueId(), k -> new StuckInfo(currentPos));
                      info.stuckTicks = 100; // Force trigger checkStuck
                      checkStuck(cart, line, info);
                      return; // Stop processing this tick
                 }
                 
                 // Strict Locking: Micro-teleport to LINE if drift > 0.2
                 if (minDistSq > 0.04) {
                      Vector correctionTarget = closestOnTrack.clone();
                      
                      // Handle Y-Smoothing:
                      // If the rail segment is officially flat (based on node coords), ignore Y drift
                      // to prevent being sucked into the floor.
                      // Heuristic: If target Y is within 1 block of current Y, trust Current Y to be smoother.
                      if (Math.abs(correctionTarget.getY() - currentPos.getY()) < 1.0) {
                           correctionTarget.setY(currentPos.getY());
                      }
                      
                      Vector correction = correctionTarget.subtract(currentPos).multiply(0.2); 
                      Location newLoc = cart.getLocation().add(correction);
                      newLoc.setDirection(cart.getLocation().getDirection()); 
                      cart.teleport(newLoc);
                 }
            }
        }
        
        // Fallback to auto-detection if no path or path failed
        if (guideDir == null) {
            guideDir = detectRailDirection(cart);
            // If guideDir is still null or ambiguous (straight track), prioritize cart facing
            if (guideDir != null && cart.getPassengers().isEmpty() && !cart.getPersistentDataContainer().has(scanKey, PersistentDataType.BYTE)) {
               // Normal operation without passenger/scan might wander, but usually fine.
            }
        }
        
        // --- Boss Bar Update ---
        if (network.isShowNextStationBar() && !cart.getPassengers().isEmpty()) {
            boolean isScan = cart.getPersistentDataContainer().has(scanKey, PersistentDataType.BYTE);
            if (!isScan) { // Don't show during scan
                 BossBar bar = cartBossBars.computeIfAbsent(cart.getUniqueId(), k -> 
                     Bukkit.createBossBar("Line Info", line.barColor, BarStyle.SOLID));
                 
                 // Update Color
                 if (bar.getColor() != line.barColor) bar.setColor(line.barColor);
                 
                 // Add passengers
                 for (Entity e : cart.getPassengers()) {
                     if (e instanceof Player && !bar.getPlayers().contains((Player)e)) {
                         bar.addPlayer((Player)e);
                     }
                 }
                 // Remove players who left (not strictly necessary per tick, but good for cleanup)
                 // bar.getPlayers() returns a List copy, safe to iterate
                 for (Player p : bar.getPlayers()) {
                     if (!cart.getPassengers().contains(p)) bar.removePlayer(p);
                 }
                 
                 // Update Info
                 Integer curPathIdx = -1;
                 if (cart.getPersistentDataContainer().has(pathIndexKey, PersistentDataType.INTEGER)) {
                     curPathIdx = cart.getPersistentDataContainer().get(pathIndexKey, PersistentDataType.INTEGER);
                 }
                 
                 String prevName = "Start";
                 int prevPathIdx = 0;

                 String nextName = station.name;
                 int nextPathIdx = station.pathIndex;
                 
                 // If station.pathIndex is -1 (not cached/found), we can't calculate accurate progress
                 if (nextPathIdx != -1) {
                     StationEntry prev = null;
                     if (targetIndex > 0) {
                         prev = line.stations.get(targetIndex - 1);
                     } else {
                         // If target is 0, check if we are on a circular line coming from the last station
                         boolean circular = false;
                         if (line.startPoint != null && line.endPoint != null) {
                            if (line.startPoint.world.equals(line.endPoint.world) &&
                                line.startPoint.x == line.endPoint.x &&
                                line.startPoint.y == line.endPoint.y &&
                                line.startPoint.z == line.endPoint.z) {
                                circular = true;
                            }
                         }
                         if (circular && !line.stations.isEmpty()) {
                             prev = line.stations.get(line.stations.size() - 1);
                         }
                     }

                     if (prev != null) {
                         prevName = prev.name;
                         prevPathIdx = prev.pathIndex; 
                         if (prevPathIdx == -1) prevPathIdx = 0; 
                     }
                     
                     int currentIdx = (curPathIdx != null ? curPathIdx : prevPathIdx);
                     
                     // Handle circular wrap-around display glitch
                     // If targeting Station 1 (index say 50), but physically at end of loop (index 500)
                     // The distance logic breaks because 500 > 50.
                     boolean circularDisp = false;
                     if (line.startPoint != null && line.endPoint != null) {
                        if (line.startPoint.world.equals(line.endPoint.world) &&
                            line.startPoint.x == line.endPoint.x &&
                            line.startPoint.y == line.endPoint.y &&
                            line.startPoint.z == line.endPoint.z) {
                            circularDisp = true;
                        }
                     }

                     if (circularDisp && targetIndex == 1 && currentIdx > line.verifiedPath.size() / 2) {
                         // We are on the first leg (0 -> 1) but physically haven't wrapped index to 0 yet.
                         // Treat currentIdx as effectively 0 (just starting) to show full bar distance
                         currentIdx = 0; 
                     }

                     int totalSeg = nextPathIdx - prevPathIdx;
                     int distCovered = currentIdx - prevPathIdx;
                     int blocksLeft = nextPathIdx - currentIdx;

                     // Handle Wrap-Around (Last Station -> First Station)
                     if (totalSeg < 0) {
                         int pathSize = line.verifiedPath.size();
                         totalSeg += pathSize;
                         
                         if (currentIdx < prevPathIdx) {
                             // We have wrapped around physically (index 0, 1, 2...)
                             distCovered = (pathSize - prevPathIdx) + currentIdx;
                         }
                         
                         // Re-calculate blocksLeft based on wrap
                         blocksLeft = totalSeg - distCovered;
                     }
                     
                     double progress = 1.0;
                     if (totalSeg > 0) {
                        progress = 1.0 - ((double)distCovered / totalSeg);
                     }
                     progress = Math.max(0.0, Math.min(1.0, progress));
                     
                     if (blocksLeft < 0) blocksLeft = 0;
                     
                     String destName = "End";
                     boolean circular = false;
                     if (line.startPoint != null && line.endPoint != null) {
                        if (line.startPoint.world.equals(line.endPoint.world) &&
                            line.startPoint.x == line.endPoint.x &&
                            line.startPoint.y == line.endPoint.y &&
                            line.startPoint.z == line.endPoint.z) {
                            circular = true;
                        }
                     }
                     if (circular && !line.stations.isEmpty()) destName = line.stations.get(0).name;
                     else if (!line.stations.isEmpty()) destName = line.stations.get(line.stations.size()-1).name;
                     
                     // Format: line1 [line] line3 [last stop of the line] | [last stop] → [next stop] ([number of blocks left])
                     String title = "§7" + network.getSignPrefixLine1() + " " +
                                    line.getChatColor() + line.name + " " + 
                                    "§7" + network.getSignPrefixLine3() + " " +
                                    line.getChatColor() + destName + " §7| " + 
                                    line.getChatColor() + prevName + " §7→ " + 
                                    line.getChatColor() + nextName + " §7(" + blocksLeft + ")";
                     
                     bar.setTitle(title);
                     bar.setProgress(progress);
                 } else {
                     String destName = "End";
                     if (!line.stations.isEmpty()) destName = line.stations.get(line.stations.size()-1).name;
                     String title = "§7" + network.getSignPrefixLine1() + " " +
                                    line.getChatColor() + line.name + " " + 
                                    "§7" + network.getSignPrefixLine3() + " " +
                                    line.getChatColor() + destName;
                     bar.setTitle(title);
                     // If we don't know path index, keep full bar? Or empty?
                     // Keep existing progress or sets to 1.0
                 }
                 bar.setVisible(true);
            }
        } else {
            // Setting off or empty -> remove bar
            BossBar bar = cartBossBars.remove(cart.getUniqueId());
            if (bar != null) bar.removeAll();
        }
        
        // Calculate goal direction (station direction) as secondary hint/blend
        Vector stationDir = targetPos.clone().subtract(currentPos).normalize();
        
        // Circular Correction: 
        // When we just looped, targetIndex is 0 (First Station). Cart is physically at End (index 500).
        // Station 0 is at (Start). Distance is ~0.
        // StationDir is (0,0,0) or random noise. 
        // guideDir (from getPathDirection) handles the jump 500 -> 0 -> 1 correctly.
        // We MUST ignore 'stationDir' if we are on a path, because stationDir is pure Euclidean
        // and doesn't know about track topology (loop).
        
        Vector finalDir;
        if (guideDir != null) {
            finalDir = guideDir;
        } else {
             finalDir = stationDir;
        }

        // --- Speed Logic using Virtual Speed (Persistent Momentum) ---
        // Calculate slope factor for consistent horizontal speed
        double hLen = Math.sqrt(finalDir.getX() * finalDir.getX() + finalDir.getZ() * finalDir.getZ());
        double slopeFactor = (hLen < 0.1) ? 1.0 : (1.0 / hLen); 
        
        // Adjust target speed so diagonal/vertical movement covers same horizontal distance
        double adjustedTargetSpeed = currentTargetSpeed * slopeFactor;

        // Retrieve stored virtual speed (to override physics drag)
        double virtSpeed = 0.0;
        if (cart.getPersistentDataContainer().has(speedKey, PersistentDataType.DOUBLE)) {
             virtSpeed = cart.getPersistentDataContainer().get(speedKey, PersistentDataType.DOUBLE);
        } else {
             // init from current real speed
             virtSpeed = cart.getVelocity().length();
        }
        
        // Safety: If stuck for > 5 ticks (shorter than derailment check), kill momentum locally
        // This replaces the old instantaneous velocity check which failed on corners/slopes.
        StuckInfo info = stuckMap.computeIfAbsent(cart.getUniqueId(), k -> new StuckInfo(cart.getLocation().toVector()));
        
        // Use horizontal distance ONLY (ignore Y) to avoid false positives from bobbing on rails
        double dx = info.lastPos.getX() - cart.getLocation().getX();
        double dz = info.lastPos.getZ() - cart.getLocation().getZ();
        double moveDistSq = dx*dx + dz*dz;
        
        // If we haven't moved horizontally this tick...
        if (moveDistSq < 0.0001) {
            // Only count as stuck if we are attempting to move (virtSpeed > 0)
            if (virtSpeed > 0.1) {
                info.stuckTicks++;
            } else {
                info.stuckTicks = 0; // Stopped intentionally
            }
        } else {
             // We moved! Reset counter.
             info.stuckTicks = 0;
             info.lastPos = cart.getLocation().toVector();
        }

        if (info.stuckTicks > 5) { // 0.25 seconds blocked
             virtSpeed = 0.0;
        }

        // Apply accel/decel to Virtual Speed
        double acc = network.getAccel(); // e.g. 0.05
        double dec = network.getDecel(); // e.g. 0.08
        
        if (virtSpeed < adjustedTargetSpeed) {
            virtSpeed = Math.min(adjustedTargetSpeed, virtSpeed + acc);
        } else if (virtSpeed > adjustedTargetSpeed) {
            virtSpeed = Math.max(adjustedTargetSpeed, virtSpeed - dec);
        }
        
        // Store updated virtual speed
        cart.getPersistentDataContainer().set(speedKey, PersistentDataType.DOUBLE, virtSpeed);

        Vector newVel = finalDir.clone().normalize().multiply(virtSpeed);
        
        try {
            float mx = (float) Math.max(virtSpeed * 2.0, 10.0);
            cart.setMaxSpeed(mx);
        } catch (Throwable t) { }
        cart.setVelocity(newVel);
        
        // Stuck detection (Teleport fix)
        // Only valid if we have a path to reset to
        if (!line.verifiedPath.isEmpty()) {
             checkStuck(cart, line, info);
        }
    }
    
    private void checkStuck(Minecart cart, Line line, StuckInfo info) {
         if (info.stuckTicks > 20) { // 1 second stuck
              // Reset to closest point
              int bestIdx = findClosest(line, cart.getLocation().toVector(), 0, line.verifiedPath.size() - 1);
              Vector target = line.verifiedPath.get(bestIdx).clone().add(new Vector(0.5, 0.1, 0.5));
              Location loc = cart.getLocation();
              loc.setX(target.getX());
              loc.setY(target.getY());
              loc.setZ(target.getZ());
              // orient pitch/yaw?
              if (bestIdx + 1 < line.verifiedPath.size()) {
                  Vector next = line.verifiedPath.get(bestIdx + 1).clone().add(new Vector(0.5, 0.1, 0.5));
                  loc.setDirection(next.subtract(target));
              }
              cart.teleport(loc);
              cart.setVelocity(new Vector(0,0,0)); // stop velocity for one tick
              cart.getPersistentDataContainer().set(pathIndexKey, PersistentDataType.INTEGER, bestIdx);
              info.stuckTicks = 0;
              info.lastPos = target;
              // notify
              cart.getPassengers().forEach(e -> e.sendMessage("§eDerail detected. Resetting to path."));
         }
    }
    
    private void finishScan(Minecart cart, Line line) {
         // Only consider successful if we have more than 1 point (start point is added on creation)
         // and we have moved some distance
         if (line.verifiedPath.size() > 1) {
             // Auto-set End Point
             Location endLoc = cart.getLocation();
             Vector vel = cart.getVelocity();
             String d = "N";
             if (vel.lengthSquared() > 0.01) {
                 vel.normalize();
                 if (Math.abs(vel.getX()) > Math.abs(vel.getZ())) {
                     d = vel.getX() > 0 ? "E" : "W";
                 } else {
                     d = vel.getZ() > 0 ? "S" : "N";
                 }
             } else {
                 Vector facing = endLoc.getDirection();
                 if (Math.abs(facing.getX()) > Math.abs(facing.getZ())) {
                     d = facing.getX() > 0 ? "E" : "W";
                 } else {
                     d = facing.getZ() > 0 ? "S" : "N";
                 }
             }
             
             line.endPoint = new Line.EndPoint(endLoc.getWorld().getName(), endLoc.getBlockX(), endLoc.getBlockY(), endLoc.getBlockZ(), d);
             cart.getPassengers().forEach(e -> e.sendMessage("§aEnd Point automatically set to current location (Dir: " + line.endPoint.dir + ")."));

             network.savePath(line);
             network.recalculateLineDistances(line, cart.getPassengers().isEmpty() ? null : (org.bukkit.command.CommandSender)cart.getPassengers().get(0));
             plugin.saveLines(); 
             cart.getPassengers().forEach(e -> e.sendMessage("§aScan complete! Path saved. (" + line.verifiedPath.size() + " points)"));
         } else {
             cart.getPassengers().forEach(e -> e.sendMessage("§cScan aborted: No path recorded (Did not move from start)."));
         }
         cart.eject();
         cart.remove();
         scanStates.remove(cart.getUniqueId());
    }

    private void handleScanning(Minecart cart, Line line) {
        // Safe speed for scanning
        double scanSpeed = toPerTick(network.getScanSpeed());
        cart.setMaxSpeed(Math.max(0.4, scanSpeed * 2.0));
        
        Block b = cart.getLocation().getBlock();
        
        // 1. If not on rail, check below (slopes often place cart slightly above rail block)
        if (!isRailBlock(b)) {
            Block below = b.getRelative(BlockFace.DOWN);
            if (isRailBlock(below)) {
                b = below;
            } else {
                // Not on rail and not above rail -> Stop
                cart.setVelocity(new Vector(0,0,0));
                
                // Detailed feedback
                final Block finalB = b;
                cart.getPassengers().forEach(e -> e.sendMessage("§cScan stopped: Cart off rail at " + finalB.getX() + "," + finalB.getY() + "," + finalB.getZ()));
                
                finishScan(cart, line);
                return;
            }
        }
        
        // 2. Record Path
        Vector v = b.getLocation().toVector();
        if (line.verifiedPath.isEmpty() || !line.verifiedPath.get(line.verifiedPath.size()-1).equals(v)) {
            line.verifiedPath.add(v);
            for (org.bukkit.entity.Entity e : cart.getPassengers()) {
                if (e instanceof org.bukkit.entity.Player) {
                    ((org.bukkit.entity.Player) e).spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText("§eRecording point " + line.verifiedPath.size()));
                }
            }
        }

        // Circular Logic: Check if returned to start with matching direction
        // Requirement: Moved at least some distance (e.g. > 1 block away from start initially) or just enough points
        // User requested distance requirement to be 1 block. 
        // We still need to ensure we don't trigger on the very first tick. 
        // If speed is ~0.4 blocks/tick, 1 block is ~3 points. Let's use > 10 points to be safe but responsive.
        if (line.verifiedPath.size() > 10 && !line.verifiedPath.isEmpty()) {
            Vector startVec = line.verifiedPath.get(0);
            // Distance check (Block Vector equality)
            if (v.getBlockX() == startVec.getBlockX() && 
                v.getBlockY() == startVec.getBlockY() && 
                v.getBlockZ() == startVec.getBlockZ()) {
                 
                 // Velocity check
                 Vector vel = cart.getVelocity();
                 if (vel.lengthSquared() > 0.01) {
                     Vector dir = vel.normalize();
                     // Check against Line Start Direction if available, or just general consistency
                     // Simplify: Assume if we hit the exact block after 50 blocks, it's a loop. 
                     // But let's check direction to be safe (avoid reversing into start).
                     
                     // Helper to get cardinal direction from vector
                     String currentDirStr = "N";
                     if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) {
                         currentDirStr = dir.getX() > 0 ? "E" : "W";
                     } else {
                         currentDirStr = dir.getZ() > 0 ? "S" : "N";
                     }
                     
                     boolean match = false;
                     if (line.startPoint != null) {
                         if (line.startPoint.dir.equalsIgnoreCase(currentDirStr)) match = true;
                     } else {
                         match = true; // No start point defined, trust location
                     }
                     
                     if (match) {
                         cart.getPassengers().forEach(e -> e.sendMessage("§aLoop detected! Finishing scan automatically."));
                         // Ensure exact closure
                         line.verifiedPath.add(startVec);
                         finishScan(cart, line);
                         return;
                     }
                 }
            }
        }
        
        // 3. Movement Logic
        Vector currentVel = cart.getVelocity();
        Vector direction = null;

        // If moving, maintain direction but ensure speed
        if (currentVel.lengthSquared() > 0.0001) {
            direction = currentVel.normalize();
            // Anti-reverse check: if we have history, don't let it simply flip 180 deg due to collision/glitch
            if (line.verifiedPath.size() >= 2) {
                Vector last = line.verifiedPath.get(line.verifiedPath.size() - 2);
                Vector pathDir = v.clone().subtract(last).normalize();
                if (pathDir.lengthSquared() > 0.01) {
                    // If moving roughly opposite to path direction (-0.5 dot product)
                    if (direction.dot(pathDir) < -0.5) {
                        direction = pathDir; // Correct course
                    }
                }
            }
        } else {
            // Stopped. Determine start direction.
            if (line.verifiedPath.size() >= 2) {
                Vector last = line.verifiedPath.get(line.verifiedPath.size() - 2);
                direction = v.clone().subtract(last).normalize();
            }
            // Fallback to cart facing (spawn direction) if no path
            if (direction == null || direction.lengthSquared() < 0.01) {
                 Vector facing = cart.getLocation().getDirection();
                 facing.setY(0);
                 if (facing.lengthSquared() > 0.01) {
                     direction = facing.normalize();
                 } else {
                     direction = detectRailDirection(cart);
                 }
            }
        }
        
        if (direction != null && !Double.isNaN(direction.getX())) {
            cart.setVelocity(direction.multiply(scanSpeed));
        }
        
        // Check if stopped for 1 second (stuck or end of line)
        Vector curPos = cart.getLocation().toVector();
        ScanState ss = scanStates.computeIfAbsent(cart.getUniqueId(), k -> new ScanState(curPos));
        if (ss.lastPos.distanceSquared(curPos) < 0.0001) {
            ss.stoppedTicks++;
        } else {
            ss.stoppedTicks = 0;
            ss.lastPos = curPos;
        }
        
        if (ss.stoppedTicks > 20) {
             finishScan(cart, line);
        }
    }
    
    private Vector getPathDirection(Minecart cart, Line line) {
        Integer idx = cart.getPersistentDataContainer().get(pathIndexKey, PersistentDataType.INTEGER);
        if (idx == null) idx = 0;
        
        // Search local neighborhood in path... 
        // If we are "lost" (idx=0 or far away), search globally
        int start = Math.max(0, idx - 10);
        int end = Math.min(line.verifiedPath.size() - 1, idx + 100);
        
        // First pass: local search
        int bestIdx = findClosest(line, cart.getLocation().toVector(), start, end);
        
        // Circular Logic: If we are near the end of the path, check the start as well
        // This handles smooth transition across the loop seam (index N -> index 0)
        boolean isCircular = false;
        if (line.startPoint != null && line.endPoint != null) {
           if (line.startPoint.world.equals(line.endPoint.world) &&
               line.startPoint.x == line.endPoint.x &&
               line.startPoint.y == line.endPoint.y &&
               line.startPoint.z == line.endPoint.z) {
               isCircular = true;
           }
        }
        
        if (isCircular && idx > line.verifiedPath.size() - 20) {
            int startBest = findClosest(line, cart.getLocation().toVector(), 0, Math.min(line.verifiedPath.size()-1, 10));
            double dOld = getDistSq(line, bestIdx, cart.getLocation().toVector());
            double dNew = getDistSq(line, startBest, cart.getLocation().toVector());
            
            // Prefer switching to start if fairly close to start, even if End is slightly closer
            // ensuring we don't stick to the end index too long.
            if (dNew < dOld + 4.0) { // Bias towards start index once we loop
                bestIdx = startBest;
            }
        }
        
        double distSq = getDistSq(line, bestIdx, cart.getLocation().toVector());
        
        // If local search failed to find a close point (> 4 blocks away), try global search
        if (distSq > 16.0) {
            bestIdx = findClosest(line, cart.getLocation().toVector(), 0, line.verifiedPath.size() - 1);
        }
        
        // Update index
        cart.getPersistentDataContainer().set(pathIndexKey, PersistentDataType.INTEGER, bestIdx);
        
        // Target is next point
        int nextIdx = bestIdx + 1;
        if (nextIdx >= line.verifiedPath.size()) {
             // Handle Circular Scan Path: 
             // If the line is circular, the end of the scan path connects to the start of the scan path.
             // We check if line is circular logic (Start Matches End)
             boolean circular = false;
             if (line.startPoint != null && line.endPoint != null) {
                if (line.startPoint.world.equals(line.endPoint.world) &&
                    line.startPoint.x == line.endPoint.x &&
                    line.startPoint.y == line.endPoint.y &&
                    line.startPoint.z == line.endPoint.z) {
                    circular = true;
                }
             }
             
             if (circular) {
                 nextIdx = 0;
                 if (line.verifiedPath.size() > 1 && nextIdx == bestIdx) return null; // Path too short
                 // If the end point is EXACTLY the same as start point, step +1 to avoid 0-vector
                 // OR if distance from current to next is tiny (< 0.5 block), aim further
                 boolean tooClose = false;
                 if (nextIdx == 0 && !line.verifiedPath.isEmpty()) {
                     Vector endVec = line.verifiedPath.get(bestIdx);
                     Vector startVec = line.verifiedPath.get(0);
                     if (endVec.distanceSquared(startVec) < 0.01) {
                         tooClose = true;
                     }
                 }
                 
                 // Also ensure we don't aim at point cart is already sitting on
                 if (tooClose || line.verifiedPath.get(nextIdx).distanceSquared(cart.getLocation().toVector()) < 0.25) {
                      nextIdx = 1; // Aim for point 1
                      // Safety: if point 1 is also tiny distance close (path density high?)
                      if (nextIdx < line.verifiedPath.size() && line.verifiedPath.get(nextIdx).distanceSquared(cart.getLocation().toVector()) < 0.25) {
                          nextIdx = 2; // Aim for point 2
                      }
                 }
                 
                 // Clamp
                 if (nextIdx >= line.verifiedPath.size()) nextIdx = line.verifiedPath.size() - 1;
             } else {
                 return null; // End of path
             }
        }

        if (nextIdx < line.verifiedPath.size()) {
            Vector currentPoint = line.verifiedPath.get(bestIdx);
            Vector nextPoint = line.verifiedPath.get(nextIdx);
            
            // "Stick to Track" Logic:
            // 1. Calculate ideal target at block center
            Vector target = nextPoint.clone().add(new Vector(0.5, 0, 0.5));
            Vector dir = target.subtract(cart.getLocation().toVector());
            
            // 2. Vertical Stability:
            // If the track segment is flat (no Y change between nodes), ignore vertical offset
            // to prevent "pushing down" into the rails which causes bobbing/physics fighting.
            if (currentPoint.getBlockY() == nextPoint.getBlockY()) {
                dir.setY(0);
            }
            
            return dir.normalize();
        }
        return null;
    }
    
    private int findClosest(Line line, Vector pos, int start, int end) {
        int best = start;
        double bestDist = Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            Vector p = line.verifiedPath.get(i).clone().add(new Vector(0.5, 0, 0.5));
            double d = p.distanceSquared(pos);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }
    
    private double getDistSq(Line line, int idx, Vector pos) {
        if (idx < 0 || idx >= line.verifiedPath.size()) return Double.MAX_VALUE;
        return line.verifiedPath.get(idx).clone().add(new Vector(0.5, 0, 0.5)).distanceSquared(pos);
    }
    
    // Node-based handlers removed — stations and limiters handled separately in updateCart()
    
    private double limiterSpeedFrom(LimiterEntry node) {
        if (node == null) return network.getDefaultSpeedVal();
        String spec = node.speedSpec == null ? "" : node.speedSpec.trim();
        if (spec.isEmpty()) return network.getDefaultSpeedVal();
        Double v = network.getSpeedVal(spec.toLowerCase());
        if (v != null) return v;
        try {
            return Double.parseDouble(spec);
        } catch (NumberFormatException ex) {
            return network.getDefaultSpeedVal();
        }
    }

    // Attempt to detect the rail's direction vector under the cart.
    // Returns a normalized Vector in world-space pointing along the rail (including small Y component for ascending rails),
    // or null if no rail detected.
    private Vector detectRailDirection(Minecart cart) {
        try {
            Block b = cart.getLocation().getBlock();
            if (b == null) return null;
            if (!isRailBlock(b)) return null;

            // Check presence of rails in cardinal neighbors
            boolean north = isRailBlock(b.getRelative(BlockFace.NORTH));
            boolean south = isRailBlock(b.getRelative(BlockFace.SOUTH));
            boolean east = isRailBlock(b.getRelative(BlockFace.EAST));
            boolean west = isRailBlock(b.getRelative(BlockFace.WEST));

            double dx = 0, dz = 0, dy = 0;
            if (east && !west) dx = 1;
            else if (west && !east) dx = -1;
            if (south && !north) dz = -1;
            else if (north && !south) dz = 1;

            // Diagonals or curves: if both east and north present, prefer diagonal
            if (dx == 0 && dz == 0) {
                if (north && east) { dx = 1; dz = 1; }
                else if (north && west) { dx = -1; dz = 1; }
                else if (south && east) { dx = 1; dz = -1; }
                else if (south && west) { dx = -1; dz = -1; }
            }

            // Detect ascending rails by checking rail block one above in neighbor directions
            if (north && isRailBlock(b.getRelative(BlockFace.NORTH).getRelative(BlockFace.UP))) dy = 0.20;
            else if (south && isRailBlock(b.getRelative(BlockFace.SOUTH).getRelative(BlockFace.UP))) dy = 0.20;
            else if (east && isRailBlock(b.getRelative(BlockFace.EAST).getRelative(BlockFace.UP))) dy = 0.20;
            else if (west && isRailBlock(b.getRelative(BlockFace.WEST).getRelative(BlockFace.UP))) dy = 0.20;

            // If still zero direction (isolated rail or straight connected rail), align with current cart velocity/facing
            if (dx == 0 && dz == 0) {
                 // For straight rails (North+South or East+West), dx/dz cancels out to 0 above.
                 // We need to pick one direction based on cart orientation.
                 Vector vel = cart.getVelocity();
                 Vector hint = (vel.lengthSquared() > 1e-6) ? vel : cart.getLocation().getDirection();
                 hint.setY(0);
                 hint.normalize();
                 
                 if (north && south) {
                     // North-South Track. Align with hint.
                     // North is -Z, South is +Z
                     if (hint.getZ() < 0) dz = -1; // Moving North
                     else dz = 1; // Moving South
                 } else if (east && west) {
                     // East-West axis
                     if (hint.getX() < 0) dx = -1; // Moving West
                     else dx = 1; // Moving East
                 }
            }

            // If STILL zero (truly isolated?), fallback
            if (dx == 0 && dz == 0) {
                Vector vel = cart.getVelocity();
                if (vel.lengthSquared() < 1e-6) return null;
                Vector v = vel.clone(); v.setY(0); if (v.lengthSquared() < 1e-6) return null;
                return v.normalize();
            }

            Vector out = new Vector(dx, dy, dz);
            return out.normalize();
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isRailBlock(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        return m == Material.RAIL || m == Material.POWERED_RAIL || m == Material.DETECTOR_RAIL || m == Material.ACTIVATOR_RAIL;
    }
    
    // Helper for segment checks
    private Vector getClosestPointOnSegment(Vector p1, Vector p2, Vector p) {
        Vector v = p2.clone().subtract(p1);
        double lenSq = v.lengthSquared();
        if (lenSq < 0.000001) return p1;
        
        Vector w = p.clone().subtract(p1);
        double t = w.dot(v) / lenSq;
        
        if (t <= 0) return p1;
        if (t >= 1) return p2;
        return p1.clone().add(v.multiply(t));
    }

    private double toPerTick(double blocksPerSecond) {
        // convert blocks/second to blocks/tick (20 ticks per second)
        return blocksPerSecond / 20.0;
    }
}

