package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * "Don't have time to browse?": a deck of mood cards. Right brings up the next mood, Left the one before; OK spins
 * the top card's posters like a slot-machine reel that slows down and lands on one title, then counts down from
 * three and opens it. During the countdown OK opens it now; Left, Right or Back cancels.
 */
final class MoodDeckView extends FrameLayout {

    interface Listener {
        void onDeckFocus(boolean focused);
        void onDeckUp();
        /** A scene for behind the deck (already black and white and darkened), or null. */
        void onDeckBackdrop(Bitmap scene);
    }

    private static final int IDLE = 0, BUSY = 1, SPINNING = 2, COUNTDOWN = 3;
    private static final int COUNT_MS = 3000;

    private final MoodDeck deck;
    private final Listener listener;
    private final float dp;
    private final Random random = new Random();
    /** cards[0] is on top; 1 and 2 peek out behind it. */
    private final Card[] cards = new Card[3];
    private final View glow;
    private int top;
    private int state = IDLE;
    /** A handled key's release is swallowed as well. */
    private int swallowUp = -1;
    private MoodDeck.Title picked;
    private int countLeft;

    private final class Card {
        final FrameLayout view;
        final GradientDrawable bg;
        final ImageView[] posters = new ImageView[3];
        final TextView name, count, opening;
        final View bar;

        Card(Context ctx) {
            view = new FrameLayout(ctx);
            view.setClipChildren(false);
            bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0, 0});
            bg.setCornerRadius(28 * dp);
            view.setBackground(bg);
            view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            view.setElevation(12 * dp);
            for (int i = 0; i < 3; i++) {
                ImageView p = new ImageView(ctx);
                p.setScaleType(ImageView.ScaleType.CENTER_CROP);
                p.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View v, Outline o) {
                        o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 10 * dp);
                    }
                });
                p.setClipToOutline(true);
                p.setElevation((i == 1 ? 6 : 3) * dp);
                LayoutParams lp = new LayoutParams(Math.round(118 * dp), Math.round(177 * dp), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                lp.topMargin = Math.round(30 * dp);
                view.addView(p, lp);
                posters[i] = p;
            }
            name = text(ctx, 30, 0xFFFFFFFF);
            LayoutParams nl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP);
            nl.topMargin = Math.round(226 * dp);
            view.addView(name, nl);
            count = text(ctx, 14, 0xA6FFFFFF);
            LayoutParams cl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP);
            cl.topMargin = Math.round(272 * dp);
            view.addView(count, cl);
            opening = text(ctx, 15, 0xD9FFFFFF);
            opening.setVisibility(INVISIBLE);
            view.addView(opening, cl);
            bar = new View(ctx);
            GradientDrawable barBg = new GradientDrawable();
            barBg.setCornerRadius(2 * dp);
            bar.setBackground(barBg);
            LayoutParams bl = new LayoutParams(0, Math.round(4 * dp), Gravity.TOP | Gravity.START);
            bl.topMargin = Math.round(304 * dp);
            bl.leftMargin = Math.round(140 * dp);
            view.addView(bar, bl);
            bar.setVisibility(INVISIBLE);
        }

        void bind(MoodDeck.Mood m) {
            bg.setColors(new int[]{m.color, 0xFF0B0B10});   // opaque: the cards behind must not show through
            // Fanned: the first title in the middle, the second and third tilted out behind it, left and right.
            int[] titleFor = {1, 0, 2};
            for (int i = 0; i < 3; i++) {
                boolean shown = titleFor[i] < m.titles.size();
                posters[i].setImageBitmap(shown ? deck.poster(m.titles.get(titleFor[i])) : null);
                posters[i].setVisibility(shown ? VISIBLE : INVISIBLE);
            }
            fan(posters[1], 0f, 0f);
            fan(posters[0], -112 * dp, -9f);
            fan(posters[2], 112 * dp, 9f);
            name.setText(m.name);
            count.setText(getResources().getQuantityString(R.plurals.my_titles, m.titles.size(), m.titles.size()));
            count.setVisibility(VISIBLE);
            opening.setVisibility(INVISIBLE);
            bar.setVisibility(INVISIBLE);
            ((GradientDrawable) bar.getBackground()).setColor(m.color);
        }
    }

    private static void fan(View p, float x, float rotation) {
        p.animate().cancel();
        p.setTranslationX(x);
        p.setTranslationY(rotation == 0f ? 0f : 10 * p.getResources().getDisplayMetrics().density);
        p.setRotation(rotation);
        p.setScaleX(1f);
        p.setScaleY(1f);
        p.setAlpha(1f);
    }

    private TextView text(Context ctx, float sp, int color) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setSingleLine(true);
        t.setTypeface(getResources().getFont(sp > 20 ? R.font.outfit_light : R.font.outfit_regular));
        return t;
    }

    MoodDeckView(Context ctx, MoodDeck deck, Listener listener) {
        super(ctx);
        this.deck = deck;
        this.listener = listener;
        dp = getResources().getDisplayMetrics().density;
        setClipChildren(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setContentDescription(getResources().getString(R.string.my_deck_title));

        TextView heading = text(ctx, 26, 0xE6FFFFFF);
        heading.setGravity(Gravity.START);
        heading.setText(R.string.my_deck_title);
        LayoutParams hl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        hl.leftMargin = Math.round(20 * dp);
        hl.topMargin = Math.round(26 * dp);   // clear of the grid's top fade (fade_rows)
        addView(heading, hl);

        glow = new View(ctx);
        glow.setBackground(glowDrawable());
        LayoutParams gl = new LayoutParams(Math.round(900 * dp), Math.round(420 * dp), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        gl.topMargin = Math.round(32 * dp);
        addView(glow, gl);
        glow.setAlpha(0.55f);

        for (int i = 2; i >= 0; i--) {   // the back cards first, so the top card draws over them
            Card c = new Card(ctx);
            LayoutParams cl = new LayoutParams(Math.round(560 * dp), Math.round(330 * dp), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            cl.topMargin = Math.round(96 * dp);
            addView(c.view, cl);
            cards[i] = c;
        }
        bindAll();
        setOnFocusChangeListener((v, focused) -> {
            cards[0].view.animate().scaleX(focused ? 1f : 0.95f).scaleY(focused ? 1f : 0.95f).setDuration(220).start();
            if (!focused) cancel();
            listener.onDeckFocus(focused);
            if (focused) listener.onDeckBackdrop(scene(deck.moods.get(top)));
        });
        cards[0].view.setScaleX(0.95f);
        cards[0].view.setScaleY(0.95f);
    }

    private MoodDeck.Mood mood(int offset) {
        return deck.moods.get((top + offset) % deck.moods.size());
    }

    private void bindAll() {
        for (int i = 0; i < 3; i++) {
            cards[i].bind(mood(i));
            peek(cards[i].view, i);
        }
        tintGlow(mood(0).color);
    }

    /** Behind the top card: up, a little smaller and dimmer for each step back. */
    private void peek(View v, int depth) {
        v.animate().cancel();
        v.setTranslationX(0f);
        v.setRotation(0f);
        v.setTranslationY(-30 * dp * depth);
        // An explicit depth: with every card at the same elevation the back cards could draw over the top one.
        v.setTranslationZ((2 - depth) * 6 * dp);
        float s = (depth == 0 && hasFocus() ? 1f : 0.95f) - 0.07f * depth;
        v.setScaleX(s);
        v.setScaleY(s);
        v.setAlpha(depth == 0 ? 1f : depth == 1 ? 0.7f : 0.4f);
        v.setVisibility(depth < deck.moods.size() ? VISIBLE : INVISIBLE);
    }

    private Bitmap scene(MoodDeck.Mood m) {
        return deck.backdrop(m.titles.get(random.nextInt(m.titles.size())));
    }

    // ---- keys ---------------------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (code == swallowUp) {
                swallowUp = -1;
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean handled = true;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (state == IDLE) flip(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            else if (state == COUNTDOWN) cancel();
        } else if (ok) {
            if (state == IDLE) spin();
            else if (state == COUNTDOWN) openNow();
        } else if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (state == COUNTDOWN || state == SPINNING) cancel();
            else listener.onDeckUp();
        } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
            cancel();
            listener.onDeckUp();
        } else {
            handled = code == KeyEvent.KEYCODE_DPAD_DOWN;
        }
        if (handled) swallowUp = code;
        return handled || super.dispatchKeyEvent(event);
    }

    // ---- swipe --------------------------------------------------------------------------------------

    /**
     * Right (dir 1): the card flies off to the left with a tilt and the next mood comes up. Left (dir -1): it flies
     * off to the right and the mood before comes back.
     */
    private void flip(int dir) {
        if (deck.moods.size() < 2) return;
        state = BUSY;
        View gone = cards[0].view;
        gone.animate().translationX(-dir * 720 * dp).rotation(-dir * 14f).alpha(0f).setDuration(320)
                .setInterpolator(new AccelerateInterpolator(1.2f)).withEndAction(() -> {
                    top = Math.floorMod(top + dir, deck.moods.size());
                    // Rebind in place, then let each card move up from where its view was one step back.
                    bindAll();
                    for (int i = 0; i < 3; i++) {
                        View v = cards[i].view;
                        float ty = v.getTranslationY(), sx = v.getScaleX(), a = v.getAlpha();
                        v.setTranslationY(ty - 30 * dp);
                        v.setScaleX(sx - 0.07f);
                        v.setScaleY(sx - 0.07f);
                        v.setAlpha(i == 2 ? 0f : a * 0.6f);
                        v.animate().translationY(ty).scaleX(sx).scaleY(sx).alpha(a).setDuration(260)
                                .setInterpolator(new DecelerateInterpolator()).start();
                    }
                    listener.onDeckBackdrop(scene(mood(0)));
                    state = IDLE;
                }).start();
    }

    // ---- reel and countdown -------------------------------------------------------------------------

    private final Runnable tick = this::tick;
    private List<MoodDeck.Title> reel;
    private int step, steps;

    /** Right or OK: the posters spin past, fast then slower, and land on one random title. */
    private void spin() {
        removeCallbacks(tick);
        MoodDeck.Mood m = mood(0);
        Card c = cards[0];
        state = SPINNING;
        c.posters[0].animate().alpha(0f).setDuration(150).start();
        c.posters[2].animate().alpha(0f).setDuration(150).start();
        c.posters[1].animate().scaleX(1.12f).scaleY(1.12f).translationY(-4 * dp).setDuration(200).start();
        c.opening.setVisibility(INVISIBLE);
        c.bar.setVisibility(INVISIBLE);
        c.count.setVisibility(VISIBLE);
        reel = new ArrayList<>(m.titles);
        Collections.shuffle(reel, random);
        picked = reel.get(0);
        steps = 18 + random.nextInt(6);
        step = 0;
        // The reel ends on the picked title: it is shown on the last step.
        postDelayed(tick, 60);
    }

    private void tick() {
        Card c = cards[0];
        step++;
        int n = reel.size();
        MoodDeck.Title t = step >= steps ? picked : reel.get((steps - step) % n);
        ImageView slot = c.posters[1];
        slot.setImageBitmap(deck.poster(t));
        float f = step / (float) steps;
        long delay = Math.round(45 + f * f * 340);
        slot.setTranslationY(-26 * dp);
        slot.animate().translationY(-4 * dp).setDuration(Math.max(40, delay - 10)).setInterpolator(new DecelerateInterpolator()).start();
        c.name.setText(t.name);
        if (step < steps) {
            postDelayed(tick, delay);
            return;
        }
        // Landed.
        listener.onDeckBackdrop(deck.backdrop(picked));
        state = COUNTDOWN;
        countLeft = 3;
        c.count.setVisibility(INVISIBLE);
        c.opening.setVisibility(VISIBLE);
        c.opening.setText(getResources().getString(R.string.my_opening_in, countLeft));
        LayoutParams bl = (LayoutParams) c.bar.getLayoutParams();
        bl.width = Math.round(280 * dp);
        c.bar.setLayoutParams(bl);
        c.bar.setPivotX(0f);
        c.bar.setScaleX(0f);
        c.bar.setVisibility(VISIBLE);
        c.bar.animate().scaleX(1f).setDuration(COUNT_MS).setInterpolator(new LinearInterpolator()).start();
        postDelayed(countdown, 1000);
    }

    private final Runnable countdown = new Runnable() {
        @Override public void run() {
            if (state != COUNTDOWN) return;
            countLeft--;
            if (countLeft <= 0) {
                openNow();
                return;
            }
            cards[0].opening.setText(getResources().getString(R.string.my_opening_in, countLeft));
            postDelayed(this, 1000);
        }
    };

    private void openNow() {
        MoodDeck.Title t = picked;
        cancel();
        if (t != null) deck.open(getContext(), t);
    }

    /** Back to the mood card as it was, with its fanned posters. */
    private void cancel() {
        removeCallbacks(tick);
        removeCallbacks(countdown);
        if (state == IDLE) return;
        state = IDLE;
        picked = null;
        cards[0].bind(mood(0));
        peek(cards[0].view, 0);
        listener.onDeckBackdrop(scene(mood(0)));
    }

    // ---- glow ---------------------------------------------------------------------------------------

    private BitmapDrawable glowDrawable() {
        int w = 360, h = 168;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Canvas canvas = new Canvas(bmp);
        canvas.scale(1f, h / (float) w, w / 2f, h / 2f);
        paint.setShader(new RadialGradient(w / 2f, h / 2f, w / 2f, new int[]{0x80FFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, -w, w, w * 2f, paint);
        BitmapDrawable d = new BitmapDrawable(getResources(), bmp);
        d.setGravity(Gravity.FILL);
        return d;
    }

    private void tintGlow(int color) {
        glow.getBackground().setTint(color);
    }
}
