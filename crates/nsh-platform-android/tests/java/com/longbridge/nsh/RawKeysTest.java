package com.longbridge.nsh;

/**
 * Coverage for which raw keys a window reports and which a capture rule
 * takes: with nothing focused, the fallback hears every key and nothing
 * captures it. Plain JDK, no Android APIs:
 *
 * <pre>
 * javac -d target/java-tests \
 *   crates/nsh-platform-android/java/com/longbridge/nsh/RawKeys.java \
 *   crates/nsh-platform-android/tests/java/com/longbridge/nsh/RawKeysTest.java
 * java -cp target/java-tests com.longbridge.nsh.RawKeysTest
 * </pre>
 */
public final class RawKeysTest {
    private static final int ROOT = 1;
    private static final int DIALOG = 7;
    private static final int VIEW = 4;

    public static void main(String[] args) {
        // Nothing focused: the fallback hears the key, whatever its own walk
        // would say, and is never asked.
        check("the root hears a key with nothing focused",
                RawKeys.reported(ROOT, ROOT, RawKeysTest::never));
        check("a dialog window's fallback hears it too",
                RawKeys.reported(DIALOG, DIALOG, RawKeysTest::never));
        // ... and no capture rule takes it, a dialog's own included.
        check("the root's key is never captured",
                !RawKeys.prevented(true, ROOT, ROOT, RawKeysTest::never));
        check("a dialog's own rule takes nothing with nothing focused",
                !RawKeys.prevented(true, DIALOG, DIALOG, RawKeysTest::never));

        // A focused view: reported when it or an ancestor listens, and its
        // rules decide.
        check("a focused view that listens is reported", RawKeys.reported(VIEW, ROOT, () -> true));
        check("a focused view that hears nothing is not", !RawKeys.reported(VIEW, ROOT, () -> false));
        check("a focused view's rule captures", RawKeys.prevented(true, VIEW, ROOT, () -> true));
        check("an unreported key is not captured",
                !RawKeys.prevented(false, VIEW, ROOT, RawKeysTest::never));

        // An input or a hosted view keeps its keys.
        check("an input's key is not reported", !RawKeys.reported(0, ROOT, RawKeysTest::never));
        System.out.println("RawKeysTest: ok");
    }

    /** A walk the decision must not take. */
    private static boolean never() {
        throw new AssertionError("asked for a decision the fallback does not need");
    }

    private static void check(String what, boolean ok) {
        if (!ok) throw new AssertionError(what);
    }
}
