package me.neonthethinker.autotask.utils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import me.neonthethinker.autotask.Autotask;

import java.lang.reflect.Method;

public class PlaceholderUtils {
    private static boolean papiEnabled = false;

    private static Method cachedOfServerMethod;
    private static Method cachedOfPlayerMethod;
    private static Method cachedOfSourceMethod;
    private static Method cachedParseTextMethod;
    private static Method cachedGetStringMethod;

    public static void init() {
        try {
            Class<?> contextClass = Class.forName("eu.pb4.placeholders.api.PlaceholderContext");
            Class<?> placeholdersClass = Class.forName("eu.pb4.placeholders.api.Placeholders");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.network.ServerPlayerEntity");
            Class<?> commandSourceClass = Class.forName("net.minecraft.server.command.ServerCommandSource");

            cachedOfServerMethod = contextClass.getMethod("of", MinecraftServer.class);
            try {
                cachedOfPlayerMethod = contextClass.getMethod("of", serverPlayerClass);
            } catch (NoSuchMethodException e) {
            }
            try {
                cachedOfSourceMethod = contextClass.getMethod("of", commandSourceClass);
            } catch (NoSuchMethodException e) {
            }
            cachedParseTextMethod = placeholdersClass.getMethod("parseText", Text.class, contextClass);
            cachedGetStringMethod = Text.class.getMethod("getString");

            papiEnabled = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            papiEnabled = false;
        }
    }

    public static boolean isPapiEnabled() {
        return papiEnabled;
    }

    public static String replace(Object contextSource, String text) {
        if (text == null || text.isEmpty()) return text;
        if (!papiEnabled) return text;

        try {
            Object context = null;
            if (contextSource instanceof MinecraftServer) {
                context = cachedOfServerMethod.invoke(null, contextSource);
            } else if (cachedOfPlayerMethod != null && contextSource != null && Class.forName("net.minecraft.server.network.ServerPlayerEntity").isInstance(contextSource)) {
                context = cachedOfPlayerMethod.invoke(null, contextSource);
            } else if (cachedOfSourceMethod != null && contextSource != null && Class.forName("net.minecraft.server.command.ServerCommandSource").isInstance(contextSource)) {
                context = cachedOfSourceMethod.invoke(null, contextSource);
            } else {
                MinecraftServer resolvedServer = Autotask.getInstance().getServer();
                if (resolvedServer != null) {
                    context = cachedOfServerMethod.invoke(null, resolvedServer);
                }
            }

            if (context == null) return text;

            Text literalText = Text.literal(text);
            Object parsedText = cachedParseTextMethod.invoke(null, literalText, context);
            return (String) cachedGetStringMethod.invoke(parsedText);
        } catch (Exception e) {
            return text;
        }
    }
}
