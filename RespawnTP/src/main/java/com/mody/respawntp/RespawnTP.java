package com.mody.respawntp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RespawnTP extends JavaPlugin implements Listener {

    private final Map<UUID, Location> originalRespawnLocations = new HashMap<>();
    private final Map<UUID, Integer> countdownTasks = new HashMap<>();
    private Location respawnLocation;
    private final int teleportDuration = 60;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        setupRespawnLocation();
        getLogger().info("Plugin RespawnTP został włączony!");
    }

    private void setupRespawnLocation() {
        World world = Bukkit.getWorld("world");
        if (world == null) {
            getLogger().severe("Świat 'world' nie istnieje! Wyłączam plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        respawnLocation = new Location(world, -1649, 250, -812);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location respawnLoc = player.getLocation();

        originalRespawnLocations.put(player.getUniqueId(), respawnLoc);
        getLogger().info("Gracz " + player.getName() + " umarł! Zapisano miejsce respawnu: " + respawnLoc);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location originalRespawn = originalRespawnLocations.getOrDefault(player.getUniqueId(), event.getRespawnLocation());

        if (respawnLocation == null) {
            player.sendMessage("§cBłąd teleportacji! Skontaktuj się z administratorem.");
            return;
        }

        getLogger().info("Gracz " + player.getName() + " się respawnuje! Teleportacja do nieba.");
        event.setRespawnLocation(respawnLocation);

        // Opóźniona teleportacja, żeby nadpisać inne pluginy
        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.teleport(respawnLocation);
            player.sendMessage("§6Trafiłeś do nieba na 60 sekund!");
            startCountdown(player, originalRespawn);
        }, 1L);
    }

    private void startCountdown(Player player, Location originalRespawn) {
        UUID playerId = player.getUniqueId();

        if (countdownTasks.containsKey(playerId)) {
            Bukkit.getScheduler().cancelTask(countdownTasks.get(playerId));
        }

        final int[] timeLeft = {teleportDuration};
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft[0] <= 0) {
                    player.sendMessage("§cTeleportacja zakończona!");
                    player.teleport(originalRespawn);
                    countdownTasks.remove(playerId);
                    cancel();
                    return;
                }
                player.sendTitle("§ePozostały czas: §6" + timeLeft[0] + "s", "§7Zostaniesz przeteleportowany!", 0, 40, 20);
                timeLeft[0]--;
            }
        }.runTaskTimer(this, 0, 20).getTaskId();

        countdownTasks.put(playerId, taskId);
    }

    @Override
    public void onDisable() {
        countdownTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        countdownTasks.clear();
        getLogger().info("Plugin RespawnTP został wyłączony!");
    }
}
