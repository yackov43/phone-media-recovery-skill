package com.yackov.androidsessionbridge;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11, 18, 32));
        getWindow().setNavigationBarColor(Color.rgb(11, 18, 32));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(11, 18, 32));
        root.setAlpha(0f);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_foreground);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.rgb(17, 28, 46));
        iconBg.setCornerRadius(dp(30));
        icon.setBackground(iconBg);
        icon.setPadding(dp(18), dp(18), dp(18), dp(18));
        icon.setScaleX(0.82f);
        icon.setScaleY(0.82f);
        root.addView(icon, new LinearLayout.LayoutParams(dp(118), dp(118)));

        TextView title = new TextView(this);
        title.setText("Android Session Bridge");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(248, 250, 252));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = wrap();
        titleLp.setMargins(0, dp(24), 0, dp(8));
        root.addView(title, titleLp);

        TextView copy = new TextView(this);
        copy.setText("Private GPT Session Control\nQA · Automation · Mini‑Agents");
        copy.setTextSize(16);
        copy.setTextColor(Color.rgb(148, 163, 184));
        copy.setGravity(Gravity.CENTER);
        copy.setLineSpacing(0f, 1.18f);
        root.addView(copy, wrap());

        TextView secure = new TextView(this);
        secure.setText("●  Secure device bridge");
        secure.setTextSize(13);
        secure.setTextColor(Color.rgb(45, 212, 191));
        secure.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams secLp = wrap();
        secLp.setMargins(0, dp(22), 0, 0);
        root.addView(secure, secLp);

        setContentView(root);

        root.animate()
                .alpha(1f)
                .setDuration(360L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        icon.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(620L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        handler.postDelayed(() -> {
            root.animate().alpha(0f).setDuration(220L).withEndAction(() -> {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }).start();
        }, 1900L);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
