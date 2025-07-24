package owleaf;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

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
                    createAction("00:00:10",
                            "say ¡Primer mensaje a los 10 segundos!",
                            "effect give @a speed 30 0"),
                    createAction("00:00:50",
                            "say ¡Este aparece a los 50 segundos!",
                            createSequence(
                                    createSequenceStep("00:00:05", "weather clear"),
                                    createSequenceStep("t200",
                                            "say ¡Ahora han pasado 60 segundos!",
                                            "time set day")
                            )),
                    createAction("00:01:30",
                            "say ¡Mensaje final a 90 segundos!"),
                    createStopAction("Tarea Finalizada")
            ));

            config.save(testFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error creando test.yml: " + e.getMessage());
        }
    }

    private Map<String, Object> createAction(String delay, Object... commands) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("delay", delay);

        List<Object> comandosList = new ArrayList<>();
        for (Object cmd : commands) {
            if (cmd instanceof Map) {
                comandosList.add(cmd);
            } else if (cmd instanceof String && !((String) cmd).isEmpty()) {
                comandosList.add(cmd);
            }
        }

        action.put("comandos", comandosList);
        return action;
    }

    private Map<String, Object> createSequence(Map<String, Object>... steps) {
        Map<String, Object> sequence = new LinkedHashMap<>();
        sequence.put("sequence", Arrays.asList(steps));
        return sequence;
    }

    private Map<String, Object> createSequenceStep(String delay, String... commands) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("delay", delay);

        if (commands.length == 1) {
            step.put("comandos", commands[0]);
        } else {
            step.put("comandos", Arrays.asList(commands));
        }

        return step;
    }

    private Map<String, Object> createStopAction(String message) {
        Map<String, Object> stopAction = new LinkedHashMap<>();
        stopAction.put("stop", message);
        return stopAction;
    }
}