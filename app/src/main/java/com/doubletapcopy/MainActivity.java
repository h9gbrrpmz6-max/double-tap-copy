package com.doubletapcopy;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        Button btnOpenSettings = findViewById(R.id.btn_open_settings);

        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        if (isServiceEnabled()) {
            tvStatus.setText(R.string.status_enabled);
            tvStatus.setTextColor(0xFF07C160);
        } else {
            tvStatus.setText(R.string.status_disabled);
            tvStatus.setTextColor(0xFF888888);
        }
    }

    private boolean isServiceEnabled() {
        String serviceName = getPackageName() + "/" + WeChatCopyService.class.getCanonicalName();
        try {
            String enabledServices = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (enabledServices != null) {
                for (String s : enabledServices.split(":")) {
                    ComponentName cn = ComponentName.unflattenFromString(s);
                    if (cn != null && TextUtils.equals(serviceName, cn.flattenToString())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
}
