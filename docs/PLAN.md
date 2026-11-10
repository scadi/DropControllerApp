# Work Plan

Goal: bring the app to feature parity with the original App Inventor app
(see "Product Context" in `AGENTS.md`). The original behavior is the reference.

Status legend: `[ ]` todo, `[x]` done.

## Current state (audit summary)
- Done: stages 0-5 (protocol, data model, pure-Java logic with unit tests, shared state in
  `DropControllerApp`, reworked `BluetoothHelper`, persistence, Settings tab, Drops tab with Single Sequence Mode
  via `SequenceRunner`, Loop Page `LoopActivity` driven by `LoopController`, Drain, About, Graph),
  bottom navigation, Bluetooth runtime permissions.
- Stage 6 (polish): done except a manual pass on a device.
- Next: stage 7 (UI redesign, Material 3). Ideas and open questions are collected in `docs/NOTES.md`.

## Decisions
- Language stays Java 11.
- Loop Page is a separate `Activity` opened from the Drops tab; closing it returns to Drops.
- The wire protocol is taken from the Arduino sketch in `firmware/` and documented in `docs/PROTOCOL.md`.

## Stage 0. Wire protocol (blocks stages 3-5)
- [x] Read the Arduino sketch (`firmware/`).
- [x] Document every command sent by the app and every message sent by the device in `docs/PROTOCOL.md`.
- [x] Resolve the open questions: decisions are recorded in `docs/PROTOCOL.md` (`[N4]`, Bulb mode times,
      trigger STOP calculation, `[FTS]` timeout, message pacing, handshake).
- [ ] Do not change message formats without asking.

## Stage 1. Foundation
- [x] Replace `DropData` with a real model: numeric fields, START/SIZE deltas (INC/DEC + 0-999),
      SOL 1-6, defaults and reset.
- [x] Add trigger models: camera (OFF / ON + time / ON + Bulb, delta) and flash 1-3 (OFF / ON + time, delta).
- [x] Put logic in plain Java classes without Android dependencies: delta apply with clamping
      (e.g. START to 0..9999), STOP calculation (start + size), loop state machine
      (TIMER / BUTTON, STOP, RESET). Cover them with JVM unit tests.
- [x] Add a shared state holder that outlives fragments (settings, drop data, connection),
      owned by `Application` or `MainActivity`.
- [x] Fix `BluetoothHelper`: UI callback interface, handle `bytes == -1` and null socket/streams,
      write off the main thread, `volatile isConnected`, line-based receive parser,
      `getApplicationContext()`, no double disconnect Toast.
- [x] Extend `AppPreferences` with int support; persist settings and drop data.
- [x] Remove dead code: `services/DropsFragment`, the demo input dialog in `SettingsFragment`,
      template `ARG_PARAM*` leftovers, duplicate imports in `MainActivity`.

## Stage 2. Settings tab
- [x] NOT-CONNECTED / CONNECTED button with paired-device list (move from Drops tab).
- [x] Number of drops picker (1-9).
- [x] Loop Mode OFF/ON, delay method TIMER/BUTTON, wait time (seconds).
- [x] Trigger pulse times (ms, default 50) for camera and flashes.
- [x] Camera and flash 1-3 enable switches.
- [x] Sounds on/off.
- [x] RESET: restore all settings and drop data to defaults.

## Stage 3. Drops tab
- [x] Per-drop rows: SOL, START, START delta, SIZE, SIZE delta; only the selected number of drops is active.
- [x] Camera trigger controls (OFF / ON + time / ON + Bulb) and flash 1-3 controls, with deltas.
- [x] Hide the controls of a trigger that is OFF in Settings (was: disable). The Triggers card disappears when
      all four triggers are OFF. Done in `DropsFragment.updateTriggerStates`, `ValueDeltaRow.setVisible`.
      Changed on the user's request; the original app greys the controls out.
- [x] START button states: grey (not connected), green (connected), ACTIVE (sequence running).
- [x] Single Sequence Mode: START sends data immediately, deltas ignored,
      button returns to START when the device reports the sequence finished.

## Stage 4. Loop Mode and Loop Page
- [x] `LoopActivity` showing per drop: SOL, START [delta], SIZE [delta], STOP, plus CT, FT1, FT2, FT3.
- [x] TIMER: first sequence starts immediately, then countdown (wait time) before each next one;
      STOP pauses at the current sequence number, START continues, RESET sets sequence to 1
      and drop data to start values.
- [x] BUTTON: each sequence waits for the Loop Page START button.
- [x] After each sequence apply deltas (INC adds, DEC subtracts), clamp to range, never reject.
- [x] Stop timers and callbacks in lifecycle methods.

## Stage 5. Remaining tabs
- [x] Drain: open a selected valve manually (protocol from stage 0).
- [x] About: app version and device firmware version after connection.
- [x] Graph: chart of drop times and trigger times; shutter shown open from its start time
      for the exposure duration. Replace the generic polyline in `GraphCanvasView`.

## Stage 6. Polish
- [x] Settings tab label turns blue when connected.
- [x] Sounds. The original app's sound events are unknown, so the events were chosen with the user (new behavior):
      sequence start, sequence end ([FTS] from the device) and the end of the Loop Mode countdown.
      Done: `SoundPlayer` (tones from `ToneGenerator`, no audio files, honors the Sounds switch),
      `LoopController.Listener.onCountdownFinished`, `SequenceRunner.Listener.onDeviceFinished`.
- [x] Move hard-coded strings to `strings.xml`.
- [x] Manifest: `uses-feature` for Bluetooth, `neverForLocation` flag on `BLUETOOTH_SCAN`,
      catch `SecurityException` when permissions are denied (already done in `BluetoothHelper`).
- [x] Declare `androidx.recyclerview` explicitly in `libs.versions.toml`.
      Not needed now: nothing in `app/src` uses RecyclerView. Add it to the catalog when the first use appears.
- [x] Handle activity re-creation (rotation) and select the current nav item on start.
- [ ] Run `./gradlew assembleDebug lintDebug testDebugUnitTest` after each stage.

## Stage 7. UI redesign (Material Design 3)
Decisions (agreed with the user):
- Appearance only: no changes to behavior, data, persistence or the wire protocol. Same widgets' data flow
  (`ValueDeltaRow`, listeners, `binding` flags); only views, styles and themes change.
- Palette: blue ("water"), light and dark. START keeps green (ready) / yellow (ACTIVE) / grey (not connected).
- Scope: all screens, plus a colored (tinted) window background.

Steps:
- [x] Read the code bound to the views (`ValueDeltaRow`, `DropsFragment`, `SettingsFragment`, `LoopActivity`,
      `MainActivity`) so view ids and types stay compatible.
- [x] Theme: blue Material 3 color scheme (`values/` and `values-night/`), shapes, typography, tinted background,
      semantic colors (connected, start states) moved to theme/colors.
      Done: `colors.xml` (light and night), `themes.xml` (roles, shapes, `Widget.DropController.Card`,
      text appearances), `bools.xml` (system bar icons). `values-night/themes.xml` removed: the night colors come from
      `values-night/colors.xml`. Legacy styles stay until their screens are redesigned.
- [x] Navigation and splash: top app bar with a connection indicator, modern bottom navigation, new splash screen.
      Done: `MaterialToolbar` with the tab title, a Bluetooth icon tinted by state and a status subtitle
      (`top_app_bar_menu.xml`, `MainActivity.updateConnectionIndicator`); splash with icon, title and a filled button.
- [x] `view_value_delta` + `ValueDeltaRow`: `TextInputLayout` fields, INC/DEC as `MaterialButtonToggleGroup`.
      Done: label above the row, outlined dense fields, INC/DEC single-selection group (selection required);
      the commit/clamp logic of `ValueDeltaRow` is unchanged.
- [x] Drops tab: one `MaterialCardView` per drop, SOL as an exposed dropdown, triggers card, bottom START button.
      Done: card per drop, triggers card, rounded `MaterialButton` START (tinted by `DropsFragment` as before).
      SOL stays a `Spinner` with an outlined background (`bg_dropdown`), not an `AutoCompleteTextView`: this keeps
      the selection code unchanged (appearance-only rule).
- [x] Loop Page: status card, one card per drop, CT/FT lines, filled/tonal action buttons.
      Done: status card (primary container), one card per drop (`item_loop_card`), one card for CT/FT1-FT3,
      START filled / STOP tonal / RESET outlined. Texts and the controller logic are unchanged.
- [x] Settings tab: grouped cards, `TextInputLayout` number fields, TIMER/BUTTON as a segmented button,
      number of drops as a dropdown, connection button/state, RESET as a tonal/outlined button.
      Done: cards for Connection / Drops / Loop Mode / Triggers / Sounds + RESET (outlined in the error color).
      The number of drops stays a `Spinner` with `bg_dropdown` (same appearance-only rule as SOL).
- [x] Drain and About: cards and the same style.
      Done: Drain has a hint card, a card with the valve switches and a tonal CLOSE ALL button; About has one card
      with the app and firmware lines. The in-screen titles were removed (the top app bar shows the tab title),
      so `drain_title` and `about_title` are deleted.
- [x] Graph: colors taken from the theme (light and dark), card around the chart.
      Done: bar colors from `graph_*` (light and night), axis/text/grid colors from the theme roles
      (`colorOnSurfaceVariant`, `colorOnSurface`, `colorOutlineVariant`); chart is inside a card.
- [x] Remove styles and strings that became unused (`SettingsRow*`, `SettingsNumberField`, `ValueField`, ...).
      Done: legacy styles, `settings_title`, `status_icon_description`, `graph_empty`, `graph_title`, and the
      `black`/`white` colors are deleted.
- [ ] Run `./gradlew assembleDebug lintDebug testDebugUnitTest` after each step; manual pass on a device
      (rotation, dark mode, 9 drops scrolling, keyboard Done). The commands pass; the device pass is left.

## Out of scope
- Drop data validation (the original app has none).
- New behavior not present in the original app.
