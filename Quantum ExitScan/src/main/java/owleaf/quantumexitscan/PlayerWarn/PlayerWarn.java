package owleaf.quantumexitscan.Players;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import owleaf.quantumexitscan.Managers.ConfigManager;
import owleaf.quantumexitscan.Managers.UUIDManager;
import owleaf.quantumexitscan.QuantumExitScan;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerWarn implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final UUIDManager uuidManager;

    public PlayerWarn(QuantumExitScan plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.uuidManager = plugin.getUUIDManager();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!config.getWarningsConfig().getBoolean("settings.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        PlayerWarnData warnData = new PlayerWarnData();
        warnData.playerName = player.getName();
        warnData.uuid = player.getUniqueId().toString();
        warnData.health = (int) player.getHealth();
        warnData.maxHealth = (int) player.getMaxHealth();

        // Verificar condiciones
        boolean shouldWarn = false;

        // Condición de vida baja
        warnData.lowLife = warnData.health <= config.getLowLifeThreshold();
        if (warnData.lowLife) {
            shouldWarn = true;
        }

        // Condición de efectos negativos
        List<String> negativeEffects = config.getNegativeEffects();
        warnData.effects = player.getActivePotionEffects().stream()
                .filter(e -> negativeEffects.contains(e.getType().getName()))
                .collect(Collectors.toList());
        if (!warnData.effects.isEmpty()) {
            shouldWarn = true;
        }

        // Condición de estados peligrosos
        warnData.isBurning = player.getFireTicks() > 0;
        warnData.isDrowning = player.getRemainingAir() < player.getMaximumAir();
        warnData.isFreezing = player.getFreezeTicks() > 0;
        if (warnData.isBurning || warnData.isDrowning || warnData.isFreezing) {
            shouldWarn = true;
        }

        // Condición de entidades cercanas
        List<Entity> nearbyEntities = player.getNearbyEntities(
                config.getDetectionRadius(),
                config.getDetectionRadius(),
                config.getDetectionRadius()
        );

        warnData.nearbyHostiles = nearbyEntities.stream()
                .filter(e -> e instanceof Monster)
                .count();

        warnData.trackingHostiles = nearbyEntities.stream()
                .filter(e -> e instanceof Monster)
                .filter(e -> ((Monster) e).getTarget() == player)
                .count();

        if (warnData.trackingHostiles >= config.getWarningTrackingEntitiesThreshold()) {
            shouldWarn = true;
        }

        // Condición de caída
        warnData.fallDistance = player.getFallDistance();
        warnData.fallType = determineFallType(player);

        if (warnData.fallDistance > 0) {
            shouldWarn = true;
        }

        if (shouldWarn) {
            String warningId = registerWarning(player, warnData);
            notifyAllowedPlayers(player, warnData, warningId);
        }
    }

    private String registerWarning(Player player, PlayerWarnData data) {
        String warningId = UUID.randomUUID().toString().substring(0, 8);
        long timestamp = Instant.now().getEpochSecond();

        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("timestamp", timestamp);
        warning.put("player", data.playerName);
        warning.put("uuid", data.uuid);
        warning.put("reason", buildReasonString(data));
        warning.put("location", buildLocationString(player.getLocation()));
        warning.put("health", data.health + "/" + data.maxHealth);
        warning.put("hostiles", data.nearbyHostiles);
        warning.put("effects", data.effects.stream()
                .map(e -> e.getType().getName())
                .collect(Collectors.toList()));

        List<Map<String, Object>> history = new ArrayList<>(
                config.getWarningsConfig().getMapList("history").stream()
                        .map(map -> new HashMap<String, Object>((Map<String, Object>) map))
                        .collect(Collectors.toList())
        );

        history.add(0, warning);
        config.getWarningsConfig().set("history", history);

        try {
            config.saveWarningsConfig();
        } catch (Exception e) {
            plugin.getLogger().severe("Error guardando advertencia: " + e.getMessage());
        }

        return warningId;
    }

    private void notifyAllowedPlayers(Player player, PlayerWarnData data, String warningId) {
        String message = buildWarningMessage(player, data, warningId);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> uuidManager.isAllowed(p.getUniqueId()))
                .forEach(p -> p.sendMessage(message));
        plugin.getLogger().warning("[Alerta] " + message.replace(ChatColor.COLOR_CHAR, '&'));
    }

    private String buildWarningMessage(Player player, PlayerWarnData data, String warningId) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.RED).append("\n⚠️ [Alerta] ").append(ChatColor.GOLD).append(data.playerName)
                .append(ChatColor.RED).append(" se desconectó en peligro!\n")
                .append(ChatColor.YELLOW).append("ID: ").append(ChatColor.WHITE).append(warningId).append("\n");

        if (data.lowLife) {
            sb.append(ChatColor.RED).append("❤️ Vida: ").append(data.health).append("/").append(data.maxHealth).append("\n");
        }

        if (!data.effects.isEmpty()) {
            sb.append(ChatColor.DARK_PURPLE).append("⚗️ Efectos: ")
                    .append(ChatColor.WHITE).append(data.effects.stream()
                            .map(e -> e.getType().getName() + " (" + (e.getAmplifier() + 1) + ")")
                            .collect(Collectors.joining(", ")))
                    .append("\n");
        }

        if (data.isBurning || data.isDrowning || data.isFreezing) {
            sb.append(ChatColor.GOLD).append("⚠️ Condiciones: ").append(ChatColor.WHITE);
            List<String> conditions = new ArrayList<>();
            if (data.isBurning) conditions.add("Quemándose");
            if (data.isDrowning) conditions.add("Ahogándose");
            if (data.isFreezing) conditions.add("Congelándose");
            sb.append(String.join(", ", conditions)).append("\n");
        }

        if (data.nearbyHostiles > 0) {
            sb.append(ChatColor.DARK_RED).append("☠ Hostiles: ").append(ChatColor.WHITE)
                    .append(data.nearbyHostiles).append("\n");
        }

        if (data.fallDistance > 0) {
            sb.append(ChatColor.AQUA).append("⬇️ Caída: ").append(ChatColor.WHITE)
                    .append(String.format("%.1f", data.fallDistance))
                    .append(" bloques hacia ").append(data.fallType).append("\n");
        }

        sb.append(ChatColor.GREEN).append("📍 Ubicación: ").append(ChatColor.WHITE)
                .append(buildLocationString(player.getLocation()))
                .append(ChatColor.GRAY).append("\nUsa ").append(ChatColor.YELLOW)
                .append("/q warnings ").append(warningId).append(ChatColor.GRAY)
                .append(" para más detalles");

        return sb.toString();
    }

    private String determineFallType(Player player) {
        Location below = player.getLocation().clone().subtract(0, 1, 0);
        Material belowType = below.getBlock().getType();
        if (belowType == Material.WATER) return "agua";
        if (belowType == Material.LAVA) return "lava";
        if (below.getY() <= player.getWorld().getMinHeight()) return "vacío";
        int airBlocks = 0;
        Location loc = player.getLocation().clone();
        while (loc.getY() > loc.getWorld().getMinHeight() && loc.getBlock().getType().isAir()) {
            airBlocks++;
            loc.subtract(0, 1, 0);
        }
        return "caída libre (" + airBlocks + " bloques)";
    }

    private long countTrackingHostiles(Player player) {
        return player.getNearbyEntities(config.getDetectionRadius(), config.getDetectionRadius(), config.getDetectionRadius())
                .stream()
                .filter(e -> e instanceof Monster)
                .filter(e -> ((Monster) e).getTarget() == player)
                .count();
    }

    private String buildReasonString(PlayerWarnData data) {
        List<String> reasons = new ArrayList<>();
        if (data.lowLife) reasons.add("Vida baja (" + data.health + "/" + data.maxHealth + ")");
        if (!data.effects.isEmpty()) reasons.add("Efectos negativos");
        if (data.isBurning) reasons.add("Quemándose");
        if (data.isDrowning) reasons.add("Ahogándose");
        if (data.isFreezing) reasons.add("Congelándose");
        if (data.nearbyHostiles > 0) reasons.add(data.nearbyHostiles + " hostiles");
        if (data.fallDistance > 0) reasons.add("Caída de " + String.format("%.1f", data.fallDistance) + " bloques");
        return String.join(", ", reasons);
    }

    private String buildLocationString(Location loc) {
        return loc.getWorld().getName() + ", " +
                loc.getBlockX() + ", " +
                loc.getBlockY() + ", " +
                loc.getBlockZ();
    }

    private static class PlayerWarnData {
        String playerName;
        String uuid;
        int health;
        int maxHealth;
        boolean lowLife;
        List<PotionEffect> effects;
        boolean isBurning;
        boolean isDrowning;
        boolean isFreezing;
        float fallDistance;
        String fallType;
        long nearbyHostiles;
    }
}