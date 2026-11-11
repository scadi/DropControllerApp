# AGENTS.md

Guidance for agentic coding agents working in this repository.

## Language Rules
- Communicate with the user in **Russian** (chat replies, explanations, questions, summaries).
- Everything written into the repository must be in **English only**: code, identifiers, comments, log messages, commit messages, and documentation such as this file.
- Existing Russian comments (e.g. in `MainActivity`) may be translated to English when you touch those lines.

## Project Overview
- Native Android app written in **Java** (source/target 11, no Kotlin). Single Gradle module `:app`, root project `DropControllerApp`.
- Controls a "drop" device over Bluetooth Classic (SPP/RFCOMM, HC-05/Arduino style). Logic was ported from an MIT App Inventor project; comments refer to "TinyDB", "ListPicker", "Clock_LoopDelayTimer", "BluetoothClient1".
- Package / applicationId: `com.example.dropcontroller`. `minSdk` 24, `compileSdk`/`targetSdk` 36.
- UI is View-based (XML layouts, `findViewById`). No Compose, no ViewBinding, no DI framework, no ViewModel/LiveData.
- Build uses Gradle Kotlin DSL (`*.kts`) and a version catalog at `gradle/libs.versions.toml`. Gradle 9.6.0,
  AGP 8.13.1. JDK 21 (JBR) is used locally.
- No Cursor rules, Copilot instructions, or `.editorconfig` exist. No checkstyle/spotless/PMD configured.

## Product Context (dropController V3 Android app)
This project is a rewrite of the original App Inventor 2 app (Play Store id `appinventor.ai_m_currey.dropController`) into a maintainable native app. The behavior of the original app is the reference; do not invent new behavior. Docs: https://www.dropcontroller.com/android-app-getting-started/ and https://www.dropcontroller.com/android-app/.

- **Device**: dropController V3 (Arduino + HC-06 module), water drop photography. Bluetooth Classic, device is paired in system settings first (PIN `1234`), then picked from the paired-devices list in the app (Settings tab, NOT-CONNECTED / CONNECTED button).
- **Tabs**: Drops, Settings, Drain, Graph, About (About shows app version and, once connected, device firmware version).
- **Settings**: number of drops (1-9), Loop Mode (OFF/ON, delay method TIMER/BUTTON, wait time in seconds), trigger pulse times (ms, default 50), camera/flash trigger enable switches, sounds on/off, theme SYSTEM/LIGHT/DARK (a device preference like sounds: not part of a preset, kept by RESET), RESET (asks for confirmation, then restores all settings and drop data to defaults).
- **Drop data** (per drop): SOL (valve 1-6), START time (ms, 0-9999), START delta (INC/DEC, 0-999), SIZE (valve open time in ms), SIZE delta (INC/DEC, 0-999).
- **Triggers**: one camera trigger (modes: OFF, ON + shutter time, ON + Bulb; Bulb opens the shutter just before the sequence and closes it just after) and three flash triggers (each OFF or ON with a time in ms). Camera and flash times can also have deltas. When a trigger is OFF in Settings its controls on the Drops tab are hidden (the original greys them out; changed on request).
- **START button**: grey/inactive when not connected, green when connected, shows ACTIVE while the device runs a sequence (device: yellow LED = idle, green LED = sequence active).
- **Single Sequence Mode** (Loop Mode OFF): START sends the drop data immediately; deltas are ignored.
- **Loop Mode**: START opens the Loop Page. TIMER: first sequence starts immediately, then a countdown (wait time) before each next one; STOP pauses at the current sequence number, RESET sets the sequence back to 1 and the drop data to start values. BUTTON: each sequence waits for the Loop Page START button. After each sequence every value with a delta is updated (INC adds, DEC subtracts).
- **Delta limits**: results are clamped to the normal range of the value (e.g. START time to 0..9999), never rejected.
- **Loop Page** shows per drop: SOL, START with delta in brackets, SIZE with delta in brackets, STOP (computed by the app), plus CT, FT1, FT2, FT3 lines.
- **Validation**: the original app does not validate drop data (overlapping drops on one valve are allowed). Do not add validation unless asked.
- **Graph tab**: chart of drop times and trigger times; the shutter is shown open from its start time for the exposure duration.
- **Presets** (new feature, not in the original app): the Drops tab has SAVE and LOAD icon buttons next to START. A preset is a named copy of the drop data and the setup settings (number of drops, Loop Mode, delay method, wait time, pulse times); the Sounds switch is not part of it. Names are 1-40 characters and unique ignoring case. Presets are disabled while a sequence runs. The Graph tab lists the presets as cards (chart, Load, long-press name to rename, trash to delete) and has Export / Import buttons.
- **Export / import**: all presets go into one JSON file (`format` marker `dropcontroller-presets`, `version` 1) chosen with the system file picker (no storage permission). Import skips a preset with the same name and content, and saves one with the same name but different content as `Name1`, `Name2`, ... Format code is in `config/PresetJson`, import rules in `PresetRepository.importAll`.
- **Drain tab**: opens a valve manually for draining.
- The wire protocol is defined by the Arduino sketch in `firmware/` and summarized in `docs/PROTOCOL.md`. The existing `D%d,F%s,S%d` message in `DropsFragment` is a tutorial example, not the real protocol. Do not change message formats without asking.

The staged work plan and current feature status live in `docs/PLAN.md`; keep its checkboxes up to date as stages are completed.

## Build / Lint / Test Commands
Run from the repo root. On Windows use `.\gradlew.bat` (or `./gradlew` in bash).

| Task | Command |
|---|---|
| Debug APK | `./gradlew assembleDebug` |
| Release APK | `./gradlew assembleRelease` |
| Clean | `./gradlew clean` |
| Install on device | `./gradlew installDebug` |
| Lint (Android defaults only) | `./gradlew lintDebug` (or `lint`) |
| Full verification | `./gradlew check` |
| All unit tests (JVM) | `./gradlew testDebugUnitTest` (or `test`) |
| All instrumented tests (device/emulator) | `./gradlew connectedDebugAndroidTest` |

### Running a single test
- Single unit test class:
  `./gradlew testDebugUnitTest --tests "com.example.dropcontroller.ExampleUnitTest"`
- Single unit test method:
  `./gradlew testDebugUnitTest --tests "com.example.dropcontroller.ExampleUnitTest.addition_isCorrect"`
- Single instrumented test (needs a connected device/emulator):
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.dropcontroller.ExampleInstrumentedTest#useAppContext`

Unit tests for the model, storage and `logic/` classes live in `src/test` (JUnit4); `src/androidTest` only has the stock example. There is no mocking library or Robolectric; add dependencies via the version catalog if needed. `org.json:json` is a `testImplementation` dependency, because `org.json` is only a stub in JVM unit tests.

## Project Layout
```
app/src/main/java/com/example/dropcontroller/
  DropControllerApp.java       Application: shared settings, drop data, BluetoothHelper, save/reset
  MainActivity.java            splash, bottom nav, BT runtime permissions
  config/AppPreferences.java   SharedPreferences wrapper (KeyValueStore)
  config/StateStorage.java     saves/loads AppSettings and SequenceData
  data/*.java                  models: AppSettings, SequenceData, DropData, triggers, deltas, Limits
  logic/*.java                 plain Java (no Android): protocol encoder/framer, timing, loop state machine,
                               device simulator for testing without hardware, protocol logs
  fragments/*Fragment.java     About, Drain, Drops, Graph, Settings
  services/BluetoothHelper.java Bluetooth connect/send/receive, listener callbacks on the main thread
  services/DeviceLink.java     byte stream abstraction (RFCOMM socket or the simulator pipes)
  services/SimulatorLink.java  in-process link to logic/DeviceSimulator (debug-only entry point)
  views/GraphCanvasView.java   custom View
app/src/main/res/              layout/, menu/, drawable/, values/, values-night/, xml/
```

## Known Pitfalls (check before editing)
- `BluetoothHelper` is a single shared instance owned by `DropControllerApp`; get it via
  `((DropControllerApp) requireActivity().getApplication()).getBluetoothHelper()` and register/unregister a
  `BluetoothHelper.Listener` in `onStart`/`onStop`.
- `androidx.recyclerview` is not declared explicitly in `app/build.gradle.kts`; it arrives transitively. Add it to `libs.versions.toml` before using it.
- `lintDebug` can report a stale manifest error from cached analysis; rerun with `--rerun-tasks` before chasing it.
- `BluetoothAdapter.getDefaultAdapter()` is deprecated but in use.
- Debug builds add a "Simulated dropController (debug)" entry to the Settings device list
  (`BluetoothHelper.connectSimulator()`, `SimulatorLink`, `logic/DeviceSimulator`). It works on an emulator and
  without hardware; the real RFCOMM path (`RfcommLink`, `connect(BluetoothDevice)`) must stay unchanged.
- Every message sent or received by `BluetoothHelper` is recorded in `logic/ProtocolLog` and shown by the debug-only
  "Protocol log (debug)" overflow item (`MainActivity.showProtocolLog()`). The same messages are appended to
  `logic/ProtocolFileLog` (`files/logs/protocol.log`, 200 KB limit) in every build; the About tab button opens that
  file and shares it through the `.fileprovider` FileProvider. Keep the log classes Android-free so they stay
  JVM-testable, and post listener callbacks to the main thread in the UI.
- Several files have uncommitted work in progress; check `git status` and do not revert changes you did not make.

## Code Style Guidelines

### Formatting
- 4-space indentation, K&R braces (opening brace on same line). Continuation lines indent 8 spaces (chained calls, wrapped arguments).
- Keep lines reasonably short (aim for <= 120 chars; existing code goes up to ~140).
- Files end with a trailing newline. UTF-8 encoding.
- XML: 4-space indent; attribute order `layout_width`, `layout_height`, then the rest.

### Imports
- Individual imports only; no wildcards (the only exception is `import static org.junit.Assert.*;` in tests).
- Group and order: `android.*`, `androidx.*`, `com.example.*`, `java.*`/`javax.*`, then static imports last. Do not add duplicates.
- Remove unused imports in files you edit.

### Types and Language Features
- Plain Java 11: generics with diamond operator, lambdas for listeners/callbacks. Do not use Java features above 11.
- Annotate public/API parameters and returns with `@NonNull` / `@Nullable` (`androidx.annotation`) in new code.
- Prefer returning empty collections over `null` for failures (existing `getPairedDevices()` returns `null`; handle that at call sites).
- Prefer proper numeric types in new model fields; existing `DropData` stores numbers as `String` (`solenoidFrequency`, `startTime`, `sizeValue`), so parse defensively (`NumberFormatException`).
- Constants should be `private static final`.
- Models are mutable POJOs with getters/setters.

### Naming
- Classes: PascalCase; fragments end with `Fragment`, adapters with `Adapter`.
- Methods and fields: camelCase. Constants: `UPPER_SNAKE_CASE`.
- Bluetooth thread inner-class fields use the `mm` prefix (`mmSocket`) per the Android sample; other fields have no prefix.
- Resource IDs: snake_case with type prefix: `btn_`, `tv_`, `et_`, `sb_`, `rv_`, `nav_`.
- Layouts: `activity_*`, `fragment_*`, `item_*`. Drawables: `ic_<name>_black_24dp`.
- Test methods: `snake_case_describing_behavior` or `method_condition` (e.g. `addition_isCorrect`).

### Android Patterns
- Fragments use the `newInstance(...)` + `Bundle` args pattern; `onCreateView` inflates `R.layout.fragment_*`.
- Tab navigation (`MainActivity.showTab`): each tab fragment is added once (tag `tab_<nav id>`) and then only hidden/shown, with `setMaxLifecycle(STARTED)` while hidden. A hidden tab gets `onPause`, but not `onStop`, and its views stay alive: register Bluetooth/sequence listeners and refresh from the shared model in `onResume`/`onPause`, not in `onStart`/`onStop`.
- No DI: construct collaborators manually (shared objects live in `DropControllerApp`). Use callback interfaces implemented by the fragment (e.g. `BluetoothHelper.Listener`).
- Do not hold an Activity/Fragment `Context` in long-lived objects; prefer `getApplicationContext()` (current `BluetoothHelper` risks a leak).
- Release resources in lifecycle methods (`onDestroy`: disconnect Bluetooth, stop handlers/timers, remove callbacks).
- Put user-visible strings in `res/values/strings.xml` rather than hard-coding in Java/XML (many existing ones are hard-coded and trigger lint warnings; do not add more).
- Persist settings via `AppPreferences` (SharedPreferences wrapper), not ad hoc `getSharedPreferences` calls.

### Bluetooth and Threading
- All blocking socket I/O (connect/read/write) must run off the main thread. Post UI updates (Toast, view changes) back via a main-looper `Handler`.
- Runtime permissions are checked in `MainActivity.checkAndRequestBluetoothPermissions()` using `ContextCompat.checkSelfPermission`, branching on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`. Keep `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` (API 31+) and legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` (maxSdk 30) manifest entries consistent.
- `@SuppressLint("MissingPermission")` is used at call sites in `BluetoothHelper`; only use it where permissions are verified upstream.
- Guard shared state: connection state (`isConnected`) is a plain field; mark it `volatile` or synchronize when modifying thread code. Handle `bytes == -1` and a null `mmSocket` in read loops.

### Error Handling and Logging
- Catch specific exceptions (`IOException`, `SecurityException`, `NumberFormatException`); do not catch `Exception`/`Throwable` broadly.
- Surface failures to the user with a `Toast` (on the main thread); use early `return` guard clauses for invalid state (not connected, null device).
- Do not leave empty `catch` blocks without a comment explaining why (e.g. "Ignore failure during close"); prefer logging.
- Logging: `android.util.Log` with a `private static final String TAG = "ClassName";` constant (existing hard-coded `"Timer"` tag is legacy). No logging framework (no Timber).

### Comments
- Existing code is tutorial-style with numbered steps and `// -----` dividers, and "Analogous to <App Inventor block>" notes. Keep such notes accurate when modifying ported logic, but do not add noise.
- Write new comments in English. Resolve or keep `// TODO:` markers honest; do not add "NEW:" markers.

## Workflow Tips
- After changes, run `./gradlew assembleDebug` and `./gradlew lintDebug` at minimum; run `./gradlew testDebugUnitTest` when logic changes.
- Do not commit `local.properties`, `.gradle/`, `build/`, `.idea/` (partially ignored), or `*.iml`.
- Keep edits minimal and consistent with surrounding code; do not mass-reformat files with existing uncommitted changes.
