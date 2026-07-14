package me.neonthethinker.autotask.utils;

import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YamlConfig {
    private Map<String, Object> data = new HashMap<>();
    private boolean hasSyntaxError = false;

    public boolean hasSyntaxError() {
        return hasSyntaxError;
    }

    @SuppressWarnings("unchecked")
    public static YamlConfig loadConfiguration(File file) {
        YamlConfig config = new YamlConfig();
        if (!file.exists()) return config;
        try (InputStream is = new FileInputStream(file)) {
            Yaml yaml = new Yaml();
            Object obj = yaml.load(is);
            if (obj instanceof Map) {
                config.data = (Map<String, Object>) obj;
            }
        } catch (Exception e) {
            config.hasSyntaxError = true;
            e.printStackTrace();
        }
        return config;
    }

    public boolean getBoolean(String key, boolean def) {
        Object val = data.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val instanceof String) {
            return Boolean.parseBoolean((String) val);
        }
        return def;
    }

    public String getString(String key, String def) {
        Object val = data.get(key);
        return val != null ? val.toString() : def;
    }

    @SuppressWarnings("unchecked")
    public List<Map<?, ?>> getMapList(String key) {
        Object val = data.get(key);
        if (val instanceof List) {
            List<Map<?, ?>> list = new ArrayList<>();
            for (Object item : (List<?>) val) {
                if (item instanceof Map) {
                    list.add((Map<?, ?>) item);
                }
            }
            return list;
        }
        return new ArrayList<>();
    }
}
