package pl.bongo.rankings;

import java.math.*;

public final class Formats {
    private Formats() {}
    public static String value(Metric metric, long value, String locale) {
        value = Math.max(0, value);
        if (metric == Metric.PLAY_TIME) {
            long minutes = value / 1200;
            return Lang.text(locale, "time", minutes / 60, minutes % 60);
        }
        if (metric == Metric.DISTANCE) {
            return value >= 100_000 ? decimal(value, 100_000, locale) + " km" : decimal(value, 100, locale) + " m";
        }
        return compact(value, locale);
    }
    public static String compact(long value, String locale) {
        if (value >= 1_000_000) return decimal(value, 1_000_000, locale) + " " + Lang.text(locale, "million");
        if (value >= 1_000) return decimal(value, 1_000, locale) + " " + Lang.text(locale, "thousand");
        return Long.toString(value);
    }
    private static String decimal(long value, long divisor, String locale) {
        String s = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor), 1, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
        return Lang.polish(locale) ? s.replace('.', ',') : s;
    }
    public static long xpAtLevel(int level) {
        double l = Math.max(0, level);
        double xp = l <= 16 ? l*l + 6*l : l <= 31 ? 2.5*l*l - 40.5*l + 360 : 4.5*l*l - 162.5*l + 2220;
        return (long)Math.min(Long.MAX_VALUE, xp);
    }
    public static long xp(int level, float progress) {
        long next = xpAtLevel(level == Integer.MAX_VALUE ? level : level + 1) - xpAtLevel(level);
        return Metric.add(xpAtLevel(level), Math.max(0, Math.round((double)Math.max(0, Math.min(1, progress)) * next)));
    }
}
