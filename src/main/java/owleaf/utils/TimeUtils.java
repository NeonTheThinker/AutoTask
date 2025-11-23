package owleaf.utils;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class TimeUtils {
    public static long parseTimeToTicks(String tiempo) {
        if (tiempo == null || tiempo.trim().isEmpty()) return 0;

        if (tiempo.startsWith("t")) {
            try {
                return Long.parseLong(tiempo.substring(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        String[] partes = tiempo.split(":");
        if (partes.length == 3) {
            try {
                int horas = Integer.parseInt(partes[0]);
                int minutos = Integer.parseInt(partes[1]);
                int segundos = Integer.parseInt(partes[2]);
                return (horas * 72000L) + (minutos * 1200L) + (segundos * 20L);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        return 0;
    }

    public static boolean isSystemTimeFormat(String tiempo) {
        return tiempo != null && tiempo.matches("^\\d{1,2}:\\d{2}:\\d{2}$");
    }

    public static long getSystemTimeDelay(String tiempo, String utcOffset) {
        if (!isSystemTimeFormat(tiempo)) return 0;

        try {
            LocalTime targetTime = LocalTime.parse(tiempo, DateTimeFormatter.ofPattern("H:mm:ss"));

            ZonedDateTime currentTime = applyUTCOffset(ZonedDateTime.now(), utcOffset);
            LocalTime currentLocalTime = currentTime.toLocalTime();

            long targetSeconds = targetTime.toSecondOfDay();
            long currentSeconds = currentLocalTime.toSecondOfDay();

            if (targetSeconds >= currentSeconds) {
                return (targetSeconds - currentSeconds) * 20;
            } else {
                return -1;
            }
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private static ZonedDateTime applyUTCOffset(ZonedDateTime dateTime, String utcOffset) {
        if (utcOffset == null || utcOffset.isEmpty()) {
            return dateTime;
        }

        try {
            boolean positive = !utcOffset.startsWith("-");
            String cleanOffset = utcOffset.replace("+", "").replace("-", "");
            String[] parts = cleanOffset.split(":");

            int hours = Integer.parseInt(parts[0]);
            int minutes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

            long totalMinutes = hours * 60 + minutes;
            if (!positive) {
                totalMinutes = -totalMinutes;
            }

            return dateTime.minus(totalMinutes, ChronoUnit.MINUTES);

        } catch (Exception e) {
            return dateTime;
        }
    }

    public static String ticksToTime(long ticks) {
        long totalSegundos = ticks / 20;
        long horas = totalSegundos / 3600;
        long minutos = (totalSegundos % 3600) / 60;
        long segundos = totalSegundos % 60;

        return String.format("%02d:%02d:%02d", horas, minutos, segundos);
    }

    public static String getCurrentTimeWithOffset(String utcOffset) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime adjusted = applyUTCOffset(now, utcOffset);

        return "Hora servidor: " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")) +
                " | Hora ajustada: " + adjusted.format(DateTimeFormatter.ofPattern("HH:mm:ss")) +
                " | UTC: " + utcOffset;
    }
}