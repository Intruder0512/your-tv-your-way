package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.util.LruCache;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The mood cards ("Don't have time to browse?", my_home): moods, the titles in each and how to open them, read from
 * assets/moods/moods.json. Posters (2:3) and backdrops (16:9, already black and white and darkened by
 * work/make_moods.py) sit beside it. A title whose app is not installed and enabled is left out.
 */
final class MoodDeck {

    static final class Title {
        final String name, pkg, link, poster, backdrop;

        Title(String name, String pkg, String link, String poster, String backdrop) {
            this.name = name;
            this.pkg = pkg;
            this.link = link;
            this.poster = poster;
            this.backdrop = backdrop;
        }
    }

    static final class Mood {
        final String name;
        final int color;
        final List<Title> titles = new ArrayList<>();

        Mood(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

    final List<Mood> moods = new ArrayList<>();
    private final Context ctx;
    /** Posters at reel size, RGB_565: about 270 KB each, so the whole set fits. */
    private final LruCache<String, Bitmap> posters = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };
    private final LruCache<String, Bitmap> backdrops = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };

    private MoodDeck(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** The deck, or null when there is no moods.json or no mood has a playable title. */
    static MoodDeck load(Context ctx) {
        MoodDeck deck = new MoodDeck(ctx);
        try (InputStream in = ctx.getAssets().open("moods/moods.json")) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            JSONObject root = new JSONObject(buf.toString("UTF-8"));
            List<Title> all = new ArrayList<>();
            JSONArray moods = root.getJSONArray("moods");
            JSONArray titles = root.getJSONArray("titles");
            for (int i = 0; i < moods.length(); i++) {
                JSONObject m = moods.getJSONObject(i);
                deck.moods.add(new Mood(m.getString("name"), 0xFF000000 | Color.parseColor(m.getString("color"))));
            }
            for (int i = 0; i < titles.length(); i++) {
                JSONObject t = titles.getJSONObject(i);
                if (!deck.installed(t.getString("pkg"))) continue;
                Title title = new Title(t.getString("title"), t.getString("pkg"), t.optString("link"),
                        t.getString("poster"), t.getString("backdrop"));
                all.add(title);
                JSONArray in2 = t.getJSONArray("moods");
                for (int j = 0; j < in2.length(); j++) {
                    for (Mood m : deck.moods) if (m.name.equals(in2.getString(j))) m.titles.add(title);
                }
            }
            for (int i = 0; i < moods.length(); i++) {
                if (moods.getJSONObject(i).optBoolean("all")) deck.moods.get(i).titles.addAll(all);
            }
        } catch (Exception e) {
            return null;
        }
        deck.moods.removeIf(m -> m.titles.isEmpty());
        return deck.moods.isEmpty() ? null : deck;
    }

    private boolean installed(String pkg) {
        try {
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    Bitmap poster(Title t) {
        Bitmap b = posters.get(t.poster);
        if (b == null) {
            b = decode("moods/posters/" + t.poster, 2);
            if (b != null) posters.put(t.poster, b);
        }
        return b;
    }

    Bitmap backdrop(Title t) {
        Bitmap b = backdrops.get(t.backdrop);
        if (b == null) {
            b = decode("moods/backdrops/" + t.backdrop, 1);
            if (b != null) backdrops.put(t.backdrop, b);
        }
        return b;
    }

    private Bitmap decode(String asset, int sample) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        o.inSampleSize = sample;
        try (InputStream in = ctx.getAssets().open(asset)) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception e) {
            return null;
        }
    }

    /** Opens the title in its app: its own page through the link, else the app's start screen. */
    void open(Context from, Title t) {
        if (t.link != null && !t.link.isEmpty()) {
            try {
                // CLEAR_TASK: an app already running (SonyLIV did) otherwise comes back on its last page and
                // ignores the link.
                from.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(t.link)).setPackage(t.pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // fall back to the app itself
            }
        }
        PackageManager pm = from.getPackageManager();
        Intent app = pm.getLeanbackLaunchIntentForPackage(t.pkg);
        if (app == null) app = pm.getLaunchIntentForPackage(t.pkg);
        if (app == null) {
            Toast.makeText(from, from.getString(R.string.cant_open, t.name), Toast.LENGTH_SHORT).show();
            return;
        }
        from.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
