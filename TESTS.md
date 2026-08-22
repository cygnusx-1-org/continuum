# Tests

The tests were started by [manunia](https://github.com/manunia), and are written in [Kotlin](https://en.wikipedia.org/wiki/Kotlin_(programming_language)) with the [Kaspresso](https://github.com/KasperskyLab/Kaspresso) framework. Kaspresso is based on [Espresso](https://developer.android.com/training/testing/espresso) and [UI Automator](https://developer.android.com/training/testing/ui-automator).

## Setup
test.properties:
```
REDDIT_CLIENT_ID=4vj5qn9RkC4i3WLv5OsuFb
```

## Usage
This has been tested with a real phone connected via a USB cable. I needs to be unlocked and USB debugging allowed. It will very likely work with an emulator. It installs a debug build of `Continuum` as the first step. Then it runs the tests, and then uninstalls the debug build of `Continuum`. This means it will replace your "normal" debug build, and wipe all it's settings. It also means tests need to be written to expect a fresh install each time. This means dealing with the `client ID`, system dialogs like permissions, etc.

```
./gradlew connectedAndroidTest
```

### UserProfileTest runs automatically
`UserProfileTest` is not left to `connectedAndroidTest`. It is wired into `./gradlew :app:check`
through the `deviceTests` task, which runs `scripts/run-device-tests.sh` against **one** device.
That matters because `connectedAndroidTest` runs on every attached device (this machine keeps more
than one emulator up) and, as described above, uninstalls afterwards — which would log the Reddit
test account out on every run.

A missing device **fails** the build rather than skipping quietly:

```
./gradlew :app:check                       # runs the instrumented tests too
./gradlew :app:check -PskipDeviceTests=true   # explicit opt-out, e.g. headless
scripts/run-device-tests.sh                # standalone
scripts/run-device-tests.sh --serial emulator-5556 --class ru.otus.pandina.tests.SettingsTest
```

The device is chosen in this order: `--serial`, `$ANDROID_SERIAL`, `deviceSerial=` in
`local.properties` (per-machine, not committed), then the sole attached device. With several
devices attached and none chosen, it stops and lists them.

Only `UserProfileTest` is in the gate. The rest of the suite needs the network (`LoginTest`,
`MainTest`) or a freshly-cleared install (`requireAnonymousInstall`), so it stays a deliberate run.

### Example output
```
> Task :app:connectedDebugAndroidTest
Starting 8 tests on Pixel 8 Pro - 16

Pixel 8 Pro - 16 Tests 0/8 completed. (0 skipped) (0 failed)
Pixel 8 Pro - 16 Tests 1/8 completed. (0 skipped) (0 failed)
Pixel 8 Pro - 16 Tests 2/8 completed. (0 skipped) (0 failed)
Pixel 8 Pro - 16 Tests 3/8 completed. (0 skipped) (0 failed)
Pixel 8 Pro - 16 Tests 4/8 completed. (0 skipped) (0 failed)
Pixel 8 Pro - 16 Tests 5/8 completed. (0 skipped) (0 failed)
Pixel 8 Pro - 16 Tests 6/8 completed. (0 skipped) (0 failed)
Pixel 8 Pro - 16 Tests 7/8 completed. (0 skipped) (0 failed)
Finished 8 tests on Pixel 8 Pro - 16

BUILD SUCCESSFUL in 2m 19s
68 actionable tasks: 1 executed, 67 up-to-date
```

## Lists of tests
* APIKeysTest.kt
  - addRedditClientIdTest, tests the ability to enter a `client ID`
* LoginTest.kt
  - loginTest, tests that the `Email or username` field is avaliable in the login screen
* MainTest.kt
  - popularPostFilterTest, tests post filters by toggling the `Text` and `Link` off
* SettingsTest.kt
  - setFontTest, tests setting a font
  - disableNotificationTest, tests the ability to disable notifications
  - setLightThemeTest, tests the ability to switch to the light theme
  - setDarkThemeTest, tests the ability to switch to the dark theme
* UserProfileTest.kt
  - otherUserProfileHeaderIsCompleteInPortrait, opens another user's profile and checks the whole
    header inflated, with the save ribbon shown
  - otherUserProfileHeaderIsCompleteInLandscape, the same in landscape, which inflates
    `layout-land/activity_view_user_detail.xml`
  - ownProfileHeaderIsCompleteInPortrait, opens the signed-in account's own profile, where the save
    ribbon is present but hidden
  - ownProfileHeaderIsCompleteInLandscape, the same in landscape

  These are the on-device half of the issue #369 regression net: a view added to `layout/` and not
  to `layout-land/` or `layout-sw600dp/` made `ViewUserDetailActivity.onCreate` throw on every
  tablet and every phone in landscape. They assert the views are *present*, not merely that nothing
  crashed, so null-guarding the crash away without restoring the feature still fails.
  `layout-sw600dp` is out of reach on a phone; that configuration is covered by the
  `userProfileHeader` Roborazzi goldens and by `LayoutVariantIdParityTest`.
