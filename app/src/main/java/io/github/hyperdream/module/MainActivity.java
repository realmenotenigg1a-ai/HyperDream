package io.github.hyperdream.module;

import android.animation.ValueAnimator;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.slider.Slider;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Settings editor and local motion lab, not an activation detector. */
public final class MainActivity extends AppCompatActivity {
    private CompoundButton enableDuration;
    private RadioGroup durationPresets;
    private CompoundButton enableSpring;
    private Slider springResponse;
    private Slider springDamping;
    private TextView hookReport;
    private RadioGroup curvePresets;
    private CompoundButton scaleDelays;
    private TextView restartStatus;
    private TextView configurationStatus;
    private final ExecutorService restartWorker = Executors.newSingleThreadExecutor();
    private MotionPreviewView preview;
    private FingerprintPreviewView fingerprintPreview;
    private StudioNavigationView navigation;
    private final StudioMotion studioMotion = new StudioMotion();
    private int activeTab = -1;
    private int[] pageScrolls = new int[StudioNavigationView.TAB_COUNT];
    private boolean diagnosticsExpanded;
    private android.view.ViewTreeObserver.OnPreDrawListener pendingScrollRestore;
    private LinearLayout cardStack;
    private boolean binding;
    private final Runnable entrance = this::animateEntrance;
    private final Runnable restartStateChanged = () -> runOnUiThread(() -> {
        if (!isFinishing() && !isDestroyed()) { setRestartButtonsEnabled(!RestartGuard.RUNNING.get()); }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
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
        if (savedInstanceState != null) {
            int[] restoredScrolls = savedInstanceState.getIntArray("page_scrolls");
            if (restoredScrolls != null && restoredScrolls.length == StudioNavigationView.TAB_COUNT) {
                pageScrolls = restoredScrolls;
            } else if (restoredScrolls != null && restoredScrolls.length == 3) {
                pageScrolls[0] = restoredScrolls[0]; pageScrolls[1] = restoredScrolls[1];
                pageScrolls[StudioNavigationView.DEVICE] = restoredScrolls[2];
            }
            diagnosticsExpanded = savedInstanceState.getBoolean("diagnostics_expanded", false);
        }
        enableDuration = findViewById(R.id.enable_duration);
        durationPresets = findViewById(R.id.duration_presets);
        configurationStatus = findViewById(R.id.configuration_status);
        curvePresets = findViewById(R.id.curve_presets);
        scaleDelays = findViewById(R.id.scale_delays);
        restartStatus = findViewById(R.id.restart_status);
        preview = findViewById(R.id.motion_preview);
        fingerprintPreview = findViewById(R.id.fingerprint_preview);
        android.content.SharedPreferences fingerprintSettings = getSharedPreferences("fingerprint_preview", MODE_PRIVATE);
        String effect = FingerprintPreviewView.sanitize(fingerprintSettings.getString("effect", FingerprintPreviewView.RIPPLE));
        fingerprintPreview.setEffect(effect);
        RadioGroup effects = findViewById(R.id.fingerprint_effects);
        effects.check(FingerprintPreviewView.HALO.equals(effect) ? R.id.fingerprint_halo
                : FingerprintPreviewView.SPARK.equals(effect) ? R.id.fingerprint_spark : R.id.fingerprint_ripple);
        effects.setOnCheckedChangeListener((group, id) -> {
            String selectedEffect = id == R.id.fingerprint_halo ? FingerprintPreviewView.HALO
                    : id == R.id.fingerprint_spark ? FingerprintPreviewView.SPARK : FingerprintPreviewView.RIPPLE;
            fingerprintSettings.edit().putString("effect", selectedEffect).apply();
            fingerprintPreview.setEffect(selectedEffect);
            if (activeTab == StudioNavigationView.FINGERPRINTS) { fingerprintPreview.play(); }
        });
        findViewById(R.id.replay_fingerprint).setOnClickListener(view -> fingerprintPreview.play());
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
        Slider.OnChangeListener springListener = (slider, value, fromUser) -> {
            updateSpringLabels();
            if (fromUser) { saveSpring(); }
        };
        springResponse.addOnChangeListener(springListener);
        springDamping.addOnChangeListener(springListener);
        Slider.OnSliderTouchListener sliderTouch = new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider slider) {}
            @Override public void onStopTrackingTouch(Slider slider) {
                StudioMotion.acknowledge(findViewById(R.id.spring_saved));
            }
        };
        springResponse.addOnSliderTouchListener(sliderTouch);
        springDamping.addOnSliderTouchListener(sliderTouch);
        findViewById(R.id.reset_spring).setOnClickListener(view -> {
            bindSpring(new SpringSettings(false, 1f, 1f));
            saveSpring();
        });
        findViewById(R.id.refresh_hooks).setOnClickListener(view -> {
            refreshHookReport();
            StudioMotion.acknowledge(hookReport);
        });
        updateDiagnosticDisclosure(false);
        findViewById(R.id.toggle_diagnostics).setOnClickListener(view -> {
            diagnosticsExpanded = !diagnosticsExpanded;
            updateDiagnosticDisclosure(true);
        });
        navigation = findViewById(R.id.studio_navigation);
        // Root applies system-bar insets once; do not add a second navigation inset.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(navigation, (view, insets) -> insets);
        navigation.setOnTabSelectedListener(tab -> showPage(tab, true));
        int initialTab = savedInstanceState == null ? StudioNavigationView.HOME
                : savedInstanceState.getInt("studio_tab", StudioNavigationView.HOME);
        if (savedInstanceState != null && savedInstanceState.getInt("nav_tab_count", 3) == 3 && initialTab == 2) {
            initialTab = StudioNavigationView.DEVICE;
        }
        navigation.selectTab(initialTab, false);
        showPage(navigation.getSelectedTab(), false);
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
        View.OnClickListener copyDiagnostics = view -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name),
                    DeviceDiagnostics.describe(this) + "\n\n" + runtimeDetails.getText()
                            + "\n\n" + HookReports.describe(this)));
            Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show();
        };
        findViewById(R.id.hook_report_button).setOnClickListener(view -> showHookReportOptions());
        findViewById(R.id.copy_diagnostics).setOnClickListener(copyDiagnostics);
        findViewById(R.id.copy_hook_diagnostics).setOnClickListener(copyDiagnostics);
        findViewById(R.id.restart_launcher).setOnClickListener(view -> showRestartConfirmation());
        findViewById(R.id.restart_systemui).setOnClickListener(view -> showSystemUiConfirmation());
        studioMotion.bind(root);
        studioMotion.refresh();
        if (savedInstanceState == null) {
            cardStack.post(entrance);
        }
    }

    private void showHookReportOptions() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.hook_report_title)
                .setMessage(R.string.hook_report_consent)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.report_without_root, (dialog, which) -> collectHookReport(false))
                .setPositiveButton(R.string.report_with_root, (dialog, which) -> collectHookReport(true))
                .show();
    }

    private void collectHookReport(boolean withRoot) {
        View button = findViewById(R.id.hook_report_button);
        button.setEnabled(false);
        Toast.makeText(this, R.string.report_collecting, Toast.LENGTH_SHORT).show();
        android.content.Context application = getApplicationContext();
        restartWorker.execute(() -> {
            String nativeOutput = withRoot ? NativeHookReport.collectRoot(application)
                    : "Not requested. Native runtime logs require explicit root collection.";
            String runtime = LauncherRuntimeProbe.describe(application);
            String report = NativeHookReport.assemble(application, runtime, nativeOutput);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) { return; }
                button.setEnabled(true);
                android.widget.ScrollView scroll = new android.widget.ScrollView(this);
                TextView text = new TextView(this);
                int padding = Math.round(20 * getResources().getDisplayMetrics().density);
                text.setPadding(padding, padding, padding, padding);
                text.setText(report);
                text.setTextIsSelectable(true);
                scroll.addView(text);
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.hook_report_title).setView(scroll)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(R.string.report_copy, (dialog, which) -> {
                            getSystemService(ClipboardManager.class).setPrimaryClip(
                                    ClipData.newPlainText("HyperDream hook report", report));
                            Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show();
                        })
                        .setPositiveButton(R.string.report_share, (dialog, which) -> {
                            android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
                            send.setType("text/plain");
                            send.putExtra(android.content.Intent.EXTRA_SUBJECT, "HyperDream hook report");
                            send.putExtra(android.content.Intent.EXTRA_TEXT, report);
                            try { startActivity(android.content.Intent.createChooser(send, getString(R.string.report_share))); }
                            catch (android.content.ActivityNotFoundException unavailable) {
                                Toast.makeText(this, R.string.report_no_share_app, Toast.LENGTH_LONG).show();
                            }
                        }).show();
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHookReport();
        studioMotion.refresh();
        RestartGuard.observe(restartStateChanged);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("studio_tab", navigation.getSelectedTab());
        state.putInt("nav_tab_count", StudioNavigationView.TAB_COUNT);
        if (activeTab >= 0) { pageScrolls[activeTab] = findViewById(R.id.studio_scroll).getScrollY(); }
        state.putIntArray("page_scrolls", pageScrolls);
        state.putBoolean("diagnostics_expanded", diagnosticsExpanded);
        super.onSaveInstanceState(state);
    }

    private void showPage(int tab, boolean animate) {
        android.widget.ScrollView scroll = findViewById(R.id.studio_scroll);
        if (activeTab >= 0 && activeTab != tab) { pageScrolls[activeTab] = scroll.getScrollY(); }
        activeTab = tab;
        ((TextView) findViewById(R.id.studio_heading)).setText(tab == StudioNavigationView.ANIMATOR
                ? R.string.animator_hero : tab == StudioNavigationView.FINGERPRINTS ? R.string.fingerprint_hero
                : tab == StudioNavigationView.DEVICE ? R.string.device_hero : R.string.glass_hero);
        ((TextView) findViewById(R.id.studio_caption)).setText(tab == StudioNavigationView.ANIMATOR
                ? R.string.animator_caption : tab == StudioNavigationView.FINGERPRINTS ? R.string.fingerprint_caption
                : tab == StudioNavigationView.DEVICE ? R.string.device_caption : R.string.glass_subtitle);
        preview.stop();
        fingerprintPreview.stop();
        int selected = tab == StudioNavigationView.ANIMATOR ? R.id.page_motion
                : tab == StudioNavigationView.FINGERPRINTS ? R.id.page_fingerprints
                : tab == StudioNavigationView.DEVICE ? R.id.page_setup : R.id.page_spring;
        for (int id : new int[]{R.id.page_spring, R.id.page_motion, R.id.page_setup, R.id.page_features, R.id.page_fingerprints}) {
            View page = findViewById(id);
            page.animate().cancel();
            boolean visible = id == selected || (tab == StudioNavigationView.DEVICE && id == R.id.page_features);
            page.setVisibility(visible ? View.VISIBLE : View.GONE);
            page.setAlpha(1f);
            page.setTranslationY(0f);
            if (visible && animate && ValueAnimator.areAnimatorsEnabled()) {
                page.setAlpha(0f);
                page.setTranslationY(10 * getResources().getDisplayMetrics().density);
                page.animate().alpha(1f).translationY(0f).setDuration(220).setStartDelay(0)
                        .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                        .start();
            }
        }
        cancelScrollRestore();
        // Restore only after the newly visible page has been measured, not against
        // the previous page's shorter scroll range. Rapid tab taps replace this listener.
        pendingScrollRestore = () -> {
            cancelScrollRestore();
            if (activeTab == tab && !isDestroyed()) {
                scroll.scrollTo(0, Math.max(0, pageScrolls[tab]));
            }
            return true;
        };
        scroll.getViewTreeObserver().addOnPreDrawListener(pendingScrollRestore);
        if (animate) { StudioMotion.acknowledge(findViewById(R.id.studio_heading)); }
        refreshHookReport();
    }

    private void cancelScrollRestore() {
        if (pendingScrollRestore != null) {
            android.view.ViewTreeObserver observer = findViewById(R.id.studio_scroll).getViewTreeObserver();
            if (observer.isAlive()) { observer.removeOnPreDrawListener(pendingScrollRestore); }
            pendingScrollRestore = null;
        }
    }

    private void updateDiagnosticDisclosure(boolean animate) {
        View details = findViewById(R.id.diagnostic_details);
        details.animate().cancel();
        details.setAlpha(1);
        details.setTranslationY(0);
        details.setVisibility(diagnosticsExpanded ? View.VISIBLE : View.GONE);
        android.widget.Button button = findViewById(R.id.toggle_diagnostics);
        button.setText(diagnosticsExpanded ? R.string.hide_diagnostics : R.string.show_diagnostics);
        button.setStateDescription(getString(diagnosticsExpanded ? R.string.expanded : R.string.collapsed));
        if (animate && diagnosticsExpanded && ValueAnimator.areAnimatorsEnabled()) {
            details.setAlpha(0);
            details.setTranslationY(6 * getResources().getDisplayMetrics().density);
            details.animate().alpha(1).translationY(0).setDuration(200).setStartDelay(0).start();
        }
    }

    private void showRestartConfirmation() {
        StudioMotion dialogMotion = new StudioMotion();
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restart_launcher)
                .setMessage(R.string.restart_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.restart_confirm_button, (confirmation, which) -> restartLauncher())
                .create();
        dialog.setOnShowListener(shown -> {
            dialogMotion.bind(dialog.getWindow().getDecorView());
            dialogMotion.refresh();
        });
        dialog.setOnDismissListener(dismissed -> dialogMotion.stop());
        dialog.show();
    }

    private void setRestartButtonsEnabled(boolean enabled) {
        findViewById(R.id.restart_launcher).setEnabled(enabled);
        findViewById(R.id.restart_systemui).setEnabled(enabled);
    }

    private void showSystemUiConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restart_systemui)
                .setMessage(R.string.systemui_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.systemui_confirm, (dialog, which) -> restartSystemUi())
                .show();
    }

    private void restartSystemUi() {
        setRestartButtonsEnabled(false);
        TextView status = findViewById(R.id.systemui_restart_status);
        status.setText(R.string.systemui_running);
        android.content.Context application = getApplicationContext();
        restartWorker.execute(() -> {
            SystemUiRestarter.Result result = SystemUiRestarter.restart(application);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) { return; }
                setRestartButtonsEnabled(true);
                int message;
                switch (result) {
                    case REQUEST_COMPLETED: message = R.string.systemui_requested; break;
                    case ROOT_UNAVAILABLE: message = R.string.systemui_no_root; break;
                    case TIMEOUT: message = R.string.systemui_timeout; break;
                    case PACKAGE_UNAVAILABLE: message = R.string.systemui_unavailable; break;
                    case PROFILE_UNSUPPORTED: message = R.string.systemui_profile; break;
                    case BUSY: message = R.string.restart_busy; break;
                    default: message = R.string.systemui_failed;
                }
                status.setText(message);
            });
        });
    }

    private void bindSpring(SpringSettings settings) {
        binding = true;
        enableSpring.setChecked(settings.enabled);
        springResponse.setValue(Math.round((settings.responseMultiplier - 0.5f) * 20));
        springDamping.setValue(Math.round((settings.damping - 0.5f) * 20));
        updateSpringLabels();
        binding = false;
    }

    private float responseValue() {
        return Math.round((0.5f + springResponse.getValue() * 0.05f) * 100) / 100f;
    }

    private float dampingValue() {
        return Math.round((0.5f + springDamping.getValue() * 0.05f) * 100) / 100f;
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
            ((TextView) findViewById(R.id.spring_saved)).setText(R.string.saved_locally);
            if (!springResponse.isPressed() && !springDamping.isPressed()) {
                StudioMotion.acknowledge(findViewById(R.id.spring_saved));
            }
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
        setRestartButtonsEnabled(false);
        restartStatus.setText(R.string.restart_running);
        android.content.Context app = getApplicationContext();
        restartWorker.execute(() -> {
            LauncherRestarter.Result result = LauncherRestarter.restart(app);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setRestartButtonsEnabled(true);
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
        ((TextView) findViewById(R.id.motion_saved)).setText(R.string.saved_locally);
        StudioMotion.acknowledge(findViewById(R.id.motion_saved));
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
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .start();
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
        RestartGuard.removeObserver(restartStateChanged);
        cancelScrollRestore();
        studioMotion.stop();
        for (int id : new int[]{R.id.spring_saved, R.id.motion_saved, R.id.studio_heading,
                R.id.diagnostic_details, R.id.hook_report}) {
            View view = findViewById(id);
            view.animate().cancel(); view.setAlpha(1); view.setTranslationY(0);
        }
        cardStack.removeCallbacks(entrance);
        for (int i = 0; i < cardStack.getChildCount(); i++) {
            View child = cardStack.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(1f);
            child.setTranslationY(0f);
        }
        preview.stop();
        fingerprintPreview.stop();
        super.onStop();
    }
}
