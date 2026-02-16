# TransMinecraftExpress

A Minecraft Spigot plugin for creating automated minecart transit lines with customizable speeds, stations, and path scanning.

## Features

- **Train Lines & Stations**: Create both loop-based and linear transit lines
- **Circular Lines**: Like a record baby, right round right round
- **No Powered Rails needed**: Just use normal rails (or powered ones if you prefer)
- **Path Scanning for really quick minecarts**: Vanilla Minecarts are a bit buggy, but with path scanning, you can go as fast as you can load your chunks
- **Display System**: Configurable signs (hanging, standing and wall) with line, station and last station information
- **Boss Bar Progress**: Real-time distance and destination display for passengers
- **Multi-Cart Trains**: Players can click on TMX carts to form a linked train
- **Animal Trains**: Leashed animals can be loaded into trains automatically
- **Reconnect/Exit Handling**: Animal transporters leave with animals + reattached leads; normal riders can remain in carts
- **Command-based Management**: easily understandable commands for creating, managing, and deleting transit infrastructure
- **Human-readable config file**: The `config.yml` is easily readable

## Installation

1. Download the latest `TransMinecraftExpress.jar` from releases
2. Place in your server's `plugins/` directory
3. Restart the server
4. The plugin will generate a `config.yml` file on first run
5. Configure settings as needed

## Quick Start

### Build a Minecart rail track
 
- Use any kind of track you want (no need for powered rails)
- Build twists, turns and long bridges to your heart's desire!

### Create and scan a Line with stations

- Create stations by standing on a piece of track. 
- For the starting/first station, you should look in the direction the cart is supposed to go off, and add a 'true' at the end to signify its the first station. 
- Afterwards, you can scan the line. It will automatically detect the end or if the line is a loop and save the track to a .bin file. 
- All stations afterwards will be ordered automatically in the order they appear in the .bin file. 
- Add as many stations as you want.

```
/tmx create line S42 #FF5733 
/tmx create station S42 Ostkreuz true
/tmx scan S42
/tmx create station S42 Gesundbrunnen
/tmx create station S42 Westkreuz
/tmx create station S42 Südkreuz
```

### Set Speed Parameters

- Speedvals are the top speed you can reach using the accel value. 
- `station` is a mandatory, but configurable speedval, it's the speed you have when first boarding a train.
- You can add as many speedvals as you want, name them as you want aswell.
- `accel` and `decel` are the acceleration and deceleration parameters
- scanspeed the speed you scan your track with, in my experience, 15 works quite well

```
/tmx set speedval station 3
/tmx set speedval scenic 20
/tmx set speedval express 100
/tmx set accel 0.05
/tmx set decel 0.08
/tmx set scanspeed 15.0
```

### Add Speed Limiters

- Limiters are created like start stations, as they have a direction (so you can use a piece of track in two directions)
- you can put two limiters on the same block with different directions
- they govern the top speed a minecart goes when it passes the Limiter. 
- If its faster, it will decelerate, if its quicker, well, weeeeeeeee
- Limiters are named automatically, e.g. `Ostkreuz_1` for the first limit after the station `Ostkreuz`

```
/tmx create limit S42 station
/tmx create limit S42 express
```

### Use a Line

1. Create a sign with `[TMX]` on the first line
2. Put the line name on the second line
3. Put a station name on the third line
4. Right-click the sign to spawn a cart to that station

### Create a Player Train

- Player A boards a TMX cart.
- Player B right-clicks that occupied TMX cart.
- A follower cart is created behind the train tail and Player B is seated in it.
- Additional players can keep joining; each new cart is appended at the end.

```
/tmx set trains true
/tmx set trainspacing 1.5
```

### Create an Animal Train

- Enable animal trains.
- Hold animals on leads and right-click a TMX sign or TMX cart.
- Animals are moved into follower carts behind the player; leads are removed.
- If that transporter leaves, animals are unloaded and leads are reattached.

```
/tmx set animaltrains true
```

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
- `/tmx set trainspacing <value>` - Distance between linked train carts (blocks)
- `/tmx set trains <true|false>` - Enable/disable joining a train by right-clicking TMX carts
- `/tmx set animaltrains <true|false>` - Enable/disable animal train creation/joining from leads
- `/tmx set suffocation <true|false>` - Toggle train-animal suffocation handling (`false` = no suffocation damage in TMX train carts)
- `/tmx set speedval <name> <value>` - Define a named speed (e.g., "station", "express")
- `/tmx set signprefix <1|2|3> <text>` - Customize sign and bossbar display text
- `/tmx set collision <true|false>` - Enable/disable entity collision
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

### Interaction Behaviors (No Direct Command)
- Right-click an occupied TMX cart to join as follower (when `trains=true`)
- Right-click a TMX sign to spawn/board a cart at that station
- Right-click with leashed animals to create animal train followers (when `animaltrains=true`)

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
- Signs show the first/start station as the destination

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
gradle copyPlugin
```

Output JAR: `build/libs/TransMinecraftExpress.jar`

Copies plugin to: `server/plugins/TransMinecraftExpress.jar`

## Support

For issues or feature requests, please provide:
- Server log output
- Line configuration (`/tmx list <line>`)
- Steps to reproduce