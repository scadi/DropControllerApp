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
- Stage 8 is done (verified on a device).
- Stages 9 and 10 are implemented (code, unit tests, build and lint pass). Left: the manual pass on a device for
  stage 9 (rotation, dark mode, 5+ presets) and stage 10 (export, move the file, import on a clean install, import twice).
  Ideas and open questions are collected in `docs/NOTES.md`.

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
      Closed: no further work on sounds (user's decision); the device check of the tones is dropped from the plan.
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
- [x] Color pass (contrast): drop card colors come from a two-color scale (`drop_card_lightest` / `drop_card_darkest`,
      light: white to `#4A7384`, dark: `#4A7080` to `#1B3340`). `DropsFragment.getCardShade` puts drop 1 at 20% of the
      way to the darkest color, then +40%, -34%, +40%, ... (20, 60, 26, 66, 32, 72, 38, 78, 44%), so the cards
      alternate between light and dark and slowly get darker overall. Text color is chosen per card by contrast (light
      cards: dark text, dark cards: light text). START label color per state
      (`start_*_text`, readable on grey / green / yellow in light and dark); light `graph_flash` darkened to
      `#D17B00` (was 1.9:1 on the card surface).
- [x] Theme selector in Settings (card with Sounds and RESET). `AppSettings.themeMode` (`ThemeMode`: SYSTEM / LIGHT /
  DARK), saved by `StateStorage`; `DropControllerApp.applyThemeMode()` calls `AppCompatDelegate.setDefaultNightMode`
  on start and after a change (open activities are recreated; `MainActivity` skips the splash and keeps the tab).
  Changed after the first version (a Dark theme switch, which could not go back to "follow the system"): the
  selector is now a SYSTEM / LIGHT / DARK segmented button (`tg_theme`, selection required). Like Sounds, the theme
  is not part of a preset; RESET keeps it.
- [x] RESET asks for confirmation first (`MaterialAlertDialogBuilder`, "Reset everything?"; Cancel keeps the data).
  New behavior, not in the original app: RESET erases all drop data. Dialogs in `SettingsFragment` are dismissed in
  `onDestroyView` so rotation does not leak a window.
- [x] Number of drops in Settings: a row of 9 drop icons instead of the dropdown. Touching or dragging over the n-th
  icon selects n drops (the first n icons are filled with the accent color, the rest are dimmed, the number is shown
  next to the title). Appearance and input only: the value still goes through `AppSettings.setNumDrops` and is saved
  at once. The `Spinner` and `bg_dropdown` stay in use for SOL on the Drops tab.
- [ ] Run `./gradlew assembleDebug lintDebug testDebugUnitTest` after each step; manual pass on a device
      (rotation, dark mode, 9 drops scrolling, keyboard Done). The commands pass; the device pass is left.

## Stage 8. Presets: model, storage, Save / Load on the Drops tab
New feature requested by the user (not in the original app): save the whole setup under a name and load it again
later instead of entering the values by hand. Stages 9 (Graph tab) and 10 (JSON file) build on this stage.

Decisions (agreed with the user unless marked "proposed"):
- A preset holds the drop data (`SequenceData`: all 9 drops, camera, flashes, deltas) and the settings that describe
  the setup (`AppSettings`: number of drops, Loop Mode ON/OFF, delay method, wait seconds, camera and flash pulse
  times). The Sounds switch is a device preference, not part of a preset: loading keeps the current value (proposed).
- Loading a preset replaces the shared model, so the Settings tab shows the number of drops, Loop Mode and trigger
  switches (camera, FT1-FT3) exactly as saved, and the Drops tab shows the saved values. Nothing else is changed.
- Presets are stored on the device (SharedPreferences, same `KeyValueStore` abstraction), separate from the current
  working state, which keeps being saved as before. Save and Load work without a Bluetooth connection and never
  touch it.
- While a sequence is running (START was pressed and the device has not finished, `SequenceRunner.isActive()`) the
  Save and Load buttons are disabled. In Loop Mode the Loop Page covers the tabs for the whole loop, and Back ends
  the loop, so presets cannot change during a loop.
- A preset has a numeric id (the storage key) and a name. The name is required (1-40 characters, trimmed) and unique
  (compared ignoring case). Saving under an existing name asks to overwrite. The id keeps unusual characters out of
  the storage keys and makes renaming cheap (proposed).
- Presets are listed in the order they were created.
- The wire protocol and the behavior of START, Loop Mode and deltas are unchanged.

Steps:
- [x] 8.1 Model and storage: `Preset` in `data/` (id, name, `AppSettings` copy, `SequenceData` copy, a
      `hasSameContentAs(...)` that ignores id, name and Sounds). `PresetRepository` (plain Java over `KeyValueStore`,
      no Android dependencies): list, add, overwrite, rename, delete, find by name. Each preset reuses `StateStorage`
      through a prefixed `KeyValueStore` wrapper (`preset.<id>.`), plus the name and the id list. `KeyValueStore`
      and `AppPreferences` need a way to remove keys (delete/overwrite).
- [x] 8.2 Unit tests (JVM, in-memory store): save/load round trip, Sounds not overwritten, overwrite by name, rename
      (duplicate, empty and too long names rejected), delete, unique names ignoring case, list order, missing
      entries fall back to defaults, `hasSameContentAs`.
- [x] 8.3 `DropControllerApp`: expose the repository and `applyPreset(...)`, which copies the preset into the shared
      `settings` / `sequence` (Sounds untouched), saves both and is refused while `SequenceRunner.isActive()`.
- [x] 8.4 Drops tab layout: one row at the bottom: START takes the free width, SAVE and LOAD are icon buttons
      (tonal, as big as their icons) to its right. Changed after the first version (START with SAVE above LOAD)
      on the user's request. Strings in `strings.xml` (used as content descriptions).
- [x] 8.5 Locking: Save and Load are disabled while `SequenceRunner.isActive()` and enabled again when the sequence
      ends (same update path as `updateStartButton`).
- [x] 8.6 Save dialog: a text field for the name (Done confirms, empty or too long name rejected with an error in the
      field, overwrite confirmation for an existing name). Commit the open fields first (`commitAll()`), then save.
- [x] 8.7 Load dialog: the list of saved presets (empty list: a message); choosing one applies it and refreshes the
      Drops tab at once. The dialog only picks; renaming and deleting are done on the Graph tab (stage 9).
- [x] 8.8 Check that loading refreshes every screen: `SettingsFragment` (switches, number of drops, Loop Mode
      fields) and `DropsFragment` (visible drops, hidden triggers) read the shared model when they start, so no extra
      code is expected; verify on a device. Verified by the user on a device.
- [x] 8.9 Run `./gradlew assembleDebug lintDebug testDebugUnitTest`; update `docs/NOTES.md`.
      Done: commands pass; `NOTES.md` has a "Stage 8 remarks" section.
      Implementation: `Preset`, `PresetRepository`, `PrefixedKeyValueStore` (+ `remove`/`removeWithPrefix` in
      `KeyValueStore`), `AppSettings.copySetupFrom`/`hasSameSetupAs`, `DropControllerApp.applyPreset`,
      `PresetRepositoryTest`, Save / Load dialogs in `DropsFragment`, `dialog_preset_name.xml`.

## Stage 9. Presets on the Graph tab (cards, Load, rename, delete)
Depends on stage 8. The current chart stays at the top of the tab; the saved presets are listed below it.

Decisions (agreed with the user):
- One card per preset: the name, its chart (`GraphModel.build` with the preset data), a Load button and a trash icon
  button next to it.
- Load applies the preset to Settings and Drops (a Toast confirms). It is disabled while a sequence runs.
- Renaming: a long press on the name turns it into an edit field. Done or leaving the field confirms. An empty name,
  a name over 40 characters or a name used by another preset is rejected with an error text in the field. Back or an
  unchanged name cancels.
- Deleting: the trash icon asks for confirmation first ("Delete <name>?", Delete / Cancel); only Delete removes the
  preset.

Steps:
- [x] 9.1 Layout and adapter-free list: `item_preset_card` (name, chart view, Load, trash) inflated into a container
      below the current chart, one card per preset; the empty state shows a short hint. `GraphCanvasView` is reused
      with the preset model. Strings in `strings.xml`.
- [x] 9.2 Load button: applies the preset (`applyPreset`), Toast, disabled while `SequenceRunner.isActive()`
      (the fragment listens to the runner in `onStart`/`onStop`).
- [x] 9.3 Rename by long press: the name `TextView` switches to a `TextInputEditText` with the rules above;
      the keyboard opens on its own; the list is rebuilt after a change.
- [x] 9.4 Delete: trash icon, confirmation dialog, list rebuilt after deleting.
- [x] 9.5 Rotation and dark mode (no state lost mid-edit is acceptable: an edit is simply cancelled on rotation).
- [x] 9.6 Run `./gradlew assembleDebug lintDebug testDebugUnitTest`; manual pass on a device with 5+ presets.

## Stage 10. Export and import of presets (JSON file)
Depends on stages 8 and 9 (the buttons live on the Graph tab).

Decisions (agreed with the user unless marked "proposed"):
- Export writes all presets into one JSON file; import reads such a file. The JSON has a `format` marker and a
  `version` number, then a list of presets (name, settings, drops, camera, flashes). Deltas are stored as direction
  and amount. Unknown or missing fields fall back to defaults and numbers are clamped by the model setters, like in
  `StateStorage`. A file that is not valid JSON or has no `presets` list is rejected as a whole (nothing imported).
- File access uses the Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` through the
  Activity Result API), so no storage permission is needed and the user can choose any place. The picker starts in
  the Downloads folder (`EXTRA_INITIAL_URI`; honored on Android 8+, older versions open the picker's last folder).
  Default file name: `dropcontroller-presets.json`.
- Import rules, per preset of the file:
  - same name and identical content (settings except Sounds, and all drop data) as an existing preset: skipped;
  - same name but different content: saved under the name with `1` appended (`Name` becomes `Name1`); if `Name1`
    exists too, the first free number is used (`Name2`, ...). If a preset with the new name and the same content
    already exists, it is skipped, so importing the same file twice does not create copies. The name is shortened
    when needed to stay within 40 characters (proposed for these extra cases);
  - otherwise: saved under its own name.
  A Toast reports the counts "imported / renamed / skipped".

Steps:
- [x] 10.1 `PresetJson` (plain Java): write and read the format. `org.json` is a stub in JVM unit tests, so add the
      `org.json:json` artifact as a `testImplementation` through `libs.versions.toml`.
- [x] 10.2 `PresetRepository.importAll(...)`: the skip / rename-with-1 / save rules above, returns the counts.
- [x] 10.3 Unit tests: JSON round trip, invalid and unknown-version files rejected, missing fields use defaults,
      import: identical skipped, same name different content renamed (`Name1`, `Name2`), repeated import adds
      nothing, long names shortened.
- [x] 10.4 Export button on the Graph tab: system file picker (starts in Downloads, default name above), writes the
      file off the main thread, disabled when there are no presets. Toast on success; `IOException` is caught and
      reported.
- [x] 10.5 Import button on the Graph tab: system file picker (starts in Downloads, filter `application/json` plus
      `*/*` because some file managers report JSON with another type), reads the file off the main thread, applies
      the import rules and refreshes the list. Toast with the counts, or an error for a bad file.
- [x] 10.6 Update `AGENTS.md` (Product Context: presets, export/import, the `org.json` test dependency) and
      `docs/NOTES.md`; run `./gradlew assembleDebug lintDebug testDebugUnitTest`; manual pass on a device (export,
      move the file, import on a clean install, import twice).

Open questions (to collect in `docs/NOTES.md` when the stages start):
- Export is all presets in one file; a single-preset export could be added later.
- The duplicate check compares all nine drops, also the hidden ones (beyond the number of drops).
- Should a preset also be loadable from the Loop Page? Proposed: no.
- Sharing presets between phones works by moving the JSON file; there is no built-in share button.

## Out of scope
- Drop data validation (the original app has none).
- New behavior not present in the original app.
