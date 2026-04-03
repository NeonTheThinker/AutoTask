package com.neonthethinker.autotask.task.handler;

import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import com.neonthethinker.autotask.AutoTasks;
import com.neonthethinker.autotask.task.TaskManager;
import com.neonthethinker.autotask.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CicloHandler {

    private final AutoTasks plugin;
    private static final Pattern CONDICION_PATTERN = Pattern.compile("(.+?)(==|!=|>=|<=|>|<)(.+)");
    private static final Pattern MODULO_CONDICION_PATTERN = Pattern.compile("(.+?)%(.+?)(==|!=)(.+)");


    public CicloHandler(AutoTasks plugin) {
        this.plugin = plugin;
    }

    public void programar(BukkitScheduler scheduler, Map<?, ?> cicloMap, long scheduledTicks, List<BukkitTask> currentTasks, String targetWorld) {
        String pasoDelayStr = (String) cicloMap.get("paso_delay");
        List<?> comandosPlantilla = (List<?>) cicloMap.get("comandos");
        List<Map<?, ?>> variantes = (List<Map<?, ?>>) cicloMap.get("variantes");

        Object pasosObj = cicloMap.get("pasos");
        int pasosFijos = -1;
        if (pasosObj instanceof Number) {
            pasosFijos = ((Number) pasosObj).intValue();
        }

        if (pasoDelayStr == null || comandosPlantilla == null) {
            plugin.getLogger().warning("Acción de ciclo mal configurada (falta paso_delay o comandos)");
            return;
        }

        if (variantes == null || variantes.isEmpty()) {
            if (pasosFijos > 0) {
                programarCicloSimple(scheduler, comandosPlantilla, scheduledTicks, TimeUtils.parseTimeToTicks(pasoDelayStr), pasosFijos, currentTasks, targetWorld);
            } else {
                plugin.getLogger().warning("Ciclo sin 'variantes' y sin 'pasos' definidos");
            }
            return;
        }

        long pasoDelayTicks = TimeUtils.parseTimeToTicks(pasoDelayStr);

        List<String> placeholders = new ArrayList<>();
        List<List<String>> listasDeValores = new ArrayList<>();
        List<Integer> cadaN = new ArrayList<>();
        List<String> modoRangos = new ArrayList<>();
        List<Boolean> esAleatorio = new ArrayList<>();
        int maxSteps = 0;

        for (Map<?, ?> variante : variantes) {
            String variable = (String) variante.get("variable");
            String rango = (String) variante.get("rango");
            String rangoAleatorio = (String) variante.get("rango_aleatorio");
            String modoRango = (String) variante.get("modo_rango");
            if (modoRango == null) {
                modoRango = "loop";
            }
            Object cadaObj = variante.get("cada");
            int cada = 1;
            if (cadaObj instanceof Number) {
                cada = ((Number) cadaObj).intValue();
            }
            if (variable == null || (rango == null && rangoAleatorio == null)) continue;
            placeholders.add("%" + variable + "%");
            cadaN.add(Math.max(1, cada));
            modoRangos.add(modoRango);
            List<String> valores;
            if (rangoAleatorio != null) {
                esAleatorio.add(true);
                valores = parseRango(rangoAleatorio);
            } else {
                esAleatorio.add(false);
                valores = parseRango(rango);
                if (valores.size() > maxSteps) {
                    maxSteps = valores.size();
                }
            }
            if (valores.isEmpty()) {
                valores.add("");
            }
            listasDeValores.add(valores);
        }
        if (pasosFijos > 0) {
            maxSteps = pasosFijos;
        }
        if (maxSteps == 0) return;

        long currentLoopDelay = scheduledTicks;
        final String maxStepsStr = String.valueOf(maxSteps);

        for (int i = 0; i < maxSteps; i++) {
            if (currentLoopDelay < 0) {
                currentLoopDelay += pasoDelayTicks;
                continue;
            }
            final long finalDelay = currentLoopDelay;

            final String pasoStr = String.valueOf(i + 1);
            final String iStr = String.valueOf(i);

            for (Object cmdPlantillaObj : comandosPlantilla) {

                String comandoPaso;
                String condicion = null;
                int veces = 1;
                boolean esBucle = false;

                if (cmdPlantillaObj instanceof String) {
                    comandoPaso = (String) cmdPlantillaObj;
                } else if (cmdPlantillaObj instanceof Map) {
                    Map<?, ?> cmdMap = (Map<?, ?>) cmdPlantillaObj;
                    if (cmdMap.containsKey("bucle")) {
                        Map<?, ?> bucleMap = (Map<?, ?>) cmdMap.get("bucle");
                        comandoPaso = (String) bucleMap.get("comando");
                        condicion = (String) bucleMap.get("si");
                        Object vecesObj = bucleMap.get("veces");
                        if (vecesObj != null) {
                            String vecesStr = reemplazarTodasVariables(vecesObj.toString(), i, pasoStr, iStr, maxStepsStr, placeholders, listasDeValores, cadaN, modoRangos, esAleatorio);
                            try {
                                veces = Integer.parseInt(vecesStr);
                            } catch (NumberFormatException e) {
                                plugin.getLogger().warning("Valor de 'veces' inválido en bucle: " + vecesStr);
                                veces = 0;
                            }
                        } else {
                            veces = 0;
                        }
                    } else if (cmdMap.containsKey("comando")) {
                        comandoPaso = (String) cmdMap.get("comando");
                        condicion = (String) cmdMap.get("si");
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
                if (comandoPaso == null || veces < 1) continue;
                if (condicion != null) {
                    condicion = reemplazarTodasVariables(condicion, i, pasoStr, iStr, maxStepsStr, placeholders, listasDeValores, cadaN, modoRangos, esAleatorio);
                    if (!evaluarCondicion(condicion)) {
                        continue;
                    }
                }

                for (int k = 0; k < veces; k++) {
                    String comandoReal = reemplazarTodasVariables(comandoPaso, i, pasoStr, iStr, maxStepsStr, placeholders, listasDeValores, cadaN, modoRangos, esAleatorio);

                    currentTasks.add(scheduler.runTaskLater(plugin, () ->
                                    TaskManager.dispatchCommand(comandoReal, targetWorld),
                            finalDelay));
                }
            }
            currentLoopDelay += pasoDelayTicks;
        }
    }

    public long calcularMaxDelay(long scheduledDelay, Map<?, ?> cicloMap) {
        String pasoDelayStr = (String) cicloMap.get("paso_delay");
        if (pasoDelayStr == null) return 0;
        int maxSteps = 0;
        Object pasosObj = cicloMap.get("pasos");
        int pasosFijos = -1;
        if (pasosObj instanceof Number) {
            pasosFijos = ((Number) pasosObj).intValue();
        }
        if (pasosFijos > 0) {
            maxSteps = pasosFijos;
        } else {
            List<Map<?, ?>> variantes = (List<Map<?, ?>>) cicloMap.get("variantes");
            if (variantes != null) {
                for (Map<?, ?> variante : variantes) {
                    String rango = (String) variante.get("rango");
                    if (rango == null) continue;
                    List<String> valores = parseRango(rango);
                    if (valores.size() > maxSteps) {
                        maxSteps = valores.size();
                    }
                }
            }
        }
        if (maxSteps > 0) {
            long pasoDelayTicks = TimeUtils.parseTimeToTicks(pasoDelayStr);
            long duracionCiclo = (long)(maxSteps - 1) * pasoDelayTicks;
            return scheduledDelay + duracionCiclo;
        }
        return scheduledDelay;
    }

    private void programarCicloSimple(BukkitScheduler scheduler, List<?> comandos, long startDelayTicks, long pasoDelayTicks, int pasos, List<BukkitTask> taskList, String targetWorld) {
        long currentLoopDelay = startDelayTicks;
        final String maxStepsStr = String.valueOf(pasos);

        for (int i = 0; i < pasos; i++) {
            if (currentLoopDelay < 0) {
                currentLoopDelay += pasoDelayTicks;
                continue;
            }
            final long finalDelay = currentLoopDelay;

            final String pasoStr = String.valueOf(i + 1);
            final String iStr = String.valueOf(i);

            for (Object comandoObj : comandos) {

                String comandoPaso;
                String condicion = null;
                int veces = 1;
                boolean esBucle = false;
                if (comandoObj instanceof String) {
                    comandoPaso = (String) comandoObj;
                } else if (comandoObj instanceof Map) {
                    Map<?, ?> cmdMap = (Map<?, ?>) comandoObj;
                    if (cmdMap.containsKey("bucle")) {
                        Map<?, ?> bucleMap = (Map<?, ?>) cmdMap.get("bucle");
                        comandoPaso = (String) bucleMap.get("comando");
                        condicion = (String) bucleMap.get("si");
                        Object vecesObj = bucleMap.get("veces");
                        if (vecesObj != null) {
                            String vecesStr = vecesObj.toString()
                                    .replace("%paso%", pasoStr)
                                    .replace("%i%", iStr)
                                    .replace("%pasos_totales%", maxStepsStr);
                            try {
                                veces = Integer.parseInt(vecesStr);
                            } catch (NumberFormatException e) {
                                plugin.getLogger().warning("Valor de 'veces' inválido en bucle: " + vecesStr);
                                veces = 0;
                            }
                        } else {
                            veces = 0;
                        }
                    } else if (cmdMap.containsKey("comando")) {
                        comandoPaso = (String) cmdMap.get("comando");
                        condicion = (String) cmdMap.get("si");
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
                if (comandoPaso == null || veces < 1) continue;
                if (condicion != null) {
                    condicion = condicion
                            .replace("%paso%", pasoStr)
                            .replace("%i%", iStr)
                            .replace("%pasos_totales%", maxStepsStr);
                    if (!evaluarCondicion(condicion)) {
                        continue;
                    }
                }

                for (int k = 0; k < veces; k++) {
                    String comandoReal = comandoPaso
                            .replace("%paso%", pasoStr)
                            .replace("%i%", iStr)
                            .replace("%pasos_totales%", maxStepsStr);

                    taskList.add(scheduler.runTaskLater(plugin, () ->
                                    TaskManager.dispatchCommand(comandoReal, targetWorld),
                            finalDelay));
                }
            }
            currentLoopDelay += pasoDelayTicks;
        }
    }

    private String obtenerValorVariante(int varianteIndex, int i,
                                        List<String> placeholders, List<List<String>> listasDeValores,
                                        List<Integer> cadaN, List<String> modoRangos, List<Boolean> esAleatorio) {

        List<String> listaValores = listasDeValores.get(varianteIndex);
        int listaSize = listaValores.size();
        if (listaSize == 0) return "";
        String valor;
        if (esAleatorio.get(varianteIndex)) {
            valor = listaValores.get(ThreadLocalRandom.current().nextInt(listaSize));
        } else {
            int cada = cadaN.get(varianteIndex);
            String modo = modoRangos.get(varianteIndex);
            int effectiveStep = (i / cada);
            int index;
            if (modo.equals("clamp")) {
                index = Math.min(effectiveStep, listaSize - 1);
            } else if (modo.equals("pingpong")) {
                int pingPongSize = (listaSize - 1) * 2;
                if (pingPongSize <= 0) {
                    index = 0;
                } else {
                    int mod = effectiveStep % pingPongSize;
                    if (mod < listaSize) {
                        index = mod;
                    } else {
                        index = pingPongSize - mod;
                    }
                }
            } else {
                index = effectiveStep % listaSize;
            }
            valor = listaValores.get(index);
        }
        return valor;
    }
    private String reemplazarTodasVariables(String texto, int i, String pasoStr, String iStr, String maxStepsStr,
                                            List<String> placeholders, List<List<String>> listasDeValores,
                                            List<Integer> cadaN, List<String> modoRangos, List<Boolean> esAleatorio) {

        texto = texto.replace("%paso%", pasoStr)
                .replace("%i%", iStr)
                .replace("%pasos_totales%", maxStepsStr);
        for (int j = 0; j < placeholders.size(); j++) {
            String placeholder = placeholders.get(j);
            if (texto.contains(placeholder)) {
                String valor = obtenerValorVariante(j, i, placeholders, listasDeValores, cadaN, modoRangos, esAleatorio);
                texto = texto.replace(placeholder, valor);
            }
        }
        return texto;
    }
    private List<String> parseRango(String rango) {
        List<String> valores = new ArrayList<>();
        if (rango == null || rango.isEmpty()) {
            return valores;
        }
        if (rango.contains(",")) {
            for (String item : rango.split(",")) {
                valores.add(item.trim());
            }
        } else if (rango.contains("-")) {
            try {
                String[] parts = rango.split("-");
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                if (start > end) {
                    for (int k = start; k >= end; k--) {
                        valores.add(String.valueOf(k));
                    }
                } else {
                    for (int k = start; k <= end; k++) {
                        valores.add(String.valueOf(k));
                    }
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                plugin.getLogger().warning("Formato de rango inválido en ciclo: " + rango);
            }
        } else {
            valores.add(rango.trim());
        }
        return valores;
    }
    private boolean evaluarCondicion(String condicion) {
        if (condicion == null || condicion.isEmpty()) {
            return false;
        }
        try {
            Matcher modMatcher = MODULO_CONDICION_PATTERN.matcher(condicion);
            if (modMatcher.find()) {
                int left = Integer.parseInt(modMatcher.group(1).trim());
                int mod = Integer.parseInt(modMatcher.group(2).trim());
                String op = modMatcher.group(3).trim();
                int right = Integer.parseInt(modMatcher.group(4).trim());
                int result = left % mod;
                if (op.equals("==")) {
                    return result == right;
                } else if (op.equals("!=")) {
                    return result != right;
                }
            }
            Matcher matcher = CONDICION_PATTERN.matcher(condicion);
            if (matcher.find()) {
                String left = matcher.group(1).trim();
                String op = matcher.group(2).trim();
                String right = matcher.group(3).trim();
                try {
                    double leftNum = Double.parseDouble(left);
                    double rightNum = Double.parseDouble(right);
                    switch (op) {
                        case "==": return leftNum == rightNum;
                        case "!=": return leftNum != rightNum;
                        case ">":  return leftNum > rightNum;
                        case "<":  return leftNum < rightNum;
                        case ">=": return leftNum >= rightNum;
                        case "<=": return leftNum <= rightNum;
                    }
                } catch (NumberFormatException e) {
                    if (op.equals("==")) {
                        return left.equals(right);
                    }
                    if (op.equals("!=")) {
                        return !left.equals(right);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error al evaluar condición de ciclo: " + condicion);
            return false;
        }
        return false;
    }
}