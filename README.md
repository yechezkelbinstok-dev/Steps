# Awake Steps

An Android app and home-screen widget that count your steps **from when you wake up to when
you go to sleep**, not midnight to midnight like Fitbit does.

It reads your Fitbit data (steps and sleep) from **Health Connect**, so it works alongside the
Fitbit / Google Health app. It never writes anything back.

## How a "day" is defined

* **A day starts when you wake up.** That's the end of your last *main* sleep. Sleep sessions
  less than 60 minutes apart are merged into one night, and sleeps shorter than the minimum
  (default 3 h, adjustable) count as naps and don't reset anything.
* **A day ends when you go to sleep.** Steps taken at 1 AM before bed still count toward the
  day you woke up on.
* **All-nighter fallback.** If no sleep is logged, a new day starts at the fallback time
  (default 5:00 AM, adjustable), but only once you've been awake at least 6 hours, so an early
  4:30 wake-up isn't cut off at 5:00.
* **No sleep data at all?** Days line up on the fallback time, which works like a normal
  calendar day that rolls over at 5 AM.

The logic is in [`DayCalculator.kt`](app/src/main/java/app/wakesteps/core/DayCalculator.kt) and
covered by unit tests.

## The widget

* Resizable: a compact 1-row version, or a larger one with a goal progress bar.
* Shows your steps, when your day started, % of goal, and when your tracker last synced.
* Tap it to open the app. Tap ↻ to refresh right away.

**How "live" it is:**

| Mechanism | When |
|---|---|
| Background refresh | every 15 min (the Android minimum) |
| Open the app | immediately |
| ↻ on the widget | immediately |
| **Live mode** (Settings) | every 60 s while the screen is on, paused while it's off. Also shows a quiet notification with your count |

The widget can only be as fresh as the data in Health Connect. The Fitbit app writes to Health
Connect whenever it syncs with your tracker. To keep that frequent, turn on **All-day sync** in
the Fitbit app and leave Fitbit unrestricted in battery settings. The widget footer
("synced 10:42") shows how old the newest data is.

## Setup

1. Install the APK. Get it from the **Actions** tab → latest *Build APK* run → `awake-steps-apk`,
   or build it yourself (below).
2. In the Fitbit app, connect **Health Connect** and allow it to write steps and sleep.
3. Open Awake Steps and allow access to steps and sleep.
4. Allow **"access data in the background"** when asked. Without it the widget can't refresh
   while the app is closed. Instead, the ↻ button briefly opens an invisible screen to refresh.
5. Long-press the home screen → Widgets → Awake Steps.
6. Optional: in Settings, set the step source to **Fitbit** if your phone also counts steps
   and the totals look inflated. Turn on **Live mode** for minute-by-minute updates.

## Build

Requires JDK 17+ and the Android SDK (API 36).

```sh
./gradlew testDebugUnitTest assembleRelease
# → app/build/outputs/apk/release/app-release.apk (signed with the debug key, for sideloading)
```

Kotlin, Jetpack Compose, Glance (widget), WorkManager, DataStore and Health Connect.
