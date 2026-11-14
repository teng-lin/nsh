package com.longbridge.nsh;

/** The feedback for a semantic update, separate from invalidating a service's node cache. */
final class A11yChanges {
    static final int NONE = 0, CHECKED_OR_SELECTED = 1, DESCRIPTION = 2, RANGE = 3;

    private A11yChanges() {}

    static int feedback(int before, int after, boolean descriptionChanged,
                        boolean hasDescription, boolean rangeChanged) {
        if (descriptionChanged && hasDescription) return DESCRIPTION;
        // A pressed state overrides checked in A11y's node, as on a ToggleButton.
        int checked = checked(after);
        if (checked != 0 && checked != checked(before)) return CHECKED_OR_SELECTED;
        if (((before ^ after) & 2) != 0) return CHECKED_OR_SELECTED;
        if (rangeChanged) return RANGE;
        return descriptionChanged ? DESCRIPTION : NONE;
    }

    private static int checked(int state) {
        if ((state & 256) != 0) return (state & 1024) != 0 ? 3 : (state & 512) != 0 ? 2 : 1;
        if ((state & 8) != 0) return (state & 32) != 0 ? 3 : (state & 16) != 0 ? 2 : 1;
        return 0;
    }
}
