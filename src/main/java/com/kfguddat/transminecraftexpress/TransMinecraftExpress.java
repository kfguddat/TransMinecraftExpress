
package com.kfguddat.transminecraftexpress;

import org.bukkit.plugin.java.JavaPlugin;

public final class TransMinecraftExpress extends JavaPlugin {
    private MinecartSpeedController speedController;
    private NetworkManager network;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        network = new NetworkManager();
        network.setDataFolder(getDataFolder());
        network.reload(getConfig());
        
        speedController = new MinecartSpeedController(this, network);
        speedController.start();
        
        EventListener listener = new EventListener(this, network);
        getServer().getPluginManager().registerEvents(listener, this);
        
        if (getCommand("tmx") != null) {
            getCommand("tmx").setExecutor(new CommandHandler(this, network));
            getCommand("tmx").setTabCompleter(new TmxTabCompleter(network));
        }
        getLogger().info("TransMinecraftExpress enabled");
    }
    
    public void saveLines() {
        // Persist global speed settings
        getConfig().set("accel", network.getAccel());
        getConfig().set("decel", network.getDecel());
        getConfig().set("scan-speed", network.getScanSpeed());
        getConfig().set("nextstationbar", network.isShowNextStationBar());
        getConfig().set("collision", network.isCollision());
        // Save speed-vals map
        getConfig().set("speed-vals", null);
        org.bukkit.configuration.ConfigurationSection sv = getConfig().createSection("speed-vals");
        for (java.util.Map.Entry<String, Double> e : network.getSpeedVals().entrySet()) {
            sv.set(e.getKey(), e.getValue());
        }
        // Sign prefix strings
        getConfig().set("sign-prefix.line1", network.getSignPrefixLine1());
        getConfig().set("sign-prefix.line2", network.getSignPrefixLine2());
        getConfig().set("sign-prefix.line3", network.getSignPrefixLine3());

        // Clear "lines" section so deleted lines are removed from config
        getConfig().set("lines", null);

        // Save lines using their display name (line.name) as the key to preserve case
        // The NetworkManager stores them keyed by lowercase for lookup, but we iterate values()
        for (Line l : network.getLines().values()) {
            l.saveTo(getConfig().createSection("lines." + l.name));
        }
        saveConfig();
        network.reload(getConfig());
    }

    @Override
    public void onDisable() {
        if (speedController != null) {
            speedController.stop();
            speedController = null;
        }
        network = null;
        getLogger().info("TransMinecraftExpress disabled");
    }
}

