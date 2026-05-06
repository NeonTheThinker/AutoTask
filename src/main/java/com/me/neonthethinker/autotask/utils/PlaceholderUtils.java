package com.me.neonthethinker.autotask.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class PlaceholderUtils {
    private static boolean papiEnabled = false;

    public static void init() {
        papiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public static boolean isPapiEnabled() {
        return papiEnabled;
    }

    public static String replace(OfflinePlayer player, String text) {
        if (text == null || text.isEmpty()) return text;

        if (papiEnabled) {
            return PAPIHook.setPlaceholders(player, text);
        }
        return text;
    }

    private static class PAPIHook {
        private static String setPlaceholders(OfflinePlayer player, String text) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
    }
}