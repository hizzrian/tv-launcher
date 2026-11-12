package com.kaca.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Katalog film/serial publik (Cinemeta) untuk latar hero, dengan cache di disk. */
final class Catalog {
    static final String[] URLS = {
            "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json",
            "https://cinemeta-catalogs.strem.io/top/catalog/series/top.json",
    };
    static final long TTL = 6 * 3600 * 1000L;
    static final int PER_TYPE = 12;

    static final class Slide {
        String id, name, type, year, genres, rating, bg, logo;

        CharSequence metaLine() {
            SpannableStringBuilder b = new SpannableStringBuilder();
            String[] parts = {type, year, genres};
            for (String p : parts) {
                if (p == null || p.isEmpty()) continue;
                if (b.length() > 0) b.append("   ·   ");
                b.append(p);
            }
            if (rating != null && !rating.isEmpty()) {
                if (b.length() > 0) b.append("   ·   ");
                int s = b.length();
                b.append("★");
                b.setSpan(new ForegroundColorSpan(0xFFFFD34D), s, s + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                b.append(" ").append(rating);
            }
            return b;
        }

        JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("name", name).put("type", type).put("year", year)
                    .put("genres", genres).put("rating", rating).put("bg", bg).put("logo", logo == null ? "" : logo);
        }

        static Slide from(JSONObject o) {
            Slide s = new Slide();
            s.id = o.optString("id");
            s.name = o.optString("name");
            s.type = o.optString("type");
            s.year = o.optString("year");
            s.genres = o.optString("genres");
            s.rating = o.optString("rating");
            s.bg = o.optString("bg");
            String l = o.optString("logo");
            s.logo = l.isEmpty() ? null : l;
            return s;
        }
    }

    static List<Slide> fetch() throws Exception {
        List<List<Slide>> perType = new ArrayList<>();
        Exception last = null;
        for (String u : URLS) {
            List<Slide> l = new ArrayList<>();
            try {
                JSONArray metas = new JSONObject(get(u)).optJSONArray("metas");
                for (int i = 0; metas != null && i < metas.length(); i++) {
                    JSONObject m = metas.optJSONObject(i);
                    if (m == null) continue;
                    String id = m.optString("id"), bg = m.optString("background");
                    if (!id.matches("tt\\d+") || bg.isEmpty()) continue;
                    JSONArray g = m.optJSONArray("genres");
                    boolean adult = false;
                    List<String> genres = new ArrayList<>();
                    for (int j = 0; g != null && j < g.length(); j++) {
                        String x = g.optString(j);
                        if (x.toLowerCase().contains("adult")) adult = true;
                        if (genres.size() < 2) genres.add(x);
                    }
                    if (adult) continue;
                    Slide s = new Slide();
                    s.id = id;
                    s.name = m.optString("name");
                    s.type = "series".equals(m.optString("type")) ? "Serial" : "Film";
                    s.year = m.optString("releaseInfo").replaceAll("[–-]$", "");
                    s.genres = android.text.TextUtils.join(", ", genres);
                    s.rating = m.optString("imdbRating");
                    s.bg = bg.replace("/medium/", "/large/");
                    String logo = m.optString("logo");
                    s.logo = logo.isEmpty() ? null : logo.replace("/medium/", "/large/");
                    l.add(s);
                }
            } catch (Exception e) {
                last = e;
            }
            Collections.shuffle(l);
            perType.add(new ArrayList<>(l.subList(0, Math.min(PER_TYPE, l.size()))));
        }
        List<Slide> out = new ArrayList<>();
        for (int i = 0; i < PER_TYPE; i++)
            for (List<Slide> l : perType) if (i < l.size()) out.add(l.get(i));
        if (out.isEmpty() && last != null) throw last;
        return out;
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            copy(in, bo);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Kaca-Launcher/1.0");
        if (c.getResponseCode() / 100 != 2) {
            c.disconnect();
            throw new Exception("HTTP " + c.getResponseCode() + " " + url);
        }
        return c;
    }

    static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    static File file(File dir, String url) {
        return new File(dir, "img_" + Integer.toHexString(url.hashCode()) + "_" + url.length());
    }

    /** Pastikan gambar ada di cache disk; kembalikan file-nya, atau null bila gagal. */
    static File ensure(File dir, String url) {
        File f = file(dir, url);
        if (f.exists() && f.length() > 0) return f;
        File tmp = new File(dir, f.getName() + ".tmp");
        try {
            HttpURLConnection c = open(url);
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                copy(in, out);
            } finally {
                c.disconnect();
            }
            return tmp.renameTo(f) ? f : null;
        } catch (Exception e) {
            tmp.delete();
            return null;
        }
    }

    static Bitmap image(File dir, String url, int targetW, boolean opaque) {
        File f = ensure(dir, url);
        if (f == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) {
            f.delete();
            return null;
        }
        int sample = 1;
        while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = opaque ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), d);
        if (b == null) f.delete();
        return b;
    }

    static void save(File dir, List<Slide> slides) {
        try {
            JSONArray a = new JSONArray();
            for (Slide s : slides) a.put(s.toJson());
            JSONObject o = new JSONObject().put("ts", System.currentTimeMillis()).put("slides", a);
            try (OutputStream out = new FileOutputStream(new File(dir, "catalog.json"))) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    static JSONObject readCache(File dir) {
        File f = new File(dir, "catalog.json");
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            copy(in, bo);
            return new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    static List<Slide> loadCache(File dir) {
        JSONObject o = readCache(dir);
        if (o == null) return null;
        List<Slide> l = new ArrayList<>();
        JSONArray a = o.optJSONArray("slides");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s != null) l.add(Slide.from(s));
        }
        return l;
    }

    static boolean isStale(File dir) {
        JSONObject o = readCache(dir);
        return o == null || System.currentTimeMillis() - o.optLong("ts") > TTL;
    }

    /** Hapus gambar cache yang tidak lagi dipakai katalog saat ini. */
    static void prune(File dir, List<Slide> slides) {
        Set<String> keep = new HashSet<>();
        for (Slide s : slides) {
            keep.add(file(dir, s.bg).getName());
            if (s.logo != null) keep.add(file(dir, s.logo).getName());
        }
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files)
            if (f.getName().startsWith("img_") && !keep.contains(f.getName())) f.delete();
    }
}
