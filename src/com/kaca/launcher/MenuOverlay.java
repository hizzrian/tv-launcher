package com.kaca.launcher;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Menu kaca sederhana yang muncul saat tombol OK ditekan lama (atau tombol Menu). */
final class MenuOverlay {
    static final class Item {
        final String label;
        final Runnable run;

        Item(String l, Runnable r) {
            label = l;
            run = r;
        }
    }

    final HomeActivity a;
    final FrameLayout view;
    final LinearLayout card, list;
    final TextView header;
    final List<TextView> rows = new ArrayList<>();
    List<Item> items = new ArrayList<>();
    int sel;
    boolean open, ignoreUp;

    MenuOverlay(HomeActivity a) {
        this.a = a;
        view = new FrameLayout(a);
        view.setVisibility(View.GONE);
        view.setAlpha(0f);
        View scrim = new View(a);
        scrim.setBackgroundColor(0x80000000);
        view.addView(scrim, new FrameLayout.LayoutParams(HomeActivity.MP, HomeActivity.MP));

        card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(a.px(22), a.px(22), a.px(22), a.px(22));
        GradientDrawable bg = HomeActivity.grad(GradientDrawable.Orientation.TOP_BOTTOM, 0xF0303036, 0xF018181C);
        bg.setCornerRadius(a.px(36));
        bg.setStroke(Math.max(1, a.px(1.5f)), 0x40FFFFFF);
        card.setBackground(bg);
        card.setElevation(a.px(30));

        header = a.text(24, 0x99FFFFFF, Typeface.create("sans-serif", Typeface.BOLD));
        header.setPadding(a.px(22), a.px(8), a.px(22), a.px(18));
        card.addView(header);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        card.addView(list);
        view.addView(card, new FrameLayout.LayoutParams(a.px(640), HomeActivity.WC, Gravity.CENTER));
    }

    boolean isOpen() {
        return open;
    }

    void open(String title, List<Item> it, boolean fromLongPress) {
        items = it;
        sel = 0;
        ignoreUp = fromLongPress;
        open = true;
        header.setText(title);
        list.removeAllViews();
        rows.clear();
        for (Item i : it) {
            TextView t = a.text(28, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setText(i.label);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(a.px(24), 0, a.px(24), 0);
            list.addView(t, new LinearLayout.LayoutParams(HomeActivity.MP, a.px(72)));
            rows.add(t);
        }
        paint();
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        card.setScaleX(.96f);
        card.setScaleY(.96f);
        view.animate().alpha(1f).setDuration(200);
        card.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(HomeActivity.EASE);
    }

    void close() {
        open = false;
        view.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (!open) view.setVisibility(View.GONE);
        });
    }

    void paint() {
        for (int i = 0; i < rows.size(); i++) {
            TextView t = rows.get(i);
            boolean s = i == sel;
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(a.px(18));
            d.setColor(s ? 0xFFFFFFFF : 0x00000000);
            t.setBackground(d);
            t.setTextColor(s ? 0xFF111111 : 0xFFFFFFFF);
        }
    }

    void keyDown(int code) {
        if (code == KeyEvent.KEYCODE_DPAD_UP && sel > 0) sel--;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && sel < items.size() - 1) sel++;
        else if (code == KeyEvent.KEYCODE_BACK) close();
        paint();
    }

    void keyUp(int code) {
        if (!HomeActivity.isSelect(code)) return;
        if (ignoreUp) {
            ignoreUp = false;
            return;
        }
        if (items.isEmpty()) return;
        Item it = items.get(sel);
        close();
        it.run.run();
    }
}
