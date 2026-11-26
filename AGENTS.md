# AGENTS.md

Guidance for agentic coding agents working in this repository.

## Language Rules
- Communicate with the user in **Russian** (chat replies, explanations, questions, summaries).
- Everything written into the repository must be in **English only**: code, identifiers, comments, log messages, commit messages, and documentation such as this file.
- Existing Russian comments (e.g. in `MainActivity`) may be translated to English when you touch those lines.

## Project Overview
- Native Android app written in **Java** (source/target 11, no Kotlin). Single Gradle module `:app`, root project `DropControllerApp`.
- Controls a "drop" device over Bluetooth Classic (SPP/RFCOMM, HC-05/Arduino style) or, with a BLE UART module such as the BT05, over Bluetooth Low Energy. Logic was ported from an MIT App Inventor project; comments refer to "TinyDB", "ListPicker", "Clock_LoopDelayTimer", "BluetoothClient1".
- Package / applicationId: the Java package and `namespace` are `com.example.dropcontroller`; the `applicationId` is `com.scadi.dropcontrollerv3`. `minSdk` 24, `compileSdk`/`targetSdk` 36.
- UI is View-based (XML layouts, `findViewById`). No Compose, no ViewBinding, no DI framework, no ViewModel/LiveData.
- Build uses Gradle Kotlin DSL (`*.kts`) and a version catalog at `gradle/libs.versions.toml`. Gradle 9.6.0,
  AGP 8.13.1. JDK 21 (JBR) is used locally.
- No Cursor rules, Copilot instructions, or `.editorconfig` exist. No checkstyle/spotless/PMD configured.

## Product Context (dropController V3 Android app)
This project is a rewrite of the original App Inventor 2 app (Play Store id `appinventor.ai_m_currey.dropController`) into a maintainable native app. The behavior of the original app is the reference; do not invent new behavior. Docs: https://www.dropcontroller.com/android-app-getting-started/ and https://www.dropcontroller.com/android-app/.

- **Permission**: Martyn Currey (author of the dropController) agreed to the app name, the dC icon, the included firmware and a Google Play release from our own account. The app name must contain "V3" (the V4 controller is a different device and does not work with this app). Never reuse his Play Store id. Do not edit the firmware files in `firmware/`. App code is MIT (`LICENSE`); the firmware is his. Status in `docs/PLAN.md` Stage 12.
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
- The wire protocol is defined by the Arduino sketch in `firmware/dropControllerV3_dC3_AS25006/` and summarized in `docs/PROTOCOL.md`. The existing `D%d,F%s,S%d` message in `DropsFragment` is a tutorial example, not the real protocol. Do not change message formats without asking.

The staged work plan and current feature status live in `docs/PLAN.md`; keep its checkboxes up to date as stages are completed.

## Build / Lint / Test Commands
Run from the repo root. On Windows use `.\gradlew.bat` (or `./gradlew` in bash).

| Task | Command |
|---|---|
| Debug APK | `./gradlew assembleDebug` |
| Release APK | `./gradlew assembleRelease` |
| Debug + Release APKs | `./gradlew buildApks` (output in `build/dist/`) |
| Clean | `./gradlew clean` |
| Install on device | `./gradlew installDebug` |
| Lint (Android defaults only) | `./gradlew lintDebug` (or `lint`) |
| Full verification | `./gradlew check` |
| All unit tests (JVM) | `./gradlew testDebugUnitTest` (or `test`) |
| All instrumented tests (device/emulator) | `./gradlew connectedDebugAndroidTest` |

APK locations: `buildApks` puts the final files in `build/dist/` (the canonical place). `assembleDebug` and
`assembleRelease` leave them in `app/build/outputs/apk/<type>/`. `app/release/` is created only by the Android Studio
"Generate Signed App Bundle or APK" wizard (git-ignored); do not rely on it.

### Running a single test
- Single unit test class:
  `./gradlew testDebugUnitTest --tests "com.example.dropcontroller.ExampleUnitTest"`
- Single unit test method:
  `./gradlew testDebugUnitTest --tests "com.example.dropcontroller.ExampleUnitTest.addition_isCorrect"`
- Single instrumented test (needs a connected device/emulator):
  `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.dropcontroller.ExampleInstrumentedTest#useAppContext`

Unit tests for the model, storage and `logic/` classes live in `src/test` (JUnit4); `src/androidTest` only has the stock example. There is no mocking library or Robolectric; add dependencies via the version catalog if needed. `org.json:json` is a `testImplementation` dependency, because `org.json` is only a stub in JVM unit tests.

### Firmware (Arduino Nano)
The firmware is a multi-file Arduino sketch in `firmware/dropControllerV3_dC3_AS25006/`; the pin map and the toolchain setup are in `firmware/README.md`. Firmware work belongs to the `arduino` agent.

| Task | Command |
|---|---|
| Compile firmware | `powershell -NoProfile -ExecutionPolicy Bypass -File firmware/build.ps1` |
| Compile and upload | `powershell -NoProfile -ExecutionPolicy Bypass -File firmware/build.ps1 -Upload -Port COM5` |

The build uses the `arduino-cli.exe` bundled with Arduino IDE 2.x in `C:\Program Files\Arduino IDE` (override with `-IdeHome` or `ARDUINO_IDE_HOME`). `firmware/setup-toolchain.ps1` runs on the first build and prepares a private arduino-cli data folder in `%LOCALAPPDATA%\DropControllerArduino\cli-data`; if `downloads.arduino.cc` is blocked it links the AVR core and tools from the Arduino IDE 1.8.x package. `discovery not found` warnings are expected and harmless. Build output goes to `firmware/build/` (git-ignored).

## Project Layout
```
app/src/main/java/com/example/dropcontroller/
  DropControllerApp.java       Application: shared settings, drop data, BluetoothHelper, save/reset
  MainActivity.java            splash, bottom nav, BT runtime permissions
  LogDialogs.java              communication log dialog (live), opened through MainActivity
  config/AppPreferences.java   SharedPreferences wrapper (KeyValueStore)
  config/StateStorage.java     saves/loads AppSettings and SequenceData
  data/*.java                  models: AppSettings, SequenceData, DropData, triggers, deltas, Limits
  logic/*.java                 plain Java (no Android): protocol encoder/framer, timing, loop state machine,
                               device simulator for testing without hardware, communication log file
  fragments/*Fragment.java     About, Drain, Drops, Graph, Settings
  services/BluetoothHelper.java Bluetooth connect/send/receive, listener callbacks on the main thread
  services/DeviceLink.java     byte stream abstraction (RFCOMM socket, BLE link or the simulator pipes)
  services/BleLink.java        BLE UART link (GATT, service FFE0 / characteristic FFE1), writes in 20-byte packets
  services/BleScanner.java     8 s scan for BLE UART modules for the "Search BLE modules" button
  services/SimulatorLink.java  in-process link to logic/DeviceSimulator (debug-only entry point)
  views/GraphCanvasView.java   custom View
app/src/main/res/              layout/, menu/, drawable/, values/, values-night/, xml/
firmware/dropControllerV3_dC3_AS25006/  Arduino Nano sketch (main .ino + four modules)
firmware/build.ps1             compile/upload helper
firmware/setup-toolchain.ps1   one-time AVR toolchain setup
firmware/README.md             pin map and toolchain setup
docs/PROTOCOL.md               wire protocol
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
  `connectSimulator()` does nothing unless `BluetoothHelper` was created with `simulatorAllowed` (debug builds).
- Every message sent or received by `BluetoothHelper`, plus connection events (`# connected`,
  `# connection lost`, ...), is appended to `logic/ProtocolFileLog` (`files/logs/protocol.log`, 200 KB limit) in
  every build, from a dedicated executor so the Bluetooth threads never wait for the disk. This is the only log (the
  old in-memory `ProtocolLog` and its debug menu item were removed as a duplicate). The About tab button opens it in
  `LogDialogs.showCommunicationLog()`: the dialog registers a `ProtocolFileLog.Listener`, and a change reloads the
  file at most every 300 ms (skipped while the user selects text; the view follows the end only when it was at the
  bottom). Share goes through the `.fileprovider` FileProvider. Keep the log class Android-free so it stays
  JVM-testable; its listeners run on the file log thread, so the UI must hop to the main thread.
- Two transports: RFCOMM (`ConnectThread`, `RfcommLink`, HC-05 / HC-06) and BLE (`BleLink`, BT05 / HM-10). Keep the
  RFCOMM path unchanged. Both end in `BluetoothHelper.connected(...)`, so everything above the `DeviceLink` is shared.
  The rule is in `logic/BluetoothTransport`: BLE only for a device found by the BLE search or a paired device that
  Android reports as LE only; no automatic fallback. The saved last device is the plain address for RFCOMM and
  `ble:<address>` for BLE. The GATT callbacks of `BleLink` run on a binder thread: they only queue data and results.
  A BLE module is normally not paired, so it is not in `getBondedDevices()`; the picker has "Search BLE modules".
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
