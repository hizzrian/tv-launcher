package com.kaca.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Daftar aplikasi TV, urutan (5 pertama = dock), dan aplikasi yang disembunyikan. */
final class AppStore {
    static final class App {
        String pkg, label;
        Drawable banner, icon;
        Intent intent;
        int color;
    }

    static final String[] DEFAULT_ORDER = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
            "com.google.android.youtube.tvmusic",
            "com.android.vending",
            "com.google.android.videos",
            "com.mitv.livetv",
            "com.catchplay.asiaplay.common.tv",
            "com.mitv.gallery",
            "com.xiaomi.mitv.mediaexplorer",
            "com.google.android.play.games",
    };
    static final String[] DEFAULT_HIDDEN = {
            "com.spocky.projengmenu",
            "com.mitv.tvhome.atv",
            "com.mitv.tvhome.mitvplus",
            "com.xiaomi.mitv.tvmanager",
            "com.xiaomi.mitv.smartshare",
    };

    final Context c;
    final SharedPreferences sp;
    final Map<String, App> all = new LinkedHashMap<>();

    AppStore(Context c) {
        this.c = c;
        sp = c.getSharedPreferences("kaca", Context.MODE_PRIVATE);
        if (!sp.getBoolean("init", false)) {
            sp.edit()
                    .putString("order", TextUtils.join("\n", DEFAULT_ORDER))
                    .putStringSet("hidden", new HashSet<>(Arrays.asList(DEFAULT_HIDDEN)))
                    .putBoolean("init", true)
                    .apply();
        }
    }

    List<App> load() {
        all.clear();
        PackageManager pm = c.getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai.packageName.equals(c.getPackageName()) || all.containsKey(ai.packageName)) continue;
            App a = new App();
            a.pkg = ai.packageName;
            a.label = String.valueOf(ri.loadLabel(pm));
            a.banner = ai.loadBanner(pm);
            if (a.banner == null) a.banner = ai.applicationInfo.loadBanner(pm);
            a.icon = ri.loadIcon(pm);
            a.intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (a.banner == null) a.color = avgColor(a.icon);
            all.put(a.pkg, a);
        }
        Set<String> hidden = hidden();
        List<App> out = new ArrayList<>();
        for (String p : order()) {
            App a = all.get(p);
            if (a != null && !hidden.contains(p) && !out.contains(a)) out.add(a);
        }
        List<App> rest = new ArrayList<>();
        for (App a : all.values()) if (!hidden.contains(a.pkg) && !out.contains(a)) rest.add(a);
        Collections.sort(rest, (x, y) -> x.label.compareToIgnoreCase(y.label));
        out.addAll(rest);
        return out;
    }

    List<String> order() {
        List<String> l = new ArrayList<>();
        for (String p : sp.getString("order", "").split("\n")) if (!p.isEmpty()) l.add(p);
        return l;
    }

    Set<String> hidden() {
        return new HashSet<>(sp.getStringSet("hidden", Collections.<String>emptySet()));
    }

    void saveOrder(List<App> visible) {
        List<String> l = new ArrayList<>();
        for (App a : visible) l.add(a.pkg);
        for (String p : order()) if (!l.contains(p)) l.add(p);
        sp.edit().putString("order", TextUtils.join("\n", l)).apply();
    }

    void hide(String pkg) {
        Set<String> h = hidden();
        h.add(pkg);
        sp.edit().putStringSet("hidden", h).apply();
    }

    void unhide(String pkg, List<App> visible) {
        Set<String> h = hidden();
        h.remove(pkg);
        List<String> l = new ArrayList<>();
        for (App a : visible) l.add(a.pkg);
        l.add(pkg);
        for (String p : order()) if (!l.contains(p)) l.add(p);
        sp.edit().putStringSet("hidden", h).putString("order", TextUtils.join("\n", l)).apply();
    }

    List<App> hiddenApps() {
        Set<String> h = hidden();
        List<App> l = new ArrayList<>();
        for (App a : all.values()) if (h.contains(a.pkg)) l.add(a);
        return l;
    }

    static int avgColor(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, 16, 16);
            d.draw(cv);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    int p = b.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    r += Color.red(p);
                    g += Color.green(p);
                    bl += Color.blue(p);
                    n++;
                }
            b.recycle();
            if (n == 0) return 0xFF2A2F3A;
            return Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
        } catch (Exception e) {
            return 0xFF2A2F3A;
        }
    }
}
