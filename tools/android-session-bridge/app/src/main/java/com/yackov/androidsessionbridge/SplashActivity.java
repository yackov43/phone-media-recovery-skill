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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(11, 18, 32));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_foreground);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.rgb(17, 28, 46));
        iconBg.setCornerRadius(dp(28));
        icon.setBackground(iconBg);
        icon.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.addView(icon, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView title = new TextView(this);
        title.setText("Android Session Bridge");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(248, 250, 252));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = wrap();
        titleLp.setMargins(0, dp(24), 0, dp(8));
        root.addView(title, titleLp);

        TextView copy = new TextView(this);
        copy.setText("גשר פרטי בין הסשן שלך ב־ChatGPT לבין הטלפון\nלבדיקות QA, שליטה, אבטחה ואוטומציה");
        copy.setTextSize(16);
        copy.setTextColor(Color.rgb(148, 163, 184));
        copy.setGravity(Gravity.CENTER);
        copy.setLineSpacing(0f, 1.2f);
        root.addView(copy, wrap());

        TextView secure = new TextView(this);
        secure.setText("●  Private control channel");
        secure.setTextSize(13);
        secure.setTextColor(Color.rgb(45, 212, 191));
        secure.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams secLp = wrap();
        secLp.setMargins(0, dp(22), 0, 0);
        root.addView(secure, secLp);

        setContentView(root);

        handler.postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, 1400);
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
