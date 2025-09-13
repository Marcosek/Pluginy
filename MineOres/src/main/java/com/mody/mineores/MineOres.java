package com.mody.mineores;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MineOres extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<String, Material> mineMaterials = new HashMap<>();
    private final Random random = new Random();
    private final Map<Block, String> trackedBlocks = new HashMap<>();
    private FileConfiguration dataConfig;
    private File dataFile;

    @Override
    public void onEnable() {
        getCommand("setmineores1").setExecutor(this);
        getCommand("setmineores2").setExecutor(this);
        getCommand("setmineores3").setExecutor(this);
        getCommand("setmineores4").setExecutor(this);

        Bukkit.getPluginManager().registerEvents(this, this);

        mineMaterials.put("1", Material.STONE);
        mineMaterials.put("2", null);
        mineMaterials.put("3", null);
        mineMaterials.put("4", Material.BLACKSTONE);

        loadMineData();
    }

    private Material getRandomOreForType(String type) {
        if (type.equals("2")) {
            int roll = random.nextInt(100);
            if (roll < 28) return Material.IRON_ORE;
            else if (roll < 56) return Material.COPPER_ORE;
            else if (roll < 73) return Material.REDSTONE_ORE;
            else if (roll < 90) return Material.LAPIS_ORE;
            else return Material.GOLD_ORE;
        } else if (type.equals("3")) {
            return random.nextBoolean() ? Material.COAL_ORE : Material.DEEPSLATE;
        }
        return null;
    }

    private void setBlocksInRange(World world, int x1, int y1, int z1, int x2, int y2, int z2, String type) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (mineMaterials.containsKey(type) && mineMaterials.get(type) != null) {
                        block.setType(mineMaterials.get(type));
                    } else {
                        Material randomOre = getRandomOreForType(type);
                        if (randomOre != null) {
                            block.setType(randomOre);
                        } else {
                            getLogger().warning("Nieznany typ materiału dla regionu: " + type);
                        }
                    }
                    trackedBlocks.put(block, type);
                }
            }
        }
        saveRegionData(world, x1, y1, z1, x2, y2, z2, type);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (trackedBlocks.containsKey(block)) {
            String type = trackedBlocks.get(block);
            Bukkit.getScheduler().runTaskLater(this, () -> regenerateBlock(block, type), 600L);
        }
    }


    private void regenerateBlock(Block block, String type) {
        if (block.getType() == Material.AIR || !trackedBlocks.containsKey(block)) {
            Material newMaterial = null;

            if (type.equals("1")) {
                newMaterial = Material.STONE;
            } else if (type.equals("4")) {
                newMaterial = Material.BLACKSTONE;
            } else {
                newMaterial = getRandomOreForType(type);
            }

            if (newMaterial != null) {
                block.setType(newMaterial);
            }
        }
    }

    private void loadMineData() {
        File folder = new File(getDataFolder(), "MineOres");
        if (!folder.exists()) {
            folder.mkdir();
        }

        dataFile = new File(folder, "MineOres.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (!dataConfig.contains("regions")) {
            return;
        }

        for (String type : dataConfig.getConfigurationSection("regions").getKeys(false)) {
            for (String worldName : dataConfig.getConfigurationSection("regions." + type).getKeys(false)) {
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                List<String> regionList = dataConfig.getStringList("regions." + type + "." + worldName);
                for (String region : regionList) {
                    String[] parts = region.split(",");
                    if (parts.length != 6) continue;

                    try {
                        int x1 = Integer.parseInt(parts[0]);
                        int y1 = Integer.parseInt(parts[1]);
                        int z1 = Integer.parseInt(parts[2]);
                        int x2 = Integer.parseInt(parts[3]);
                        int y2 = Integer.parseInt(parts[4]);
                        int z2 = Integer.parseInt(parts[5]);


                        setBlocksInRange(world, x1, y1, z1, x2, y2, z2, type);
                    } catch (NumberFormatException e) {
                        getLogger().warning("Błędne współrzędne w mineores.yml: " + region);
                    }
                }
            }
        }
    }

    private void saveMineData() {
        try {

            File folder = new File(getDataFolder(), "MineOres");
            if (!folder.exists()) {
                folder.mkdir();
            }


            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveRegionData(World world, int x1, int y1, int z1, int x2, int y2, int z2, String type) {
        String worldName = world.getName();
        String regionData = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;


        List<String> regionList = dataConfig.getStringList("regions." + type + "." + worldName);
        regionList.add(regionData);
        dataConfig.set("regions." + type + "." + worldName, regionList);


        saveMineData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 6) {
            sender.sendMessage("Błędna liczba argumentów! Poprawny format: /" + label + " <x1> <y1> <z1> <x2> <y2> <z2>");
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Ta komenda może być używana tylko przez gracza.");
            return false;
        }

        Player player = (Player) sender;
        World world = player.getWorld();

        try {
            int x1 = Integer.parseInt(args[0]);
            int y1 = Integer.parseInt(args[1]);
            int z1 = Integer.parseInt(args[2]);
            int x2 = Integer.parseInt(args[3]);
            int y2 = Integer.parseInt(args[4]);
            int z2 = Integer.parseInt(args[5]);


            if (label.equalsIgnoreCase("setmineores1")) {
                setBlocksInRange(world, x1, y1, z1, x2, y2, z2, "1");
            } else if (label.equalsIgnoreCase("setmineores2")) {
                setBlocksInRange(world, x1, y1, z1, x2, y2, z2, "2");
            } else if (label.equalsIgnoreCase("setmineores3")) {
                setBlocksInRange(world, x1, y1, z1, x2, y2, z2, "3");
            } else if (label.equalsIgnoreCase("setmineores4")) {
                setBlocksInRange(world, x1, y1, z1, x2, y2, z2, "4");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("Wszystkie współrzędne muszą być liczbami.");
            return false;
        }

        return true;
    }
}
