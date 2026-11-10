# Wire Protocol (app <-> dropController V3 firmware)

Source of truth: `firmware/*.ino` (version `dC3_AS25006`, requires Android app version 30401 or higher).
Do not change message formats without asking. Verify every format against the sketch before coding.

## Transport and framing
- Bluetooth Classic SPP (HC-06), 9600 baud. The device reads both the Bluetooth stream (SoftwareSerial) and USB serial
  (Windows app); the Android app only uses Bluetooth.
- Every message is enclosed in square brackets: `[` payload `]`. Bytes outside brackets are ignored.
- Payload is ASCII. The device buffer holds 30 characters; the longest message is 13 characters.
- No line terminators are needed. The device sends replies in the same bracket format.
- Numbers are fixed-width, zero-padded decimal digits. The device does no range checking except the trigger stop cap below.

## App -> device

| Message | Meaning |
|---|---|
| `[HELLO]` | Connection request. Device answers `[HELLO]` and stops blinking the waiting LED. |
| `[BYE]` | Connection closed by the app. |
| `[AYT]` | Are you there. Device answers `[YIA]`. |
| `[VERSION]` | Firmware version request. Device answers `[V,<version>,<compile date>]`. |
| `[O<v>1]` / `[O<v>0]` | Drain: open / close valve `v` (1-6). |
| `[OX]` | Close all valves. |

### Drop sequence
Send these messages in this order. The device starts the sequence as soon as it receives `[EOD]`.

| Message | Format | Example |
|---|---|---|
| Start marker | `S<n>` (n = sequence number, debugging only; `SOD` is also accepted, any `S...` works) | `[S1]` |
| Number of drops | `N<d>`, one digit (see open question 1) | `[N4]` |
| Drop (one per drop) | `D` + drop no. (2 digits) + SOL (1 digit, 1-6) + START ms (4 digits) + STOP ms (5 digits) | `[D011222233333]` |
| Flash trigger 1-3 | `F<n>Y` + START ms (4) + STOP ms (5), or `F<n>N` when OFF | `[F1Y100001100]`, `[F3N]` |
| Camera trigger | `CY` + START (4) + STOP (5), `CB` + START (4) + STOP (5) for Bulb, or `CN` when OFF | `[CY100001050]` |
| End of data | `EOD` | `[EOD]` |

Rules taken from the firmware:
- The app sends STOP, not SIZE: `STOP = START + SIZE` (computed by the app, also shown on the Loop Page).
- Drop numbers must be 01..numDrops in order; the device indexes its arrays by drop number.
- `F1`, `F2`, `F3` and `C` messages are all required even when OFF. If any is missing, or the drop count
  does not match the number of `D` messages, the device rejects the data and sends `[FTS]` without running.
- Trigger stop times (flash and camera) are capped to 20000 ms by the device. Drop stop times are not capped.
- The device holds a trigger HIGH from its START until its STOP, so the pulse length is `STOP - START`.
  Inference, to be confirmed against the original app: the trigger pulse time setting (default 50 ms)
  is used as `STOP = START + pulse` for CT and FT in normal (non-Bulb) mode.
- Mirror lock up (`[MN]` / `[MY<ms>]`) is not used by this firmware version; the device ignores it.
- Messages that do not match are ignored silently.

## Device -> app

| Message | Meaning |
|---|---|
| `[HELLO]` | Reply to `[HELLO]`. |
| `[YIA]` | Reply to `[AYT]`. |
| `[V,<version>,<date>]` | Reply to `[VERSION]`, e.g. `[V,dC3_AS25006,Jan  1 2025]`. Show on the About tab. |
| `[FTS]` | Finished the sequence. Also sent after rejected data. The START button goes back from ACTIVE. |
| `[M,<title>,<message>]` | Pop-up message. Declared by the firmware but not currently sent over Bluetooth. |

The device does not report "active" state. The app shows ACTIVE from sending `[EOD]` until `[FTS]` arrives.
The firmware comment says the app uses a timeout when `[FTS]` never arrives.

## LEDs (for reference)
Yellow (waiting) blinks until `[HELLO]` or an `S...` message is received, then stays on while idle.
Green (active) is on while a sequence runs.

## Decisions (where the firmware and the site leave details open)
These are the choices used by the new app. Change them only after checking against the real device.
1. Drop count message is `[N<d>]` with the digit directly after `N` (e.g. `[N4]`). The `[ND4]` form shown in the
   sketch comments would make `dropDataIsOK()` fail: `processNewData.ino:21` computes `'D' - 48 = 20`.
2. Trigger STOP time: for flashes and for the camera in normal mode `STOP = START + trigger pulse time`
   (Settings, default 50 ms), capped to 20000 ms (same cap as the firmware).
3. Bulb mode (`CB`): `START = 0000`, `STOP = latest STOP of all drops and enabled flashes + 100 ms`
   (the firmware's unused `bulbPost` default), capped to 20000 ms. The shutter opens at the start of the
   sequence and closes just after it, as described on the site. The camera time and its delta are not used.
4. Message pacing: the app waits 30 ms between messages so the HC-06 and the SoftwareSerial buffer are not flooded.
5. Sequence timeout: after `[EOD]` the app waits for `[FTS]` for `latest STOP of the sequence + 5000 ms`.
   On timeout the START button returns to its idle state and the app does not retry.
6. Connection handshake: the app is shown as CONNECTED as soon as the RFCOMM socket opens. It then sends
   `[HELLO]` (the device stops blinking its waiting LED) and `[VERSION]`; the reply to `[VERSION]` is shown on the
   About tab. Replies are not required for the connection to be usable.
7. Graph shutter bar: drawn from the camera trigger START to its STOP as sent to the device
   (for Bulb: whole sequence). Revisit in stage 5 if the original behavior differs.
