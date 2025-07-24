package owleaf.quantumexitscan;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;

public class CommandManager implements CommandExecutor {
    private final QuantumExitScan plugin;
    private final ConfigManager config;
    private final UUIDManager uuidManager;

    public CommandManager(QuantumExitScan plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.uuidManager = plugin.getUUIDManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReload(sender);
            case "radio":
                return handleRadio(sender, args);
            case "life":
                return handleLife(sender, args);
            case "reset":
                return handleReset(sender);
            case "list":
                return handleList(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        config.reloadConfig();
        uuidManager.reloadUUIDs();
        sender.sendMessage(ChatColor.GREEN + "Configuración recargada correctamente.");
        sendConfigInfo(sender);
        return true;
    }

    private boolean handleReset(CommandSender sender) {
        try {
            config.resetToDefaults();
            sender.sendMessage(ChatColor.GREEN + "Configuración restablecida a valores por defecto.");
            sendConfigInfo(sender);
            return true;
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Error al resetear la configuración.");
            plugin.getLogger().severe("Error: " + e.getMessage());
            return false;
        }
    }

    private boolean handleRadio(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(formatUsage("/q radio <1-50>", "Cambia radio de detección"));
            return true;
        }

        try {
            int newRadius = Integer.parseInt(args[1]);
            if (newRadius < 1 || newRadius > 50) {
                sender.sendMessage(ChatColor.RED + "El radio debe estar entre 1 y 50 bloques.");
                return true;
            }

            try {
                config.setDetectionRadius(newRadius);
                sender.sendMessage(String.format("%sRadio cambiado a %s%d bloques%s",
                        ChatColor.GREEN, ChatColor.WHITE, newRadius, ChatColor.GREEN));
                return true;
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + "Error al guardar los cambios.");
                return false;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Debe ser un número entre 1 y 50.");
            return false;
        }
    }

    private boolean handleLife(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(formatUsage("/q life <1-20>", "Cambia umbral de vida baja"));
            return true;
        }

        try {
            int newThreshold = Integer.parseInt(args[1]);
            if (newThreshold < 1 || newThreshold > 20) {
                sender.sendMessage(ChatColor.RED + "El umbral debe estar entre 1 y 20 puntos.");
                return true;
            }

            try {
                config.setLowLifeThreshold(newThreshold);
                sender.sendMessage(String.format("%sUmbral cambiado a %s%d puntos%s",
                        ChatColor.GREEN, ChatColor.WHITE, newThreshold, ChatColor.GREEN));
                return true;
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + "Error al guardar los cambios.");
                return false;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Debe ser un número entre 1 y 20.");
            return false;
        }
    }

    private boolean handleList(CommandSender sender) {
        List<String> players = uuidManager.getAllowedPlayers();
        sender.sendMessage(ChatColor.GOLD + "=== Jugadores autorizados ===");

        if (players.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No hay jugadores autorizados");
        } else {
            players.forEach(name ->
                    sender.sendMessage(String.format("%s- %s%s",
                            ChatColor.GRAY, ChatColor.WHITE, name)));
        }
        return true;
    }

    private void sendConfigInfo(CommandSender sender) {
        sender.sendMessage(String.format("%s- %sRadio: %s%d bloques",
                ChatColor.GRAY, ChatColor.WHITE, ChatColor.WHITE, config.getDetectionRadius()));
        sender.sendMessage(String.format("%s- %sUmbral vida: %s%d puntos",
                ChatColor.GRAY, ChatColor.WHITE, ChatColor.WHITE, config.getLowLifeThreshold()));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Comandos de QuantumExitScan ===");
        sender.sendMessage(formatUsage("/q reload", "Recarga la configuración"));
        sender.sendMessage(formatUsage("/q radio <1-50>", "Cambia radio de detección"));
        sender.sendMessage(formatUsage("/q life <1-20>", "Cambia umbral de vida"));
        sender.sendMessage(formatUsage("/q reset", "Restablece configuración"));
        sender.sendMessage(formatUsage("/q list", "Muestra jugadores autorizados"));
    }

    private String formatUsage(String command, String description) {
        return String.format("%s%s %s- %s",
                ChatColor.WHITE, command, ChatColor.GRAY, description);
    }
}