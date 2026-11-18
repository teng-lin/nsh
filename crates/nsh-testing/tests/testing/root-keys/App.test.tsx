import { open, test } from "nsh/test";

// Linux: the desktop whose raw keys the headless driver delivers.
const app = () => open("./App.tsx", { platform: "linux" });

test("the app's root view hears the keys while nothing has the focus", async () => {
  const a = await app();
  await a.waitFor("Keys: none");
  await a.keyboard.press("a");
  // Heard as from a focused view, and taken by no capture rule.
  await a.waitFor("Keys: root down a false, root up a false");
});

test("a capture rule takes keys from a focused view only", async () => {
  const a = await app();
  await a.waitFor("Keys: none");
  // Uncaptured, Tab still moves the focus in; its release is the stop's.
  await a.keyboard.press("tab");
  await a.waitFor("Keys: root down Tab false, root up Tab true");
  await a.keyboard.press("a");
  await a.waitFor("Keys: root down Tab false, root up Tab true, root down a true, root up a true");
});

test("a shortcut keeps the key from the root view", async () => {
  const a = await app();
  await a.waitFor("Keys: none");
  await a.keyboard.press("ctrl+s");
  await a.keyboard.press("b");
  await a.waitFor("Keys: shortcut, root down b false, root up b false");
});

test("a dialog shown over the window hears its keys first", async () => {
  const a = await app();
  await a.press("Open");
  await a.waitFor("In the dialog");
  await a.keyboard.press("a");
  await a.waitFor("Keys: dialog down a false, root down a false, root up a false");
});

test("a window of the app's own sends its keys to its own root view", async () => {
  const a = await app();
  await a.press("Window");
  await a.waitFor("In the window");
  await a.keyboard.press("b");
  await a.waitFor("Keys: window down b false, window up b false");
});
