package owleaf.quantumexitscan;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    private int detectionRadius;
    private int lowLifeThreshold;
    private boolean trackEffects;
    private boolean trackEnvironmental;
    private boolean trackFallDamage;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setupConfig() {
        plugin.saveDefaultConfig();
        reloadConfig();
    }

    public void reloadConfig() {
        config = plugin.getConfig();

        detectionRadius = config.getInt("detection-radius", 10);
        lowLifeThreshold = config.getInt("low-life-threshold", 15);
        trackEffects = config.getBoolean("tracking.effects", true);
        trackEnvironmental = config.getBoolean("tracking.environmental", true);
        trackFallDamage = config.getBoolean("tracking.fall-damage", true);

        validateConfigValues();
    }

    private void validateConfigValues() {
        if (detectionRadius < 1 || detectionRadius > 100) {
            plugin.getLogger().warning("Radio inválido. Usando valor por defecto (10)");
            detectionRadius = 10;
        }

        if (lowLifeThreshold < 1 || lowLifeThreshold > 20) {
            plugin.getLogger().warning("Umbral de vida inválido. Usando valor por defecto (15)");
            lowLifeThreshold = 15;
        }
    }

    public void resetToDefaults() throws IOException {
        detectionRadius = 10;
        lowLifeThreshold = 15;
        trackEffects = true;
        trackEnvironmental = true;
        trackFallDamage = true;

        config.set("detection-radius", detectionRadius);
        config.set("low-life-threshold", lowLifeThreshold);
        config.set("tracking.effects", trackEffects);
        config.set("tracking.environmental", trackEnvironmental);
        config.set("tracking.fall-damage", trackFallDamage);

        plugin.saveConfig();
    }

    // Getters
    public int getDetectionRadius() { return detectionRadius; }
    public int getLowLifeThreshold() { return lowLifeThreshold; }
    public boolean isTrackEffects() { return trackEffects; }
    public boolean isTrackEnvironmental() { return trackEnvironmental; }
    public boolean isTrackFallDamage() { return trackFallDamage; }

    // Setters
    public void setDetectionRadius(int radius) throws IOException {
        this.detectionRadius = radius;
        config.set("detection-radius", radius);
        plugin.saveConfig();
    }

    public void setLowLifeThreshold(int threshold) throws IOException {
        this.lowLifeThreshold = threshold;
        config.set("low-life-threshold", threshold);
        plugin.saveConfig();
    }
}