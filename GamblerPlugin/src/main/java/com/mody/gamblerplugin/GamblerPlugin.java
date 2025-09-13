package com.mody.gamblerplugin;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class GamblerPlugin extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private final Map<UUID, Location> npcLocations = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        loadNPCs();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("kubki") && args.length >= 4 && args[0].equalsIgnoreCase("stworz") && args[1].equalsIgnoreCase("npc")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cTylko gracz może użyć tej komendy!");
                return true;
            }

            Player player = (Player) sender;
            try {
                int x = Integer.parseInt(args[2]);
                int y = Integer.parseInt(args[3]);
                int z = Integer.parseInt(args[4]);
                Location loc = new Location(player.getWorld(), x, y, z);
                createNPC(loc);
                player.sendMessage("§aNPC Gambler został stworzony!");
                saveNPCs();
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                player.sendMessage("§cPoprawna składnia: /kubki stworz npc <x> <y> <z>");
            }
            return true;
        }
        return false;
    }

    private void createNPC(Location loc) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        NPC npc = registry.createNPC(EntityType.VILLAGER, "§6Gambler");
        npc.spawn(loc);
        npc.setProtected(true);
        npcLocations.put(npc.getUniqueId(), loc);
    }

    private void loadNPCs() {
        ConfigurationSection npcsSection = config.getConfigurationSection("npcs");
        if (npcsSection == null) return;

        for (String uuidStr : npcsSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Location loc = (Location) npcsSection.get(uuidStr);
                if (loc != null) {
                    createNPC(loc);
                    npcLocations.put(uuid, loc);
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Nieprawidłowy UUID w configu: " + uuidStr);
            }
        }
    }

    private void saveNPCs() {
        config.set("npcs", null);
        npcLocations.forEach((uuid, loc) -> config.set("npcs." + uuid.toString(), loc));
        saveConfig();
    }

    @EventHandler
    public void onNPCInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.VILLAGER &&
                event.getRightClicked().getCustomName() != null &&
                event.getRightClicked().getCustomName().equals("§6Gambler")) {

            event.setCancelled(true);
            openGUI(event.getPlayer());
        }
    }

    private void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, "§6Kubki Szczęścia");

        ItemStack cup = new ItemStack(Material.CLAY_BALL);
        ItemMeta meta = cup.getItemMeta();
        meta.setDisplayName("§eWYBIERZ (Koszt: 2 srebrne monety)");
        cup.setItemMeta(meta);

        Arrays.asList(2, 4, 6).forEach(slot -> gui.setItem(slot, cup));
        player.openInventory(gui);
    }

    @EventHandler
    public void onGUIClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§6Kubki Szczęścia")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() != Material.CLAY_BALL) return;


        ItemStack silverCoin = new ItemStack(Material.PAPER, 2);


        if (player.getInventory().containsAtLeast(silverCoin, 2)) {
            player.getInventory().removeItem(silverCoin);

            if (random.nextInt(3) == 0) { // 33.3% szans

                ItemStack reward = new ItemStack(Material.PAPER, 4);
                ItemMeta meta = reward.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(1002);
                    reward.setItemMeta(meta);
                }
                player.getInventory().addItem(reward);
                player.sendMessage("§a§lWYGRANA! §aOtrzymujesz 4 srebrne monety!");
            } else {
                player.sendMessage("§c§lPRZEGRANA! §cStraciłeś 2 srebrne monety.");
            }
        } else {
            player.sendMessage("§cNie masz wystarczająco srebrnych monet!");
        }

        player.closeInventory();
    }

    @Override
    public void onDisable() {
        if (config != null) {
            saveNPCs();
        }
    }
}