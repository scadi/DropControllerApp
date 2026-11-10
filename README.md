# DropController App

Native Android app for the **dropController V3**, an Arduino-based solenoid valve controller for water drop
photography. The app talks to the controller over Bluetooth Classic (HC-06 module), sets up the drops, the camera
trigger and the flash triggers, and starts the sequence.

This project is a rewrite of the original dropController Android app (made with MIT App Inventor) as a maintainable
native Java app. The behavior of the original app is the reference.

## Original project

- Website: <https://www.dropcontroller.com/>
- Android app, getting started: <https://www.dropcontroller.com/android-app-getting-started/>
- Android app, reference: <https://www.dropcontroller.com/android-app/>
- dropControllerV3 overview: <https://www.dropcontroller.com/dropcontrollerv3/>
- dropControllerV3 basic set up: <https://www.dropcontroller.com/basic-set-up/>

The dropController hardware, firmware and the original app are by Martyn Currey. See the website for the hardware,
kits and set up guides.

## Features

- **Connection**: pick the paired controller from the list of paired Bluetooth devices (pair it in the system
  settings first). The Settings tab shows the connection state.
- **Drops tab**: for 1 to 9 drops set the valve (SOL 1-6), the START time and the SIZE (valve open time), each with
  an INC / DEC delta. Camera trigger (OFF, ON with a time, or Bulb) and three flash triggers with times and deltas.
  The START button is grey when not connected, green when connected and shows ACTIVE while a sequence runs.
- **Single Sequence Mode**: START sends the drop data to the controller at once.
- **Loop Mode**: runs sequences one after another, with a TIMER (countdown between sequences) or BUTTON (start each
  one by hand) delay method. After each sequence every value with a delta is increased or decreased. The Loop Page
  shows the values of every drop and trigger, with STOP and RESET.
- **Presets**: save the drop data and the setup (number of drops, Loop Mode, delay method, wait time, pulse times) under
  a name and load it later. Presets are listed as cards on the Graph tab, which can also rename and delete them.
- **Export / import**: all presets go into one JSON file, chosen with the system file picker.
- **Graph tab**: chart of the drop times and the trigger times, with the shutter shown open for the exposure.
- **Drain tab**: opens a valve by hand, for draining and flushing.
- **Settings**: number of drops, Loop Mode, trigger pulse times, which triggers are enabled, sounds, theme (system,
  light or dark) and RESET to the defaults (asks for confirmation).
- **About tab**: app version and, once connected, the firmware version of the controller.

## Requirements

- Android 7.0 (API 24) or newer, with Bluetooth.
- A dropController V3 with the firmware from `firmware/` (see `docs/PROTOCOL.md`).

## Build

The project is a single Gradle module (`:app`) written in Java 11 with Gradle Kotlin DSL. A JDK 21 is used for the
build.

```
./gradlew assembleDebug          # debug APK
./gradlew installDebug           # install on a connected device
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug              # Android lint
```

On Windows use `.\gradlew.bat`.

## Project layout

```
app/src/main/java/com/example/dropcontroller/
  config/      saved settings, drop data and presets
  data/        models: settings, drops, triggers, deltas, limits
  logic/       plain Java: protocol encoder, timing, loop state machine
  fragments/   the tabs (Drops, Settings, Drain, Graph, About)
  services/    Bluetooth connection and sequence runner
  views/       custom views (graph, value and delta rows)
firmware/      Arduino sketch of the controller
docs/          PROTOCOL.md (wire protocol), PLAN.md (work plan and status), NOTES.md (open questions)
```

## Documentation

- [docs/PROTOCOL.md](docs/PROTOCOL.md): messages exchanged with the controller.
- [docs/PLAN.md](docs/PLAN.md): staged work plan and the status of each feature.
- [docs/NOTES.md](docs/NOTES.md): open questions and ideas.
- [AGENTS.md](AGENTS.md): conventions for contributors and coding agents.
