package io.github.hyperdream.module;

import android.app.Activity;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

/** Local settings editor, not an activation detector. Changes apply next launcher start. */
public final class MainActivity extends Activity {
    private Switch enableDuration;
    private RadioGroup durationPresets;
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        enableDuration = findViewById(R.id.enable_duration);
        durationPresets = findViewById(R.id.duration_presets);
        bind(SettingsStore.read(this));
        enableDuration.setOnCheckedChangeListener((button, checked) -> save());
        durationPresets.setOnCheckedChangeListener((group, checkedId) -> save());
        findViewById(R.id.reset_settings).setOnClickListener(view -> {
            bind(new AnimationSettings(false, 1f));
            save();
        });
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
        binding = false;
    }

    private void save() {
        if (binding) {
            return;
        }
        int selected = durationPresets.getCheckedRadioButtonId();
        float scale = 1f;
        if (selected == R.id.duration_fast) {
            scale = 0.5f;
        } else if (selected == R.id.duration_quick) {
            scale = 0.75f;
        } else if (selected == R.id.duration_relaxed) {
            scale = 1.5f;
        }
        SettingsStore.write(this, enableDuration.isChecked(), scale);
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }
}
