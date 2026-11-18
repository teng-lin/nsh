import { keymap, mount, signal, Text, View } from "nsh";
import { Dialog } from "nsh/base";
import { Window } from "nsh/desktop";

// A root view that hears the raw keys and captures every one of them, a
// stop inside it, a dialog and a window of the app's own that hear them
// too, and a shortcut: the log says who heard each transition, and whether
// a capture rule took it.
function App() {
  const keys = signal<string[]>([]);
  const open = signal(false);
  const windowOpen = signal(false);
  const hear = (where: string) => (e: { key: string; defaultPrevented: boolean }) =>
    keys.update((items) => [...items, `${where} ${e.key} ${e.defaultPrevented}`]);
  // The root logs a release only of a press it heard: where a shortcut's
  // release goes once its press was taken differs between the desktops and
  // the phones, and is not what these tests check.
  const held = new Set<string>();
  const rootDown = (e: { key: string; defaultPrevented: boolean }) => {
    held.add(e.key);
    hear("root down")(e);
  };
  const rootUp = (e: { key: string; defaultPrevented: boolean }) => {
    if (held.delete(e.key)) hear("root up")(e);
  };
  keymap.bind("ctrl+s", () => keys.update((items) => [...items, "shortcut"]));
  return (
    <View style={{ flex: 1 }} captureKeys="all" onKeyDown={rootDown} onKeyUp={rootUp}>
      <View testID="stop" focusable style={{ height: 40 }}>
        <Text>Stop</Text>
      </View>
      <View onPress={() => open.set(true)} style={{ height: 40 }}>
        <Text>Open</Text>
      </View>
      <View onPress={() => windowOpen.set(true)} style={{ height: 40 }}>
        <Text>Window</Text>
      </View>
      <Dialog open={open()} onOpenChange={(e) => open.set(e.open)} onKeyDown={hear("dialog down")}>
        <Text>In the dialog</Text>
      </Dialog>
      <Window id="notes" title="Notes" open={windowOpen()} onOpenChange={(e) => windowOpen.set(e.open)}>
        <View style={{ flex: 1 }} onKeyDown={hear("window down")} onKeyUp={hear("window up")}>
          <Text>In the window</Text>
        </View>
      </Window>
      <Text>{`Keys: ${keys().join(", ") || "none"}`}</Text>
    </View>
  );
}

mount(App);
