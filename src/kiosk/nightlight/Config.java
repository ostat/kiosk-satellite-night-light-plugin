// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import java.util.Locale;
import java.util.Map;

/** An immutable snapshot of the plugin settings. Read from any thread. */
final class Config {
    /** How the night light looks: one even colour, a soft glow, or slowly drifting blobs. */
    enum Style { FLAT, GLOW, LIQUID }

    /** The sounds the soother and the night light each choose from. */
    enum Sound {
        MUSIC_BOX, CLASSIC, DREAMY, WAVES, WHITE, PINK, BROWN, RAIN, STORM, NONE;

        boolean music() {
            return this == MUSIC_BOX || this == CLASSIC || this == DREAMY;
        }

        boolean noise() {
            return this == WHITE || this == PINK || this == BROWN || this == RAIN || this == STORM;
        }
    }

    final long colourMs;
    final long fadeMs;
    final int[] palette;
    final double secondsPerColour;
    final double colourBrightness;
    /** Liquid colour style: soft colour blobs that drift and blend during the soother, instead of one glow. */
    final boolean liquid;
    final int warmColor;
    final Style nightStyle;
    /** The colours the night light drifts very slowly through, or null for the one solid night light colour. */
    final int[] nightPalette;
    /** True when the night light drifts through nightPalette. */
    final boolean nightWaves;
    final double warmBrightness;
    /** The soother's sound. */
    final Sound sound;
    /** Soother volume, from 0 to 1. */
    final double volume;
    /** The night light's sound, chosen separately from the soother's. */
    final Sound nightSound;
    /** Night light volume, from 0 to 1, separate from the soother's. */
    final double nightVolume;
    final boolean countdown;
    /** Bed and up times in ms since local midnight. */
    final long bedMs;
    final long wakeMs;
    /**
     * Night mode, like the Clock screensaver's: when the light sensor reads at or below nightLux, the button, hint and
     * stars take nightColor, or hide with nightHide.
     */
    final boolean nightMode;
    final double nightLux;
    final int nightColor;
    final boolean nightHide;
    /** Keep the night light sound playing after the screensaver closes. */
    final boolean keepSound;
    /** Play the sound again when the plugin starts, if it was on. */
    final boolean resumeSound;
    /** Seconds the sound takes to fade in when it starts and out when it stops. */
    final double soundFade;
    /** The sound plays on its own; saved by the plugin so a restart can resume it. */
    final boolean soundOn;

    Config(Map<String, Object> s) {
        colourMs = (long) (number(s, "colourMinutes", 30, 1, 240) * 60_000L);
        fadeMs = (long) (number(s, "fadeMinutes", 3, 0.5, 30) * 60_000L);
        palette = Palettes.named(string(s, "palette", "Rainbow"));
        switch (string(s, "colourSpeed", "Slow")) {
            case "Very slow": secondsPerColour = 40; break;
            case "Gentle": secondsPerColour = 12; break;
            default: secondsPerColour = 22;
        }
        colourBrightness = number(s, "colourBrightness", 70, 10, 100) / 100.0;
        liquid = "Liquid".equals(string(s, "colourStyle", "Glow"));
        warmColor = color(s, "warmColor", 0xFF9440);
        switch (string(s, "nightStyle", "Glow")) {
            case "Flat": nightStyle = Style.FLAT; break;
            case "Liquid": nightStyle = Style.LIQUID; break;
            default: nightStyle = Style.GLOW;
        }
        String nightColours = string(s, "nightColours", "Solid");
        nightPalette = "Solid".equals(nightColours) ? null : Palettes.named(nightColours);
        nightWaves = nightPalette != null;
        warmBrightness = number(s, "warmBrightness", 25, 1, 100) / 100.0;
        sound = sound(string(s, "sound", "Music box lullaby"), Sound.MUSIC_BOX);
        volume = number(s, "volume", 40, 0, 100) / 100.0;
        // A new key: earlier versions saved values under sleepSound that are not choices any more.
        nightSound = sound(string(s, "nightSound", "No sound"), Sound.NONE);
        nightVolume = number(s, "nightVolume", 25, 0, 100) / 100.0;
        countdown = bool(s, "countdown", false);
        bedMs = time(s, "bedTime", "19:00");
        wakeMs = time(s, "wakeTime", "07:00");
        nightMode = bool(s, "nightMode", false);
        nightLux = number(s, "nightLux", 5, 1, 100);
        nightColor = color(s, "nightColor", 0xA63A32);
        nightHide = bool(s, "nightHide", false);
        keepSound = bool(s, "keepSound", false);
        resumeSound = bool(s, "resumeSound", false);
        soundFade = number(s, "soundFade", 2, 0, 60);
        soundOn = bool(s, "soundOn", false);
    }

    static Sound sound(String name, Sound fallback) {
        switch (name) {
            case "Music box lullaby": return Sound.MUSIC_BOX;
            case "Brahms & Mozart lullabies": return Sound.CLASSIC;
            case "Dreamy pads": return Sound.DREAMY;
            case "Ocean waves": return Sound.WAVES;
            case "White noise": return Sound.WHITE;
            case "Pink noise": return Sound.PINK;
            case "Brown noise": return Sound.BROWN;
            case "Rain": return Sound.RAIN;
            case "Thunderstorm": return Sound.STORM;
            case "No sound": return Sound.NONE;
            default: return fallback;
        }
    }

    static double number(Map<String, Object> s, String key, double fallback, double min, double max) {
        Object v = s == null ? null : s.get(key);
        double d = v instanceof Number ? ((Number) v).doubleValue() : fallback;
        if (Double.isNaN(d) || Double.isInfinite(d)) d = fallback;
        return Math.max(min, Math.min(max, d));
    }

    static String string(Map<String, Object> s, String key, String fallback) {
        Object v = s == null ? null : s.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    static boolean bool(Map<String, Object> s, String key, boolean fallback) {
        Object v = s == null ? null : s.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    static long time(Map<String, Object> s, String key, String fallback) {
        long t = parseTime(string(s, key, fallback));
        return t >= 0 ? t : parseTime(fallback);
    }

    /** Parses a time of day such as 19:00, 7:30pm or 7pm into ms since midnight, or -1. */
    static long parseTime(String v) {
        if (v == null) return -1;
        v = v.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        boolean pm = v.endsWith("pm");
        boolean am = v.endsWith("am");
        if (pm || am) v = v.substring(0, v.length() - 2);
        String[] parts = v.split("[:.]");
        if (parts.length > 2) return -1;
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
            if (am || pm) {
                if (hour < 1 || hour > 12) return -1;
                hour = hour % 12 + (pm ? 12 : 0);
            }
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return (hour * 60L + minute) * 60_000L;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Parses #RRGGBB (or #AARRGGBB, alpha ignored) into 0xRRGGBB. */
    static int color(Map<String, Object> s, String key, int fallback) {
        String v = string(s, key, null);
        if (v == null) return fallback;
        v = v.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() != 6 && v.length() != 8) return fallback;
        try {
            return (int) (Long.parseLong(v, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
