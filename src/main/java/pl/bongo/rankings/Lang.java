package pl.bongo.rankings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Lang {
    private static final Properties EN = load("en"), PL = load("pl");
    private Lang() {}
    private static Properties load(String lang) {
        Properties p = new Properties();
        try (var stream = Lang.class.getResourceAsStream("/assets/bongo_rankings/lang/" + lang + ".properties")) {
            if (stream == null) throw new IllegalStateException("Missing language " + lang);
            p.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
        return p;
    }
    public static boolean polish(String locale) {
        return locale != null && (locale.equalsIgnoreCase("pl") || locale.toLowerCase(Locale.ROOT).startsWith("pl_"));
    }
    public static String text(String locale, String key, Object... args) {
        String fallback = EN.getProperty(key);
        if (fallback == null) throw new IllegalArgumentException("Missing English translation: " + key);
        return String.format(Locale.ROOT, polish(locale) ? PL.getProperty(key, fallback) : fallback, args);
    }
    public static Set<String> keys(boolean polish) { return (polish ? PL : EN).stringPropertyNames(); }
}
