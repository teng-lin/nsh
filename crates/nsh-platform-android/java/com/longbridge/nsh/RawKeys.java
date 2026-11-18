package com.longbridge.nsh;

import java.util.function.BooleanSupplier;

/**
 * Whether a key transition of a window is reported to the engine as a raw
 * key ({@code onKeyDown} / {@code onKeyUp}), and whether a capture rule
 * takes it, from the node the window's focus names ({@code
 * Mounter.keyNode}: a managed view, {@code 0} for an input or hosted view,
 * the window's fallback when nothing in it has the focus). Free of Android
 * types, so {@code RawKeysTest} checks it on a plain JDK.
 *
 * <p>With nothing focused the key always goes to the fallback ({@code
 * ROOT_ID}, or a dialog window's dialog): the core hands it to the app's
 * root view or the dialog shown over it ({@code Core::key_target}), whose
 * listeners and those around it may live outside this native window, so
 * the window's own walk cannot tell whether one hears it. No capture rule
 * takes it then (rules take keys from a focused view), a dialog's own
 * included, and Android's defaults still run.
 */
final class RawKeys {
    private RawKeys() {}

    /**
     * Whether the transition goes to the engine: a managed view's when it
     * or an enabled ancestor in its window hears raw keys or captures them
     * ({@code listens}, asked only then), the fallback's always.
     */
    static boolean reported(int node, int fallback, BooleanSupplier listens) {
        return node > 0 && (node == fallback || listens.getAsBoolean());
    }

    /**
     * Whether a mounted capture rule takes the reported transition: only a
     * focused managed view's ({@code captured}, asked only then).
     */
    static boolean prevented(boolean reported, int node, int fallback, BooleanSupplier captured) {
        return reported && node != fallback && captured.getAsBoolean();
    }
}
