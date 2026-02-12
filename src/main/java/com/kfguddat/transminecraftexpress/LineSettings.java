package com.kfguddat.transminecraftexpress;

import org.bukkit.configuration.ConfigurationSection;

public final class LineSettings {
    public static final LineSettings DEFAULT = new LineSettings(
            1.2D,
            0.2D,
            0.06D,
            0.08D,
            6.0D,
            1.5D
    );

    public final double targetSpeed;
    public final double approachSpeed;
    public final double accelPerTick;
    public final double decelPerTick;
    public final double approachRadius;
    public final double stopRadius;

    public LineSettings(
            double targetSpeed,
            double approachSpeed,
            double accelPerTick,
            double decelPerTick,
            double approachRadius,
            double stopRadius
    ) {
        this.targetSpeed = targetSpeed;
        this.approachSpeed = approachSpeed;
        this.accelPerTick = accelPerTick;
        this.decelPerTick = decelPerTick;
        this.approachRadius = approachRadius;
        this.stopRadius = stopRadius;
    }

    public static LineSettings fromConfig(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }
        return new LineSettings(
                section.getDouble("target-speed", DEFAULT.targetSpeed),
                section.getDouble("approach-speed", DEFAULT.approachSpeed),
                section.getDouble("accel-per-tick", DEFAULT.accelPerTick),
                section.getDouble("decel-per-tick", DEFAULT.decelPerTick),
                section.getDouble("approach-radius", DEFAULT.approachRadius),
                section.getDouble("stop-radius", DEFAULT.stopRadius)
        );
    }
}
