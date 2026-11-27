package io.github.hyperdream.module;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.animation.OvershootInterpolator;

/** App-local glass navigation. Captures only its sibling ScrollView, never the screen or launcher. */
public final class LiquidGlassNavigationView extends View {
    public static final int HOME = 0;
    public static final int ANIMATOR = 1;
    public static final int DEVICE = 2;

    public interface OnTabSelectedListener { void onTabSelected(int tab); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] point = new float[2];
    private final int[] sourceLocation = new int[2];
    private final int[] ownLocation = new int[2];
    private final String[] labels;
    private final String[] drawnLabels = new String[3];
    private final float density;
    private final int touchSlop;
    private View source;
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::requestBackdropRefresh;
    private final ViewTreeObserver.OnPreDrawListener dirtyListener = () -> {
        // A lens redraw alone must not start another capture. Only changed source content does.
        if (source != null && source.isDirty()) { requestBackdropRefresh(); }
        return true;
    };
    private final OnLayoutChangeListener layoutListener = (view, l, t, r, b, ol, ot, or, ob) -> requestBackdropRefresh();
    private final Runnable captureFrame = this::captureBackdrop;
    private final NavigationNodes nodes = new NavigationNodes();
    private boolean observing;
    private boolean capturePosted;
    private boolean paused;
    private boolean captureFailureLogged;
    private long lastCapture;
    private Bitmap captured;
    private Bitmap blurred;
    private Canvas captureCanvas;
    private int[] pixels, scratch, blurredPixels;
    private NavigationGlassShader glassShader;
    private GlassNavigationGeometry geometry = new GlassNavigationGeometry(0, 0, 0);
    private ValueAnimator settle;
    private ValueAnimator selectionPulse;
    private float iconEmphasis = 1;
    private OnTabSelectedListener listener;
    private int selected;
    private float lensX, lensY;
    private float downX, downY, grabX, grabY;
    private boolean dragCandidate, dragging;
    private int activePointer = -1;
    private int hovered = -1;
    private int accessibilityFocus = -1;

    public LiquidGlassNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        labels = new String[]{context.getString(R.string.nav_home), context.getString(R.string.tab_motion),
                context.getString(R.string.tab_device)};
        text.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
        text.setTextAlign(Paint.Align.CENTER);
        setFocusable(true);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        initializeShader();
    }

    private void initializeShader() {
        if (Build.VERSION.SDK_INT >= 33 && glassShader == null) {
            try {
                glassShader = new NavigationGlassShader(getContext());
            } catch (java.io.IOException | RuntimeException | LinkageError unsupported) {
                // Real captured/blurred bitmap fallback, not a screenshot asset.
                Log.w("HyperDreamGlass", "AGSL unavailable; using bitmap glass fallback");
            }
        }
    }

    public void setOnTabSelectedListener(OnTabSelectedListener value) { listener = value; }
    public int getSelectedTab() { return selected; }

    public void setBackdropSource(View value) {
        if (value == this) { throw new IllegalArgumentException("Backdrop must exclude the navigation view"); }
        android.view.ViewParent parent = getParent();
        while (parent instanceof View) {
            if (parent == value) { throw new IllegalArgumentException("Backdrop must not contain this navigation view"); }
            parent = parent.getParent();
        }
        stopObserving();
        source = value;
        if (isAttachedToWindow()) { startObserving(); }
        requestBackdropRefresh();
    }

    public void selectTab(int tab, boolean animate) {
        int next = Math.max(HOME, Math.min(DEVICE, tab));
        boolean changed = selected != next;
        selected = next;
        settleLens(animate);
        if (selectionPulse != null) { selectionPulse.cancel(); }
        iconEmphasis = 1;
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            selectionPulse = ValueAnimator.ofFloat(1, 1.10f, 1);
            selectionPulse.setDuration(220);
            selectionPulse.addUpdateListener(frame -> {
                iconEmphasis = (float) frame.getAnimatedValue(); invalidate();
            });
            selectionPulse.start();
        }
        if (changed) {
            sendVirtualEvent(selected, AccessibilityEvent.TYPE_VIEW_SELECTED);
            if (listener != null) { listener.onTabSelected(selected); }
        }
        invalidate();
    }

    public void requestBackdropRefresh() {
        if (source == null || paused || !isAttachedToWindow() || getWindowVisibility() != VISIBLE || capturePosted) { return; }
        capturePosted = true;
        long delay = Math.max(0, 33 - (SystemClock.uptimeMillis() - lastCapture));
        postDelayed(captureFrame, delay);
    }

    public void resumeGlass() {
        paused = false;
        requestBackdropRefresh();
    }

    public void pauseGlass() {
        paused = true;
        removeCallbacks(captureFrame);
        capturePosted = false;
        cancelSettle();
        if (selectionPulse != null) { selectionPulse.cancel(); selectionPulse = null; }
        iconEmphasis = 1;
        activePointer = -1;
        dragging = false;
        positionLens(geometry.tabCenter(selected), getHeight() / 2f);
    }

    private void startObserving() {
        if (source == null || observing) { return; }
        source.getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        source.getViewTreeObserver().addOnPreDrawListener(dirtyListener);
        source.addOnLayoutChangeListener(layoutListener);
        observing = true;
    }

    private void stopObserving() {
        if (source != null && observing) {
            if (source.getViewTreeObserver().isAlive()) {
                source.getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
                source.getViewTreeObserver().removeOnPreDrawListener(dirtyListener);
            }
            source.removeOnLayoutChangeListener(layoutListener);
        }
        observing = false;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        initializeShader();
        paused = false;
        startObserving();
        requestBackdropRefresh();
    }

    @Override protected void onDetachedFromWindow() {
        pauseGlass();
        stopObserving();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE && captureFrame != null) {
            removeCallbacks(captureFrame);
            capturePosted = false;
        } else if (captureFrame != null) { requestBackdropRefresh(); }
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cancelSettle();
        geometry = new GlassNavigationGeometry(w, h, 2 * density);
        positionLens(geometry.tabCenter(selected), h / 2f);
        for (int i = 0; i < 3; i++) {
            drawnLabels[i] = TextUtils.ellipsize(labels[i], text,
                    Math.max(1, w / 3f - 12 * density), TextUtils.TruncateAt.END).toString();
        }
        requestBackdropRefresh();
    }

    private void captureBackdrop() {
        capturePosted = false;
        if (paused || !isAttachedToWindow() || source == null || getWidth() <= 0 || getHeight() <= 0
                || source.getWidth() <= 0 || source.getHeight() <= 0 || getWindowVisibility() != VISIBLE) { return; }
        lastCapture = SystemClock.uptimeMillis();
        int width = Math.min(320, getWidth());
        int height = Math.max(1, Math.round(getHeight() * (width / (float) getWidth())));
        if (captured == null || captured.getWidth() != width || captured.getHeight() != height) {
            // Do not recycle bitmaps that may still be in a RenderThread display list.
            captured = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            captureCanvas = new Canvas(captured);
            pixels = new int[width * height];
            scratch = new int[pixels.length];
            blurredPixels = new int[pixels.length];
        }
        captured.eraseColor(Color.BLACK);
        boolean captureSucceeded = false;
        source.getLocationInWindow(sourceLocation);
        getLocationInWindow(ownLocation);
        int save = captureCanvas.save();
        try {
            captureCanvas.scale(width / (float) getWidth(), height / (float) getHeight());
            captureCanvas.translate(sourceLocation[0] - ownLocation[0], sourceLocation[1] - ownLocation[1]);
            source.draw(captureCanvas);
            captureSucceeded = true;
        } catch (RuntimeException unsupportedCapture) {
            if (!captureFailureLogged) {
                Log.w("HyperDreamGlass", "Backdrop capture unavailable; using tinted surface");
                captureFailureLogged = true;
            }
        } finally {
            captureCanvas.restoreToCount(save);
        }
        if (!captureSucceeded) { captured.eraseColor(Color.BLACK); }
        captured.getPixels(pixels, 0, width, 0, 0, width, height);
        int radius = Math.max(1, Math.min(32, Math.round(10 * density * width / getWidth())));
        BackdropBlur.frost(pixels, scratch, blurredPixels, width, height, radius);
        blurred.setPixels(blurredPixels, 0, width, 0, 0, width, height);
        if (Build.VERSION.SDK_INT >= 33 && glassShader != null) {
            glassShader.setBackdrop(blurred, width / (float) getWidth(), height / (float) getHeight());
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) { return; }
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        boolean shaderDrawn = false;
        if (Build.VERSION.SDK_INT >= 33 && glassShader != null && captured != null && canvas.isHardwareAccelerated()) {
            try {
                glassShader.draw(canvas, paint, w, h, lensX, lensY, geometry.lensRadius,
                        lensX - 36 * density, lensY - 65 * density);
                shaderDrawn = true;
            } catch (RuntimeException renderingFailure) {
                glassShader = null;
                paint.setShader(null);
            }
        }
        if (!shaderDrawn) { drawBitmapGlass(canvas); }
        for (int tab = 0; tab < 3; tab++) {
            float cx = geometry.tabCenter(tab);
            float iconY = h * 0.34f;
            int color = tab == selected ? Color.rgb(196, 255, 237) : Color.rgb(231, 239, 248);
            paint.setColor(color);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.1f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            int iconSave = canvas.save();
            if (tab == selected) { canvas.scale(iconEmphasis, iconEmphasis, cx, iconY); }
            drawIcon(canvas, tab, cx, iconY);
            canvas.restoreToCount(iconSave);
            text.setColor(color);
            canvas.drawText(drawnLabels[tab] == null ? labels[tab] : drawnLabels[tab], cx, h * 0.78f, text);
        }
        if (isFocused() || accessibilityFocus >= 0) {
            int focus = accessibilityFocus >= 0 ? accessibilityFocus : selected;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * density);
            paint.setColor(Color.rgb(167, 255, 225));
            rect.set(getWidth() * focus / 3f + 4 * density, 5 * density,
                    getWidth() * (focus + 1) / 3f - 4 * density, getHeight() - 5 * density);
            canvas.drawRoundRect(rect, 20 * density, 20 * density, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBitmapGlass(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        rect.set(1, 1, w - 1, h - 1);
        path.reset();
        path.addRoundRect(rect, h / 2, h / 2, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(path);
        paint.setColor(Color.WHITE);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        if (blurred != null) { canvas.drawBitmap(blurred, null, rect, paint); }
        paint.setColor(0xD1090D11);
        canvas.drawRect(0, 0, w, h, paint);
        canvas.restoreToCount(save);
        path.reset();
        path.addCircle(lensX, lensY, geometry.lensRadius, Path.Direction.CW);
        save = canvas.save();
        canvas.clipPath(path);
        canvas.scale(1.04f, 1.04f, lensX, lensY);
        paint.setColor(Color.WHITE);
        if (blurred != null) { canvas.drawBitmap(blurred, null, rect, paint); }
        paint.setColor(0xD0182831);
        canvas.drawRect(0, 0, w, h, paint);
        canvas.restoreToCount(save);
        paint.setShader(new LinearGradient(lensX - geometry.lensRadius, lensY - geometry.lensRadius,
                lensX + geometry.lensRadius, lensY + geometry.lensRadius,
                new int[]{0xB6FFFFFF, 0x3072B9CD, 0x70E4F6FF}, null, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * density);
        canvas.drawCircle(lensX, lensY, Math.max(0, geometry.lensRadius - density), paint);
        paint.setShader(null);
        paint.setColor(0x6589AABD);
        paint.setStrokeWidth(density);
        canvas.drawRoundRect(rect, h / 2, h / 2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawIcon(Canvas canvas, int tab, float cx, float cy) {
        float s = 12 * density;
        path.reset();
        if (tab == HOME) {
            path.moveTo(cx - s, cy); path.lineTo(cx, cy - s * 0.85f); path.lineTo(cx + s, cy);
            path.moveTo(cx - s * 0.72f, cy - s * 0.15f); path.lineTo(cx - s * 0.72f, cy + s);
            path.lineTo(cx - s * 0.2f, cy + s); path.lineTo(cx - s * 0.2f, cy + s * 0.3f);
            path.lineTo(cx + s * 0.2f, cy + s * 0.3f); path.lineTo(cx + s * 0.2f, cy + s);
            path.lineTo(cx + s * 0.72f, cy + s); path.lineTo(cx + s * 0.72f, cy - s * 0.15f);
            canvas.drawPath(path, paint);
        } else if (tab == ANIMATOR) {
            path.moveTo(cx - s * 0.65f, cy - s); path.lineTo(cx + s, cy);
            path.lineTo(cx - s * 0.65f, cy + s); path.close();
            canvas.drawPath(path, paint);
        } else {
            rect.set(cx - s * 0.62f, cy - s * 1.05f, cx + s * 0.62f, cy + s * 1.05f);
            canvas.drawRoundRect(rect, 4 * density, 4 * density, paint);
            canvas.drawLine(cx - s * 0.18f, cy + s * 0.72f, cx + s * 0.18f, cy + s * 0.72f, paint);
        }
    }

    private void positionLens(float x, float y) {
        geometry.clamp(x, y, point);
        lensX = point[0]; lensY = point[1];
        invalidate();
    }

    private void cancelSettle() {
        if (settle != null) { settle.cancel(); settle = null; }
    }

    private void settleLens(boolean animate) {
        cancelSettle();
        geometry.clamp(geometry.tabCenter(selected), getHeight() / 2f, point);
        float endX = point[0], endY = point[1];
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || getWidth() == 0) {
            positionLens(endX, endY);
            return;
        }
        float startX = lensX, startY = lensY;
        settle = ValueAnimator.ofFloat(0, 1);
        settle.setDuration(280);
        settle.setInterpolator(new OvershootInterpolator(0.55f));
        settle.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            positionLens(startX + (endX - startX) * fraction, startY + (endY - startY) * fraction);
        });
        settle.start();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) { return false; }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelSettle();
                activePointer = event.getPointerId(0);
                downX = event.getX(); downY = event.getY();
                grabX = lensX - downX; grabY = lensY - downY;
                dragCandidate = grabX * grabX + grabY * grabY <= geometry.lensRadius * geometry.lensRadius;
                dragging = false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                int index = event.findPointerIndex(activePointer);
                if (index < 0) { return true; }
                float x = event.getX(index), y = event.getY(index);
                if (dragCandidate && (dragging || Math.hypot(x - downX, y - downY) > touchSlop)) {
                    dragging = true;
                    if (getParent() != null) { getParent().requestDisallowInterceptTouchEvent(true); }
                    positionLens(x + grabX, y + grabY);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (activePointer < 0) { setPressed(false); return true; }
                int up = event.findPointerIndex(activePointer);
                float target = dragging ? lensX : up < 0 ? downX : event.getX(up);
                activePointer = -1;
                setPressed(false);
                selectTab(geometry.nearestTab(target), true);
                dragging = false;
                performClick();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == activePointer) {
                    activePointer = -1; dragging = false; setPressed(false); settleLens(true);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                activePointer = -1; dragging = false; setPressed(false); settleLens(true);
                return true;
            default: return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        sendVirtualEvent(selected, AccessibilityEvent.TYPE_VIEW_CLICKED);
        return true;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            selectTab(selected + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1), true);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            performClick(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() { return nodes; }
    @Override public CharSequence getAccessibilityClassName() { return "android.widget.RadioGroup"; }

    private void sendVirtualEvent(int id, int type) {
        AccessibilityManager manager = getContext().getSystemService(AccessibilityManager.class);
        if (manager == null || !manager.isEnabled() || getParent() == null) { return; }
        AccessibilityEvent event = AccessibilityEvent.obtain(type);
        event.setPackageName(getContext().getPackageName());
        event.setClassName("android.widget.RadioButton");
        event.setSource(this, id);
        event.getText().add(labels[id]);
        getParent().requestSendAccessibilityEvent(this, event);
    }

    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        AccessibilityManager manager = getContext().getSystemService(AccessibilityManager.class);
        if (manager != null && manager.isTouchExplorationEnabled()) {
            int next = event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT ? -1 : geometry.nearestTab(event.getX());
            if (hovered != next) {
                if (hovered >= 0) { sendVirtualEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT); }
                if (next >= 0) { sendVirtualEvent(next, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER); }
                hovered = next;
            }
            return true;
        }
        return super.dispatchHoverEvent(event);
    }

    private final class NavigationNodes extends AccessibilityNodeProvider {
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
            if (id == View.NO_ID) {
                AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain(LiquidGlassNavigationView.this);
                onInitializeAccessibilityNodeInfo(root);
                root.setClassName(getAccessibilityClassName());
                for (int i = 0; i < 3; i++) { root.addChild(LiquidGlassNavigationView.this, i); }
                return root;
            }
            if (id < 0 || id > 2) { return null; }
            AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
            node.setSource(LiquidGlassNavigationView.this, id);
            node.setParent(LiquidGlassNavigationView.this);
            node.setPackageName(getContext().getPackageName());
            node.setClassName("android.widget.RadioButton");
            node.setText(labels[id]);
            node.setContentDescription(labels[id]);
            node.setEnabled(isEnabled());
            node.setClickable(true);
            node.setFocusable(true);
            node.setCheckable(true);
            node.setChecked(selected == id);
            node.setSelected(selected == id);
            node.setAccessibilityFocused(accessibilityFocus == id);
            node.setVisibleToUser(isShown());
            Rect bounds = new Rect(Math.round(getWidth() * id / 3f), 0,
                    Math.round(getWidth() * (id + 1) / 3f), getHeight());
            node.setBoundsInParent(bounds);
            getLocationOnScreen(ownLocation);
            bounds.offset(ownLocation[0], ownLocation[1]);
            node.setBoundsInScreen(bounds);
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            node.addAction(accessibilityFocus == id ? AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                    : AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            return node;
        }

        @Override public boolean performAction(int id, int action, Bundle args) {
            if (id < 0 || id > 2 || !isEnabled()) { return false; }
            if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                selectTab(id, true); performClick(); return true;
            }
            if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                accessibilityFocus = id;
                sendVirtualEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                invalidate(); return true;
            }
            if (action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                accessibilityFocus = -1;
                sendVirtualEvent(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                invalidate(); return true;
            }
            return false;
        }
    }

    float lensCenterX() { return lensX; }
    float lensCenterY() { return lensY; }
    boolean isLensContained() { return geometry.containsLens(lensX, lensY); }
}
