package com.roostergroup.tpm;

import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class TPM extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static TPM instance;
    private Economy economy;
    private File playerDataFolder;
    private final Map<UUID, PlayerData> playerDataCache = new HashMap<>();

    // 配置项
    private boolean enableHome, enableLobby, enablePoint, enableList,
            enablePointbuy, enablePr, enableHelp, enableSuicide, enableReload,
            enableSet;
    private String lobbyWorld;
    private double lobbyX, lobbyY, lobbyZ, lobbyYaw, lobbyPitch;
    private double pointbuyCost;
    private int initialPoints;
    // 新增配置项
    private boolean renameCostEnabled;
    private double renameCost;
    private boolean deleteRefundEnabled;
    private double deleteRefund;
    private boolean moveCostEnabled;
    private double moveCost;

    // 消息前缀
    private static final String PREFIX = ChatColor.GOLD + "[TPM]" + ChatColor.RESET + " ";

    private String getVersion() {
        return getDescription().getVersion();
    }

    @Override
    public void onEnable() {
        instance = this;
        try {
            // 首次加载时自动生成 config.yml（如果不存在）
            saveDefaultConfig();
            loadConfig();

            // 初始化玩家数据文件夹
            playerDataFolder = new File(getDataFolder(), "playerdata");
            if (!playerDataFolder.exists()) {
                playerDataFolder.mkdirs();
            }

            // 注册命令
            Objects.requireNonNull(getCommand("tpm")).setExecutor(this);
            Objects.requireNonNull(getCommand("tpm")).setTabCompleter(this);
            Objects.requireNonNull(getCommand("suicide")).setExecutor(this);

            // 尝试接入 Vault 经济
            if (setupEconomy()) {
                getLogger().info("已成功接入 Vault 经济系统。");
            } else {
                getLogger().warning("未找到 Vault 经济插件，购买额度功能将被禁用。");
            }

            // 初始化 bStats（插件 ID: 33004）
            try {
                Metrics metrics = new Metrics(this, 33004);
                getLogger().info("已接入 bStats 统计（插件 ID: 33004）。");
                getLogger().info("统计数据匿名，可在 /plugins/bStats/config.yml 中禁用。");
            } catch (Exception e) {
                getLogger().warning("bStats 初始化失败，不影响插件正常运行。");
            }

            printStartupSuccess();
            getLogger().info("TPM 插件已启用！");
        } catch (Exception e) {
            printStartupFailure();
            getLogger().severe("插件加载失败！即将禁用！");
            getLogger().severe("可以在 Github 仓库上看解决办法或提交 Issue");
            getLogger().severe("插件 Github 仓库：https://github.com/RoosterGroup/Teleport-Manager");
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        for (UUID uuid : playerDataCache.keySet()) {
            savePlayerData(uuid);
        }
        playerDataCache.clear();
        getLogger().info("TPM 插件已禁用。");
    }

    // ------------------- 启动日志 -------------------
    private void printStartupSuccess() {
        String version = getVersion();
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[TPM]RoosterGroup         " + ChatColor.RESET + "ʟᴏᴀᴅᴇᴅ");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[TPM] ═╦═       ╓    ╖  ");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[TPM]  ║        ║╲╱║");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[TPM]  ║ ELEPORT║    ║ANAGER");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[TPM]Version " + version + "                 ");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[TPM]仓库地址：https://github.com/RoosterGroup/Teleport-Manager");
    }

    private void printStartupFailure() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[TPM] ╲╱");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[TPM] ╱╲ FAILED!");
    }

    // ------------------- 配置加载 -------------------
    private void loadConfig() {
        try {
            reloadConfig();
            FileConfiguration config = getConfig();
            if (!config.contains("enable-home")) {
                throw new IllegalStateException("配置文件损坏或缺少关键节点");
            }
            // 原有配置
            enableHome = config.getBoolean("enable-home", true);
            enableLobby = config.getBoolean("enable-lobby", true);
            enablePoint = config.getBoolean("enable-point", true);
            enableList = config.getBoolean("enable-list", true);
            enablePointbuy = config.getBoolean("enable-pointbuy", true);
            enablePr = config.getBoolean("enable-pr", true);
            enableHelp = config.getBoolean("enable-help", true);
            enableSuicide = config.getBoolean("enable-suicide", true);
            enableReload = config.getBoolean("enable-reload", true);
            enableSet = config.getBoolean("enable-set", true);

            lobbyWorld = config.getString("lobby-world", "world");
            lobbyX = config.getDouble("lobby-x", 0.0);
            lobbyY = config.getDouble("lobby-y", 64.0);
            lobbyZ = config.getDouble("lobby-z", 0.0);
            lobbyYaw = config.getDouble("lobby-yaw", 0.0);
            lobbyPitch = config.getDouble("lobby-pitch", 0.0);

            pointbuyCost = config.getDouble("pointbuy-cost", 12000.0);
            initialPoints = config.getInt("initial-points", 4);
            if (initialPoints < 0) initialPoints = 0;

            // 新增配置
            renameCostEnabled = config.getBoolean("rename-cost-enabled", true);
            renameCost = config.getDouble("rename-cost", 500.0);
            deleteRefundEnabled = config.getBoolean("delete-refund-enabled", true);
            deleteRefund = config.getDouble("delete-refund", 10000.0);
            moveCostEnabled = config.getBoolean("move-cost-enabled", true);
            moveCost = config.getDouble("move-cost", 500.0);

        } catch (Exception e) {
            getLogger().warning("❌无法加载配置，可能是不存在或损坏，已重新生成。");
            File configFile = new File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                configFile.delete();
            }
            saveDefaultConfig();
            reloadConfig();
            FileConfiguration config = getConfig();
            if (!config.contains("enable-home")) {
                throw new RuntimeException("重新生成配置后仍无法读取，请检查插件文件权限。");
            }
            // 重新读取配置（直接赋值）
            enableHome = config.getBoolean("enable-home", true);
            enableLobby = config.getBoolean("enable-lobby", true);
            enablePoint = config.getBoolean("enable-point", true);
            enableList = config.getBoolean("enable-list", true);
            enablePointbuy = config.getBoolean("enable-pointbuy", true);
            enablePr = config.getBoolean("enable-pr", true);
            enableHelp = config.getBoolean("enable-help", true);
            enableSuicide = config.getBoolean("enable-suicide", true);
            enableReload = config.getBoolean("enable-reload", true);
            enableSet = config.getBoolean("enable-set", true);
            lobbyWorld = config.getString("lobby-world", "world");
            lobbyX = config.getDouble("lobby-x", 0.0);
            lobbyY = config.getDouble("lobby-y", 64.0);
            lobbyZ = config.getDouble("lobby-z", 0.0);
            lobbyYaw = config.getDouble("lobby-yaw", 0.0);
            lobbyPitch = config.getDouble("lobby-pitch", 0.0);
            pointbuyCost = config.getDouble("pointbuy-cost", 12000.0);
            initialPoints = config.getInt("initial-points", 4);
            if (initialPoints < 0) initialPoints = 0;
            renameCostEnabled = config.getBoolean("rename-cost-enabled", true);
            renameCost = config.getDouble("rename-cost", 500.0);
            deleteRefundEnabled = config.getBoolean("delete-refund-enabled", true);
            deleteRefund = config.getDouble("delete-refund", 10000.0);
            moveCostEnabled = config.getBoolean("move-cost-enabled", true);
            moveCost = config.getDouble("move-cost", 500.0);
        }
    }

    private void reloadPluginConfig() {
        loadConfig();
        playerDataCache.clear();
        getLogger().info("配置已重载。");
    }

    // Vault 经济初始化
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    // 玩家数据管理
    private PlayerData getPlayerData(UUID uuid) {
        PlayerData data = playerDataCache.get(uuid);
        if (data == null) {
            data = loadPlayerData(uuid);
            playerDataCache.put(uuid, data);
        }
        return data;
    }

    private PlayerData loadPlayerData(UUID uuid) {
        File file = new File(playerDataFolder, uuid.toString() + ".yml");
        PlayerData data = new PlayerData(initialPoints, new ArrayList<>());

        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            data.capacity = yaml.getInt("capacity", initialPoints);
            List<Map<?, ?>> rawList = yaml.getMapList("waypoints");
            for (Map<?, ?> map : rawList) {
                String name = (String) map.get("name");
                String world = (String) map.get("world");
                double x = (double) map.get("x");
                double y = (double) map.get("y");
                double z = (double) map.get("z");
                if (name != null && world != null) {
                    data.waypoints.add(new Waypoint(name, world, x, y, z));
                }
            }
        } else {
            savePlayerData(uuid, data);
        }
        return data;
    }

    private void savePlayerData(UUID uuid) {
        PlayerData data = playerDataCache.get(uuid);
        if (data != null) {
            savePlayerData(uuid, data);
        }
    }

    private void savePlayerData(UUID uuid, PlayerData data) {
        File file = new File(playerDataFolder, uuid.toString() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("capacity", data.capacity);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Waypoint wp : data.waypoints) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", wp.name);
            map.put("world", wp.world);
            map.put("x", wp.x);
            map.put("y", wp.y);
            map.put("z", wp.z);
            list.add(map);
        }
        yaml.set("waypoints", list);
        try {
            yaml.save(file);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "无法保存玩家数据: " + uuid, e);
        }
    }

    // 辅助方法
    private boolean checkEnabled(CommandSender sender, boolean enabled, String feature) {
        if (!enabled) {
            sender.sendMessage(PREFIX + "功能 §c" + feature + "§r 未启用。");
            return false;
        }
        return true;
    }

    private boolean hasAvailableSlot(PlayerData data) {
        return data.waypoints.size() < data.capacity;
    }

    private int getRemaining(PlayerData data) {
        return data.capacity - data.waypoints.size();
    }

    // 查找传送点（不区分大小写）
    private Waypoint findWaypoint(PlayerData data, String name) {
        for (Waypoint wp : data.waypoints) {
            if (wp.name.equalsIgnoreCase(name)) {
                return wp;
            }
        }
        return null;
    }

    // 解析相对坐标（支持 ~ ~ ~ 语法）
    private double[] parseRelativeCoords(Player player, String xStr, String yStr, String zStr) {
        Location loc = player.getLocation();
        double x, y, z;
        x = parseCoord(xStr, loc.getX());
        y = parseCoord(yStr, loc.getY());
        z = parseCoord(zStr, loc.getZ());
        return new double[]{x, y, z};
    }

    private double parseCoord(String str, double current) {
        if (str.startsWith("~")) {
            String num = str.substring(1);
            if (num.isEmpty()) {
                return current;
            }
            try {
                return current + Double.parseDouble(num);
            } catch (NumberFormatException e) {
                return current;
            }
        }
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return current;
        }
    }

    // 格式化坐标（保留两位小数）
    private String formatCoord(double value) {
        return String.format("%.2f", value);
    }

    // 获取方块中心位置（整数坐标 + 0.5）
    private Location getBlockCenter(Location loc) {
        return new Location(
                loc.getWorld(),
                loc.getBlockX() + 0.5,
                loc.getY(),
                loc.getBlockZ() + 0.5,
                loc.getYaw(),
                loc.getPitch()
        );
    }

    // ------------------- 命令处理 -------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("suicide")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
                return true;
            }
            if (!checkEnabled(sender, enableSuicide, "自杀")) return true;
            performSuicide((Player) sender);
            return true;
        }

        if (command.getName().equalsIgnoreCase("tpm")) {
            if (args.length == 0) {
                return handleHelp(sender);
            }

            String sub = args[0].toLowerCase();

            switch (sub) {
                case "home":
                case "h":
                    return handleHome(sender);
                case "lobby":
                case "l":
                    return handleLobby(sender);
                case "pointadd":
                case "pa":
                    return handlePointAdd(sender, args);
                case "point":
                case "p":
                    return handlePoint(sender, args);
                case "list":
                    return handleList(sender);
                case "pointbuy":
                case "pb":
                    return handlePointBuy(sender, args);
                case "pr":
                    return handlePr(sender);
                case "help":
                    return handleHelp(sender);
                case "suicide":
                case "kill":
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
                        return true;
                    }
                    if (!checkEnabled(sender, enableSuicide, "自杀")) return true;
                    performSuicide((Player) sender);
                    return true;
                case "version":
                case "v":
                    return handleVersion(sender);
                case "set":
                    return handleSet(sender, args);
                case "reload":
                    return handleReload(sender);
                default:
                    sender.sendMessage(PREFIX + "未知子命令，输入 §6/tpm help§r 查看帮助。");
                    return true;
            }
        }
        return false;
    }

    private boolean handleHome(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
            return true;
        }
        if (!checkEnabled(sender, enableHome, "家")) return true;
        Player player = (Player) sender;
        Location bed = player.getBedSpawnLocation();
        if (bed == null) {
            bed = player.getWorld().getSpawnLocation();
        }
        // 传送到方块中心
        player.teleport(getBlockCenter(bed));
        player.sendMessage(PREFIX + "已传送到重生点。");
        return true;
    }

    private boolean handleLobby(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
            return true;
        }
        if (!checkEnabled(sender, enableLobby, "主城")) return true;
        Player player = (Player) sender;
        World world = Bukkit.getWorld(lobbyWorld);
        if (world == null) {
            player.sendMessage(PREFIX + "主城世界 §c" + lobbyWorld + "§r 不存在。");
            return true;
        }
        Location loc = new Location(world, lobbyX, lobbyY, lobbyZ, (float) lobbyYaw, (float) lobbyPitch);
        // 传送到方块中心
        player.teleport(getBlockCenter(loc));
        player.sendMessage(PREFIX + "已传送到主城。");
        return true;
    }

    private boolean handlePointAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
            return true;
        }
        if (!checkEnabled(sender, enablePoint, "添加传送点")) return true;
        if (args.length < 5) {
            sender.sendMessage(PREFIX + "用法: §6/tpm pointadd <名称> <X> <Y> <Z>§r 或 §6/tpm pa <名称> ~ ~ ~");
            return true;
        }

        Player player = (Player) sender;
        String name = args[1];
        double x, y, z;

        // 检测是否使用相对坐标
        if (args[2].startsWith("~") || args[3].startsWith("~") || args[4].startsWith("~")) {
            double[] coords = parseRelativeCoords(player, args[2], args[3], args[4]);
            x = coords[0];
            y = coords[1];
            z = coords[2];
        } else {
            try {
                x = Double.parseDouble(args[2]);
                y = Double.parseDouble(args[3]);
                z = Double.parseDouble(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "坐标必须为数字。");
                return true;
            }
        }

        UUID uuid = player.getUniqueId();
        PlayerData data = getPlayerData(uuid);

        if (findWaypoint(data, name) != null) {
            sender.sendMessage(PREFIX + "已存在同名传送点 §c" + name + "§r。");
            return true;
        }

        if (!hasAvailableSlot(data)) {
            sender.sendMessage(PREFIX + "§c传送点额度已满！§r 剩余额度: " + getRemaining(data));
            return true;
        }

        String worldName = player.getWorld().getName();
        data.waypoints.add(new Waypoint(name, worldName, x, y, z));
        savePlayerData(uuid, data);
        sender.sendMessage(PREFIX + "传送点 §a" + name + "§r 已添加 (世界: " + worldName + ", " +
                formatCoord(x) + ", " + formatCoord(y) + ", " + formatCoord(z) + ")");
        sender.sendMessage(PREFIX + "剩余传送点额度: " + getRemaining(data));
        return true;
    }

    private boolean handlePoint(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
            return true;
        }
        if (!checkEnabled(sender, enablePoint, "传送点传送")) return true;
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "用法: §6/tpm point <名称>§r 或 §6/tpm p <名称>");
            return true;
        }

        Player player = (Player) sender;
        String name = args[1];
        PlayerData data = getPlayerData(player.getUniqueId());
        Waypoint wp = findWaypoint(data, name);

        if (wp == null) {
            player.sendMessage(PREFIX + "未找到传送点 §c" + name + "§r。");
            return true;
        }

        World world = Bukkit.getWorld(wp.world);
        if (world == null) {
            player.sendMessage(PREFIX + "传送点所在世界 §c" + wp.world + "§r 不存在。");
            return true;
        }
        Location loc = new Location(world, wp.x, wp.y, wp.z);
        // 传送到方块中心
        player.teleport(getBlockCenter(loc));
        player.sendMessage(PREFIX + "已传送到传送点 §a" + name + "§r。");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
            return true;
        }
        if (!checkEnabled(sender, enableList, "传送点列表")) return true;

        Player player = (Player) sender;
        PlayerData data = getPlayerData(player.getUniqueId());
        if (data.waypoints.isEmpty()) {
            player.sendMessage(PREFIX + "你还没有任何传送点。");
            return true;
        }
        player.sendMessage(PREFIX + "你的传送点列表:");
        for (Waypoint wp : data.waypoints) {
            player.sendMessage("  §a" + wp.name + "§r - 世界: " + wp.world +
                    " 坐标: " + formatCoord(wp.x) + ", " + formatCoord(wp.y) + ", " + formatCoord(wp.z));
        }
        player.sendMessage(PREFIX + "当前额度: " + data.capacity + "，已用: " + data.waypoints.size() +
                "，剩余: " + getRemaining(data));
        return true;
    }

    private boolean handlePointBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
            return true;
        }
        if (!checkEnabled(sender, enablePointbuy, "购买额度")) return true;
        if (economy == null) {
            sender.sendMessage(PREFIX + "§c经济插件未启用，无法购买额度。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "用法: §6/tpm pointbuy <数量>§r 或 §6/tpm pb <数量>");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + "数量必须为整数。");
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(PREFIX + "数量必须大于 0。");
            return true;
        }

        Player player = (Player) sender;
        double cost = amount * pointbuyCost;
        if (!economy.has(player, cost)) {
            player.sendMessage(PREFIX + "§c余额不足！§r 需要 §6" + cost + "§r 金币，当前余额: " +
                    economy.getBalance(player));
            return true;
        }

        economy.withdrawPlayer(player, cost);
        PlayerData data = getPlayerData(player.getUniqueId());
        data.capacity += amount;
        savePlayerData(player.getUniqueId(), data);
        player.sendMessage(PREFIX + "§a购买成功！§r 增加了 " + amount + " 个传送点额度，花费 " + cost + " 金币。");
        player.sendMessage(PREFIX + "当前总额度: " + data.capacity + "，剩余额度: " + getRemaining(data));
        return true;
    }

    private boolean handlePr(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
            return true;
        }
        if (!checkEnabled(sender, enablePr, "查看额度")) return true;

        Player player = (Player) sender;
        PlayerData data = getPlayerData(player.getUniqueId());
        int remaining = getRemaining(data);
        player.sendMessage(PREFIX + "剩余传送点额度: §6" + remaining + "§r (总额度: " + data.capacity +
                "，已用: " + data.waypoints.size() + ")");
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        if (!checkEnabled(sender, enableHelp, "帮助")) return true;

        List<String> help = Arrays.asList(
                "§6===== TPM 帮助 =====",
                "/tpm home / h - 传送到重生点",
                "/tpm lobby / l - 传送到主城",
                "/tpm pointadd / pa <名称> <X> <Y> <Z> - 添加传送点（支持 ~ ~ ~）",
                "/tpm point / p <名称> - 传送到指定传送点",
                "/tpm list - 列出所有传送点",
                "/tpm pointbuy / pb <数量> - 购买传送点额度",
                "/tpm pr - 查看剩余额度",
                "/tpm set <传送点> rename <新名称> - 重命名传送点",
                "/tpm set <传送点> delete - 删除传送点",
                "/tpm set <传送点> move <X> <Y> <Z> - 移动传送点（支持 ~ ~ ~）",
                "/tpm version / v - 查看版本和下载链接",
                "/tpm help - 显示此帮助",
                "/tpm suicide / kill - 自杀",
                "/tpm reload - 重载插件 (需要OP权限)"
        );
        for (String line : help) {
            sender.sendMessage(PREFIX + line);
        }
        return true;
    }

    private boolean handleVersion(CommandSender sender) {
        String version = getVersion();
        sender.sendMessage(PREFIX + "§6TPM 版本: §a" + version);
        sender.sendMessage(PREFIX + "§6Github 仓库（下载最新版）: §bhttps://github.com/RoosterGroup/Teleport-Manager/releases");
        sender.sendMessage(PREFIX + "§7点击链接可直接跳转（部分客户端支持）");
        return true;
    }

    // ------------------- /tpm set 命令 -------------------
    private boolean handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
            return true;
        }
        if (!checkEnabled(sender, enableSet, "传送点管理")) return true;
        if (args.length < 3) {
            sender.sendMessage(PREFIX + "用法: §6/tpm set <传送点> <操作> [参数]");
            sender.sendMessage(PREFIX + "操作: rename <新名称>, delete, move <X> <Y> <Z>");
            return true;
        }

        Player player = (Player) sender;
        String wpName = args[1];
        String operation = args[2].toLowerCase();
        PlayerData data = getPlayerData(player.getUniqueId());
        Waypoint wp = findWaypoint(data, wpName);

        if (wp == null) {
            player.sendMessage(PREFIX + "未找到传送点 §c" + wpName + "§r。");
            return true;
        }

        switch (operation) {
            case "rename":
                return handleRename(player, data, wp, args);
            case "delete":
                return handleDelete(player, data, wp);
            case "move":
                return handleMove(player, data, wp, args);
            default:
                player.sendMessage(PREFIX + "未知操作: §c" + operation + "§r。可用: rename, delete, move");
                return true;
        }
    }

    // 重命名
    private boolean handleRename(Player player, PlayerData data, Waypoint wp, String[] args) {
        if (args.length < 4) {
            player.sendMessage(PREFIX + "用法: §6/tpm set <传送点> rename <新名称>");
            return true;
        }
        String newName = args[3];

        // 检查新名称是否已被占用
        if (findWaypoint(data, newName) != null) {
            player.sendMessage(PREFIX + "已存在同名传送点 §c" + newName + "§r。");
            return true;
        }

        // 检查是否收费
        if (renameCostEnabled && economy != null) {
            if (!economy.has(player, renameCost)) {
                player.sendMessage(PREFIX + "§c余额不足！§r 重命名需要 §6" + renameCost + "§r 金币。");
                return true;
            }
            economy.withdrawPlayer(player, renameCost);
            player.sendMessage(PREFIX + "已扣除 §6" + renameCost + "§r 金币。");
        }

        String oldName = wp.name;
        wp.name = newName;
        savePlayerData(player.getUniqueId(), data);
        player.sendMessage(PREFIX + "传送点 §a" + oldName + "§r 已重命名为 §a" + newName + "§r。");
        return true;
    }

    // 删除
    private boolean handleDelete(Player player, PlayerData data, Waypoint wp) {
        String name = wp.name;

        // 检查是否返还
        if (deleteRefundEnabled && economy != null) {
            economy.depositPlayer(player, deleteRefund);
            player.sendMessage(PREFIX + "已返还 §6" + deleteRefund + "§r 金币。");
        }

        data.waypoints.remove(wp);
        savePlayerData(player.getUniqueId(), data);
        player.sendMessage(PREFIX + "传送点 §c" + name + "§r 已删除。");
        player.sendMessage(PREFIX + "剩余传送点额度: " + getRemaining(data));
        return true;
    }

    // 移动
    private boolean handleMove(Player player, PlayerData data, Waypoint wp, String[] args) {
        if (args.length < 6) {
            player.sendMessage(PREFIX + "用法: §6/tpm set <传送点> move <X> <Y> <Z>");
            player.sendMessage(PREFIX + "支持相对坐标: §6/tpm set <传送点> move ~ ~ ~");
            return true;
        }

        double x, y, z;
        if (args[3].startsWith("~") || args[4].startsWith("~") || args[5].startsWith("~")) {
            double[] coords = parseRelativeCoords(player, args[3], args[4], args[5]);
            x = coords[0];
            y = coords[1];
            z = coords[2];
        } else {
            try {
                x = Double.parseDouble(args[3]);
                y = Double.parseDouble(args[4]);
                z = Double.parseDouble(args[5]);
            } catch (NumberFormatException e) {
                player.sendMessage(PREFIX + "坐标必须为数字。");
                return true;
            }
        }

        // 检查是否收费
        if (moveCostEnabled && economy != null) {
            if (!economy.has(player, moveCost)) {
                player.sendMessage(PREFIX + "§c余额不足！§r 移动传送点需要 §6" + moveCost + "§r 金币。");
                return true;
            }
            economy.withdrawPlayer(player, moveCost);
            player.sendMessage(PREFIX + "已扣除 §6" + moveCost + "§r 金币。");
        }

        String oldWorld = wp.world;
        String newWorld = player.getWorld().getName();
        wp.world = newWorld;
        wp.x = x;
        wp.y = y;
        wp.z = z;
        savePlayerData(player.getUniqueId(), data);
        player.sendMessage(PREFIX + "传送点 §a" + wp.name + "§r 已移动到 (世界: " + newWorld +
                ", " + formatCoord(x) + ", " + formatCoord(y) + ", " + formatCoord(z) + ")");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!checkEnabled(sender, enableReload, "重载")) return true;
        if (!sender.isOp()) {
            sender.sendMessage(PREFIX + "§c你没有权限执行此命令！");
            return true;
        }
        reloadPluginConfig();
        sender.sendMessage(PREFIX + "配置已重载。");
        return true;
    }

    private void performSuicide(Player player) {
        Location loc = player.getLocation();
        player.setHealth(0.0);
        Bukkit.broadcastMessage(PREFIX + "§c" + player.getName() + "§r 自杀了，坐标: " +
                loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
    }

    // ------------------- Tab 补全 -------------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("tpm")) {
            if (args.length == 1) {
                List<String> subs = Arrays.asList("home", "h", "lobby", "l", "pointadd", "pa",
                        "point", "p", "list", "pointbuy", "pb", "pr", "help", "suicide", "kill",
                        "version", "v", "set", "reload");
                return filter(subs, args[0]);
            } else if (args.length >= 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("point") || sub.equals("p")) {
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        PlayerData data = getPlayerData(player.getUniqueId());
                        List<String> names = new ArrayList<>();
                        for (Waypoint wp : data.waypoints) {
                            names.add(wp.name);
                        }
                        return filter(names, args[1]);
                    }
                } else if (sub.equals("set")) {
                    if (args.length == 2) {
                        // 补全传送点名称
                        if (sender instanceof Player) {
                            Player player = (Player) sender;
                            PlayerData data = getPlayerData(player.getUniqueId());
                            List<String> names = new ArrayList<>();
                            for (Waypoint wp : data.waypoints) {
                                names.add(wp.name);
                            }
                            return filter(names, args[1]);
                        }
                    } else if (args.length == 3) {
                        // 补全操作
                        List<String> ops = Arrays.asList("rename", "delete", "move");
                        return filter(ops, args[2]);
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(s);
            }
        }
        return result;
    }

    // ------------------- 内部数据类 -------------------
    private static class PlayerData {
        int capacity;
        List<Waypoint> waypoints;

        PlayerData(int capacity, List<Waypoint> waypoints) {
            this.capacity = capacity;
            this.waypoints = waypoints;
        }
    }

    private static class Waypoint {
        String name;
        String world;
        double x, y, z;

        Waypoint(String name, String world, double x, double y, double z) {
            this.name = name;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static TPM getInstance() {
        return instance;
    }
}