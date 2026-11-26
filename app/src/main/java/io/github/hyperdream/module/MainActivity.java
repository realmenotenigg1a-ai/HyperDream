package io.github.hyperdream.module;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Settings editor and local motion lab, not an activation detector. */
public final class MainActivity extends Activity {
    private Switch enableDuration;
    private RadioGroup durationPresets;
    private RadioGroup curvePresets;
    private Switch scaleDelays;
    private TextView restartStatus;
    private TextView configurationStatus;
    private final ExecutorService restartWorker = Executors.newSingleThreadExecutor();
    private MotionPreviewView preview;
    private LinearLayout cardStack;
    private boolean binding;
    private final Runnable entrance = this::animateEntrance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.screen_root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        root.requestApplyInsets();
        enableDuration = findViewById(R.id.enable_duration);
        durationPresets = findViewById(R.id.duration_presets);
        configurationStatus = findViewById(R.id.configuration_status);
        curvePresets = findViewById(R.id.curve_presets);
        scaleDelays = findViewById(R.id.scale_delays);
        restartStatus = findViewById(R.id.restart_status);
        preview = findViewById(R.id.motion_preview);
        cardStack = findViewById(R.id.card_stack);
        bind(SettingsStore.read(this));
        enableDuration.setOnCheckedChangeListener((button, checked) -> save());
        durationPresets.setOnCheckedChangeListener((group, checkedId) -> save());
        curvePresets.setOnCheckedChangeListener((group, checkedId) -> save());
        scaleDelays.setOnCheckedChangeListener((button, checked) -> save());
        findViewById(R.id.reset_settings).setOnClickListener(view -> {
            bind(new AnimationSettings(false, 1f));
            save();
        });
        findViewById(R.id.play_preview).setOnClickListener(view -> preview.play(selectedScale(), selectedCurve(), scaleDelays.isChecked()));
        TextView details = findViewById(R.id.device_details);
        details.setText(DeviceDiagnostics.describe(this));
        findViewById(R.id.copy_diagnostics).setOnClickListener(view -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), details.getText()));
        });
        findViewById(R.id.restart_launcher).setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.restart_launcher)
                .setMessage(R.string.restart_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.restart_confirm_button, (dialog, which) -> restartLauncher())
                .show());
        if (savedInstanceState == null) {
            cardStack.post(entrance);
        }
    }

    private void bind(AnimationSettings settings) {
        binding = true;
        enableDuration.setChecked(settings.enabled);
        int selected = R.id.duration_default;
        if (settings.durationScale == 0.5f) {
            selected = R.id.duration_fast;
        } else if (settings.durationScale == 0.75f) {
            selected = R.id.duration_quick;
        } else if (settings.durationScale == 1.5f) {
            selected = R.id.duration_relaxed;
        }
        durationPresets.check(selected);
        int curveId = R.id.curve_system;
        if (AnimationSettings.CURVE_SMOOTH.equals(settings.curve)) {
            curveId = R.id.curve_smooth;
        } else if (AnimationSettings.CURVE_EMPHASIZED.equals(settings.curve)) {
            curveId = R.id.curve_emphasized;
        } else if (AnimationSettings.CURVE_LINEAR.equals(settings.curve)) {
            curveId = R.id.curve_linear;
        }
        curvePresets.check(curveId);
        scaleDelays.setChecked(settings.scaleDelays);
        showConfiguration(settings);
        binding = false;
    }

    private float selectedScale() {
        int selected = durationPresets.getCheckedRadioButtonId();
        if (selected == R.id.duration_fast) {
            return 0.5f;
        } else if (selected == R.id.duration_quick) {
            return 0.75f;
        } else if (selected == R.id.duration_relaxed) {
            return 1.5f;
        }
        return 1f;
    }

    private String selectedCurve() {
        int selected = curvePresets.getCheckedRadioButtonId();
        if (selected == R.id.curve_smooth) {
            return AnimationSettings.CURVE_SMOOTH;
        } else if (selected == R.id.curve_emphasized) {
            return AnimationSettings.CURVE_EMPHASIZED;
        } else if (selected == R.id.curve_linear) {
            return AnimationSettings.CURVE_LINEAR;
        }
        return AnimationSettings.CURVE_SYSTEM;
    }

    private void restartLauncher() {
        View button = findViewById(R.id.restart_launcher);
        button.setEnabled(false);
        restartStatus.setText(R.string.restart_running);
        android.content.Context app = getApplicationContext();
        restartWorker.execute(() -> {
            LauncherRestarter.Result result = LauncherRestarter.restart(app);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                button.setEnabled(true);
                int message;
                switch (result) {
                    case REQUEST_COMPLETED: message = R.string.restart_completed; break;
                    case ROOT_UNAVAILABLE: message = R.string.restart_no_root; break;
                    case LAUNCHER_UNAVAILABLE: message = R.string.launcher_not_installed; break;
                    case NOT_DEFAULT_HOME: message = R.string.restart_not_default; break;
                    case PROFILE_UNSUPPORTED: message = R.string.restart_profile; break;
                    case TIMEOUT: message = R.string.restart_timeout; break;
                    case BUSY: message = R.string.restart_busy; break;
                    case SAVE_FAILED: message = R.string.restart_save_failed; break;
                    default: message = R.string.restart_failed;
                }
                restartStatus.setText(message);
            });
        });
    }

    private void save() {
        if (binding) {
            return;
        }
        preview.stop();
        AnimationSettings settings = new AnimationSettings(enableDuration.isChecked(), selectedScale(),
                selectedCurve(), scaleDelays.isChecked());
        SettingsStore.write(this, settings);
        showConfiguration(settings);
        configurationStatus.setText(getString(R.string.config_saved,
                configurationStatus.getText()));
    }

    private void showConfiguration(AnimationSettings settings) {
        if (!settings.enabled) {
            configurationStatus.setText(R.string.config_off);
        } else if (!settings.hasOverrides()) {
            configurationStatus.setText(R.string.config_original);
        } else {
            configurationStatus.setText(getString(R.string.config_enabled,
                    Float.toString(settings.durationScale), settings.curve,
                    getString(settings.scaleDelays ? R.string.delay_scaled : R.string.delay_original)));
        }
    }

    private void animateEntrance() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return;
        }
        float distance = 14 * getResources().getDisplayMetrics().density;
        for (int i = 0; i < cardStack.getChildCount(); i++) {
            View child = cardStack.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(distance);
            child.animate().alpha(1f).translationY(0f)
                    .setDuration(320).setStartDelay(Math.min(i * 35L, 210L))
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f)).start();
        }
    }

    @Override
    protected void onDestroy() {
        // Let an already-confirmed short restart finish after Home backgrounds this Activity.
        // No thread interruption between force-stop and relaunch; no long-lived service.
        restartWorker.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        cardStack.removeCallbacks(entrance);
        for (int i = 0; i < cardStack.getChildCount(); i++) {
            View child = cardStack.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(1f);
            child.setTranslationY(0f);
        }
        preview.stop();
        super.onStop();
    }
}
