package io.github.hyperdream.module;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.SeekBar;

import java.util.ArrayList;
import java.util.List;

/** Consistent native pressed feedback without consuming touch, click or accessibility events. */
final class StudioMotion {
    private final List<View> controls = new ArrayList<>();

    void bind(View view) {
        if (view instanceof Button || view instanceof SeekBar) { controls.add(view); }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { bind(group.getChildAt(i)); }
        }
    }

    void refresh() { refresh(ValueAnimator.areAnimatorsEnabled()); }

    void refresh(boolean motionEnabled) {
        for (View control : controls) {
            control.setStateListAnimator(null);
            control.setScaleX(1); control.setScaleY(1);
            if (!motionEnabled) { continue; }
            StateListAnimator states = new StateListAnimator();
            states.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed},
                    scale(control, 0.975f, 85));
            states.addState(new int[]{}, scale(control, 1f, 190));
            control.setStateListAnimator(states);
        }
    }

    private static AnimatorSet scale(View view, float value, long duration) {
        AnimatorSet animator = new AnimatorSet();
        animator.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, value),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, value));
        animator.setDuration(duration);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        return animator;
    }

    static void acknowledge(View view) {
        view.animate().cancel();
        view.setTranslationY(0);
        view.setAlpha(1);
        if (ValueAnimator.areAnimatorsEnabled() && view.isShown()) {
            view.setAlpha(0.6f);
            view.animate().alpha(1).setDuration(180).setStartDelay(0).start();
        }
    }

    void stop() {
        for (View control : controls) {
            control.setStateListAnimator(null);
            control.setScaleX(1); control.setScaleY(1);
        }
    }
}
