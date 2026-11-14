package com.longbridge.nsh;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

/**
 * Runs with app_process on API 30+ with an accessibility service enabled. Uses real Android views,
 * without mounting a window or sending events to the user's service. The recording view intercepts
 * only the final send, so the assertions exercise the production change detection and event choice.
 */
public final class A11yStateEvents {
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        if (!context.getSystemService(AccessibilityManager.class).isEnabled()) {
            throw new AssertionError("enable an accessibility service before running this test");
        }
        RecordingView view = new RecordingView(context);
        A11y off = state(8), on = state(24);
        update(view, null, off);
        expect("initial mount is silent", view, 0, 0);
        update(view, off, state(8));
        expect("unchanged props are silent", view, 0, 0);
        update(view, off, on);
        expect("checked uses committed state", view, AccessibilityEvent.TYPE_VIEW_CLICKED, 0);
        update(view, on, off);
        expect("unchecked", view, AccessibilityEvent.TYPE_VIEW_CLICKED, 0);
        update(view, state(0), state(2));
        expect("selected", view, AccessibilityEvent.TYPE_VIEW_CLICKED, 0);
        update(view, state(64), state(192));
        expect("expanded", view, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION);
        A11y low = state(0), high = state(0);
        low.hasValue = high.hasValue = true;
        low.min = high.min = 0; low.max = high.max = 10;
        low.now = 3; high.now = 4;
        update(view, low, high);
        expect("range", view, AccessibilityEvent.TYPE_VIEW_SELECTED, 0);
        update(view, high, high);
        expect("unchanged range", view, 0, 0);
        view.focused = false;
        update(view, off, on);
        expect("an unfocused checkbox only invalidates the cache", view,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
        view.focused = true;
        view.shown = false;
        update(view, on, off);
        expect("an unshown view is silent", view, 0, 0);
        view.shown = true;
        A11y hidden = state(8); hidden.hidden = true;
        update(view, on, hidden);
        expect("a hidden element is silent", view, 0, 0);
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED);
        on.onInitializeAccessibilityEvent(view, event);
        if (!event.isChecked() || !"android.widget.Switch".contentEquals(event.getClassName())) {
            throw new AssertionError("event must report the same switch role and checked state as its node");
        }
        event.recycle();
        System.out.println("A11yStateEvents: 12 passed");
        System.exit(0);
    }

    private static A11y state(int bits) {
        A11y value = new A11y(null, 1);
        value.state = bits;
        value.role = 22;
        return value;
    }

    private static void update(RecordingView view, A11y before, A11y after) {
        view.count = view.type = view.changes = 0;
        after.apply(view);
        // View's own setters may emit their own framework events; this checks the explicit
        // semantic update, after those setters and with the new delegate installed.
        view.count = view.type = view.changes = 0;
        after.changed(view, before);
        if (view.getAccessibilityDelegate() != after) throw new AssertionError("lost the delegate");
    }

    private static void expect(String label, RecordingView view, int type, int changes) {
        if (view.type != type || view.changes != changes || view.count != (type == 0 ? 0 : 1)) {
            throw new AssertionError(label + ": event " + view.type + "/" + view.changes
                    + " count=" + view.count + ", expected " + type + "/" + changes);
        }
    }

    private static final class RecordingView extends View {
        boolean focused = true, shown = true;
        int count, type, changes;

        RecordingView(Context context) { super(context); }

        @Override public boolean isShown() { return shown; }
        @Override public boolean isAccessibilityFocused() { return focused; }
        @Override public void sendAccessibilityEvent(int eventType) { count++; type = eventType; }
        @Override public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
            count++; type = event.getEventType(); changes = event.getContentChangeTypes(); event.recycle();
        }
    }
}
