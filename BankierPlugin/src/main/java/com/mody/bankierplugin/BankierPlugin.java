package com.mody.bankierplugin;

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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BankierPlugin extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private final Map<UUID, Location> npcLocations = new HashMap<>();
    private final Map<UUID, Inventory> playerInventories = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        loadNPCs();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin Bankier został włączony!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("bankier") && args.length >= 4 &&
                args[0].equalsIgnoreCase("stworz") && args[1].equalsIgnoreCase("npc")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Tylko gracz może użyć tej komendy!");
                return true;
            }

            Player player = (Player) sender;
            try {
                Location loc = new Location(
                        player.getWorld(),
                        Integer.parseInt(args[2]),
                        Integer.parseInt(args[3]),
                        Integer.parseInt(args[4])
                );
                createNPC(loc);
                player.sendMessage(ChatColor.GREEN + "NPC Bankier został stworzony!");
                saveNPCs();
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                player.sendMessage(ChatColor.RED + "Poprawna składnia: /bankier stworz npc <x> <y> <z>");
            }
            return true;
        }
        return false;
    }

    private void createNPC(Location loc) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        NPC npc = registry.createNPC(EntityType.WANDERING_TRADER, ChatColor.GOLD + "Bankier"); //
        npc.spawn(loc);
        npc.setProtected(true);
        npcLocations.put(npc.getUniqueId(), loc);
        getLogger().info("Stworzono NPC Bankier na pozycji: " + loc);
    }

    private void loadNPCs() {
        ConfigurationSection npcsSection = config.getConfigurationSection("bankiers");
        if (npcsSection == null) return;

        for (String uuidStr : npcsSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Location loc = (Location) npcsSection.get(uuidStr);
                if (loc != null && loc.getWorld() != null) {
                    createNPC(loc);
                    npcLocations.put(uuid, loc);
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Nieprawidłowy UUID w configu: " + uuidStr);
            }
        }
    }

    private void saveNPCs() {
        config.set("bankiers", null);
        npcLocations.forEach((uuid, loc) -> {
            if (loc.getWorld() != null) {
                config.set("bankiers." + uuid, loc);
            }
        });
        saveConfig();
    }

    @EventHandler
    public void onNPCInteract(PlayerInteractEntityEvent event) {
        if (CitizensAPI.getNPCRegistry().isNPC(event.getRightClicked())) {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getRightClicked());
            getLogger().info("Gracz " + event.getPlayer().getName() + " kliknął NPC: " + npc.getName()); // Debug
            if (npc != null && npc.getName().equals(ChatColor.GOLD + "Bankier")) {
                event.setCancelled(true);
                getLogger().info("Otwarto sejf dla " + event.getPlayer().getName()); // Debug
                openBank(event.getPlayer());
            }
        }
    }

    private void openBank(Player player) {
        Inventory bank = playerInventories.computeIfAbsent(
                player.getUniqueId(),
                k -> Bukkit.createInventory(null, 27, ChatColor.GOLD + "Sejf Bankiera")
        );
        player.openInventory(bank);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.GOLD + "Sejf Bankiera")) return;

        ItemStack item = event.getCurrentItem();
        if (item == null) return;

        Material type = item.getType();
        boolean isAllowed = type == Material.GOLD_INGOT ||
                type == Material.IRON_INGOT ||
                type == Material.DIAMOND;

        if (!isAllowed && event.getRawSlot() < event.getInventory().getSize()) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(ChatColor.RED + "Możesz przechowywać tylko złoto, żelazo i diamenty!");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(ChatColor.GOLD + "Sejf Bankiera")) {
            event.getPlayer().sendMessage(ChatColor.GREEN + "Zawartość sejfu została zapisana!");
        }
    }

    @Override
    public void onDisable() {
        saveNPCs();
    }
}