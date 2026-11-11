# Code review: device simulator and communication logs

Date: 2026-11-11. Scope: the uncommitted changes for the in-process device simulator, the in-memory protocol
log, the persistent communication log file and the About tab entry point. Line numbers are from the review date
and may shift after edits; use the method names when they disagree.

Status legend: `[ ]` open, `[x]` fixed (add the date or commit when checking an item off).

Verification at review time: `testDebugUnitTest` (178 tests), `assembleDebug`, `assembleRelease` and `lintDebug`
all passed. No critical defects were found.

After the fixes (2026-11-11): `testDebugUnitTest` (182 tests), `assembleDebug`, `assembleRelease` and `lintDebug`
all pass; all items below are checked off.

## Medium priority

- [x] **M1. Trim the file log with hysteresis.** `logic/ProtocolFileLog.java` (`append()` and
      `trimIfNeeded()`): after every append, once the file is near `maxBytes`, the whole file is read and
      rewritten. At the 200 KB limit that is roughly 400 KB of disk I/O per message, on the Bluetooth read thread
      and on the single write executor, so it can delay receiving `[FTS]` and sending the next commands.
      Fix: trigger the trim only above `maxBytes`, but trim down to about 75% of the limit
      (`targetBytes = maxBytes - maxBytes / 4`), so the rewrite happens once per ~50 KB of new data. Optionally
      move file writes to a dedicated single-thread executor to keep the Bluetooth threads free.
      *Fixed 2026-11-11: `trimIfNeeded()` now trims to `maxBytes - maxBytes / 4`; covered by
      `ProtocolFileLogTest.rotation_trimsWellBelowTheLimit`. File writes stay on the Bluetooth threads (the trim is
      now rare, so the extra executor was not worth the ordering complexity).*
- [x] **M2. Do not let a `ProtocolLog` listener break the connection.** `logic/ProtocolLog.java`
      (`notifyListeners()`): an exception from a listener escapes into `ConnectedThread.run()`, which only catches
      `IOException`, so the read thread ends without `connectionLost()`. In `ConnectedThread.write()` it fails the
      write-executor task instead. Fix: catch `RuntimeException` per listener (a log must never break the
      connection) or document and enforce a non-throwing listener contract.
      *Fixed 2026-11-11: `notifyListeners()` catches `RuntimeException` per listener; covered by
      `ProtocolLogTest.throwingListener_doesNotBreakTheLogOrOtherListeners`.*
- [x] **M3. Keep file I/O off the main thread.** `MainActivity.showCommunicationLog()` /
      `showFileLogText()` read the whole log file in the UI thread, and the Clear button truncates it there too.
      A 200 KB file is usually fast, but slow storage can cause jank. Fix: read and clear on a background executor
      and update the `TextView` through `runOnUiThread`.
      *Fixed 2026-11-11: `MainActivity` has a single-thread `logIoExecutor` (shut down in `onDestroy`) used by
      `loadFileLogText()` and `clearFileLog()`; the text and the scroll happen on the UI thread.*

## Low priority

- [x] **L1. Avoid the hard cast in `AboutFragment`.** `AboutFragment.java` casts `requireActivity()` to
      `MainActivity`; this is safe today but breaks if the fragment is hosted elsewhere. Check `instanceof` or
      introduce a small `CommunicationLogHost` interface.
      *Fixed 2026-11-11: the fragment checks `instanceof MainActivity` and hides the button in another host.*
- [x] **L2. Dismiss the log dialog before `super.onDestroy()`.** `MainActivity.onDestroy()` currently calls
      `super.onDestroy()` first and dismisses `protocolLogDialog` afterwards.
      *Fixed 2026-11-11: both dialogs are dismissed and the log executor is shut down before `super.onDestroy()`.*
- [x] **L3. Clean up the shared dialog field.** `protocolLogDialog` is used for both the in-memory and the file
      dialog, and a second open request is silently ignored. Consider separate fields and set the field to `null`
      in the dismiss listener.
      *Fixed 2026-11-11: separate `protocolLogDialog` / `communicationLogDialog` fields, each cleared in its
      dismiss listener.*
- [x] **L4. Rename the communication log resources.** `btn_protocol_log`, `about_protocol_log`,
      `protocol_log_clear` and `protocol_log_close` now describe the communication log; rename them for clarity.
      *Fixed 2026-11-11: `btn_communication_log`, `about_communication_log`, and the shared `log_clear` /
      `log_close` (used by both dialogs).*
- [x] **L5. Fix the Stage 11 documentation.** `docs/PLAN.md` step 11.7 still says the About button shows the live
      in-memory log; it now opens the file log from 11.8. Also move 11.7/11.8 after 11.6 so the numbering is in
      order.
      *Fixed 2026-11-11: 11.6 now precedes 11.7, and 11.7 describes only the debug overflow item.*
- [x] **L6. Distinguish "empty" from "unreadable" in the file log.** `ProtocolFileLog.readAll()` returns `""`
      both for a missing file and for a read error, so the dialog shows "No messages yet" in both cases. Return
      `null` or a small result object, or expose a separate error flag.
      *Fixed 2026-11-11: `readAll()` is `@Nullable` and returns `null` on a read error; the dialog shows
      "Could not read the log file". Covered by `ProtocolFileLogTest.readAll_returnsNullWhenTheFileCannotBeRead`.*
- [x] **L7. Cancel a pending `[FTS]` when a new sequence starts.** `SimulatorLink.scheduleFinish()` keeps the
      scheduled task, so a new sequence can produce an extra or early `[FTS]`. Store the `ScheduledFuture` and
      cancel it when the simulator starts another sequence.
      *Fixed 2026-11-11: `SimulatorLink` keeps `pendingFinish` and cancels it before scheduling the next `[FTS]`;
      covered by `SimulatorLinkTest.newSequence_cancelsThePendingFinishedFlag`.*
- [x] **L8. Make the log dialog height flexible.** `dialog_protocol_log.xml` uses a fixed `360dp` height; in
      landscape or on small screens the dialog may not fit. Use a screen-relative maximum height.
      *Fixed 2026-11-11: `MainActivity.limitLogDialogHeight()` caps the scroll view at 60% of the screen height.*
- [x] **L9. Do not force-scroll the log on every update.** `MainActivity.renderProtocolLog()` scrolls to the
      bottom for every new message, which fights the user while they read older entries. Scroll only when the
      view is already at the bottom.
      *Fixed 2026-11-11: the listener checks `isScrolledToBottom()` before scrolling; the dialog still opens at
      the newest entry.*
- [x] **L10. Remove the duplicated debug check.** `MainActivity.isDebuggable()` and
      `SettingsFragment.isSimulatorAvailable()` implement the same check; extract a shared helper.
      *Fixed 2026-11-11: `DropControllerApp.isDebuggable()` is the single implementation.*
- [x] **L11. Replace the `null` sentinel in the Settings device list.** `SettingsFragment` adds `null` to
      `List<BluetoothDevice>` to mark the simulator. It works, but a future iteration over the list can throw
      `NullPointerException`; use a small entry type with a label and an optional device.
      *Fixed 2026-11-11: a private `DeviceEntry` (label + nullable device) is used instead of the `null` entry.*
- [x] **L12. Keep crash dumps out of the repository.** `hs_err_pid31496.log` is untracked in the repository root.
      Delete it and add `hs_err_pid*.log` to `.gitignore`.
      *Fixed 2026-11-11: the dump is deleted and `.gitignore` ignores `hs_err_pid*.log` / `replay_pid*.log`.*

## Out of scope (pre-existing, not caused by this change)

- [x] **P1. Remove duplicated Gradle properties.** `gradle.properties` contains the `android.dependency.*` and
      `android.r8.*` keys twice.
      *Fixed 2026-11-11: the duplicated block is removed; the build still passes.*
- [x] **P2. Align the Gradle version documentation.** `AGENTS.md` says Gradle 8.13, while
      `gradle/wrapper/gradle-wrapper.properties` uses 9.6.0.
      *Fixed 2026-11-11: `AGENTS.md` now says Gradle 9.6.0.*

## Already verified during the review

- [x] The file-log rotation edge case where the trim boundary lands on the last newline (the file became empty)
      is fixed and covered by `ProtocolFileLogTest.rotation_keepsTheLastLineWhenItIsLargerThanTheLimit`.
- [x] The real RFCOMM path (`RfcommLink`, `BluetoothHelper.connect(BluetoothDevice)`) is unchanged.
- [x] The simulator entry and the in-memory protocol log are debug-only; the file log and the About button work
      in release builds as requested.
- [x] The communication log is shared through the `.fileprovider` `FileProvider`, so no storage permission is
      needed.
