package com.kaca.launcher;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Latar hero: artwork film/serial yang berganti otomatis dengan crossfade dan efek Ken Burns. */
final class Hero {
    static final long HOLD = 8000, FADE = 1100, KB = 11000;
    static final int MP = HomeActivity.MP, WC = HomeActivity.WC;

    final HomeActivity a;
    final FrameLayout layer, imgs;
    final LinearLayout title, inner;
    final ImageView logo;
    final TextView h1, meta;
    final ImageView[] main = new ImageView[2];
    final List<ImageView[]> sets = new ArrayList<>();
    final ExecutorService ex = Executors.newFixedThreadPool(2);
    final File dir;

    List<Catalog.Slide> slides = new ArrayList<>();
    int cur = -1, front = 0;
    volatile boolean loading;
    boolean paused, grid;
    float gridT = 0f;
    ValueAnimator gridAnim;

    final Runnable tick = new Runnable() {
        @Override public void run() {
            if (paused || grid) {
                a.h.postDelayed(this, 1000);
                return;
            }
            show(cur + 1);
        }
    };

    static ImageView img(Context c) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    Hero(HomeActivity a) {
        this.a = a;
        dir = new File(a.getFilesDir(), "hero");
        dir.mkdirs();

        layer = new FrameLayout(a);
        imgs = new FrameLayout(a);
        main[0] = img(a);
        main[1] = img(a);
        imgs.addView(main[0], new FrameLayout.LayoutParams(MP, MP));
        imgs.addView(main[1], new FrameLayout.LayoutParams(MP, MP));
        sets.add(main);
        layer.addView(imgs, new FrameLayout.LayoutParams(MP, MP));

        View sb = new View(a);
        sb.setBackground(HomeActivity.grad(GradientDrawable.Orientation.BOTTOM_TOP, 0x99000000, 0x00000000));
        layer.addView(sb, new FrameLayout.LayoutParams(MP, a.px(1080 * .48f), Gravity.BOTTOM));
        View sl = new View(a);
        sl.setBackground(HomeActivity.grad(GradientDrawable.Orientation.LEFT_RIGHT, 0x8C000000, 0x00000000));
        layer.addView(sl, new FrameLayout.LayoutParams(a.px(1920 * .42f), MP, Gravity.START));
        View st = new View(a);
        st.setBackground(HomeActivity.grad(GradientDrawable.Orientation.TOP_BOTTOM, 0x59000000, 0x00000000));
        layer.addView(st, new FrameLayout.LayoutParams(MP, a.px(1080 * .18f), Gravity.TOP));

        title = new LinearLayout(a);
        title.setOrientation(LinearLayout.VERTICAL);
        inner = new LinearLayout(a);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setAlpha(0f);

        logo = new ImageView(a);
        logo.setAdjustViewBounds(true);
        logo.setMaxWidth(a.px(640));
        logo.setMaxHeight(a.px(210));
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setVisibility(View.GONE);

        h1 = a.text(110, Color.WHITE, Typeface.create("sans-serif-black", Typeface.NORMAL));
        h1.setLetterSpacing(-.03f);
        h1.setShadowLayer(a.px(30), 0, a.px(6), 0x73000000);

        meta = a.text(26, 0xD9FFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        meta.setShadowLayer(a.px(12), 0, a.px(2), 0x99000000);

        inner.addView(logo, new LinearLayout.LayoutParams(WC, WC));
        inner.addView(h1, new LinearLayout.LayoutParams(WC, WC));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(WC, WC);
        mp.topMargin = a.px(22);
        inner.addView(meta, mp);
        title.addView(inner, new LinearLayout.LayoutParams(WC, WC));
    }

    void addMirror(ImageView x, ImageView y) {
        sets.add(new ImageView[]{x, y});
    }

    void start() {
        ex.execute(() -> {
            final List<Catalog.Slide> c = Catalog.loadCache(dir);
            if (c != null && !c.isEmpty()) a.h.post(() -> setSlides(c));
            if (c == null || c.isEmpty() || Catalog.isStale(dir)) refresh();
        });
    }

    void checkStale() {
        if (loading) return;
        ex.execute(() -> {
            if (!loading && Catalog.isStale(dir)) refresh();
        });
    }

    void refresh() {
        loading = true;
        try {
            final List<Catalog.Slide> s = Catalog.fetch();
            if (!s.isEmpty()) {
                Catalog.save(dir, s);
                Catalog.prune(dir, s);
                a.h.post(() -> setSlides(s));
            }
        } catch (Exception e) {
            a.h.postDelayed(() -> ex.execute(this::refresh), 30 * 60 * 1000L);
        } finally {
            loading = false;
        }
    }

    void setSlides(List<Catalog.Slide> s) {
        boolean first = slides.isEmpty();
        slides = s;
        if (first) show(0);
    }

    void show(int i) {
        a.h.removeCallbacks(tick);
        if (slides.isEmpty()) return;
        final int n = slides.size(), k = ((i % n) + n) % n;
        final Catalog.Slide s = slides.get(k);
        final int w = a.screenW, lw = a.px(640);
        ex.execute(() -> {
            final Bitmap bg = Catalog.image(dir, s.bg, w, true);
            final Bitmap lg = s.logo == null ? null : Catalog.image(dir, s.logo, lw, false);
            a.h.post(() -> {
                if (bg == null) {
                    cur = k;
                    a.h.postDelayed(tick, 3000);
                    return;
                }
                apply(k, s, bg, lg);
            });
        });
    }

    void apply(int k, final Catalog.Slide s, Bitmap bg, final Bitmap lg) {
        cur = k;
        int nf = 1 - front;
        float dx = (Math.random() < .5 ? -1 : 1) * a.screenW * .016f;
        float dy = (float) (-Math.random() * a.screenH * .012f);
        for (ImageView[] set : sets) {
            ImageView nv = set[nf], ov = set[front];
            nv.setImageBitmap(bg);
            kenBurns(nv, dx, dy);
            nv.animate().alpha(1f).setDuration(FADE);
            ov.animate().alpha(0f).setDuration(FADE);
        }
        front = nf;

        inner.animate().alpha(0f).translationY(a.px(10)).setDuration(inner.getAlpha() > 0 ? 450 : 0)
                .withEndAction(() -> {
                    if (lg != null) {
                        logo.setImageBitmap(lg);
                        logo.setVisibility(View.VISIBLE);
                        h1.setVisibility(View.GONE);
                    } else {
                        logo.setVisibility(View.GONE);
                        h1.setText(s.name);
                        h1.setVisibility(View.VISIBLE);
                    }
                    meta.setText(s.metaLine());
                    inner.animate().alpha(1f).translationY(0).setDuration(550).setInterpolator(HomeActivity.EASE);
                });

        a.h.postDelayed(tick, HOLD + FADE);
        if (slides.size() > 1) {
            final Catalog.Slide nx = slides.get((k + 1) % slides.size());
            ex.execute(() -> {
                Catalog.ensure(dir, nx.bg);
                if (nx.logo != null) Catalog.ensure(dir, nx.logo);
            });
        }
    }

    void kenBurns(ImageView v, float dx, float dy) {
        Object old = v.getTag();
        if (old instanceof Animator) ((Animator) old).cancel();
        ObjectAnimator an = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.02f, 1.10f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.02f, 1.10f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -dx, dx),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, dy));
        an.setDuration(KB);
        an.setInterpolator(new LinearInterpolator());
        an.start();
        v.setTag(an);
    }

    void setGrid(boolean g) {
        grid = g;
        if (gridAnim != null) gridAnim.cancel();
        gridAnim = ValueAnimator.ofFloat(gridT, g ? 1f : 0f);
        gridAnim.setDuration(650);
        gridAnim.setInterpolator(HomeActivity.EASE);
        gridAnim.addUpdateListener(va -> {
            gridT = (float) va.getAnimatedValue();
            applyGrid();
        });
        gridAnim.start();
    }

    void applyGrid() {
        float t = gridT;
        if (t < 0.01f) {
            imgs.setRenderEffect(null);
        } else {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(1f + .3f * t);
            ColorMatrix br = new ColorMatrix();
            float b = 1f - .5f * t;
            br.setScale(b, b, b, 1f);
            cm.postConcat(br);
            float r = a.px(40) * t + 1f;
            imgs.setRenderEffect(RenderEffect.createChainEffect(
                    RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(cm)),
                    RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)));
        }
        imgs.setScaleX(1f + .08f * t);
        imgs.setScaleY(1f + .08f * t);
    }

    void setPaused(boolean p) {
        paused = p;
    }
}
