package owleaf.quantumexitscan.Players;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import owleaf.quantumexitscan.Managers.ConfigManager;
import owleaf.quantumexitscan.QuantumExitScan;

import java.util.List;

public class PlayerExitDetect implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;

    public PlayerExitDetect(QuantumExitScan plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        int radius = config.getDetectionRadius();

        List<Entity> nearbyEntities = player.getNearbyEntities(radius, radius, radius);
        nearbyEntities.remove(player);

        String lifeStatus = (player.getHealth() <= config.getLowLifeThreshold()) ? "= Low Life" : "";

        plugin.getLogger().info("[QuantumExitScan] === Entidades cerca de " + player.getName() + " al desconectarse ===");
        plugin.getLogger().info("[QuantumExitScan] Vida actual de " + player.getName() + ": " +
                (int)player.getHealth() + " puntos de vida " + lifeStatus);
        plugin.getLogger().info("[QuantumExitScan] Radio de detección: " + radius + " bloques");
        plugin.getLogger().info("[QuantumExitScan] Total de entidades detectadas: " + nearbyEntities.size());

        for (Entity entity : nearbyEntities) {
            String trackingStatus = (entity instanceof Monster && ((Monster) entity).getTarget() == player)
                    ? "= Tracking" : "";
            plugin.getLogger().info("[QuantumExitScan] - " + entity.getType() + ": " +
                    (entity.getCustomName() != null ? entity.getCustomName() : entity.getName()) +
                    " en X:" + entity.getLocation().getBlockX() +
                    " Y:" + entity.getLocation().getBlockY() +
                    " Z:" + entity.getLocation().getBlockZ() + " " + trackingStatus);
        }

        plugin.getLogger().info("[QuantumExitScan] ======================");
        plugin.getLogger().info("[QuantumExitScan] " + player.getName() + " left the game");
    }
}