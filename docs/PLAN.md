# Work Plan

Goal: bring the app to feature parity with the original App Inventor app
(see "Product Context" in `AGENTS.md`). The original behavior is the reference.

Status legend: `[ ]` todo, `[x]` done.

## Current state (audit summary)
- Done: stages 0-5 (protocol, data model, pure-Java logic with unit tests, shared state in
  `DropControllerApp`, reworked `BluetoothHelper`, persistence, Settings tab, Drops tab with Single Sequence Mode
  via `SequenceRunner`, Loop Page `LoopActivity` driven by `LoopController`, Drain, About, Graph),
  bottom navigation, Bluetooth runtime permissions.
- Missing: sounds.
- Next: stage 6 (polish). Ideas and open questions are collected in `docs/NOTES.md`.

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
- [x] Disable controls when the trigger is OFF.
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
- [ ] Sounds (if the sound events can be identified from the original app).
- [x] Move hard-coded strings to `strings.xml`.
- [x] Manifest: `uses-feature` for Bluetooth, `neverForLocation` flag on `BLUETOOTH_SCAN`,
      catch `SecurityException` when permissions are denied (already done in `BluetoothHelper`).
- [x] Declare `androidx.recyclerview` explicitly in `libs.versions.toml`.
      Not needed now: nothing in `app/src` uses RecyclerView. Add it to the catalog when the first use appears.
- [x] Handle activity re-creation (rotation) and select the current nav item on start.
- [ ] Run `./gradlew assembleDebug lintDebug testDebugUnitTest` after each stage.

## Out of scope
- Drop data validation (the original app has none).
- New behavior not present in the original app.
