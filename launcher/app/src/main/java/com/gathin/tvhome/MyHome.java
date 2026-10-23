package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This TV's own home screen (res/values/my_home.xml): floating white logos in the dock with a tagline and a glow in
 * the app's colour, the time alone in the corner, bare top icons, More apps, a wallpaper-only screensaver and the
 * weather. Kept apart from Config so the stock launcher's code paths stay as they were when my_home is false.
 */
final class MyHome {

    /** A dock item that switches the TV to an input instead of opening an app (my_dock_inputs). */
    static final class DockInput {
        final String label, inputId, logo;
        final int color;

        DockInput(String label, String inputId, String logo, int color) {
            this.label = label;
            this.inputId = inputId;
            this.logo = logo;
            this.color = color;
        }
    }

    final boolean on;
    final Map<String, String> logos;
    final Map<String, Integer> colors;
    final List<DockInput> inputs;
    final Map<String, String[]> taglines;
    final int screensaverMinutes;
    /** Open-Meteo position, or NaN for no weather. */
    final double latitude, longitude;

    private MyHome(boolean on, Map<String, String> logos, Map<String, Integer> colors, List<DockInput> inputs,
                   Map<String, String[]> taglines, int screensaverMinutes, double latitude, double longitude) {
        this.on = on;
        this.logos = logos;
        this.colors = colors;
        this.inputs = inputs;
        this.taglines = taglines;
        this.screensaverMinutes = screensaverMinutes;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    static MyHome load(Context ctx) {
        Resources res = ctx.getResources();
        if (!res.getBoolean(R.bool.my_home)) {
            return new MyHome(false, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(),
                    Collections.emptyMap(), 0, Double.NaN, Double.NaN);
        }
        Map<String, String> logos = pairs(res.getStringArray(R.array.my_dock_logos));
        Map<String, Integer> colors = new HashMap<>();
        for (Map.Entry<String, String> e : pairs(res.getStringArray(R.array.my_dock_colors)).entrySet()) {
            Integer c = color(e.getValue());
            if (c != null) colors.put(e.getKey(), c);
        }
        List<DockInput> inputs = new ArrayList<>();
        for (String line : res.getStringArray(R.array.my_dock_inputs)) {
            String[] f = line.split("\\|");
            if (f.length < 4) continue;
            Integer c = color(f[3].trim());
            inputs.add(new DockInput(f[0].trim(), f[1].trim(), f[2].trim(), c != null ? c : 0xFF444444));
        }
        Map<String, String[]> taglines = new HashMap<>();
        for (Map.Entry<String, String> e : pairs(res.getStringArray(R.array.my_taglines)).entrySet()) {
            taglines.put(e.getKey(), e.getValue().split("\\|"));
        }
        return new MyHome(true, logos, colors, inputs, taglines, res.getInteger(R.integer.my_screensaver_minutes),
                number(res.getString(R.string.my_weather_latitude)), number(res.getString(R.string.my_weather_longitude)));
    }

    /**
     * The wallpaper on screen as a look name: the picture WallpaperPlayer last showed ("2-batman-superman.jpg"),
     * without its order number and extension ("batman-superman"); null before the first one.
     */
    static String look(Context ctx) {
        String image = ctx.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE).getString("image", "");
        if (image.isEmpty()) return null;
        int dot = image.lastIndexOf('.');
        return (dot > 0 ? image.substring(0, dot) : image).replaceFirst("^\\d+-", "");
    }

    boolean weather() {
        return on && !Double.isNaN(latitude) && !Double.isNaN(longitude);
    }

    /**
     * The tagline for this dock item on the visit-th return to Home, or null when it has none. Lines written for the
     * wallpaper now showing ("look/key", see {@link #look}) win over the plain ones.
     */
    String tagline(String look, String key, int visit) {
        String[] lines = key == null ? null : look != null && taglines.containsKey(look + "/" + key)
                ? taglines.get(look + "/" + key) : taglines.get(key);
        if (lines == null || lines.length == 0) return null;
        return lines[Math.floorMod(visit, lines.length)].trim();
    }

    /** "key=value" lines; the first '=' splits, so values may hold more. */
    private static Map<String, String> pairs(String[] lines) {
        Map<String, String> map = new HashMap<>();
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return map;
    }

    private static Integer color(String s) {
        try {
            return 0xFF000000 | Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double number(String s) {
        try {
            return s == null || s.trim().isEmpty() ? Double.NaN : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
