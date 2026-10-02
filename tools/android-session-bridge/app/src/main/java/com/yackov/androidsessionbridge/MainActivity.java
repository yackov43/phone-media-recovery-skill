package com.yackov.androidsessionbridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status;
    private EditText code;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Android Session Bridge");
        title.setTextSize(24);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView help = new TextView(this);
        help.setText("\nPair this phone once. After pairing and enabling Accessibility, your authorized ChatGPT session can run QA actions without Termux or tunnels.\n");
        root.addView(help);

        code = new EditText(this);
        code.setHint("6-digit pairing code");
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(code);

        Button pair = new Button(this);
        pair.setText("Pair phone");
        pair.setOnClickListener(v -> pair());
        root.addView(pair);

        Button access = new Button(this);
        access.setText("Enable Accessibility control");
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(access);

        status = new TextView(this);
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        status.setText(id.paired ? "\nPaired. Enable Accessibility if it is not already enabled." : "\nNot paired yet.");
        root.addView(status);

        setContentView(root);
    }

    private void pair() {
        String pairingCode = code.getText().toString().trim();
        if (pairingCode.length() < 6) {
            status.setText("Enter the pairing code shown in ChatGPT.");
            return;
        }
        status.setText("Pairing…");
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        io.execute(() -> {
            try {
                JSONObject r = new BridgeApi().enroll(pairingCode, id, android.os.Build.MODEL);
                boolean ok = r.optBoolean("ok", false);
                if (ok) DeviceIdentity.markPaired(this, true);
                runOnUiThread(() -> {
                    if (ok) {
                        status.setText("Paired successfully. Now enable Accessibility control.");
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } else {
                        status.setText("Pairing failed: " + r.optString("error", "unknown"));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Pairing error: " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
