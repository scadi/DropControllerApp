# Notes and Ideas

Remarks, open questions and improvement ideas collected while working on `docs/PLAN.md`.
Nothing here is committed work: move an item into `PLAN.md` (with a checkbox) when it is accepted.
Mark an item `[x]` here when it has been dealt with, and say where.

## Open questions (need the real device or the original app)
- [ ] Trigger pulse time: confirm `STOP = START + pulse` for CT/FT in normal mode (`PROTOCOL.md`, decision 2).
- [ ] Bulb mode: confirm the shutter close margin (+100 ms) and that the camera time/delta are ignored.
- [ ] Message pacing of 30 ms: verify on a real HC-06 that nothing is dropped.
- [ ] Graph shutter bar: confirm it matches the original app (`PROTOCOL.md`, decision 7).
- [x] Which events play sounds in the original app: still unknown. Sounds were added for events chosen with the
      user (sequence start, sequence end, Loop Mode countdown end); see "Sounds" below.
- [ ] Original behavior when the CONNECTED button is tapped while connected (the app currently disconnects).
- [ ] Original behavior of the RESET button: does it ask for confirmation?

## Improvement ideas (not in the plan)
- [ ] Ask for confirmation before RESET (it erases all drop data). Skipped in stage 2 to match the original.
- [ ] Remember the last used device and offer a one-tap reconnect.
- [ ] Auto-reconnect after a lost connection.
- [ ] Replace deprecated `BottomNavigationView.OnNavigationItemSelectedListener` with
      `OnItemSelectedListener` (`MainActivity`).
- [ ] Bump outdated dependencies reported by lint (AGP, appcompat, material, activity, constraintlayout).
- [ ] Remove unused `colors.xml` entries (`black`, `white`) or use them in the theme.
- [ ] Tablet / landscape layouts for the Drops tab (9 drop rows are tall).
- [ ] Keep the screen on while a loop sequence is running.
- [ ] Foreground service for long loop runs, so Android does not kill the timer when the screen is off.
- [ ] Export / import of drop presets.

## Stage 2 remarks
- [ ] Settings: the "Camera trigger" and "Flash trigger 1-3" switches write `SequenceData`; stage 3 must read the same
      objects so the Drops tab disables the controls of a trigger that is OFF.
- [ ] Settings: lowering "Number of drops" keeps the data of the hidden drops (the model always holds 9). Check with
      the original app whether hidden drops should be reset.
- [ ] Settings: Loop Mode wait time, delay method and wait seconds stay editable while a loop is running; the Loop Page
      (stage 4) must read them once at start.
- [ ] Settings: number fields accept 0-999 (3 digits, clamped by `AppSettings`); empty input keeps the old value.
- [ ] Settings: the layout has no per-trigger pulse for each flash; the original has one flash pulse time for all
      flashes (assumed from `AppSettings.flashPulseMs`). Verify.
- [ ] No UI tests for the Settings tab (no Robolectric/Espresso setup); the model and storage are covered by JVM tests.
- [x] Settings tab label should turn blue when connected (done in stage 6, `MainActivity.updateSettingsTabLabel`).
      Not verified on a device: the color is applied with a `ForegroundColorSpan` on the menu title; check that the
      Material bottom navigation keeps the span (otherwise use an item text color state instead).

## Stage 3 remarks
- [ ] Not tested on a device or emulator: layouts, focus handling of number fields and the START flow are only
      verified by compiling and lint. A manual pass is needed (esp. keyboard Done, rotation, 9 drops scrolling).
- [ ] `SequenceRunner` uses `Handler`/`Looper`, so it has no JVM unit test. Idea: extract the timeout calculation
      or inject a scheduler interface to make it testable.
- [ ] The camera ON/OFF and Bulb split: ON/OFF lives in Settings (switch), Bulb lives on the Drops tab (switch).
      The original has one 3-state camera control (OFF / ON + time / ON + Bulb). Consider a single control on the
      Drops tab for parity.
- [x] Flash and camera controls on the Drops tab are hidden when the trigger is OFF (the original greys them out;
      changed on the user's request, see `PLAN.md`, stage 3). The values stay in the model and are still sent.
- [ ] In Bulb mode the camera time/delta row is disabled because they are not used (`PROTOCOL.md`, decision 3).
- [ ] Sequence number sent in `[S<n>]` is always 1 in Single Sequence Mode; the Loop Page (stage 4) should pass its
      sequence counter.
- [ ] Values are clamped silently when a field is committed (e.g. 12000 becomes 9999). A short hint or Toast could
      help, but the original does not validate (see "Out of scope").
- [ ] Drop views for hidden drops are `GONE` but still inflated (9 rows). Fine for now; a RecyclerView is not needed.
- [ ] START is disabled (not just grey) while not connected. Check whether the original shows a message on tap.
- [ ] Sequence timeout path shows a Toast only if the Drops tab is visible.
- [ ] `SequenceRunner` registers its `BluetoothHelper` listener for the app lifetime (never removed on purpose).

## Stage 4 remarks
- [ ] Not tested on a device: the Loop Page flow (TIMER countdown, BUTTON, STOP, RESET, disconnect in the middle)
      is verified only by `LoopControllerTest` (state machine) and compilation. `LoopActivity` has no tests.
- [ ] The loop works on a copy of the drop data taken when the Loop Page opens. The Drops tab keeps the start values
      after the loop ends, and RESET on the Loop Page only resets that copy. Check with the original app whether the
      Drops tab should show the last loop values.
- [ ] Leaving the Loop Page (Back) ends the loop; a sequence already sent still runs on the device until `[FTS]`.
      The original may ask for confirmation or keep running. Verify.
- [ ] The loop is driven by a `Handler` in the activity. If Android stops the app in the background (screen off for
      long), the countdown is not guaranteed. Idea: foreground service (see "Improvement ideas").
- [ ] The Loop Page is declared with `configChanges` so rotation does not recreate it. Process death loses the loop
      state (the page then starts a fresh loop in TIMER mode); persisting the sequence number is not planned.
- [ ] START on the Loop Page is disabled while not connected; STOP is disabled while idle, RESET while a sequence
      runs. These rules are an interpretation of the site description, not verified against the original.
- [ ] After a timeout (`[FTS]` never arrived) the loop treats the sequence as finished and goes on. Alternative: stop
      the loop and show an error. Decide after testing with the real device.
- [ ] Loop Page lines: `DROP n  SOL s  STOP t` and `START x [+d]  SIZE y [+d]`, `CT:`/`FT1-3:` lines. Exact text and
      order of the original page are unknown; adjust if a screenshot is available.
- [ ] The Loop Page does not show the wait time setting; only the live countdown.
- [ ] `[S<n>]` now carries the real sequence number (debug use only on the device).

## Stage 5 remarks
- [ ] Not tested on a device: Drain, About and Graph are verified only by compilation, lint and `GraphModelTest`.
- [ ] Graph shows the data of the Drops/Settings tabs (start values); the Loop Page copy is not reflected.
- [ ] Graph bar colors are hard-coded in `GraphCanvasView`; row labels use the theme text color (dark mode).
- [ ] Drain closes all valves ([OX]) when the tab is left (`onStop`), so the switches match the device.
- [ ] About shows `versionName` from the manifest (currently 1.0). The firmware requires app version 30401 or higher
      (`PROTOCOL.md`); the device does not check it over Bluetooth, so it is not enforced.

## Sounds (stage 6)
- [ ] Not tested on a device: tones, volume (`STREAM_MUSIC`, 80%) and how they sound need a manual check.
- [ ] The tones are `ToneGenerator` beeps (start `TONE_PROP_BEEP`, end `TONE_PROP_ACK`, countdown end
      `TONE_PROP_BEEP2`). Replace them with audio files if better sounds are wanted.
- [ ] A start tone is skipped when another tone played less than 500 ms before (the countdown-end tone or the end tone
      when the wait time is 0), because the new tone would cut the old one.
- [ ] No sound for a timeout or a lost connection (only for [FTS] from the device).
- [ ] The countdown-end tone also plays if the Loop Page is open but the screen is off, as long as the app runs.
- [ ] `SoundPlayer` is not covered by JVM tests (Android audio classes); the events behind it are
      (`LoopControllerTest`).

## Technical debt
- [ ] `BluetoothHelper` uses `BluetoothDevice.getName()` through a permission check at every call; consider caching.
- [x] Hard-coded strings in `activity_main.xml`, `bottom_nav_menu.xml`, `fragment_graph.xml`
      (moved to `strings.xml` in stage 6).
- [x] `lint` warned about the unnecessary wrapper `LinearLayout` in `fragment_drops.xml` (gone in stage 3).
