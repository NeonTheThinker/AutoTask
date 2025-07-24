package owleaf;

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
        if (partes.length != 3) return 0;

        try {
            int horas = Integer.parseInt(partes[0]);
            int minutos = Integer.parseInt(partes[1]);
            int segundos = Integer.parseInt(partes[2]);
            return (horas * 72000L) + (minutos * 1200L) + (segundos * 20L);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String ticksToTime(long ticks) {
        long totalSegundos = ticks / 20;
        long horas = totalSegundos / 3600;
        long minutos = (totalSegundos % 3600) / 60;
        long segundos = totalSegundos % 60;

        return String.format("%02d:%02d:%02d", horas, minutos, segundos);
    }
}