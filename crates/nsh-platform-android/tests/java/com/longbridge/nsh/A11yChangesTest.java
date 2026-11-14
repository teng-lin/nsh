package com.longbridge.nsh;

/** Plain-JDK coverage of feedback selection; A11yStateEvents tests the Android event path. */
public final class A11yChangesTest {
    public static void main(String[] args) {
        expect("unchecked to checked", 8, 24, false, false, false, A11yChanges.CHECKED_OR_SELECTED);
        expect("checked to unchecked", 24, 8, false, false, false, A11yChanges.CHECKED_OR_SELECTED);
        expect("unchanged checkbox", 24, 24, false, false, false, A11yChanges.NONE);
        expect("selected", 0, 2, false, false, false, A11yChanges.CHECKED_OR_SELECTED);
        expect("unselected", 2, 0, false, false, false, A11yChanges.CHECKED_OR_SELECTED);
        expect("unchanged selected", 2, 2, false, false, false, A11yChanges.NONE);
        expect("pressed toggle", 256, 768, false, false, false, A11yChanges.CHECKED_OR_SELECTED);
        expect("released toggle", 768, 256, false, false, false, A11yChanges.CHECKED_OR_SELECTED);
        expect("pressed wins over checked", 256 | 8, 256 | 24,
                false, false, false, A11yChanges.NONE);
        expect("mixed wins over click feedback", 8, 40, true, true, false, A11yChanges.DESCRIPTION);
        expect("clearing mixed reads new checked state", 40, 24,
                true, false, false, A11yChanges.CHECKED_OR_SELECTED);
        expect("expanded", 64, 192, true, true, false, A11yChanges.DESCRIPTION);
        expect("changed range", 0, 0, false, false, true, A11yChanges.RANGE);
        expect("range text wins over numeric range", 0, 0, true, true, true, A11yChanges.DESCRIPTION);
        expect("checked and selected notify once", 8, 24 | 2,
                false, false, false, A11yChanges.CHECKED_OR_SELECTED);
        expect("disabled only invalidates cached node", 0, 1, false, false, false, A11yChanges.NONE);
        expect("irrelevant checked bits without checkable", 0, 16,
                false, false, false, A11yChanges.NONE);
        expect("checked with an unavailable description", 72, 216,
                true, false, false, A11yChanges.CHECKED_OR_SELECTED);
        expect("checked with an available description", 72, 216,
                true, true, false, A11yChanges.DESCRIPTION);
        System.out.println("A11yChangesTest: 19 passed");
    }

    private static void expect(String label, int before, int after, boolean descriptionChanged,
                               boolean hasDescription, boolean rangeChanged, int wanted) {
        int got = A11yChanges.feedback(before, after, descriptionChanged, hasDescription, rangeChanged);
        if (got != wanted) throw new AssertionError(label + ": expected " + wanted + ", got " + got);
    }
}
