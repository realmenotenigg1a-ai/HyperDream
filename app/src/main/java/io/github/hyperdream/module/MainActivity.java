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
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Settings editor and local motion lab, not an activation detector. */
public final class MainActivity extends Activity {
    private Switch enableDuration;
    private RadioGroup durationPresets;
    private Switch enableSpring;
    private SeekBar springResponse;
    private SeekBar springDamping;
    private TextView hookReport;
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
        TextView identity = findViewById(R.id.build_identity);
        identity.setText(getString(R.string.build_identity, BuildConfig.VERSION_NAME, BuildConfig.BUILD_ID));
        enableSpring = findViewById(R.id.enable_spring);
        springResponse = findViewById(R.id.spring_response);
        springDamping = findViewById(R.id.spring_damping);
        hookReport = findViewById(R.id.hook_report);
        bind(SettingsStore.read(this));
        bindSpring(SettingsStore.readSpring(this));
        enableSpring.setOnCheckedChangeListener((button, checked) -> saveSpring());
        SeekBar.OnSeekBarChangeListener springListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateSpringLabels();
                if (fromUser) { saveSpring(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        };
        springResponse.setOnSeekBarChangeListener(springListener);
        springDamping.setOnSeekBarChangeListener(springListener);
        findViewById(R.id.reset_spring).setOnClickListener(view -> {
            bindSpring(new SpringSettings(false, 1f, 1f));
            saveSpring();
        });
        findViewById(R.id.refresh_hooks).setOnClickListener(view -> refreshHookReport());
        RadioGroup tabs = findViewById(R.id.studio_tabs);
        tabs.setOnCheckedChangeListener((group, checkedId) -> showPage(checkedId, true));
        int initialTab = savedInstanceState == null ? R.id.tab_spring
                : savedInstanceState.getInt("studio_tab", R.id.tab_spring);
        tabs.check(initialTab);
        showPage(initialTab, false);
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
        TextView runtimeDetails = findViewById(R.id.runtime_details);
        android.content.Context application = getApplicationContext();
        restartWorker.execute(() -> {
            String runtime = LauncherRuntimeProbe.describe(application);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) { runtimeDetails.setText(runtime); }
            });
        });
        findViewById(R.id.copy_diagnostics).setOnClickListener(view -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name),
                    DeviceDiagnostics.describe(this) + "\n\n" + runtimeDetails.getText()
                            + "\n\n" + HookReports.describe(this)));
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

    @Override
    protected void onResume() {
        super.onResume();
        refreshHookReport();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("studio_tab", ((RadioGroup) findViewById(R.id.studio_tabs)).getCheckedRadioButtonId());
        super.onSaveInstanceState(state);
    }

    private void showPage(int tab, boolean animate) {
        preview.stop();
        int selected = tab == R.id.tab_motion ? R.id.page_motion
                : tab == R.id.tab_device ? R.id.page_setup : R.id.page_spring;
        for (int id : new int[]{R.id.page_spring, R.id.page_motion, R.id.page_setup, R.id.page_features}) {
            View page = findViewById(id);
            page.animate().cancel();
            boolean visible = id == selected || (tab == R.id.tab_device && id == R.id.page_features);
            page.setVisibility(visible ? View.VISIBLE : View.GONE);
            page.setAlpha(1f);
            page.setTranslationY(0f);
            if (visible && animate && ValueAnimator.areAnimatorsEnabled()) {
                page.setAlpha(0f);
                page.setTranslationY(10 * getResources().getDisplayMetrics().density);
                page.animate().alpha(1f).translationY(0f).setDuration(220).setStartDelay(0)
                        .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f)).start();
            }
        }
        refreshHookReport();
    }

    private void bindSpring(SpringSettings settings) {
        binding = true;
        enableSpring.setChecked(settings.enabled);
        springResponse.setProgress(Math.round((settings.responseMultiplier - 0.5f) * 20));
        springDamping.setProgress(Math.round((settings.damping - 0.5f) * 20));
        updateSpringLabels();
        binding = false;
    }

    private float responseValue() {
        return Math.round((0.5f + springResponse.getProgress() * 0.05f) * 100) / 100f;
    }

    private float dampingValue() {
        return Math.round((0.5f + springDamping.getProgress() * 0.05f) * 100) / 100f;
    }

    private void updateSpringLabels() {
        ((TextView) findViewById(R.id.spring_response_label)).setText(
                getString(R.string.spring_response_value, Float.toString(responseValue())));
        ((TextView) findViewById(R.id.spring_damping_label)).setText(
                getString(R.string.spring_damping_value, Float.toString(dampingValue())));
    }

    private void saveSpring() {
        if (!binding) {
            SettingsStore.writeSpring(this, new SpringSettings(enableSpring.isChecked(),
                    responseValue(), dampingValue()));
            refreshHookReport();
        }
    }

    private void refreshHookReport() {
        if (hookReport != null) { hookReport.setText(HookReports.describe(this)); }
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
                refreshHookReport();
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
