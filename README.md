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
```

# TPM 传送点管理插件 使用教程

## 简介
TPM (Teleport Manager) 是一款轻量、易用的 Minecraft 传送点管理插件，支持玩家自主创建、管理个人传送点，并提供主城、重生点传送和额度购买功能。

## 前置插件
- **Vault**（必需）用于经济系统，同时需要安装一款经济插件（如 EssentialsX、CMI）。
- 服务端版本：Purpur 1.21+（兼容 Paper/Bukkit 1.21）。

## 命令列表
| 命令 | 别名 | 用途 |
|------|------|------|
| `/tpm home` | `/tpm h` | 传送至个人重生点（床或世界出生点） |
| `/tpm lobby` | `/tpm l` | 传送至主城（坐标在 config.yml 设置） |
| `/tpm pointadd <名称> <X> <Y> <Z>` | `/tpm pa` | 添加一个传送点（名称不能重复） |
| `/tpm point <名称>` | `/tpm p` | 传送至指定的传送点 |
| `/tpm list` | - | 列出你所有的传送点 |
| `/tpm pointbuy <数量>` | `/tpm pb` | 购买额外的传送点额度（默认 12000 金币/个） |
| `/tpm pr` | - | 查看剩余传送点额度 |
| `/tpm version` | `/tpm v` | 查看插件版本和 Github 下载链接 |
| `/tpm help` | - | 显示帮助信息 |
| `/tpm suicide` | `/tpm kill`、`/suicide` | 自杀（无需 OP）并广播死亡坐标 |
| `/tpm reload` | - | 重载配置文件（需要 OP 权限） |

## 权限
- `tpm.reload` – 允许使用 `/tpm reload`（默认仅 OP）。

## 配置文件说明 (`config.yml`)
- `enable-*` – 各个功能的开关（`true` 开启，`false` 关闭）。
- `lobby-world` / `lobby-x/y/z/yaw/pitch` – 主城坐标（需包含世界名）。
- `pointbuy-cost` – 每个传送点额度的价格（金币）。
- `initial-points` – 每位玩家初始免费额度（默认 4）。

## 常见问题
1. **为什么购买额度提示“经济插件未启用”？**  
   请确保已安装 Vault 和一款经济插件（如 EssentialsX）。

2. **如何让玩家使用 `/tpm list` 等命令？**  
   默认所有命令对所有玩家开放，除了 `/tpm reload` 需要 OP。

3. **传送点数据保存在哪里？**  
   每个玩家的数据存储在 `plugins/TPM/playerdata/<UUID>.yml`。

## 结语
TPM 致力于为服务器提供一个简单且功能完备的传送管理方案。如有问题，欢迎前往 Github 仓库提交 Issue。
