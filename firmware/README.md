# dropController V3 firmware

Arduino Nano (ATmega328P) sketch for the dropController V3 board. The device
accepts commands over Bluetooth Classic (HC-06 on SoftwareSerial) or USB serial
and drives six solenoid valves, a camera shutter/focus output and three flash
outputs.

The firmware version is `dC3_AS25006` and it requires Android app version
30401 or higher. The wire protocol is documented in
[`docs/PROTOCOL.md`](../docs/PROTOCOL.md); always verify a change there against
this sketch.

## Sketch layout

All `.ino` files live in `dropControllerV3_dC3_AS25006/`, because the Arduino
IDE requires the sketch folder to have the same name as the main file and
compiles every `.ino` file in that folder.

| File | Contents |
|---|---|
| `dropControllerV3_dC3_AS25006.ino` | Pin map, global state, `setup()` and `loop()` |
| `processNewData.ino` | Parses the bracketed commands received from the app |
| `parseDropData.ino` | Copies the received commands to the drop data arrays |
| `makeDrops.ino` | Sequence timing: valves, camera and flash triggers |
| `minorFunctions.ino` | Serial framing, version reply and helpers |

## Pin map

| Pin | Function |
|---|---|
| D2-D7 | Solenoids 6-1 (`ST6`-`ST1`) |
| D8, D9, D10 | Flash triggers 3, 2, 1 |
| D11, D12 | Camera focus, camera shutter |
| A0 (14), A1 (15) | Waiting (yellow) LED, active (green) LED |
| A4, A5 (18, 19) | SoftwareSerial RX, TX to the HC-06 module |

Serial links run at 9600 baud. Debug output is controlled by the
`USB_DEBUG` and `SOFTSERIAL_DEBUG` flags at the top of the main sketch.

## Bluetooth module (HC-06)

The device needs a **Bluetooth Classic** module. The Android app connects with
the Serial Port Profile (`00001101-0000-1000-8000-00805F9B34FB`), so an HC-06 or
an HC-05 works and a BLE module does not: `BT05`, `AT-09`, `MLT-BT05`, `HM-10`,
`CC2541` and similar advertise Bluetooth Low Energy only, reject the classic
pairing and can never open an SPP socket. A listing that says "HC-06 BLE" is the
wrong part.

Wire the module to the SoftwareSerial pins used by the sketch:

| HC-06 pin | Nano pin | Note |
|---|---|---|
| `VCC` | 5V | the module has its own 3.3 V regulator, input range 3.6-6 V |
| `GND` | GND | a shared ground is required |
| `TXD` | A4 (18) | the 3.3 V output is a valid high level for the ATmega |
| `RXD` | A5 (19) | through a 5 V to 3.3 V divider, for example 1 kOhm / 2 kOhm |
| `EN`, `STATE` | - | not connected. On an HC-05 `EN` high selects 38400 baud AT mode |

The `LEVEL: 3.3V` marking on the ZS-040 board describes the logic level. The
3.6-6 V supply range does not make the `RXD` input 5 V tolerant, so keep the
divider:

```text
A5 ----[ 1k ]----+---- HC-06 RXD
                 |
              [ 2k ]
                 |
                GND
```

The module keeps the factory settings the firmware expects: 9600 baud, name
`HC-06`, PIN `1234`. It does not need reflashing. The same ZS-040 board is used
for HC-05 and HC-06, so check the name the module advertises; an HC-05 also works
as long as `EN` is left floating.

Unlike the hardware serial pins D0/D1, A4 and A5 do not conflict with the USB
port, so the module can stay connected while the firmware is uploaded and while
the Serial Monitor is open.

### Bench test

1. Check the module alone with a USB-TTL adapter: adapter `TX` to module `RXD`,
   adapter `RX` to module `TXD`, `GND` to `GND`, 5 V to `VCC`. Open a terminal at
   9600 baud with no line ending and send `AT`. The answer is `OK`; `AT+VERSION`
   reports the module firmware. No answer at 9600 means the module is configured
   for 38400 or 115200.
2. Power the Nano from USB with the module wired to A4/A5. The module LED blinks
   while it is not paired and stays on after a connection.
3. Pair the module in the Android settings with PIN `1234`. A BLE module never
   asks for a PIN, which is another way to tell the parts apart.
4. Connect with a Bluetooth SPP terminal app (for example Serial Bluetooth
   Terminal) at 9600 and send the commands below. The sketch answers on both the
   Bluetooth and the USB port, so the Serial Monitor at 9600 shows the same
   traffic.

| Send | Expect | Checks |
|---|---|---|
| `[AYT]` | `[YIA]` | both directions of the link |
| `[VERSION]` | `[V,dC3_AS25006,<compile date>]` | framing and the firmware version |
| `[HELLO]` | `[HELLO]` | the yellow (waiting) LED stops blinking and stays on |
| `[O11]` | - | valve 1 opens, `D8` goes high |
| `[O10]` | - | valve 1 closes |
| `[OX]` | - | all valves close |
| `[BYE]` | - | the yellow LED goes off and blinking resumes |

Never connect a solenoid directly to a pin; use the driver on the board. For a
bench test use an LED with a 1 kOhm resistor or a multimeter on `D8`.

If there is no answer at all, swap `A4` and `A5` first: swapped `TXD`/`RXD` is
the most common wiring mistake. A module that resets when the connection starts
needs more current; power it from a separate 5 V supply with a 100 uF capacitor
across `VCC` and `GND`.

## Building

`build.ps1` uses the `arduino-cli.exe` bundled with Arduino IDE 2.x:

```text
C:\Program Files\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe
```

The IDE does not bundle the AVR core; it normally downloads it from
`downloads.arduino.cc`. On networks where that host returns HTTP 403,
`setup-toolchain.ps1` falls back to the AVR core, avr-gcc, avrdude and ctags
from an Arduino IDE 1.8.x package (for example the Microsoft Store build) and
links them into a private arduino-cli data folder:

```text
%LOCALAPPDATA%\DropControllerArduino\cli-data
```

Prepare the toolchain once:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File firmware/setup-toolchain.ps1
```

`build.ps1` runs the setup automatically on the first build. The private data
folder keeps the project independent of the IDE's `Arduino15` state and works
offline. When `downloads.arduino.cc` is reachable, the setup falls back to the
standard `arduino-cli core install arduino:avr` and installs the core into the
same private folder.

Compile only:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File firmware/build.ps1
```

Compile and upload to a Nano with the old bootloader:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File firmware/build.ps1 -Upload -Port COM5
```

Use `-Cpu atmega328` for a Nano with the new bootloader (uploads at 115200 baud
instead of 57600). The build output goes to `firmware/build/`.

Configuration:

- `-IdeHome` or `ARDUINO_IDE_HOME` changes the Arduino IDE installation folder.
- `-DataDir` or `DC3_ARDUINO_DATA` changes the private arduino-cli data folder.
- `-SourceToolchain` or `DC3_ARDUINO_SOURCE` changes the Arduino IDE 1.8.x
  folder used as the core source for `setup-toolchain.ps1`.

The `discovery not found` warnings from arduino-cli are expected: the IDE
bundle does not ship the serial/mDNS discovery helpers, and they are not needed
to compile or to upload to a port given with `-Port`.

## Troubleshooting uploads

The CH340-based Nano used here has the old bootloader. If an upload from
Arduino IDE fails, check these settings:

- **Tools -> Processor -> ATmega328P (Old Bootloader)**. The plain
  `ATmega328P` option uses the new bootloader at 115200 baud; clones usually
  need the old one at 57600 baud. `build.ps1` defaults to
  `-Cpu atmega328old` for this reason. The verbose IDE log then shows
  `-b115200` when the wrong option is selected.
- **Tools -> Port** must be the CH340 port (for example `COM9`), not one of the
  Bluetooth serial ports. The port name appears in Device Manager under
  `Ports (COM & LPT)` as `USB-SERIAL CH340`.
- `avrdude: ser_open(): can't open device "\\.\COM9": access denied` means the
  port is busy. Close the IDE's Serial Monitor, any other IDE window, a
  terminal program, or another `build.ps1` upload, then retry. Unplugging and
  replugging the Nano also releases the port.
- A `stk500_recv(): programmer is not responding` error after the port opens
  almost always means the wrong processor option (new instead of old
  bootloader) or a clone with a different bootloader baud rate.
- Arduino IDE 2.x shows the CH340 port as `Unknown board` with `VID: 1A86` and
  `PID: 7523`. That is expected: clone boards carry no board descriptor, so
  select `Arduino Nano` and the processor by hand.
- If Arduino IDE 2.x has no `Arduino AVR Boards` entry in **Tools -> Board**, the
  AVR core is not installed and the IDE has to download it from
  `downloads.arduino.cc`. When that download fails, Arduino IDE 1.8.x (which
  ships the core) or `build.ps1` still work.

After a successful upload the device answers `[VERSION]` at 9600 baud with
`[V,dC3_AS25006,<compile date>]`.
