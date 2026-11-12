package com.kaca.launcher;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Satu ikon aplikasi: banner dengan sudut membulat, kilap kaca, dan efek saat dipilih. */
final class TileView extends FrameLayout {
    final HomeActivity a;
    final FrameLayout face;
    final View ring, sheen;
    boolean focused;

    TileView(HomeActivity a, AppStore.App app) {
        super(a);
        this.a = a;
        setClipChildren(false);
        setElevation(a.px(22));
        final float r = a.px(30);

        face = new FrameLayout(a);
        face.setOutlineProvider(HomeActivity.round(r));
        face.setClipToOutline(true);
        face.setElevation(a.px(6));

        if (app.banner != null) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(app.banner);
            face.addView(iv, new LayoutParams(HomeActivity.MP, HomeActivity.MP));
        } else {
            face.setBackground(HomeActivity.grad(GradientDrawable.Orientation.TL_BR, app.color, darken(app.color, .5f)));
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(a);
            ic.setImageDrawable(app.icon);
            col.addView(ic, new LinearLayout.LayoutParams(a.px(84), a.px(84)));
            TextView t = a.text(26, Color.WHITE, Typeface.create("sans-serif", Typeface.BOLD));
            t.setText(app.label);
            t.setSingleLine();
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(a.px(16), a.px(12), a.px(16), 0);
            col.addView(t, new LinearLayout.LayoutParams(HomeActivity.WC, HomeActivity.WC));
            face.addView(col, new LayoutParams(HomeActivity.MP, HomeActivity.MP));
        }

        View gloss = new View(a);
        gloss.setBackground(HomeActivity.grad(GradientDrawable.Orientation.TOP_BOTTOM, 0x38FFFFFF, 0x0AFFFFFF, 0x0F000000));
        face.addView(gloss, new LayoutParams(HomeActivity.MP, HomeActivity.MP));

        GradientDrawable ed = new GradientDrawable();
        ed.setCornerRadius(r);
        ed.setColor(0);
        ed.setStroke(Math.max(1, a.px(1.5f)), 0x2EFFFFFF);
        View edge = new View(a);
        edge.setBackground(ed);
        face.addView(edge, new LayoutParams(HomeActivity.MP, HomeActivity.MP));

        sheen = new View(a);
        sheen.setBackground(HomeActivity.grad(GradientDrawable.Orientation.LEFT_RIGHT, 0x00FFFFFF, 0x61FFFFFF, 0x00FFFFFF));
        sheen.setRotation(18);
        sheen.setAlpha(0f);
        LayoutParams sp = new LayoutParams(a.px(HomeActivity.TW * .4f), a.px(HomeActivity.TH * 1.6f));
        sp.topMargin = -a.px(HomeActivity.TH * .3f);
        face.addView(sheen, sp);

        GradientDrawable rd = new GradientDrawable();
        rd.setCornerRadius(r);
        rd.setColor(0);
        rd.setStroke(Math.max(2, a.px(3)), 0xA6FFFFFF);
        ring = new View(a);
        ring.setBackground(rd);
        ring.setAlpha(0f);
        face.addView(ring, new LayoutParams(HomeActivity.MP, HomeActivity.MP));

        addView(face, new LayoutParams(HomeActivity.MP, HomeActivity.MP));
    }

    static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    void setFocusedTile(boolean f) {
        if (f == focused) return;
        focused = f;
        animate().scaleX(f ? 1.15f : 1f).scaleY(f ? 1.15f : 1f)
                .translationY(f ? -a.px(6) : 0).translationZ(f ? a.px(12) : 0)
                .setDuration(350).setInterpolator(HomeActivity.EASE);
        face.animate().translationZ(f ? a.px(26) : 0).setDuration(350).setInterpolator(HomeActivity.EASE);
        ring.animate().alpha(f ? 1f : 0f).setDuration(250);
        if (f) {
            float w = a.px(HomeActivity.TW);
            sheen.animate().cancel();
            sheen.setTranslationX(-w * .6f);
            sheen.setAlpha(1f);
            sheen.animate().translationX(w * 1.3f).alpha(0f).setStartDelay(50).setDuration(900)
                    .setInterpolator(HomeActivity.EASE);
        }
    }
}
