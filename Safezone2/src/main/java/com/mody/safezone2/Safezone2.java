package com.mody.safezone2;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Team;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.Collections;
import java.util.List;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import org.bukkit.event.player.PlayerQuitEvent;

public class Safezone2 extends JavaPlugin implements Listener {

    private final Map<String, Zone> zones = new HashMap<>();
    private final Map<UUID, Boolean> playerInZone = new HashMap<>();
    private final Map<String, Zone> guildZones = new HashMap<>();
    private static final Set<String> ALLOWED_ZONE_TYPES = new HashSet<>(Arrays.asList("miasto", "kopalnia", "guild"));
    private final Map<UUID, Role> playerRoles = new HashMap<>();
    private final Map<UUID, String> guildRoles = new HashMap<>();
    private final Map<UUID, String> lastZoneNames = new HashMap<>();
    private final Map<String, Zone> mineZones = new HashMap<>();
    private final Map<UUID, Integer> wantedStars = new HashMap<>();
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        loadWantedData();
        loadRoles();
        loadZones();
        loadMineBlocks();

        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerNameTag(player);
        }
        // Komenda do tworzenia stref
        getCommand("setzone").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("safezone.set")) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do ustawiania stref.");
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Ta komenda musi być wykonywana przez gracza.");
                return true;
            }
            if (args.length != 8) {
                sender.sendMessage(ChatColor.RED + "Użycie: /setzone <typ> <nazwa> <x1> <y1> <z1> <x2> <y2> <z2>");
                return true;
            }

            String type = args[0];
            String name = args[1];

            if (!ALLOWED_ZONE_TYPES.contains(type.toLowerCase())) {
                sender.sendMessage(ChatColor.RED + "Niepoprawny typ strefy! Dostępne typy: " + ALLOWED_ZONE_TYPES);
                return true;
            }

            try {
                double x1 = Double.parseDouble(args[2]);
                double y1 = Double.parseDouble(args[3]);
                double z1 = Double.parseDouble(args[4]);
                double x2 = Double.parseDouble(args[5]);
                double y2 = Double.parseDouble(args[6]);
                double z2 = Double.parseDouble(args[7]);

                Player player = (Player) sender;
                World world = player.getWorld();
                Location corner1 = new Location(world, x1, y1, z1);
                Location corner2 = new Location(world, x2, y2, z2);

                Zone zone = new Zone(name, type, corner1, corner2);
                zones.put(name, zone);
                saveZones();

                sender.sendMessage(ChatColor.GREEN + "Strefa typu " + type + " została ustawiona!");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Wprowadź poprawne współrzędne liczbowe!");
            }
            return true;
        });

        // Komenda do tworzenia stref gildii
        getCommand("setzoneguild").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("safezone.set")) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do ustawiania strefy.");
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Ta komenda musi być wykonywana przez gracza.");
                return true;
            }
            if (args.length != 7) {
                sender.sendMessage(ChatColor.RED + "Użycie: /setzoneguild <nazwa_gildi> <x1> <y1> <z1> <x2> <y2> <z2>");
                return true;
            }

            String guildName = args[0];

            try {
                double x1 = Double.parseDouble(args[1]);
                double y1 = Double.parseDouble(args[2]);
                double z1 = Double.parseDouble(args[3]);
                double x2 = Double.parseDouble(args[4]);
                double y2 = Double.parseDouble(args[5]);
                double z2 = Double.parseDouble(args[6]);

                Player player = (Player) sender;
                World world = player.getWorld();
                Location corner1 = new Location(world, x1, y1, z1);
                Location corner2 = new Location(world, x2, y2, z2);

                Zone zone = new Zone(guildName, "guild", corner1, corner2);
                guildZones.put(guildName, zone);
                saveZones();

                sender.sendMessage(ChatColor.GREEN + "Strefa gildii " + guildName + " została ustawiona!");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Wprowadź poprawne współrzędne liczbowe!");
            }
            return true;
        });

        // Komenda do przypisywania ról
        getCommand("setrole").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("safezone.setrole")) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do przypisywania ról.");
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Użycie: /setrole <nick> <rola>");
                return true;
            }

            String playerName = args[0];
            String roleName = args[1];

            Role role = Role.getRoleByName(roleName);
            if (role == null) {
                sender.sendMessage(ChatColor.RED + "Niepoprawna rola! Dostępne role: " + Role.getAvailableRoles());
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(playerName);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Gracz o podanym nicku nie jest online!");
                return true;
            }

            if (playerRoles.containsKey(targetPlayer.getUniqueId()) && playerRoles.get(targetPlayer.getUniqueId()) != null) {
                if (playerRoles.get(targetPlayer.getUniqueId()) == Role.BRAK) {
                    playerRoles.put(targetPlayer.getUniqueId(), role);
                    saveRole(targetPlayer.getUniqueId(), role.getRoleName());
                    updateScoreboard(targetPlayer);
                    sender.sendMessage(ChatColor.GREEN + "Gracz " + playerName + " otrzymał rolę " + roleName);
                } else {
                    sender.sendMessage(ChatColor.RED + "Gracz " + playerName + " już ma przypisaną rolę. Najpierw usuń starą rolę.");
                }
            } else {
                playerRoles.put(targetPlayer.getUniqueId(), role);
                saveRole(targetPlayer.getUniqueId(), role.getRoleName());
                updateScoreboard(targetPlayer);
                updatePlayerNameTag(targetPlayer);
                sender.sendMessage(ChatColor.GREEN + "Gracz " + playerName + " otrzymał rolę " + roleName);
            }
            return true;
        });

        // Komenda do usuwania stref
        getCommand("deletezone").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("safezone.delete")) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do usuwania stref.");
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Użycie: /deletezone <nazwa>");
                return true;
            }

            String zoneName = args[0];
            if (zones.containsKey(zoneName)) {
                zones.remove(zoneName);
                saveZones();
                sender.sendMessage(ChatColor.GREEN + "Strefa " + zoneName + " została usunięta!");
            } else {
                sender.sendMessage(ChatColor.RED + "Nie znaleziono strefy o nazwie " + zoneName);
            }
            return true;
        });

        // Komenda do usuwania ról
        getCommand("deleterole").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("safezone.deleterole")) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do usuwania ról.");
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Użycie: /deleterole <nick> <rola>");
                return true;
            }

            String playerName = args[0];
            String roleName = args[1];

            Role role = Role.getRoleByName(roleName);
            if (role == null) {
                sender.sendMessage(ChatColor.RED + "Niepoprawna rola! Dostępne role: " + Role.getAvailableRoles());
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(playerName);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Gracz o podanym nicku nie jest online!");
                return true;
            }

            if (!playerRoles.containsKey(targetPlayer.getUniqueId())) {
                sender.sendMessage(ChatColor.RED + "Gracz " + playerName + " nie ma przypisanej roli " + roleName);
                return true;
            }

            playerRoles.remove(targetPlayer.getUniqueId());
            saveRole(targetPlayer.getUniqueId(), "BRAK");
            updateScoreboard(targetPlayer);
            sender.sendMessage(ChatColor.GREEN + "Rola " + roleName + " została usunięta z gracza " + playerName);
            return true;
        });

        // Komenda do przypisywania ról gildii
        getCommand("setroleguild").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("safezone.setroleguild")) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do przypisywania ról gildii.");
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Użycie: /setroleguild <nick> <nazwa_gildi>");
                return true;
            }

            String playerName = args[0];
            String guildName = args[1];

            Player targetPlayer = Bukkit.getPlayer(playerName);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Gracz o podanym nicku nie jest online!");
                return true;
            }

            guildRoles.put(targetPlayer.getUniqueId(), guildName);
            saveRole(targetPlayer.getUniqueId(), "guild_" + guildName);
            updateScoreboard(targetPlayer);
            updatePlayerNameTag(targetPlayer);
            sender.sendMessage(ChatColor.GREEN + "Gracz " + playerName + " otrzymał rolę gildii " + guildName);
            return true;
        });

        // Komenda do usuwania ról gildii
        getCommand("deleteroleguild").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("safezone.deleteroleguild")) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do usuwania ról gildii.");
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Użycie: /deleteroleguild <nick> <nazwa_gildi>");
                return true;
            }

            String playerName = args[0];
            String guildName = args[1];

            Player targetPlayer = Bukkit.getPlayer(playerName);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Gracz o podanym nicku nie jest online!");
                return true;
            }

            if (guildRoles.containsKey(targetPlayer.getUniqueId()) && guildRoles.get(targetPlayer.getUniqueId()).equals(guildName)) {
                guildRoles.remove(targetPlayer.getUniqueId());
                saveRole(targetPlayer.getUniqueId(), "BRAK");
                updateScoreboard(targetPlayer);
                sender.sendMessage(ChatColor.GREEN + "Rola gildii " + guildName + " została usunięta z gracza " + playerName);
            } else {
                sender.sendMessage(ChatColor.RED + "Gracz " + playerName + " nie ma przypisanej roli gildii " + guildName);
            }
            return true;
        });

        getCommand("deletezoneguild").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("safezone.deleteguild")) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do usuwania stref gildii.");
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Użycie: /deletezoneguild <nazwa_gildi>");
                return true;
            }

            String guildName = args[0];

            if (guildZones.containsKey(guildName)) {
                guildZones.remove(guildName);
                saveZones();
                sender.sendMessage(ChatColor.GREEN + "Strefa gildii " + guildName + " została usunięta!");
            } else {
                sender.sendMessage(ChatColor.RED + "Nie znaleziono strefy gildii o nazwie " + guildName);
            }
            return true;
        });

        // Komenda do ustawiania bloków w kopalni
        getCommand("setmineblocks").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("safezone.setmineblocks")) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do ustawiania bloków w kopalni.");
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Ta komenda musi być wykonywana przez gracza.");
                return true;
            }
            if (args.length != 7) {
                sender.sendMessage(ChatColor.RED + "Użycie: /setmineblocks <nazwa> <x1> <y1> <z1> <x2> <y2> <z2>");
                return true;
            }

            try {
                String mineName = args[0];
                int x1 = Integer.parseInt(args[1]);
                int y1 = Integer.parseInt(args[2]);
                int z1 = Integer.parseInt(args[3]);
                int x2 = Integer.parseInt(args[4]);
                int y2 = Integer.parseInt(args[5]);
                int z2 = Integer.parseInt(args[6]);

                Player player = (Player) sender;
                World world = player.getWorld();
                Zone mineZone = new Zone(mineName, "kopalnia",
                        new Location(world, x1, y1, z1),
                        new Location(world, x2, y2, z2));

                mineZones.put(mineName, mineZone);
                saveMineBlocks();

                sender.sendMessage(ChatColor.GREEN + "Strefa kopalni '" + mineName + "' została dodana i zapisana!");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Wprowadź poprawne współrzędne liczbowe!");
            }
            return true;
        });

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location blockLocation = event.getBlock().getLocation();
        Role playerRole = playerRoles.get(player.getUniqueId());
        String guildRole = guildRoles.get(player.getUniqueId());

        boolean isInAnyZone = false;

        for (Zone zone : zones.values()) {
            if (zone.isInside(blockLocation)) {
                isInAnyZone = true;
                if (zone.getType().equals("kopalnia")) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Nie możesz budować w strefie kopalni.");
                    return;
                }
                if (playerRole == null || !playerRole.canBuildInZone(zone)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Nie masz uprawnień do budowania w tej strefie.");
                }
                return;
            }
        }

        for (Zone guildZone : guildZones.values()) {
            if (guildZone.isInside(blockLocation)) {
                isInAnyZone = true;
                if (playerRole == Role.KROL) {
                    return;
                }
                if (guildRole != null && guildRole.equals(guildZone.getName())) {
                    return;
                }
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Nie masz uprawnień do budowania w tej strefie.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location blockLocation = event.getBlock().getLocation();
        Role playerRole = playerRoles.get(player.getUniqueId());
        String guildRole = guildRoles.get(player.getUniqueId());

        for (Zone mineZone : mineZones.values()) {
            if (mineZone.isInside(blockLocation)) {
                return;
            }
        }


        for (Zone zone : zones.values()) {
            if (zone.isInside(blockLocation)) {
                if (zone.getType().equals("kopalnia")) {
                    Material originalMaterial = event.getBlock().getType();
                    event.setDropItems(false);
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Nie możesz niszczyć w strefie kopalni.");
                    Bukkit.getScheduler().runTask(this, () -> {
                        event.getBlock().setType(originalMaterial);
                    });
                    return;
                }
                if (playerRole == null || !playerRole.canDestroyInZone(zone)) {
                    Material originalMaterial = event.getBlock().getType();
                    event.setDropItems(false);
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Nie masz uprawnień do niszczenia w tej strefie.");
                    Bukkit.getScheduler().runTask(this, () -> {
                        event.getBlock().setType(originalMaterial);
                    });
                    return;
                }
            }
        }


        for (Zone guildZone : guildZones.values()) {
            if (guildZone.isInside(blockLocation)) {
                if (playerRole == Role.KROL) {
                    return;
                }
                if (guildRole != null && guildRole.equals(guildZone.getName())) {
                    return;
                }
                Material originalMaterial = event.getBlock().getType();
                event.setDropItems(false);
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Nie masz uprawnień do niszczenia w tej strefie.");
                Bukkit.getScheduler().runTask(this, () -> {
                    event.getBlock().setType(originalMaterial);
                });
                return;
            }
        }
    }


    @EventHandler
    public void onPlayerKill(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player killer = (Player) event.getDamager();

        if (victim.getHealth() - event.getFinalDamage() > 0) {
            return;
        }

        int victimStars = wantedStars.getOrDefault(victim.getUniqueId(), 0);
        int killerStars = wantedStars.getOrDefault(killer.getUniqueId(), 0);

        if (victimStars > 0) {
            killer.sendMessage(ChatColor.GOLD + "Zabiłeś poszukiwanego gracza! Otrzymujesz " + victimStars + " sztabek złota!");
            killer.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, victimStars));
        } else if (killerStars < 5) {
            wantedStars.put(killer.getUniqueId(), killerStars + 1);
            killer.sendMessage(ChatColor.RED + "Zyskałeś gwiazdkę! Teraz masz " + (killerStars + 1) + " gwiazdek.");
        }

        wantedStars.put(victim.getUniqueId(), 0);
        updateScoreboard(killer);
        updateScoreboard(victim);
        saveWantedData();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        wantedStars.putIfAbsent(player.getUniqueId(), 0);
        getLogger().info("Gracz " + player.getName() + " dołączył - gwiazdki: " + wantedStars.get(player.getUniqueId()));

        updateScoreboard(player);
        event.setJoinMessage(null);

        loadRoles();
        setupScoreboard(player);
        updatePlayerNameTag(player);

        Role role = playerRoles.get(uuid);
        if (role != null) {
            updatePlayerNameTag(player);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        Role role = playerRoles.get(playerId);
        String rolePrefix;

        if (role != null) {
            if (role == Role.NEUTRALNY) {
                rolePrefix = ChatColor.GREEN + "[NEUTRALNY] ";
            } else if (role == Role.KROL) {
                rolePrefix = ChatColor.GOLD + "[KROL] ";
            } else {
                rolePrefix = ChatColor.GRAY + "[" + role.getRoleName() + "] ";
            }
        } else {
            rolePrefix = ChatColor.GRAY + "[BRAK] ";
        }

        String guildRole = guildRoles.get(playerId);
        if (guildRole != null) {
            rolePrefix = ChatColor.GRAY + "[" + guildRole + "] ";
        }

        String message = event.getMessage();
        event.setFormat(rolePrefix + ChatColor.GRAY + player.getName() + ": " + ChatColor.WHITE + message);
    }

    private void updatePlayerNameTag(Player player) {
        Role role = playerRoles.get(player.getUniqueId());
        String roleName;

        if (role != null) {
            if (role == Role.NEUTRALNY) {
                roleName = ChatColor.GREEN + "[NEUTRALNY] ";
            } else if (role == Role.KROL) {
                roleName = ChatColor.GOLD + "[KROL] ";
            } else {
                roleName = ChatColor.GRAY + "[" + role.getRoleName() + "] ";
            }
        } else {
            roleName = ChatColor.GRAY + "[BRAK] ";
        }

        String guildRole = guildRoles.get(player.getUniqueId());
        if (guildRole != null) {
            roleName = ChatColor.GRAY + "[" + guildRole + "] ";
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(roleName);
        if (team == null) {
            team = scoreboard.registerNewTeam(roleName);
            team.setPrefix(roleName);
        }
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
        player.setDisplayName(roleName + ChatColor.GRAY + player.getName());
        player.setPlayerListName(roleName + ChatColor.GRAY + player.getName());
    }
    private void saveMineBlocks() {
        File file = new File(getDataFolder(), "mineblocks.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, Zone> entry : mineZones.entrySet()) {
            String path = "mineZones." + entry.getKey() + ".";
            config.set(path + "world", entry.getValue().getCorner1().getWorld().getName());
            config.set(path + "x1", entry.getValue().getCorner1().getX());
            config.set(path + "y1", entry.getValue().getCorner1().getY());
            config.set(path + "z1", entry.getValue().getCorner1().getZ());
            config.set(path + "x2", entry.getValue().getCorner2().getX());
            config.set(path + "y2", entry.getValue().getCorner2().getY());
            config.set(path + "z2", entry.getValue().getCorner2().getZ());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            getLogger().severe("Błąd zapisu mineblocks.yml: " + e.getMessage());
        }
    }
    private void loadMineBlocks() {
        File file = new File(getDataFolder(), "mineblocks.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        mineZones.clear(); // Czyścimy stare dane, żeby uniknąć duplikatów

        if (config.contains("mineZones")) {
            for (String key : config.getConfigurationSection("mineZones").getKeys(false)) {
                String path = "mineZones." + key + ".";
                Zone zone = new Zone(
                        key,
                        "kopalnia",
                        new Location(
                                Bukkit.getWorld(config.getString(path + "world")),
                                config.getDouble(path + "x1"),
                                config.getDouble(path + "y1"),
                                config.getDouble(path + "z1")
                        ),
                        new Location(
                                Bukkit.getWorld(config.getString(path + "world")),
                                config.getDouble(path + "x2"),
                                config.getDouble(path + "y2"),
                                config.getDouble(path + "z2")
                        )
                );
                mineZones.put(key, zone);
            }
        }

        getLogger().info("Załadowano " + mineZones.size() + " stref kopalni z pliku mineblocks.yml!");
    }

    private void loadRoles() {
        File dataFile = new File(getDataFolder(), "player_roles.yml");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Błąd tworzenia player_roles.yml: " + e.getMessage());
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        for (String key : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String roleName = dataConfig.getString(key);
                Role role = Role.getRoleByName(roleName);

                if (role != null) {
                    playerRoles.put(uuid, role);
                    getLogger().info("Wczytano rolę " + roleName + " dla gracza " + uuid);
                } else {
                    if (roleName.startsWith("guild_")) {
                        String guildName = roleName.substring(6);
                        guildRoles.put(uuid, guildName);
                        getLogger().info("Wczytano rolę gildii " + guildName + " dla gracza " + uuid);
                    } else if (roleName.equals("BRAK")) {
                        playerRoles.put(uuid, Role.BRAK);
                        getLogger().info("Gracz " + uuid + " ma przypisaną rolę BRAK.");
                    } else {
                        playerRoles.put(uuid, null);
                        getLogger().info("Gracz " + uuid + " nie ma przypisanej roli.");
                    }
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Nieprawidłowy UUID w player_roles.yml: " + key);
            }
        }
    }

    private void saveRole(UUID uuid, String roleName) {
        if (roleName == null) {
            dataConfig.set(uuid.toString(), "BRAK");
        } else {
            dataConfig.set(uuid.toString(), roleName);
        }
        try {
            dataConfig.save(new File(getDataFolder(), "player_roles.yml"));
            getLogger().info("Zapisano rolę " + roleName + " dla gracza " + uuid);
        } catch (IOException e) {
            getLogger().severe("Błąd zapisu player_roles.yml: " + e.getMessage());
        }
    }

    private void saveZones() {
        File dataFile = new File(getDataFolder(), "zones.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, Zone> entry : zones.entrySet()) {
            String path = "zones." + entry.getKey() + ".";
            config.set(path + "type", entry.getValue().getType());
            config.set(path + "world", entry.getValue().getCorner1().getWorld().getName());
            config.set(path + "x1", entry.getValue().getCorner1().getX());
            config.set(path + "y1", entry.getValue().getCorner1().getY());
            config.set(path + "z1", entry.getValue().getCorner1().getZ());
            config.set(path + "x2", entry.getValue().getCorner2().getX());
            config.set(path + "y2", entry.getValue().getCorner2().getY());
            config.set(path + "z2", entry.getValue().getCorner2().getZ());
        }

        for (Map.Entry<String, Zone> entry : guildZones.entrySet()) {
            String path = "guild_zones." + entry.getKey() + ".";
            config.set(path + "world", entry.getValue().getCorner1().getWorld().getName());
            config.set(path + "x1", entry.getValue().getCorner1().getX());
            config.set(path + "y1", entry.getValue().getCorner1().getY());
            config.set(path + "z1", entry.getValue().getCorner1().getZ());
            config.set(path + "x2", entry.getValue().getCorner2().getX());
            config.set(path + "y2", entry.getValue().getCorner2().getY());
            config.set(path + "z2", entry.getValue().getCorner2().getZ());
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Błąd zapisu stref: " + e.getMessage());
        }
    }

    private void loadZones() {
        File dataFile = new File(getDataFolder(), "zones.yml");
        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        if (config.contains("zones")) {
            for (String key : config.getConfigurationSection("zones").getKeys(false)) {
                String path = "zones." + key + ".";
                Zone zone = new Zone(
                        key,
                        config.getString(path + "type"),
                        new Location(
                                Bukkit.getWorld(config.getString(path + "world")),
                                config.getDouble(path + "x1"),
                                config.getDouble(path + "y1"),
                                config.getDouble(path + "z1")
                        ),
                        new Location(
                                Bukkit.getWorld(config.getString(path + "world")),
                                config.getDouble(path + "x2"),
                                config.getDouble(path + "y2"),
                                config.getDouble(path + "z2")
                        )
                );
                zones.put(key, zone);
            }
        }

        if (config.contains("guild_zones")) {
            for (String key : config.getConfigurationSection("guild_zones").getKeys(false)) {
                String path = "guild_zones." + key + ".";
                Zone zone = new Zone(
                        key,
                        "guild",
                        new Location(
                                Bukkit.getWorld(config.getString(path + "world")),
                                config.getDouble(path + "x1"),
                                config.getDouble(path + "y1"),
                                config.getDouble(path + "z1")
                        ),
                        new Location(
                                Bukkit.getWorld(config.getString(path + "world")),
                                config.getDouble(path + "x2"),
                                config.getDouble(path + "y2"),
                                config.getDouble(path + "z2")
                        )
                );
                guildZones.put(key, zone);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            for (Zone zone : zones.values()) {
                if (zone.getType().equals("miasto")) {
                    if (zone.isInside(attacker.getLocation()) || zone.isInside(victim.getLocation())) {
                        event.setCancelled(true);
                        attacker.sendMessage(ChatColor.RED + "PvP jest wyłączone w strefie miasta.");
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.setQuitMessage(null);
        saveWantedData();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location playerLocation = player.getLocation();
        UUID playerId = player.getUniqueId();

        boolean isInAnyZone = false;
        String currentZoneName = null;

        for (Zone zone : zones.values()) {
            if (zone.isInside(playerLocation)) {
                isInAnyZone = true;
                currentZoneName = zone.getName();
                break;
            }
        }

        for (Zone guildZone : guildZones.values()) {
            if (guildZone.isInside(playerLocation)) {
                isInAnyZone = true;
                currentZoneName = guildZone.getName();
                break;
            }
        }

        boolean wasInZone = playerInZone.getOrDefault(playerId, false);

        if (isInAnyZone && !wasInZone) {
            sendTitle(player, ChatColor.GREEN + "Wchodzisz do strefy", " " + currentZoneName, 10, 30, 20);
            playerInZone.put(playerId, true);
            lastZoneNames.put(playerId, currentZoneName);
        } else if (!isInAnyZone && wasInZone) {
            String lastZoneName = lastZoneNames.getOrDefault(playerId, "BRAK");
            sendTitle(player, ChatColor.RED + "Opuszczasz strefę", " " + lastZoneName, 10, 30, 20);
            playerInZone.put(playerId, false);
        }
    }

    private void handleZoneMovement(Player player, Location playerLocation, Zone zone, String zoneMessage) {
        boolean isInZone = zone.isInside(playerLocation);
        boolean wasInZone = playerInZone.getOrDefault(player.getUniqueId(), false);

        if (isInZone && !wasInZone) {
            sendTitle(player, ChatColor.GREEN + "Wchodzisz do strefy: " + zoneMessage, "", 10, 30, 20);
            playerInZone.put(player.getUniqueId(), true);
        } else if (!isInZone && wasInZone) {
            sendTitle(player, ChatColor.RED + "Opuszczasz strefę: " + zoneMessage, "", 10, 30, 20);
            playerInZone.put(player.getUniqueId(), false);
        }

        checkRolePermissions(player, zone);
    }

    private void checkRolePermissions(Player player, Zone zone) {
        Role role = playerRoles.get(player.getUniqueId());
        if (role == null) {
            player.sendMessage(ChatColor.RED + "Nie masz przypisanej roli.");
        } else {
            if (role.hasPermissionForZone(zone)) {
                player.sendMessage(ChatColor.GREEN + "Masz uprawnienia w tej strefie.");
            } else {
                player.sendMessage(ChatColor.RED + "Nie masz uprawnień do tej strefy.");
            }
        }
    }

    private void setupScoreboard(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("info", "dummy", ChatColor.RED + "Silesia " + ChatColor.GRAY + "-" + ChatColor.RED + " SMP");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        objective.getScore(ChatColor.WHITE + "Nick: " + ChatColor.RED + player.getName()).setScore(4);
        objective.getScore(" ").setScore(3);

        Team roleTeam = scoreboard.registerNewTeam("roleTeam");
        String roleEntry = ChatColor.BLACK + "" + ChatColor.BLACK;
        roleTeam.addEntry(roleEntry);
        roleTeam.setPrefix(getRoleString(player));
        objective.getScore(roleEntry).setScore(2);

        objective.getScore(ChatColor.WHITE + "Reputacja: " + ChatColor.RED + "0").setScore(1);

        Team starsTeam = scoreboard.registerNewTeam("starsTeam");
        String starsEntry = ChatColor.DARK_BLUE + "" + ChatColor.BLACK;
        starsTeam.addEntry(starsEntry);
        starsTeam.setPrefix(getStarsString(player));
        objective.getScore(starsEntry).setScore(0);

        player.setScoreboard(scoreboard);
    }

    private String getRoleString(Player player) {
        String role;
        Role playerRole = playerRoles.get(player.getUniqueId());
        if (playerRole != null) {
            role = playerRole.getRoleName();
        } else {
            role = "BRAK";
        }

        String guildRole = guildRoles.getOrDefault(player.getUniqueId(), null);
        if (guildRole != null) {
            role = guildRole;
        }

        return ChatColor.WHITE + "Rola: " + ChatColor.RED + role;
    }

    private void updateScoreboard(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) {
            setupScoreboard(player);
            return;
        }

        Team roleTeam = scoreboard.getTeam("roleTeam");
        if (roleTeam != null) {
            roleTeam.setPrefix(getRoleString(player));
        }

        Team starsTeam = scoreboard.getTeam("starsTeam");
        if (starsTeam != null) {
            starsTeam.setPrefix(getStarsString(player));
        }

        player.setScoreboard(scoreboard);
    }

    private String getStarsString(Player player) {
        int stars = wantedStars.getOrDefault(player.getUniqueId(), 0);
        int clampedStars = Math.min(stars, 5);
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.WHITE).append("Gwiazdki: ");
        for (int i = 0; i < clampedStars; i++) {
            sb.append(ChatColor.GOLD).append("★");
        }
        return sb.toString();
    }

    private void loadWantedData() {
        File dataFile = new File(getDataFolder(), "wanted.yml");
        if (!dataFile.exists()) {
            getLogger().warning("Plik wanted.yml nie istnieje, tworzenie nowego...");
            return;
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        getLogger().info("Ładowanie danych o gwiazdkach z wanted.yml...");

        for (String key : dataConfig.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(key);
                int stars = dataConfig.getInt(key, 0);
                wantedStars.put(playerUUID, stars);
                getLogger().info("Załadowano: " + playerUUID + " -> " + stars + " gwiazdek");
            } catch (IllegalArgumentException e) {
                getLogger().warning("Nieprawidłowy UUID w wanted.yml: " + key);
            }
        }
    }

    private void saveWantedData() {
        File dataFile = new File(getDataFolder(), "wanted.yml");
        dataConfig = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : wantedStars.entrySet()) {
            dataConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void changePlayerRole(Player player, Role newRole) {
        playerRoles.put(player.getUniqueId(), newRole);
        updateScoreboard(player);
    }

    private void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    public static class Zone {
        private final String name;
        private final String type;
        private final Location corner1;
        private final Location corner2;

        public Zone(String name, String type, Location corner1, Location corner2) {
            this.name = name;
            this.type = type;
            this.corner1 = corner1;
            this.corner2 = corner2;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public Location getCorner1() {
            return corner1;
        }

        public Location getCorner2() {
            return corner2;
        }

        public boolean isInside(Location location) {

            if (location.getWorld() == null || corner1.getWorld() == null || !location.getWorld().equals(corner1.getWorld())) {
                return false;
            }
            double minX = Math.min(corner1.getX(), corner2.getX());
            double maxX = Math.max(corner1.getX(), corner2.getX());
            double minY = Math.min(corner1.getY(), corner2.getY());
            double maxY = Math.max(corner1.getY(), corner2.getY());
            double minZ = Math.min(corner1.getZ(), corner2.getZ());
            double maxZ = Math.max(corner1.getZ(), corner2.getZ());

            return location.getX() >= minX && location.getX() <= maxX
                    && location.getY() >= minY && location.getY() <= maxY
                    && location.getZ() >= minZ && location.getZ() <= maxZ;
        }
    }

    public enum Role {
        KROL("KROL", ChatColor.GRAY + "[KROL] "),
        NEUTRALNY("NEUTRALNY", ChatColor.GRAY + "[NEUTRALNY] "),
        BRAK("BRAK", ChatColor.GRAY + "[BRAK] ");

        private final String roleName;
        private final String prefix;

        Role(String roleName, String prefix) {
            this.roleName = roleName;
            this.prefix = prefix;
        }

        public String getRoleName() {
            return roleName;
        }

        public String getPrefix() {
            return prefix;
        }

        public static Role getRoleByName(String name) {
            for (Role role : values()) {
                if (role.getRoleName().equalsIgnoreCase(name)) {
                    return role;
                }
            }
            return null;
        }

        public static Set<String> getAvailableRoles() {
            Set<String> roles = new HashSet<>();
            for (Role role : values()) {
                roles.add(role.getRoleName());
            }
            return roles;
        }

        public boolean canBuildInZone(Zone zone) {

            if (zone.getType().equals("kopalnia")) {
                return false;
            }
            return this == KROL || (this == NEUTRALNY && zone.getType().equals("miasto"));
        }

        public boolean canDestroyInZone(Zone zone) {

            if (zone.getType().equals("kopalnia")) {
                return false;
            }
            return this == KROL || (this == NEUTRALNY && zone.getType().equals("miasto"));
        }

        public boolean hasPermissionForZone(Zone zone) {
            if (zone.getType().equals("kopalnia")) {
                return false;
            }
            return this == KROL || (this == NEUTRALNY && (zone.getType().equals("miasto") || zone.getType().equals("neutral")));
        }
    }

}