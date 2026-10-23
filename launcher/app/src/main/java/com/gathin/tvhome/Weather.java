package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * The temperature next to the clock (MyHome): Open-Meteo's current weather, fetched on a plain thread at most every
 * 30 minutes and only when Home asks, i.e. while it is showing. The last answer is kept so Home has it at once.
 */
final class Weather {

    static final long REFRESH_MS = 30 * 60_000L;
    private static final String PREFS = "weather";

    private Weather() {
    }

    /** Hands the cached reading to done at once (if any), then a fresh one when the cache is older than REFRESH_MS. */
    static void update(Context ctx, double lat, double lon, BiConsumer<Integer, Integer> done) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.contains("temp")) done.accept(p.getInt("temp", 0), p.getInt("code", 0));
        if (System.currentTimeMillis() - p.getLong("at", 0) < REFRESH_MS) return;
        Handler main = new Handler(Looper.getMainLooper());
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                        + "&current=temperature_2m,weather_code,is_day&timezone=auto", lat, lon);
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] b = new byte[4096];
                    for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
                    JSONObject cur = new JSONObject(buf.toString("UTF-8")).getJSONObject("current");
                    int temp = (int) Math.round(cur.getDouble("temperature_2m"));
                    int code = cur.getInt("weather_code") + (cur.optInt("is_day", 1) == 0 ? 1000 : 0);
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putInt("temp", temp).putInt("code", code).putLong("at", System.currentTimeMillis()).apply();
                    main.post(() -> done.accept(temp, code));
                } finally {
                    c.disconnect();
                }
            } catch (Exception ignored) {
                // Offline or Open-Meteo down: keep showing the last reading, try again on the next visit.
            }
        }, "weather").start();
    }

    /** A thin outline icon for a WMO weather code (plus 1000 at night), in white, drawn at any size. */
    static final class Icon extends Drawable {
        private static final int SUN = 0, MOON = 1, CLOUD = 2, PARTLY = 3, RAIN = 4, STORM = 5, SNOW = 6, FOG = 7;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int kind;
        private final boolean night;

        Icon(int code, float strokePx) {
            night = code >= 1000;
            int c = code % 1000;
            if (c == 0) kind = night ? MOON : SUN;
            else if (c <= 2) kind = PARTLY;
            else if (c == 3) kind = CLOUD;
            else if (c == 45 || c == 48) kind = FOG;
            else if (c >= 95) kind = STORM;
            else if ((c >= 71 && c <= 77) || c == 85 || c == 86) kind = SNOW;
            else kind = RAIN;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokePx);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(0xFFFFFFFF);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()), x = b.left, y = b.top;
            switch (kind) {
                case SUN: sun(c, x + s * 0.5f, y + s * 0.5f, s * 0.2f, s * 0.36f); break;
                case MOON: moon(c, x + s * 0.5f, y + s * 0.5f, s * 0.32f); break;
                case PARTLY:
                    if (night) moon(c, x + s * 0.38f, y + s * 0.36f, s * 0.2f);
                    else sun(c, x + s * 0.36f, y + s * 0.36f, s * 0.13f, s * 0.24f);
                    cloud(c, x + s * 0.12f, y + s * 0.4f, s * 0.82f); break;
                case FOG:
                    for (int i = 0; i < 3; i++) c.drawLine(x + s * (0.14f + 0.06f * (i % 2)), y + s * (0.36f + i * 0.16f),
                            x + s * (0.86f - 0.06f * ((i + 1) % 2)), y + s * (0.36f + i * 0.16f), paint);
                    break;
                default:
                    cloud(c, x + s * 0.08f, y + s * 0.12f, s * 0.84f);
                    if (kind == RAIN) for (int i = 0; i < 3; i++) c.drawLine(x + s * (0.32f + i * 0.18f), y + s * 0.74f,
                            x + s * (0.27f + i * 0.18f), y + s * 0.9f, paint);
                    if (kind == SNOW) for (int i = 0; i < 3; i++) c.drawPoint(x + s * (0.3f + i * 0.2f), y + s * (0.8f + (i % 2) * 0.08f), paint);
                    if (kind == STORM) {
                        Path bolt = new Path();
                        bolt.moveTo(x + s * 0.54f, y + s * 0.64f);
                        bolt.lineTo(x + s * 0.44f, y + s * 0.8f);
                        bolt.lineTo(x + s * 0.56f, y + s * 0.8f);
                        bolt.lineTo(x + s * 0.46f, y + s * 0.96f);
                        c.drawPath(bolt, paint);
                    }
            }
        }

        private void sun(Canvas c, float cx, float cy, float r, float ray) {
            c.drawCircle(cx, cy, r, paint);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                c.drawLine(cx + cos * (r + (ray - r) * 0.35f), cy + sin * (r + (ray - r) * 0.35f), cx + cos * ray, cy + sin * ray, paint);
            }
        }

        private void moon(Canvas c, float cx, float cy, float r) {
            Path p = new Path();
            p.addArc(new RectF(cx - r, cy - r, cx + r, cy + r), 70, 260);
            p.arcTo(new RectF(cx - r * 0.2f, cy - r * 0.95f, cx + r * 1.1f, cy + r * 0.6f), 230, -150);
            c.drawPath(p, paint);
        }

        /** A cloud in a box w wide and w*0.55 tall, its top-left at (x, y). */
        private void cloud(Canvas c, float x, float y, float w) {
            float h = w * 0.55f;
            Path p = new Path();
            p.moveTo(x + w * 0.22f, y + h);
            p.arcTo(new RectF(x, y + h * 0.42f, x + w * 0.44f, y + h), 90, 180);
            p.arcTo(new RectF(x + w * 0.16f, y, x + w * 0.66f, y + h * 0.82f), 200, 150);
            p.arcTo(new RectF(x + w * 0.52f, y + h * 0.3f, x + w, y + h), 280, 170);
            p.close();
            c.drawPath(p, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
