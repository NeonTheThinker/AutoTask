package owleaf;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class Test {
    private final AutoTasks plugin;

    public Test(AutoTasks plugin) {
        this.plugin = plugin;
    }

    public void createTestFile() {
        File testFile = new File(plugin.getAutotasksFolder(), "test.yml");
        if (testFile.exists()) return;

        try {
            testFile.createNewFile();
            YamlConfiguration config = new YamlConfiguration();

            config.set("acciones", Arrays.asList(
                    createAction("00:01:00",
                            "say ¡Tarea automática después de 1 minuto!",
                            "effect give @a speed 30 0"),
                    createAction("00:05:00",
                            "say ¡Han pasado 5 minutos!",
                            "weather clear"),
                    createAction("00:10:00",
                            "say ¡Tarea completada después de 10 minutos!",
                            "time set day")
            ));

            config.save(testFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error creando test.yml: " + e.getMessage());
        }
    }

    private Map<String, Object> createAction(String delay, String... commands) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("delay", delay);
        action.put("comandos", Arrays.asList(commands));
        return action;
    }
}