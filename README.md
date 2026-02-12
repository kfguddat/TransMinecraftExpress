# TransMinecraftExpress

A Minecraft Spigot plugin for creating automated minecart transit lines with customizable speeds, stations, and path scanning.

## Features

- **Circular & Linear Line Support**: Create both loop-based and linear transit lines
- **Automatic Path Scanning**: Record rail paths with in-game cart scanning
- **Station Management**: Add stations with automatic naming and ordering
- **Speed Controls**: Set acceleration, deceleration, and speed limits per line or at specific limiters
- **Display System**: Configurable signs with dynamic station information and distance tracking
- **Boss Bar Progress**: Real-time distance and destination display for passengers
- **Path Guidance**: Automatic steering along recorded paths with collision handling
- **Command-based Management**: Full CLI for creating, managing, and deleting transit infrastructure

## Installation

1. Download the latest `TransMinecraftExpress.jar` from releases
2. Place in your server's `plugins/` directory
3. Restart the server
4. The plugin will generate a `config.yml` file on first run
5. Configure settings as needed

## Quick Start

### Create a Transit Line

```
/tmx create line MyLine #FF5733
/tmx create start MyLine
/tmx create station MyLine Station1 true
/tmx create station MyLine Station2
/tmx scan MyLine
```

### Set Speed Parameters

```
/tmx set accel 0.05
/tmx set decel 0.08
/tmx set scanspeed 2.0
/tmx set speedval station 0.3
/tmx set speedval express 0.5
```

### Add Speed Limiters

```
/tmx create limit MyLine station
/tmx create limit MyLine express
```

### Use a Line

1. Create a sign with `[TMX]` on the first line
2. Put the line name on the second line
3. Put a station name on the third line
4. Right-click the sign to spawn a cart to that station

## Command Reference

### Creation Commands
- `/tmx create line <name> <hexColor>` - Create a new transit line
- `/tmx create station <line> <name> [isStart]` - Add a station
- `/tmx create start <line>` - Set the scan start point (player location)
- `/tmx create end <line>` - Set the end point (auto-detected after scan)
- `/tmx create limit <line> <speedSpec>` - Add a speed limiter at current location

### Management Commands
- `/tmx scan <line>` - Begin path recording (ride the cart along your rails)
- `/tmx list [line]` - List all lines or detailed info for a specific line
- `/tmx manage line [name]` - Interactive line management with move/delete options
- `/tmx manage move line <name> <up|down>` - Change line priority order

### Configuration Commands
- `/tmx set accel <value>` - Global acceleration (blocks/tick²)
- `/tmx set decel <value>` - Global deceleration
- `/tmx set scanspeed <value>` - Speed during path scanning
- `/tmx set speedval <name> <value>` - Define a named speed (e.g., "station", "express")
- `/tmx set signprefix <1|2|3> <text>` - Customize sign display text
- `/tmx set collision <true|false>` - Enable/disable NPC collision
- `/tmx set nextstationbar <true|false>` - Show boss bar with station info
- `/tmx set linecolor <line> <hexColor>` - Change line boss bar color
- `/tmx set end <line>` - Manually set the end point (player location)

### Deletion Commands
- `/tmx delete line <name>` - Delete a transit line
- `/tmx delete station <line> <name>` - Remove a station
- `/tmx delete limit <line> <name>` - Remove a speed limiter
- `/tmx delete speedval <name>` - Remove a named speed definition

### Rename Commands
- `/tmx rename line <oldName> <newName>` - Rename a line
- `/tmx rename station <line> <oldName> <newName>` - Rename a station
- `/tmx rename limit <line> <oldName> <newName>` - Rename a limiter

### Other
- `/tmx reload` - Reload configuration from file

## Configuration

Edit `plugins/config.yml` to customize:

```yaml
acceleration: 0.05
deceleration: 0.08
scan_speed: 2.0
show_next_station_bar: true
collision_enabled: false

speeds:
  station: 0.3
  express: 0.5

sign_prefixes:
  line1: "§7[Line]"
  line2: "§7[Stn]"
  line3: "§7[End]"
```

## How It Works

1. **Path Scanning**: Ride a cart along your rails using `/tmx scan`. The plugin records every block touched.
2. **Station Indexing**: Stations are sorted by distance along the scanned path.
3. **Physical Movement**: The plugin calculates direction based on the recorded path and applies smooth acceleration/deceleration.
4. **Circular Lines**: If a line's start and end points match, it loops back to the beginning.
5. **Speed Control**: Acceleration ramps up to the target speed set by the station or limiter at the cart's current location.

## Circular vs Linear Lines

**Circular Line** (Loop):
- Start Point = End Point
- Cart automatically loops from the last station back to the first
- Signs show the first station as the destination

**Linear Line**:
- Start Point ≠ End Point
- Cart is ejected at the end and removed
- Signs show the last station as the destination

## Requirements

- Spigot/Paper 1.21+
- Java 21+

## License

This project is provided as-is for use on Minecraft servers.

## Development

Built with:
- Gradle
- Bukkit API (Paper)
- Java 21

### Building

```bash
gradle build
```

Output JAR: `build/libs/TransMinecraftExpress-0.1.0.jar`

## Troubleshooting

**Cart not moving**: Ensure the path was scanned with `/tmx scan` and at least 1 station exists.

**Path not recognized**: Run `/tmx scan` again to re-record the route.

**Signals "Distance = 0"**: This is normal when the cart is very close to or at a station.

**Wobbling on loops**: Ensure the start and end points exactly match (same block coordinate and direction).

## Support

For issues or feature requests, please provide:
- Server log output
- Line configuration (`/tmx list <line>`)
- Steps to reproduce