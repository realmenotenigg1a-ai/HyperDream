package io.github.hyperdream.module;

import android.app.Activity;
import android.os.Bundle;

/** Informational screen; opening this activity does not prove module activation. */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
