package owleaf.quantumexitscan;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class UUIDManager {
    private final QuantumExitScan plugin;
    private File uuidFile;
    private FileConfiguration uuidConfig;
    private final Set<UUID> allowedUUIDs = new HashSet<>();

    public UUIDManager(QuantumExitScan plugin) {
        this.plugin = plugin;
        setupUUIDFile();
    }

    private void setupUUIDFile() {
        uuidFile = new File(plugin.getDataFolder(), "uuid.yml");
        if (!uuidFile.exists()) {
            try {
                uuidFile.createNewFile();
                uuidConfig = YamlConfiguration.loadConfiguration(uuidFile);
                uuidConfig.set("allowed-uuids", Collections.emptyList());
                uuidConfig.save(uuidFile);
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear uuid.yml: " + e.getMessage());
            }
        }
        reloadUUIDs();
    }

    public void reloadUUIDs() {
        uuidConfig = YamlConfiguration.loadConfiguration(uuidFile);
        allowedUUIDs.clear();
        allowedUUIDs.addAll(uuidConfig.getStringList("allowed-uuids").stream()
                .map(UUID::fromString)
                .collect(Collectors.toSet()));
    }

    public boolean isAllowed(UUID uuid) {
        return allowedUUIDs.contains(uuid);
    }

    public void addUUID(UUID uuid) throws IOException {
        if (!allowedUUIDs.contains(uuid)) {
            allowedUUIDs.add(uuid);
            saveUUIDs();
        }
    }

    public void removeUUID(UUID uuid) throws IOException {
        if (allowedUUIDs.contains(uuid)) {
            allowedUUIDs.remove(uuid);
            saveUUIDs();
        }
    }

    public List<String> getAllowedPlayers() {
        return allowedUUIDs.stream()
                .map(uuid -> {
                    Player player = Bukkit.getPlayer(uuid);
                    return player != null ? player.getName() : "Offline";
                })
                .collect(Collectors.toList());
    }

    public Set<UUID> getAllowedUUIDs() {
        return Collections.unmodifiableSet(allowedUUIDs);
    }

    private void saveUUIDs() throws IOException {
        uuidConfig.set("allowed-uuids", allowedUUIDs.stream()
                .map(UUID::toString)
                .collect(Collectors.toList()));
        uuidConfig.save(uuidFile);
    }
}