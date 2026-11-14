package com.longbridge.nsh;

/** The feedback for a semantic update, separate from invalidating a service's node cache. */
final class A11yChanges {
    static final int NONE = 0, CHECKED_OR_SELECTED = 1, DESCRIPTION = 2, RANGE = 3;

    private A11yChanges() {}

    static int exposedState(int state, boolean stateDescriptions) {
        // Before API 30, mixed has no state description and reads as the ordinary checked flag.
        return stateDescriptions ? state : state & ~(32 | 1024);
    }

    static int feedback(int before, int after, boolean descriptionChanged,
                        boolean hasDescription, boolean rangeChanged) {
        if (descriptionChanged && hasDescription) return DESCRIPTION;
        // A pressed state overrides checked in A11y's node, as on a ToggleButton.
        int checked = checked(after);
        if (checked != 0 && checked != checked(before)) return CHECKED_OR_SELECTED;
        // Selection names the current item, not a toggle. Clearing it only invalidates the cache;
        // TalkBack has no derived "unselected" speech, and a click would add a bare tick.
        if ((before & 2) == 0 && (after & 2) != 0) return CHECKED_OR_SELECTED;
        if (rangeChanged) return RANGE;
        return descriptionChanged ? DESCRIPTION : NONE;
    }

    private static int checked(int state) {
        if ((state & 256) != 0) return (state & 1024) != 0 ? 3 : (state & 512) != 0 ? 2 : 1;
        if ((state & 8) != 0) return (state & 32) != 0 ? 3 : (state & 16) != 0 ? 2 : 1;
        return 0;
    }
}
