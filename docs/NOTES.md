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
- [ ] Original behavior of the RESET button: does it ask for confirmation? (The app asks now, by decision.)

## Improvement ideas (not in the plan)
- [x] Ask for confirmation before RESET (it erases all drop data). Done in stage 7 (`SettingsFragment.confirmReset`).
- [ ] Remember the last used device and offer a one-tap reconnect.
- [ ] Auto-reconnect after a lost connection.
- [x] Replaced deprecated `BottomNavigationView.OnNavigationItemSelectedListener` with
      `NavigationBarView.OnItemSelectedListener` (`MainActivity`).
- [ ] Bump outdated dependencies reported by lint (AGP, appcompat, material, activity, constraintlayout).
- [x] Remove unused `colors.xml` entries (`black`, `white`) or use them in the theme (deleted in stage 7).
- [ ] Colors on a device: check the drop card shades (light and dark), the START label on all three states and the
      darker flash bar in the light graph. With the current scale (20-78%) every light card gets dark text (5.3:1 or
      better) and every dark card gets light text (6.4:1 or better).
- [ ] Theme selector: not tested on a device. Check that changing it on the Settings tab keeps the tab and the
      values, that the Loop Page (if open) follows, and that the first launch follows the system theme.
- [x] Dark theme switch had no "follow the system" choice after the first tap. Replaced by a SYSTEM / LIGHT / DARK
      segmented button (stage 7).
- [ ] Interactive Settings (drop icons, wait time slider, pulse chips, trigger chips, dimmed Loop options): not tested
      on a device. Check the drop icon sizes on a narrow screen, dragging over the icons inside the scrolling page,
      the slider against the field (including a time above 120 s), and the look of the checked chips in both themes.
- [ ] Light theme: the surface/background (1.09:1) and card outline (1.65:1) contrast is low by Material 3 design;
      raise `md_outline_variant` if the cards blend into the background on a real screen.
- [ ] Tablet / landscape layouts for the Drops tab (9 drop rows are tall).
- [ ] Keep the screen on while a loop sequence is running.
- [ ] Foreground service for long loop runs, so Android does not kill the timer when the screen is off.
- [x] Export / import of drop presets (stages 8-10 of `PLAN.md`).

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
Closed on the user's request: sounds are not worked on any more, so nothing here is a todo. The remarks stay as
background for a future change.
- [x] Not tested on a device: tones, volume (`STREAM_MUSIC`, 80%) and how they sound need a manual check.
- [x] The tones are `ToneGenerator` beeps (start `TONE_PROP_BEEP`, end `TONE_PROP_ACK`, countdown end
      `TONE_PROP_BEEP2`). Replace them with audio files if better sounds are wanted.
- [x] A start tone is skipped when another tone played less than 500 ms before (the countdown-end tone or the end tone
      when the wait time is 0), because the new tone would cut the old one.
- [x] No sound for a timeout or a lost connection (only for [FTS] from the device).
- [x] The countdown-end tone also plays if the Loop Page is open but the screen is off, as long as the app runs.
- [x] `SoundPlayer` is not covered by JVM tests (Android audio classes); the events behind it are
      (`LoopControllerTest`).

## Technical debt
- [ ] `BluetoothHelper` uses `BluetoothDevice.getName()` through a permission check at every call; consider caching.
- [x] Hard-coded strings in `activity_main.xml`, `bottom_nav_menu.xml`, `fragment_graph.xml`
      (moved to `strings.xml` in stage 6).
- [x] `lint` warned about the unnecessary wrapper `LinearLayout` in `fragment_drops.xml` (gone in stage 3).

## Stage 8 remarks
- [ ] Not tested on a device: the Save / Load dialogs, the START + SAVE/LOAD button block (START should match the
      height of the two stacked buttons), keyboard Done in the name field, rotation with an open dialog (it is
      dismissed on rotation), loading a preset and then opening the Settings tab.
- [ ] Hidden drops (beyond the number of drops) are saved and loaded too, and take part in `hasSameContentAs`.
- [ ] Loading a preset while Loop Mode is ON keeps the Loop Mode setting from the preset; the Loop Page is not
      involved (it covers the tabs while a loop runs).
- [ ] `PresetRepository.getAll()` reads every preset from SharedPreferences on each call; fine for a handful of
      presets. Cache it if the Graph tab (stage 9) makes this slow.
- [ ] The Save dialog overwrites by name without comparing content; an identical setup under the same name is simply
      written again.

## Stage 9 and 10 remarks
- [ ] Not tested on a device: preset cards (5+ presets, scrolling), rename by long press and the keyboard, delete
      dialog, rotation and dark mode, the system file pickers (start folder Downloads), export, moving the file to
      another phone, import on a clean install, importing the same file twice (second import: all skipped).
- [ ] A rename in progress is cancelled on rotation or when the tab is left (accepted in the plan).
- [ ] Export is all presets in one file; a single-preset export could be added later.
- [ ] The duplicate check compares all nine drops, also the hidden ones (beyond the number of drops).
- [ ] A preset is not loadable from the Loop Page (decided: no).
- [ ] Sharing presets between phones works by moving the JSON file; there is no built-in share button.
- [ ] Import rule for renamed presets: if `Name1` already holds identical content, the incoming preset is skipped
      instead of creating `Name2`. This keeps repeated imports from adding copies.
- [ ] Import reads at most 5 million characters; a larger file is rejected with a message.
- [ ] Import/export file work runs on one background thread; the repository is only used on the main thread.
