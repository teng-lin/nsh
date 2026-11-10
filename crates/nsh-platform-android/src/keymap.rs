//! The app's keyboard shortcuts (`nsh/keymap`) on a hardware keyboard (a
//! tablet's, a Chromebook's, an emulator's): the table the engine mounts
//! ([`MountItem::Keymap`](nsh_core::MountItem::Keymap)), kept here rather
//! than sent to Java, and matched against every key on the spot.
//!
//! `NshView.dispatchKeyEvent` asks before its focused descendant gets a
//! key (`Native.keyDown` / `Native.keyUp`, on the UI thread): a key that
//! fires a binding is consumed (the focused input never sees it: no caret
//! move, no line break, no typed character) and reported to JS with the
//! root's `KeyCommand` event; JS runs the handler afterwards. Its release
//! is consumed too, and reported to a binding that asks for it
//! (`KeyBinding::release`, Rust's `on_key_event`) with the time the key was
//! held, so an app can tell a long press from a short one ([`HeldKeys`],
//! tested on the host in the core). The input method sees keys before the
//! view hierarchy does, so a composition keeps its keys. `NshView` holds the focus when
//! no input does, so keys reach it wherever the focus is in it; a text
//! input or hosted view (a web view, a native component) having the focus
//! is "in text". Android repeats a held key's events itself (their repeat
//! count): a binding fires on them only when it asks to (`repeat`).
//!
//! Keys are named by their key code for the keys that type nothing, and
//! by the character they type without modifiers on the device's layout
//! otherwise, the US layout's of the same key when that is no ASCII, as on
//! the desktops. `mod` is Control.

use std::{sync::Arc, time::Duration};

use jni::{
    JNIEnv,
    objects::JClass,
    sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong},
};
use nsh_core::{
    EventKind, ROOT_ID,
    keymap::{HeldKeys, Key, Keymap, Keystroke, Modifiers},
};

/// `KeyEvent.META_*_ON`.
const META_SHIFT_ON: i32 = 0x1;
const META_ALT_ON: i32 = 0x2;
const META_CTRL_ON: i32 = 0x1000;
const META_META_ON: i32 = 0x10000;

/// A view's table, and the keys it consumed that are still down.
#[derive(Default)]
pub(crate) struct KeymapState {
    table: Option<Arc<Keymap>>,
    /// The keys down whose press the table consumed or whose release a
    /// binding hears, by key code.
    held: HeldKeys<i32>,
}

impl KeymapState {
    /// `MountItem::Keymap`: replaces the table.
    pub(crate) fn set(&mut self, keymap: Arc<Keymap>) {
        self.table = (!keymap.is_empty()).then_some(keymap);
    }

    /// A new app: no bindings yet.
    pub(crate) fn clear(&mut self) {
        *self = KeymapState::default();
    }
}

/// The keystroke of a key event: key code `code`, `unicode` the character
/// it types without modifiers (`KeyEvent.getUnicodeChar(0)`, 0 for none),
/// `meta` its meta state.
pub(crate) fn keystroke(code: i32, unicode: i32, meta: i32) -> Option<Keystroke> {
    let modifiers = Modifiers {
        ctrl: meta & META_CTRL_ON != 0,
        alt: meta & META_ALT_ON != 0,
        shift: meta & META_SHIFT_ON != 0,
        cmd: meta & META_META_ON != 0,
    };
    let key = match Key::from_android_keycode(code)? {
        us @ Key::Char(c) if c != ' ' => match char::from_u32(unicode as u32) {
            Some(c) if c.is_ascii_graphic() => Key::Char(c.to_ascii_lowercase()),
            _ => us,
        },
        named => named,
    };
    Some(Keystroke { modifiers, key })
}

/// `held` milliseconds (`KeyEvent.getEventTime() - getDownTime()`).
fn millis(held: jlong) -> Duration {
    Duration::from_millis(held.max(0) as u64)
}

/// A key went down (`repeat`: an auto-repeat of a held key, down for
/// `held` ms): whether a binding takes it, firing it.
#[unsafe(no_mangle)]
#[allow(clippy::too_many_arguments)]
extern "system" fn Java_com_longbridge_nsh_Native_keyDown(
    _: JNIEnv<'_>,
    _: JClass<'_>,
    handle: jlong,
    code: jint,
    unicode: jint,
    meta: jint,
    repeat: jboolean,
    editing: jboolean,
    held: jlong,
) -> jboolean {
    // SAFETY: Java passes its live handle.
    let host = unsafe { crate::host::host(handle) };
    let Some(stroke) = keystroke(code, unicode, meta) else {
        return JNI_FALSE;
    };
    let action = {
        let mut state = host.keymap.lock().unwrap_or_else(|e| e.into_inner());
        let Some(table) = state.table.clone() else {
            return JNI_FALSE;
        };
        let (editing, repeat) = (editing != 0, repeat != 0);
        state
            .held
            .press(&table, ROOT_ID, code, stroke, editing, repeat, millis(held))
    };
    if let Some(report) = action.report {
        host.dispatch(ROOT_ID, EventKind::KeyCommand, report);
    }
    if action.consumed { JNI_TRUE } else { JNI_FALSE }
}

/// A key went up after `held` ms down: whether its press was consumed
/// (then so is this); a binding hearing its release fires.
#[unsafe(no_mangle)]
extern "system" fn Java_com_longbridge_nsh_Native_keyUp(
    _: JNIEnv<'_>,
    _: JClass<'_>,
    handle: jlong,
    code: jint,
    held: jlong,
) -> jboolean {
    // SAFETY: Java passes its live handle.
    let host = unsafe { crate::host::host(handle) };
    let action = {
        let mut state = host.keymap.lock().unwrap_or_else(|e| e.into_inner());
        state.held.release(code, millis(held))
    };
    if let Some(report) = action.report {
        host.dispatch(ROOT_ID, EventKind::KeyCommand, report);
    }
    if action.consumed { JNI_TRUE } else { JNI_FALSE }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn names_keys_by_what_they_type() {
        let parse = |t| Keystroke::parse(t, nsh_core::platform::Os::Android).ok();
        assert_eq!(keystroke(20, 0, 0), parse("down"));
        assert_eq!(keystroke(66, 0, 0), parse("enter"));
        assert_eq!(keystroke(39, 'k' as i32, META_CTRL_ON), parse("mod+k"));
        assert_eq!(keystroke(76, '/' as i32, META_SHIFT_ON), parse("shift+/"));
        // A layout typing no ASCII there: the US layout's key.
        assert_eq!(keystroke(39, 'л' as i32, 0), parse("k"));
        // A layout typing another letter there (AZERTY's A on Q's key).
        assert_eq!(keystroke(45, 'a' as i32, 0), parse("a"));
        assert_eq!(keystroke(59, 0, META_SHIFT_ON), None);
    }
}
