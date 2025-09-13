package com.mody.parcelplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.stream.Collectors;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ParcelPlugin extends JavaPlugin implements Listener {

    private final Map<String, Parcel> parcels = new HashMap<>();
    private File configFile;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        configFile = new File(getDataFolder(), "parcels.yml");
        loadParcels();
    }

    @Override
    public void onDisable() {
        saveParcels();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tylko gracze mogą używać tej komendy!");
            return true;
        }

        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("parcel")) {
            if (args.length == 0) {
                sendHelp(player);
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "stworz":
                    return createParcel(player, args);
                case "dodaj":
                    return addMember(player, args);
                case "zniszcz":
                    return destroyParcel(player, args);
                case "usun":
                    return removeMember(player, args);
                default:
                    sendHelp(player);
            }
        }
        return false;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6Parcel System §7- Pomoc:");
        player.sendMessage("§a/parcel stworz <nazwa> <gracz> <x1> <y1> <z1> <x2> <y2> <z2> §7- Tworzy nowy parcel");
        player.sendMessage("§a/parcel dodaj <nazwa> <gracz> §7- Dodaje gracza do parcela");
        player.sendMessage("§a/parcel zniszcz <nazwa> §7- Usuwa parcel");
        player.sendMessage("§a/parcel usun <nazwa> <gracz> §7- Usuwa gracza z parcela");
    }

    private boolean createParcel(Player player, String[] args) {
        if (args.length != 9) {
            player.sendMessage("§cUżycie: /parcel stworz <nazwa> <gracz> <x1> <y1> <z1> <x2> <y2> <z2>");
            return true;
        }

        String parcelName = args[1];
        String ownerName = args[2];

        try {
            int x1 = Integer.parseInt(args[3]);
            int y1 = Integer.parseInt(args[4]);
            int z1 = Integer.parseInt(args[5]);
            int x2 = Integer.parseInt(args[6]);
            int y2 = Integer.parseInt(args[7]);
            int z2 = Integer.parseInt(args[8]);

            Player owner = Bukkit.getPlayer(ownerName);
            if (owner == null) {
                player.sendMessage("§cGracz " + ownerName + " nie jest online!");
                return true;
            }

            World world = player.getWorld();
            int maxY = Math.max(y1, y2) + 20;

            Parcel newParcel = new Parcel(
                    parcelName,
                    owner.getUniqueId(),
                    world.getName(),
                    Math.min(x1, x2),
                    Math.min(y1, y2),
                    Math.min(z1, z2),
                    Math.max(x1, x2),
                    maxY,
                    Math.max(z1, z2)
            );

            parcels.put(parcelName, newParcel);
            saveParcels();
            player.sendMessage("§aParcel §e" + parcelName + " §azostał utworzony!");

        } catch (NumberFormatException e) {
            player.sendMessage("§cNieprawidłowe współrzędne!");
        }
        return true;
    }

    private boolean addMember(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§cUżycie: /parcel dodaj <nazwa> <gracz>");
            return true;
        }

        String parcelName = args[1];
        String memberName = args[2];
        Parcel parcel = parcels.get(parcelName);

        if (parcel == null) {
            player.sendMessage("§cParcel §e" + parcelName + " §cnie istnieje!");
            return true;
        }

        if (!parcel.isOwner(player.getUniqueId())) {
            player.sendMessage("§cNie jesteś właścicielem tego parcela!");
            return true;
        }

        Player member = Bukkit.getPlayer(memberName);
        if (member == null) {
            player.sendMessage("§cGracz §e" + memberName + " §cnie jest online!");
            return true;
        }

        parcel.addMember(member.getUniqueId());
        saveParcels();
        player.sendMessage("§aDodano gracza §e" + memberName + " §ado parcela!");
        return true;
    }

    private boolean destroyParcel(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§cUżycie: /parcel zniszcz <nazwa>");
            return true;
        }

        String parcelName = args[1];
        Parcel parcel = parcels.get(parcelName);

        if (parcel == null) {
            player.sendMessage("§cParcel §e" + parcelName + " §cnie istnieje!");
            return true;
        }

        if (!parcel.isOwner(player.getUniqueId())) {
            player.sendMessage("§cNie jesteś właścicielem tego parcela!");
            return true;
        }

        parcels.remove(parcelName);
        saveParcels();
        player.sendMessage("§aParcel §e" + parcelName + " §azostał zniszczony!");
        return true;
    }

    private boolean removeMember(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§cUżycie: /parcel usun <nazwa> <gracz>");
            return true;
        }

        String parcelName = args[1];
        String memberName = args[2];
        Parcel parcel = parcels.get(parcelName);

        if (parcel == null) {
            player.sendMessage("§cParcel §e" + parcelName + " §cnie istnieje!");
            return true;
        }

        if (!parcel.isOwner(player.getUniqueId())) {
            player.sendMessage("§cNie jesteś właścicielem tego parcela!");
            return true;
        }

        Player member = Bukkit.getPlayer(memberName);
        if (member == null) {
            player.sendMessage("§cGracz §e" + memberName + " §cnie istnieje!");
            return true;
        }

        parcel.removeMember(member.getUniqueId());
        saveParcels();
        player.sendMessage("§aUsunięto gracza §e" + memberName + " §az parcela!");
        return true;
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        checkPermission(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        checkPermission(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Set<Material> blockedBlocks = new HashSet<>(Arrays.asList(
                // Wszystkie drzwi
                Material.OAK_DOOR, Material.IRON_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
                Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.CRIMSON_DOOR,
                Material.WARPED_DOOR, Material.MANGROVE_DOOR, Material.BAMBOO_DOOR, Material.CHERRY_DOOR,

                // Wszystkie trapdoory
                Material.OAK_TRAPDOOR, Material.IRON_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
                Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR, Material.CRIMSON_TRAPDOOR,
                Material.WARPED_TRAPDOOR, Material.MANGROVE_TRAPDOOR, Material.BAMBOO_TRAPDOOR, Material.CHERRY_TRAPDOOR,

                // Wszystkie pojemniki
                Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.ENDER_CHEST,
                Material.SHULKER_BOX, Material.BLACK_SHULKER_BOX, Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX,
                Material.CYAN_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
                Material.LIGHT_GRAY_SHULKER_BOX, Material.LIME_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
                Material.PINK_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.WHITE_SHULKER_BOX,
                Material.YELLOW_SHULKER_BOX
        ));

        if (blockedBlocks.contains(event.getClickedBlock().getType())) {
            checkPermission(event.getPlayer(), event.getClickedBlock().getLocation(), event);
        }
    }

    private void checkPermission(Player player, Location loc, Object event) {
        for (Parcel parcel : parcels.values()) {
            if (parcel.isInParcel(loc)) {
                if (parcel.getOwner().equals(player.getUniqueId()) || parcel.getMembers().contains(player.getUniqueId())) {
                    return; // Gracz ma uprawnienia – nic nie robimy
                }
                player.sendMessage("§cNie masz uprawnień w tym parcelu!");
                cancelEvent(event);
                return;
            }
        }
    }


    private void cancelEvent(Object event) {
        if (event instanceof BlockPlaceEvent) {
            ((BlockPlaceEvent) event).setCancelled(true);
        } else if (event instanceof BlockBreakEvent) {
            BlockBreakEvent bEvent = (BlockBreakEvent) event;
            bEvent.setDropItems(false);
            bEvent.setCancelled(true);
            Material original = bEvent.getBlock().getType();
            Bukkit.getScheduler().runTask(this, () -> bEvent.getBlock().setType(original));
        } else if (event instanceof PlayerInteractEvent) {
            ((PlayerInteractEvent) event).setCancelled(true);
        }
    }

    private void saveParcels() {
        YamlConfiguration config = new YamlConfiguration();

        for (Parcel parcel : parcels.values()) {
            String path = "parcels." + parcel.getName() + ".";
            config.set(path + "owner", parcel.getOwner().toString());
            config.set(path + "members", new ArrayList<>(parcel.getMembers().stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList())));
            config.set(path + "world", parcel.getWorld());
            config.set(path + "minX", parcel.getMinX());
            config.set(path + "minY", parcel.getMinY());
            config.set(path + "minZ", parcel.getMinZ());
            config.set(path + "maxX", parcel.getMaxX());
            config.set(path + "maxY", parcel.getMaxY());
            config.set(path + "maxZ", parcel.getMaxZ());
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Błąd zapisu parceli: " + e.getMessage());
        }
    }

    private void loadParcels() {
        if (!configFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (!config.contains("parcels")) return;

        for (String parcelName : config.getConfigurationSection("parcels").getKeys(false)) {
            String path = "parcels." + parcelName + ".";
            UUID owner = UUID.fromString(config.getString(path + "owner"));
            Set<UUID> members = new HashSet<>();
            for (String member : config.getStringList(path + "members")) {
                members.add(UUID.fromString(member));
            }

            Parcel parcel = new Parcel(
                    parcelName,
                    owner,
                    config.getString(path + "world"),
                    config.getInt(path + "minX"),
                    config.getInt(path + "minY"),
                    config.getInt(path + "minZ"),
                    config.getInt(path + "maxX"),
                    config.getInt(path + "maxY"),
                    config.getInt(path + "maxZ")
            );

            parcel.getMembers().addAll(members);
            parcels.put(parcelName, parcel);
        }
    }

    public static class Parcel {
        private final String name;
        private final UUID owner;
        private final Set<UUID> members = new HashSet<>();
        private final String world;
        private final int minX, minY, minZ;
        private final int maxX, maxY, maxZ;

        public Parcel(String name, UUID owner, String world,
                      int x1, int y1, int z1, int x2, int y2, int z2) {
            this.name = name;
            this.owner = owner;
            this.world = world;
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
        }

        public boolean isInParcel(Location loc) {
            return loc.getWorld().getName().equals(world) &&
                    loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
                    loc.getBlockY() >= minY && loc.getBlockY() <= maxY &&
                    loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
        }

        public boolean hasPermission(UUID player) {
            return owner.equals(player) || members.contains(player);
        }

        public String getName() { return name; }
        public UUID getOwner() { return owner; }
        public Set<UUID> getMembers() { return members; }
        public String getWorld() { return world; }
        public int getMinX() { return minX; }
        public int getMinY() { return minY; }
        public int getMinZ() { return minZ; }
        public int getMaxX() { return maxX; }
        public int getMaxY() { return maxY; }
        public int getMaxZ() { return maxZ; }
        public boolean isOwner(UUID uuid) { return owner.equals(uuid); }
        public void addMember(UUID uuid) { members.add(uuid); }
        public void removeMember(UUID uuid) { members.remove(uuid); }
    }
}