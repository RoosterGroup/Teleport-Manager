# TPM - Teleport Manager

**TPM** is a lightweight, feature-rich teleportation management plugin for Minecraft servers running Purpur 1.21+ (Bukkit/Paper compatible). It allows players to create and manage their own waypoints, teleport to spawn/lobby, and purchase additional waypoint slots using in-game currency.

## Features
- **Home & Lobby Teleport** – Instantly return to your bed/spawn or the server's lobby.
- **Custom Waypoints** – Add up to `initial-points` waypoints (default 4) with names and coordinates.
- **Waypoint List** – View all your saved waypoints with `/tpm list`.
- **Expandable Slots** – Buy extra waypoint slots with money (supports Vault economy).
- **Suicide Command** – Kill yourself (no OP required) with death coordinate broadcast.
- **Reload Support** – Reload configuration on the fly (OP only).
- **Toggleable Features** – Every command can be enabled/disabled in `config.yml`.

## Requirements
- **Vault** (and an economy plugin like EssentialsX) – for purchase features.
- **Purpur 1.21+** (or any Paper/Bukkit 1.21 fork).

## Commands
| Command | Alias | Description |
|---------|-------|-------------|
| `/tpm home` | `/tpm h` | Teleport to your bed spawn point |
| `/tpm lobby` | `/tpm l` | Teleport to the configured lobby |
| `/tpm pointadd <name> <x> <y> <z>` | `/tpm pa` | Add a new waypoint |
| `/tpm point <name>` | `/tpm p` | Teleport to a saved waypoint |
| `/tpm list` | - | List all your waypoints |
| `/tpm pointbuy <amount>` | `/tpm pb` | Buy extra waypoint slots |
| `/tpm pr` | - | Check remaining slots |
| `/tpm version` | `/tpm v` | Show version and download link |
| `/tpm help` | - | Show help menu |
| `/tpm suicide` | `/tpm kill`、`/suicide` | Commit suicide (broadcasts location) |
| `/tpm reload` | - | Reload config (OP only) |

## Configuration
All options are in `config.yml`:
```yaml
enable-home: true
enable-lobby: true
enable-point: true
enable-list: true
enable-pointbuy: true
enable-pr: true
enable-help: true
enable-suicide: true
enable-reload: true

lobby-world: "world"
lobby-x: 0.0
lobby-y: 64.0
lobby-z: 0.0
lobby-yaw: 0.0
lobby-pitch: 0.0

pointbuy-cost: 12000.0
initial-points: 4
