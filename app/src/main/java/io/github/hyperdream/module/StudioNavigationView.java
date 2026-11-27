package io.github.hyperdream.module;

import android.content.Context;
import android.util.AttributeSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Actual Material navigation with a small index adapter for saved studio destinations. */
public final class StudioNavigationView extends BottomNavigationView {
    public static final int HOME = 0, ANIMATOR = 1, FINGERPRINTS = 2, DEVICE = 3, TAB_COUNT = 4;
    private static final int[] IDS = {R.id.nav_home, R.id.nav_animator, R.id.nav_fingerprints, R.id.nav_device};
    public interface OnTabSelectedListener { void onTabSelected(int tab); }

    public StudioNavigationView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        setOnItemSelectedListener(item -> {
            for (int i = 0; i < IDS.length; i++) {
                if (IDS[i] == item.getItemId()) { listener.onTabSelected(i); return true; }
            }
            return false;
        });
    }

    public int getSelectedTab() {
        for (int i = 0; i < IDS.length; i++) { if (getSelectedItemId() == IDS[i]) { return i; } }
        return HOME;
    }

    public void selectTab(int tab, boolean animate) {
        setSelectedItemId(IDS[Math.max(HOME, Math.min(DEVICE, tab))]);
    }
}
