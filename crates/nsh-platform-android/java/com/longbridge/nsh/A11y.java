package com.longbridge.nsh;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import java.util.Objects;

/**
 * Accessibility of one view, from the app's properties
 * (`accessibilityLabel`, `accessibilityRole`, `accessibilityState`...),
 * applied through an accessibility delegate.
 */
final class A11y extends View.AccessibilityDelegate {
    /** Class names reported for roles, in `encode.rs` `role_index` order. */
    private static final String[] CLASS_NAMES = {
        null, "android.widget.Button", "android.widget.Button", "android.widget.EditText",
        "android.widget.ImageView", "android.widget.ImageButton", "android.inputmethodservice.Keyboard$Key",
        "android.widget.TextView", "android.widget.SeekBar", "android.view.View", "android.view.View",
        "android.view.View", "android.widget.CheckBox", "android.widget.Spinner", "android.view.View",
        "android.view.View", "android.view.View", "android.widget.ProgressBar", "android.widget.RadioButton",
        "android.widget.RadioGroup", "android.view.View", "android.widget.SpinButton", "android.widget.Switch",
        "android.view.View", "android.widget.TabWidget", "android.view.View", "android.widget.Toolbar",
        "android.widget.AbsListView", "android.widget.GridView",
        "android.widget.GridView", "android.view.View", "android.view.View", "android.view.View", "android.view.View",
        "android.widget.ListView", "android.view.View", "android.view.View", "android.view.View",
        "android.view.View", "android.view.View", "android.view.View", "android.view.View", "android.view.View",
    };
    /** Spoken role descriptions for roles without a widget class. */
    private static final String[] ROLE_DESCRIPTIONS = {
        null, null, "link", null, null, null, null, null, null, null, "summary", "alert", null, null, "menu",
        "menu bar", "menu item", null, null, null, "scroll bar", null, null, "tab", "tab list", "timer", null,
        null, null, null, "row", "cell", "column header", "row group", null, "option", null, "region",
        "navigation", "tree", "tree item", "menu item", "menu item",
    };
    /** Spoken states of `accessibilityState.current`, by its kind in the state's bits 11 to 13. */
    private static final String[] CURRENT_DESCRIPTIONS = {
        null, "current", "current page", "current step", "current location", "current date", "current time",
    };
    private static final int ROLE_ADJUSTABLE = 8;
    private static final int ROLE_HEADER = 9;
    /** Ids of custom actions (outside the framework's action ids). */
    private static final int CUSTOM_ACTION_BASE = 0x3f000000;

    final Mounter mounter;
    final int id;
    int accessible = -1;
    String label, hint, testId;
    int role;
    int state;
    boolean hasValue;
    float min = Float.NaN, max = Float.NaN, now = Float.NaN;
    String valueText;
    boolean hidden;
    int liveRegion;
    String[] actionNames = new String[0];
    String[] actionLabels = new String[0];

    A11y(Mounter mounter, int id) {
        this.mounter = mounter;
        this.id = id;
    }

    /** Applies the view-level settings (the rest happens per query). */
    void apply(View v) {
        v.setAccessibilityDelegate(this);
        if (hidden) {
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        } else if (accessible == 0) {
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        } else if (accessible == 1 || label != null) {
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        } else {
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
        if (Build.VERSION.SDK_INT >= 28) v.setScreenReaderFocusable(accessible == 1);
        v.setContentDescription(label);
        v.setAccessibilityLiveRegion(liveRegion == 2 ? View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
                : liveRegion == 1 ? View.ACCESSIBILITY_LIVE_REGION_POLITE : View.ACCESSIBILITY_LIVE_REGION_NONE);
        if (Build.VERSION.SDK_INT >= 28) v.setAccessibilityHeading(role == ROLE_HEADER);
    }

    /** Notify only after the new delegate is installed: the service queries the new state. */
    void changed(View v, A11y before) {
        if (before == null || hidden || before.hidden) return;
        boolean sameNumbers = Float.compare(min, before.min) == 0
                && Float.compare(max, before.max) == 0 && Float.compare(now, before.now) == 0;
        if (state == before.state && role == before.role && hasValue == before.hasValue
                && Objects.equals(valueText, before.valueText) && sameNumbers) return;
        if (!v.isShown()) return;
        AccessibilityManager manager = v.getContext().getSystemService(AccessibilityManager.class);
        if (manager == null || !manager.isEnabled()) return;
        String description = stateDescription();
        boolean descriptionChanged = !Objects.equals(description, before.stateDescription());
        boolean rangeChanged = hasRange() && (!before.hasRange() || !sameNumbers);

        boolean stateDescriptions = Build.VERSION.SDK_INT >= 30;
        int feedback = A11yChanges.feedback(A11yChanges.exposedState(before.state, stateDescriptions),
                A11yChanges.exposedState(state, stateDescriptions), descriptionChanged,
                description != null && stateDescriptions, rangeChanged);
        if (v.isAccessibilityFocused() && feedback == A11yChanges.CHECKED_OR_SELECTED) {
            // TalkBack derives checked/selected feedback from this event, including an async
            // state change after ACTION_CLICK. A generic content change only invalidates its cache.
            v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        } else if (v.isAccessibilityFocused() && feedback == A11yChanges.RANGE) {
            v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        } else {
            AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            event.setContentChangeTypes(feedback == A11yChanges.DESCRIPTION && Build.VERSION.SDK_INT >= 30
                    ? AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
                    : AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
            v.sendAccessibilityEventUnchecked(event);
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(host, event);
        String className = (state & 256) != 0 ? "android.widget.ToggleButton"
                : role < CLASS_NAMES.length ? CLASS_NAMES[role] : null;
        if (className != null) event.setClassName(className);
        if ((state & 1) != 0) event.setEnabled(false);
        if ((state & 256) != 0) event.setChecked((state & 512) != 0);
        else if ((state & 8) != 0) event.setChecked((state & 16) != 0);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        String className = role < CLASS_NAMES.length ? CLASS_NAMES[role] : null;
        if (className != null) info.setClassName(className);
        String description = role < ROLE_DESCRIPTIONS.length ? ROLE_DESCRIPTIONS[role] : null;
        if (description != null && Build.VERSION.SDK_INT >= 30) {
            info.getExtras().putCharSequence("AccessibilityNodeInfo.roleDescription", description);
        }
        if (hint != null && Build.VERSION.SDK_INT >= 26) info.setHintText(hint);
        if (testId != null) info.setViewIdResourceName(testId);
        if ((state & 1) != 0) info.setEnabled(false);
        if ((state & 2) != 0) info.setSelected(true);
        if ((state & 8) != 0) {
            info.setCheckable(true);
            info.setChecked((state & 16) != 0);
        }
        if ((state & 256) != 0) {
            // A toggle button (`accessibilityState.pressed`), read as on or off.
            info.setClassName("android.widget.ToggleButton");
            info.setCheckable(true);
            info.setChecked((state & 512) != 0);
        }
        if ((state & 64) != 0) {
            info.addAction((state & 128) != 0 ? AccessibilityAction.ACTION_COLLAPSE : AccessibilityAction.ACTION_EXPAND);
        }
        if (hasRange()) {
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                    AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, min, max, now));
        }
        String stateText = stateDescription();
        if (stateText != null && Build.VERSION.SDK_INT >= 30) info.setStateDescription(stateText);
        if (role == ROLE_ADJUSTABLE || has("increment")) info.addAction(AccessibilityAction.ACTION_SCROLL_FORWARD);
        if (role == ROLE_ADJUSTABLE || has("decrement")) info.addAction(AccessibilityAction.ACTION_SCROLL_BACKWARD);
        if (has("activate") || mounter.wants(id, Native.PRESS)) {
            info.addAction(AccessibilityAction.ACTION_CLICK);
            info.setClickable(true);
        }
        if (has("longpress") || mounter.wants(id, Native.LONG_PRESS)) {
            info.addAction(AccessibilityAction.ACTION_LONG_CLICK);
            info.setLongClickable(true);
        }
        for (int i = 0; i < actionNames.length; i++) {
            if (isStandard(actionNames[i])) continue;
            String l = actionLabels[i] != null ? actionLabels[i] : actionNames[i];
            info.addAction(new AccessibilityAction(CUSTOM_ACTION_BASE + i, l));
        }
    }

    private boolean hasRange() {
        return hasValue && valueText == null && !Float.isNaN(now) && !Float.isNaN(min) && !Float.isNaN(max);
    }

    private String stateDescription() {
        StringBuilder text = new StringBuilder();
        if ((state & 8) != 0 && (state & 32) != 0) text.append("mixed");
        if ((state & 256) != 0 && (state & 1024) != 0) {
            if (text.length() > 0) text.append(", ");
            text.append("mixed");
        }
        if ((state & 64) != 0) {
            if (text.length() > 0) text.append(", ");
            text.append((state & 128) != 0 ? "expanded" : "collapsed");
        }
        int current = (state >> 11) & 7;
        if (current != 0 && current < CURRENT_DESCRIPTIONS.length) {
            if (text.length() > 0) text.append(", ");
            text.append(CURRENT_DESCRIPTIONS[current]);
        }
        if (hasValue && valueText != null) {
            if (text.length() > 0) text.append(", ");
            text.append(valueText);
        }
        return text.length() == 0 ? null : text.toString();
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if (action >= CUSTOM_ACTION_BASE && action < CUSTOM_ACTION_BASE + actionNames.length) {
            report(actionNames[action - CUSTOM_ACTION_BASE]);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_CLICK && has("activate")) {
            report("activate");
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_CLICK && mounter.wants(id, Native.PRESS)) {
            mounter.event(id, Native.PRESS);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
            if (has("longpress")) {
                report("longpress");
                return true;
            }
            if (mounter.wants(id, Native.LONG_PRESS)) {
                mounter.event(id, Native.LONG_PRESS);
                return true;
            }
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && has("increment")) {
            report("increment");
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && has("decrement")) {
            report("decrement");
            return true;
        }
        if ((action == AccessibilityNodeInfo.ACTION_EXPAND || action == AccessibilityNodeInfo.ACTION_COLLAPSE)
                && has(action == AccessibilityNodeInfo.ACTION_EXPAND ? "expand" : "collapse")) {
            report(action == AccessibilityNodeInfo.ACTION_EXPAND ? "expand" : "collapse");
            return true;
        }
        return super.performAccessibilityAction(host, action, args);
    }

    private boolean has(String name) {
        for (String n : actionNames) {
            if (n.equals(name)) return true;
        }
        return false;
    }

    private static boolean isStandard(String name) {
        switch (name) {
            case "activate":
            case "increment":
            case "decrement":
            case "longpress":
            case "expand":
            case "collapse":
            case "magicTap":
            case "escape":
                return true;
            default:
                return false;
        }
    }

    private void report(String name) {
        mounter.event(id, Native.ACCESSIBILITY_ACTION, name, 0, 0, 0);
    }
}
