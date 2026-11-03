package com.pool.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {

    private static final Properties PROPS = new Properties();
    private static boolean loaded = false;

    static {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                PROPS.load(in);
                loaded = true;
            }
        } catch (IOException e) {
            System.err.println("No se pudo leer config.properties: " + e.getMessage());
        }
    }

    private AppConfig() { }

    public static boolean isLoaded() { return loaded; }

    public static String getString(String key, String defaultValue) {
        return PROPS.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        String v = PROPS.getProperty(key);
        if (v == null) return defaultValue;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}