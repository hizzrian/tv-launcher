package com.kaca.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Layar utama launcher Kaca. Tata letak dirancang di kanvas virtual 1920x1080 lalu diskalakan. */
public class HomeActivity extends Activity {
    static final int COLS = 5;
    static final float TW = 312, TH = 188, GAP = 36;
    static final float DOCK_X = 72, DOCK_Y = 628, DOCK_W = 1776, DOCK_H = 260, DOCK_PAD = 36;
    static final float GRID_Y0 = 934, ROW_H = 296, ROW1_SHIFT = 518;
    static final Interpolator EASE = new PathInterpolator(.2f, .8f, .2f, 1f);
    static final int MP = ViewGroup.LayoutParams.MATCH_PARENT, WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    final Handler h = new Handler(Looper.getMainLooper());
    float S = 1f;
    int screenW, screenH;
    FrameLayout root, content, mirrorHolder;
    View dockDim, chev;
    GearView gear;
    boolean topFocus = false;
    TextView label, clock;
    Hero hero;
    AppStore store;
    MenuOverlay menu;
    final List<AppStore.App> apps = new ArrayList<>();
    final List<TileView> tiles = new ArrayList<>();
    int idx = 0;
    boolean grid = false, longPressed = false;
    BroadcastReceiver pkgReceiver;

    final Runnable reload = this::reloadApps;
    final Runnable clockTick = new Runnable() {
        @Override public void run() {
            boolean is24 = DateFormat.is24HourFormat(HomeActivity.this);
            clock.setText(DateFormat.format(is24 ? "HH:mm" : "h:mm a", new Date()));
            long now = System.currentTimeMillis();
            h.postDelayed(this, 60000 - now % 60000 + 50);
        }
    };

    int px(float v) {
        return Math.round(v * S);
    }

    FrameLayout.LayoutParams lp(float x, float y, float w, float hh) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w < 0 ? (int) w : px(w), hh < 0 ? (int) hh : px(hh));
        p.leftMargin = px(x);
        p.topMargin = px(y);
        return p;
    }

    static ViewOutlineProvider round(final float r) {
        return new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        };
    }

    static GradientDrawable grad(GradientDrawable.Orientation o, int... colors) {
        return new GradientDrawable(o, colors);
    }

    static boolean isSelect(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    TextView text(float sizePx, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(sizePx));
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    GradientDrawable glassBg(float radius) {
        GradientDrawable g = grad(GradientDrawable.Orientation.TOP_BOTTOM, 0x38FFFFFF, 0x17FFFFFF);
        g.setCornerRadius(px(radius));
        g.setStroke(Math.max(1, px(1.5f)), 0x4DFFFFFF);
        return g;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        screenW = size.x;
        screenH = size.y;
        S = Math.min(screenW / 1920f, screenH / 1080f);

        root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setBackground(grad(GradientDrawable.Orientation.TL_BR, 0xFF1B2A4A, 0xFF05070D));

        hero = new Hero(this);
        root.addView(hero.layer, new FrameLayout.LayoutParams(MP, MP));
        root.addView(hero.title, lp(110, 110, 1000, WC));

        chev = new ChevronView(this);
        root.addView(chev, lp(943, 30, 34, 12));

        // Bar kanan atas: [gear][jam]. Gear tersembunyi sampai dipilih dengan tombol ↑ dari dock.
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClipChildren(false);
        gear = new GearView(this);
        gear.setAlpha(0f);
        gear.setScaleX(.8f);
        gear.setScaleY(.8f);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(px(56), px(56));
        gp.rightMargin = px(16);
        topBar.addView(gear, gp);
        clock = text(26, Color.WHITE, Typeface.create("sans-serif", Typeface.BOLD));
        clock.setPadding(px(24), px(12), px(24), px(12));
        clock.setBackground(glassBg(999));
        topBar.addView(clock, new LinearLayout.LayoutParams(WC, WC));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(WC, WC, Gravity.TOP | Gravity.END);
        cp.topMargin = px(46);
        cp.rightMargin = px(70);
        root.addView(topBar, cp);

        content = new FrameLayout(this);
        content.setClipChildren(false);
        root.addView(content, new FrameLayout.LayoutParams(MP, px(4200)));
        buildDock();

        label = text(22, Color.WHITE, Typeface.create("sans-serif", Typeface.BOLD));
        label.setGravity(Gravity.CENTER);
        label.setShadowLayer(px(10), 0, px(2), 0x80000000);
        label.setAlpha(0f);
        content.addView(label, lp(0, 0, TW + 200, 40));

        menu = new MenuOverlay(this);
        root.addView(menu.view, new FrameLayout.LayoutParams(MP, MP));

        setContentView(root);
        try {
            getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
        } catch (Exception ignored) {
        }

        store = new AppStore(this);
        reloadApps();
        registerPackages();
        clockTick.run();
        hero.start();
    }

    void buildDock() {
        FrameLayout dock = new FrameLayout(this);
        dock.setOutlineProvider(round(px(44)));
        dock.setClipToOutline(true);
        dock.setElevation(px(18));

        // Salinan latar hero yang diburamkan: inilah efek "kaca" pada dock.
        mirrorHolder = new FrameLayout(this);
        mirrorHolder.setTranslationX(-px(DOCK_X));
        mirrorHolder.setTranslationY(-px(DOCK_Y));
        ImageView ma = Hero.img(this), mb = Hero.img(this);
        mirrorHolder.addView(ma, new FrameLayout.LayoutParams(screenW, screenH));
        mirrorHolder.addView(mb, new FrameLayout.LayoutParams(screenW, screenH));
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.8f);
        mirrorHolder.setRenderEffect(RenderEffect.createChainEffect(
                RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(cm)),
                RenderEffect.createBlurEffect(px(42), px(42), Shader.TileMode.CLAMP)));
        dock.addView(mirrorHolder, new FrameLayout.LayoutParams(screenW, screenH));

        View tint = new View(this);
        tint.setBackground(grad(GradientDrawable.Orientation.TOP_BOTTOM, 0x33FFFFFF, 0x14FFFFFF));
        dock.addView(tint, new FrameLayout.LayoutParams(MP, MP));

        GradientDrawable hl = new GradientDrawable();
        hl.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        hl.setColors(new int[]{0x40FFFFFF, 0x00FFFFFF});
        hl.setGradientCenter(0.12f, 0f);
        hl.setGradientRadius(px(620));
        View hv = new View(this);
        hv.setBackground(hl);
        dock.addView(hv, new FrameLayout.LayoutParams(MP, MP));

        dockDim = new View(this);
        dockDim.setBackgroundColor(Color.BLACK);
        dockDim.setAlpha(0f);
        dock.addView(dockDim, new FrameLayout.LayoutParams(MP, MP));

        GradientDrawable bd = new GradientDrawable();
        bd.setCornerRadius(px(44));
        bd.setColor(0);
        bd.setStroke(Math.max(1, px(1.5f)), 0x47FFFFFF);
        View border = new View(this);
        border.setBackground(bd);
        dock.addView(border, new FrameLayout.LayoutParams(MP, MP));

        View top = new View(this);
        top.setBackground(grad(GradientDrawable.Orientation.LEFT_RIGHT, 0x00FFFFFF, 0x99FFFFFF, 0x00FFFFFF));
        dock.addView(top, lp(44, 1, DOCK_W - 88, 2));

        content.addView(dock, lp(DOCK_X, DOCK_Y, DOCK_W, DOCK_H));
        hero.addMirror(ma, mb);
    }

    float tileX(int i) {
        return 108 + (i % COLS) * (TW + GAP);
    }

    float tileY(int i) {
        int r = i / COLS;
        return r == 0 ? DOCK_Y + DOCK_PAD : GRID_Y0 + (r - 1) * ROW_H;
    }

    void reloadApps() {
        String keep = apps.isEmpty() ? null : apps.get(Math.min(idx, apps.size() - 1)).pkg;
        apps.clear();
        apps.addAll(store.load());
        rebuildTiles();
        if (keep != null)
            for (int i = 0; i < apps.size(); i++) if (apps.get(i).pkg.equals(keep)) idx = i;
        idx = Math.max(0, Math.min(idx, apps.size() - 1));
        render();
    }

    void rebuildTiles() {
        for (TileView t : tiles) content.removeView(t);
        tiles.clear();
        for (int i = 0; i < apps.size(); i++) {
            TileView t = new TileView(this, apps.get(i));
            content.addView(t, lp(tileX(i), tileY(i), TW, TH));
            tiles.add(t);
        }
    }

    void render() {
        if (apps.isEmpty()) return;
        for (int i = 0; i < tiles.size(); i++) tiles.get(i).setFocusedTile(!topFocus && i == idx);
        int r = idx / COLS;
        boolean g = r > 0;
        float ty = g ? -px(ROW1_SHIFT + (r - 1) * ROW_H) : 0;
        content.animate().translationY(ty).setDuration(650).setInterpolator(EASE);
        mirrorHolder.animate().translationY(-px(DOCK_Y) - ty).setDuration(650).setInterpolator(EASE);
        if (g != grid) {
            grid = g;
            hero.setGrid(g);
            dockDim.animate().alpha(g ? .4f : 0f).setDuration(650);
            chev.animate().alpha(g ? 0f : 1f).setDuration(400);
            hero.title.animate().alpha(g ? 0f : 1f).translationY(g ? -px(30) : 0).setDuration(500).setInterpolator(EASE);
        }
        label.setText(apps.get(idx).label);
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) label.getLayoutParams();
        p.leftMargin = px(tileX(idx) - 100);
        p.topMargin = px(tileY(idx) + 208);
        label.setLayoutParams(p);
        label.animate().cancel();
        label.setAlpha(0f);
        if (g) label.animate().alpha(1f).setStartDelay(120).setDuration(250);
    }

    void move(int dx, int dy) {
        int n = apps.size();
        if (n == 0) return;
        int c = idx % COLS, old = idx;
        if (dx > 0 && c < COLS - 1 && idx + 1 < n) idx++;
        else if (dx < 0 && c > 0) idx--;
        else if (dy > 0 && idx + COLS < n) idx += COLS;
        else if (dy > 0 && idx / COLS < (n - 1) / COLS) idx = n - 1;
        else if (dy < 0 && idx >= COLS) idx -= COLS;
        else if (dy < 0) {
            setTopFocus(true);
            return;
        }
        if (idx != old) render();
    }

    void setTopFocus(boolean on) {
        topFocus = on;
        gear.setOn(on);
        gear.animate().alpha(on ? 1f : 0f).scaleX(on ? 1.12f : .8f).scaleY(on ? 1.12f : .8f)
                .setDuration(300).setInterpolator(EASE);
        for (int i = 0; i < tiles.size(); i++) tiles.get(i).setFocusedTile(!on && i == idx);
    }

    void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Pengaturan TV tidak bisa dibuka", Toast.LENGTH_SHORT).show();
        }
    }

    void launch(AppStore.App a) {
        try {
            startActivity(a.intent);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak bisa membuka " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    void moveApp(int from, int to) {
        AppStore.App x = apps.remove(from);
        to = Math.max(0, Math.min(to, apps.size()));
        apps.add(to, x);
        idx = to;
        store.saveOrder(apps);
        rebuildTiles();
        render();
    }

    void openAppMenu(boolean fromLongPress) {
        if (apps.isEmpty()) return;
        final AppStore.App a = apps.get(idx);
        final int n = apps.size(), i0 = idx;
        List<MenuOverlay.Item> it = new ArrayList<>();
        it.add(new MenuOverlay.Item("Buka", () -> launch(a)));
        if (i0 >= COLS) it.add(new MenuOverlay.Item("Pindahkan ke dock", () -> moveApp(i0, COLS - 1)));
        else if (n > COLS) it.add(new MenuOverlay.Item("Keluarkan dari dock", () -> moveApp(i0, COLS)));
        if (i0 > 0) it.add(new MenuOverlay.Item("Geser ke kiri", () -> moveApp(i0, i0 - 1)));
        if (i0 < n - 1) it.add(new MenuOverlay.Item("Geser ke kanan", () -> moveApp(i0, i0 + 1)));
        it.add(new MenuOverlay.Item("Sembunyikan", () -> {
            store.hide(a.pkg);
            reloadApps();
        }));
        final List<AppStore.App> hidden = store.hiddenApps();
        if (!hidden.isEmpty())
            it.add(new MenuOverlay.Item("Aplikasi tersembunyi (" + hidden.size() + ")", () -> openHiddenMenu(hidden)));
        it.add(new MenuOverlay.Item("Pengaturan TV", this::openSettings));
        menu.open(a.label, it, fromLongPress);
    }

    void openHiddenMenu(List<AppStore.App> hidden) {
        List<MenuOverlay.Item> it = new ArrayList<>();
        for (final AppStore.App a : hidden)
            it.add(new MenuOverlay.Item(a.label, () -> {
                store.unhide(a.pkg, apps);
                reloadApps();
            }));
        menu.open("Tampilkan lagi", it, false);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (menu.isOpen()) {
            menu.keyDown(code);
            return true;
        }
        if (topFocus) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_BACK) {
                setTopFocus(false);
                return true;
            }
            if (isSelect(code)) {
                if (e.getRepeatCount() == 0) {
                    longPressed = false;
                    e.startTracking();
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_LEFT
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_MENU) return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: move(-1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: move(0, 1); return true;
            case KeyEvent.KEYCODE_DPAD_UP: move(0, -1); return true;
            case KeyEvent.KEYCODE_MENU: openAppMenu(false); return true;
            case KeyEvent.KEYCODE_BACK:
                if (idx >= COLS) {
                    idx %= COLS;
                    render();
                }
                return true;
        }
        if (isSelect(code)) {
            if (e.getRepeatCount() == 0) {
                longPressed = false;
                e.startTracking();
            }
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (isSelect(code) && !menu.isOpen() && !topFocus) {
            longPressed = true;
            openAppMenu(true);
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (menu.isOpen()) {
            menu.keyUp(code);
            return true;
        }
        if (isSelect(code)) {
            if (topFocus) openSettings();
            else if (!longPressed && !e.isCanceled() && !apps.isEmpty()) launch(apps.get(idx));
            longPressed = false;
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyUp(code, e);
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        // Launcher: tombol Back tidak menutup layar utama.
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (menu.isOpen()) menu.close();
        if (topFocus) setTopFocus(false);
        if (idx != 0) {
            idx = 0;
            render();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        hero.setPaused(false);
        hero.checkStale();
    }

    @Override protected void onPause() {
        super.onPause();
        hero.setPaused(true);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        hero.ex.shutdownNow();
    }

    void registerPackages() {
        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                h.removeCallbacks(reload);
                h.postDelayed(reload, 600);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
    }

    /** Tombol bulat kaca dengan ikon roda gigi; putih solid saat dipilih. */
    static final class GearView extends View {
        final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
                ic = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean on;

        GearView(Context c) {
            super(c);
            rim.setStyle(Paint.Style.STROKE);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
        }

        void setOn(boolean v) {
            on = v;
            setElevation(v ? getHeight() * .3f : 0);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, R = Math.min(getWidth(), getHeight()) / 2f;
            bg.setColor(on ? 0xFFFFFFFF : 0x38FFFFFF);
            c.drawCircle(cx, cy, R - 1, bg);
            if (!on) {
                rim.setColor(0x4DFFFFFF);
                rim.setStrokeWidth(Math.max(1, R * .05f));
                c.drawCircle(cx, cy, R - 1, rim);
            }
            ic.setColor(on ? 0xFF111111 : 0xFFFFFFFF);
            float g = R * .30f;
            ic.setStyle(Paint.Style.STROKE);
            ic.setStrokeWidth(R * .15f);
            c.drawCircle(cx, cy, g, ic);
            ic.setStyle(Paint.Style.FILL);
            float tw = R * .16f, top = cy - g - R * .2f, bottom = cy - g + R * .04f;
            for (int i = 0; i < 8; i++) {
                c.save();
                c.rotate(i * 45f, cx, cy);
                c.drawRoundRect(cx - tw / 2, top, cx + tw / 2, bottom, tw * .3f, tw * .3f, ic);
                c.restore();
            }
        }
    }

    static final class ChevronView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        ChevronView(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xD9FFFFFF);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight();
            p.setStrokeWidth(hh * .25f);
            path.reset();
            path.moveTo(w * .06f, hh * .83f);
            path.lineTo(w * .5f, hh * .17f);
            path.lineTo(w * .94f, hh * .83f);
            c.drawPath(path, p);
        }
    }
}
