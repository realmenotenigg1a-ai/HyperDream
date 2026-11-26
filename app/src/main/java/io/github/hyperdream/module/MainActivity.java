package io.github.hyperdream.module;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

/** Settings editor and local motion lab, not an activation detector. */
public final class MainActivity extends Activity {
    private Switch enableDuration;
    private RadioGroup durationPresets;
    private TextView configurationStatus;
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
        preview = findViewById(R.id.motion_preview);
        cardStack = findViewById(R.id.card_stack);
        bind(SettingsStore.read(this));
        enableDuration.setOnCheckedChangeListener((button, checked) -> save());
        durationPresets.setOnCheckedChangeListener((group, checkedId) -> save());
        findViewById(R.id.reset_settings).setOnClickListener(view -> {
            bind(new AnimationSettings(false, 1f));
            save();
        });
        findViewById(R.id.play_preview).setOnClickListener(view -> preview.play(selectedScale()));
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

    private void save() {
        if (binding) {
            return;
        }
        preview.stop();
        AnimationSettings settings = new AnimationSettings(enableDuration.isChecked(), selectedScale());
        SettingsStore.write(this, settings.enabled, settings.durationScale);
        showConfiguration(settings);
        configurationStatus.setText(getString(R.string.config_saved,
                configurationStatus.getText()));
    }

    private void showConfiguration(AnimationSettings settings) {
        if (!settings.enabled) {
            configurationStatus.setText(R.string.config_off);
        } else if (settings.durationScale == 1f) {
            configurationStatus.setText(R.string.config_original);
        } else {
            configurationStatus.setText(getString(R.string.config_enabled,
                    Float.toString(settings.durationScale)));
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
