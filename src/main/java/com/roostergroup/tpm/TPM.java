package com.roostergroup.tpm;

import net.milkbowl.vault.economy.Economy;
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
            enablePointbuy, enablePr, enableHelp, enableSuicide, enableReload;
    private String lobbyWorld;
    private double lobbyX, lobbyY, lobbyZ, lobbyYaw, lobbyPitch;
    private double pointbuyCost;
    private int initialPoints;

    // 消息前缀
    private static final String PREFIX = ChatColor.GOLD + "[TPM]" + ChatColor.RESET + " ";

    @Override
    public void onEnable() {
        instance = this;
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

        getLogger().info("TPM 插件已启用！");
    }

    @Override
    public void onDisable() {
        // 保存所有在线玩家的数据
        for (UUID uuid : playerDataCache.keySet()) {
            savePlayerData(uuid);
        }
        playerDataCache.clear();
        getLogger().info("TPM 插件已禁用。");
    }

    // 加载配置
    private void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        enableHome = config.getBoolean("enable-home", true);
        enableLobby = config.getBoolean("enable-lobby", true);
        enablePoint = config.getBoolean("enable-point", true);
        enableList = config.getBoolean("enable-list", true);
        enablePointbuy = config.getBoolean("enable-pointbuy", true);
        enablePr = config.getBoolean("enable-pr", true);
        enableHelp = config.getBoolean("enable-help", true);
        enableSuicide = config.getBoolean("enable-suicide", true);
        enableReload = config.getBoolean("enable-reload", true);

        lobbyWorld = config.getString("lobby-world", "world");
        lobbyX = config.getDouble("lobby-x", 0.0);
        lobbyY = config.getDouble("lobby-y", 64.0);
        lobbyZ = config.getDouble("lobby-z", 0.0);
        lobbyYaw = config.getDouble("lobby-yaw", 0.0);
        lobbyPitch = config.getDouble("lobby-pitch", 0.0);

        pointbuyCost = config.getDouble("pointbuy-cost", 12000.0);
        initialPoints = config.getInt("initial-points", 4);

        // 确保初始额度不小于 0
        if (initialPoints < 0) initialPoints = 0;
    }

    // 重载配置（供 reload 命令调用）
    private void reloadPluginConfig() {
        loadConfig();
        // 清除缓存中的数据，下次使用时重新加载（保留玩家数据文件不变）
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

    // 获取玩家数据（若缓存没有则加载）
    private PlayerData getPlayerData(UUID uuid) {
        PlayerData data = playerDataCache.get(uuid);
        if (data == null) {
            data = loadPlayerData(uuid);
            playerDataCache.put(uuid, data);
        }
        return data;
    }

    // 从文件加载玩家数据，若不存在则创建默认
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
            // 新建默认数据并保存
            data.capacity = initialPoints;
            data.waypoints = new ArrayList<>();
            savePlayerData(uuid, data);
        }
        return data;
    }

    // 保存玩家数据（使用缓存中的最新数据）
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

    // 检查功能是否开启，若关闭则发送提示并返回 false
    private boolean checkEnabled(CommandSender sender, boolean enabled, String feature) {
        if (!enabled) {
            sender.sendMessage(PREFIX + "功能 §c" + feature + "§r 未启用。");
            return false;
        }
        return true;
    }

    // 检查玩家是否有足够额度添加传送点
    private boolean hasAvailableSlot(PlayerData data) {
        return data.waypoints.size() < data.capacity;
    }

    // 获取剩余额度
    private int getRemaining(PlayerData data) {
        return data.capacity - data.waypoints.size();
    }

    // ------ 命令处理 ------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("suicide")) {
            // /suicide 自杀
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
                // 默认显示帮助
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
                    // /tpm suicide 或 /tpm kill
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
                        return true;
                    }
                    if (!checkEnabled(sender, enableSuicide, "自杀")) return true;
                    performSuicide((Player) sender);
                    return true;
                case "reload":
                    return handleReload(sender);
                default:
                    sender.sendMessage(PREFIX + "未知子命令，输入 §6/tpm help§r 查看帮助。");
                    return true;
            }
        }
        return false;
    }

    // 处理 /tpm home
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
        player.teleport(bed);
        player.sendMessage(PREFIX + "已传送到重生点。");
        return true;
    }

    // 处理 /tpm lobby
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
        player.teleport(loc);
        player.sendMessage(PREFIX + "已传送到主城。");
        return true;
    }

    // 处理 /tpm pointadd <name> <x> <y> <z>
    private boolean handlePointAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
            return true;
        }
        if (!checkEnabled(sender, enablePoint, "添加传送点")) return true;
        if (args.length < 5) {
            sender.sendMessage(PREFIX + "用法: §6/tpm pointadd <名称> <X> <Y> <Z>§r 或 §6/tpm pa ...");
            return true;
        }

        Player player = (Player) sender;
        String name = args[1];
        double x, y, z;
        try {
            x = Double.parseDouble(args[2]);
            y = Double.parseDouble(args[3]);
            z = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + "坐标必须为数字。");
            return true;
        }

        UUID uuid = player.getUniqueId();
        PlayerData data = getPlayerData(uuid);

        // 检查名称是否重复
        for (Waypoint wp : data.waypoints) {
            if (wp.name.equalsIgnoreCase(name)) {
                sender.sendMessage(PREFIX + "已存在同名传送点 §c" + name + "§r。");
                return true;
            }
        }

        // 检查额度
        if (!hasAvailableSlot(data)) {
            sender.sendMessage(PREFIX + "§c传送点额度已满！§r 剩余额度: " + getRemaining(data));
            return true;
        }

        // 添加传送点
        String worldName = player.getWorld().getName();
        data.waypoints.add(new Waypoint(name, worldName, x, y, z));
        savePlayerData(uuid, data);
        sender.sendMessage(PREFIX + "传送点 §a" + name + "§r 已添加 (世界: " + worldName + ", " + x + ", " + y + ", " + z + ")");
        sender.sendMessage(PREFIX + "剩余传送点额度: " + getRemaining(data));
        return true;
    }

    // 处理 /tpm point <name>
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

        for (Waypoint wp : data.waypoints) {
            if (wp.name.equalsIgnoreCase(name)) {
                World world = Bukkit.getWorld(wp.world);
                if (world == null) {
                    player.sendMessage(PREFIX + "传送点所在世界 §c" + wp.world + "§r 不存在。");
                    return true;
                }
                Location loc = new Location(world, wp.x, wp.y, wp.z);
                player.teleport(loc);
                player.sendMessage(PREFIX + "已传送到传送点 §a" + name + "§r。");
                return true;
            }
        }
        player.sendMessage(PREFIX + "未找到传送点 §c" + name + "§r。");
        return true;
    }

    // 处理 /tpm list
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
            player.sendMessage("  §a" + wp.name + "§r - 世界: " + wp.world + " 坐标: " + wp.x + ", " + wp.y + ", " + wp.z);
        }
        player.sendMessage(PREFIX + "当前额度: " + data.capacity + "，已用: " + data.waypoints.size() + "，剩余: " + getRemaining(data));
        return true;
    }

    // 处理 /tpm pointbuy <数量>
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
            player.sendMessage(PREFIX + "§c余额不足！§r 需要 §6" + cost + "§r 金币，当前余额: " + economy.getBalance(player));
            return true;
        }

        // 扣钱
        economy.withdrawPlayer(player, cost);
        // 增加额度
        PlayerData data = getPlayerData(player.getUniqueId());
        data.capacity += amount;
        savePlayerData(player.getUniqueId(), data);
        player.sendMessage(PREFIX + "§a购买成功！§r 增加了 " + amount + " 个传送点额度，花费 " + cost + " 金币。");
        player.sendMessage(PREFIX + "当前总额度: " + data.capacity + "，剩余额度: " + getRemaining(data));
        return true;
    }

    // 处理 /tpm pr
    private boolean handlePr(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令只能由玩家执行。");
            return true;
        }
        if (!checkEnabled(sender, enablePr, "查看额度")) return true;

        Player player = (Player) sender;
        PlayerData data = getPlayerData(player.getUniqueId());
        int remaining = getRemaining(data);
        player.sendMessage(PREFIX + "剩余传送点额度: §6" + remaining + "§r (总额度: " + data.capacity + "，已用: " + data.waypoints.size() + ")");
        return true;
    }

    // 处理 /tpm help
    private boolean handleHelp(CommandSender sender) {
        if (!checkEnabled(sender, enableHelp, "帮助")) return true;

        List<String> help = Arrays.asList(
                "§6===== TPM 帮助 =====",
                "/tpm home / h - 传送到重生点",
                "/tpm lobby / l - 传送到主城",
                "/tpm pointadd / pa <名称> <X> <Y> <Z> - 添加传送点",
                "/tpm point / p <名称> - 传送到指定传送点",
                "/tpm list - 列出所有传送点",
                "/tpm pointbuy / pb <数量> - 购买传送点额度",
                "/tpm pr - 查看剩余额度",
                "/tpm help - 显示此帮助",
                "/tpm suicide / kill - 自杀",
                "/tpm reload - 重载插件 (需要OP权限)"
        );
        for (String line : help) {
            sender.sendMessage(PREFIX + line);
        }
        return true;
    }

    // 处理 /tpm reload
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

    // 自杀逻辑
    private void performSuicide(Player player) {
        Location loc = player.getLocation();
        player.setHealth(0.0);
        // 显示死亡信息（原版会显示，但我们额外发送坐标）
        Bukkit.broadcastMessage(PREFIX + "§c" + player.getName() + "§r 自杀了，坐标: " +
                loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
    }

    // ------ Tab 补全 ------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("tpm")) {
            if (args.length == 1) {
                // 提供子命令列表
                List<String> subs = Arrays.asList("home", "h", "lobby", "l", "pointadd", "pa",
                        "point", "p", "list", "pointbuy", "pb", "pr", "help", "suicide", "kill", "reload");
                return filter(subs, args[0]);
            } else if (args.length >= 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("point") || sub.equals("p")) {
                    // 提供玩家已有的传送点名称
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        PlayerData data = getPlayerData(player.getUniqueId());
                        List<String> names = new ArrayList<>();
                        for (Waypoint wp : data.waypoints) {
                            names.add(wp.name);
                        }
                        return filter(names, args[1]);
                    }
                } else if (sub.equals("pointadd") || sub.equals("pa")) {
                    // 参数较多，不补全，但可补全世界？忽略。
                } else if (sub.equals("pointbuy") || sub.equals("pb")) {
                    // 数量不补全
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

    // ------ 内部数据类 ------
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

    // 提供静态方法供其他类使用（如有需要）
    public static TPM getInstance() {
        return instance;
    }
}