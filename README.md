# Awake Steps

An Android app and home/lock-screen widget that count your steps, calories and distance **from when you wake up to when
you go to sleep**, not midnight to midnight like Fitbit does.

It reads your Fitbit data (steps and sleep) from **Health Connect**, so it works alongside the
Fitbit / Google Health app. It never writes anything back.

## How a "day" is defined

* **A day starts when you wake up** from your main sleep, and **ends when you go to sleep**.
  Steps at 1 AM before bed still count toward the day you woke up on.
* **Sleep or nap? Time of day matters, not just length.**
  * Sleep mostly during **your night** starts a new day even if it's short: 1 h or more
    (adjustable). 4–6 AM is sleep.
  * Sleep mostly during the **day** is a nap unless it's long: 5 h or more (adjustable), e.g.
    recovery sleep after an all-nighter. 4–6 PM is a nap.
  * **Your night is learned** from your last two weeks of sleep: 4 hours either side of your
    usual mid-sleep time, plus midnight–6 AM, which always counts. If you usually sleep
    3 AM–11 AM, a short 9–11 AM sleep counts as sleep for you.
  * Waking up for up to 3 hours in the middle of the night doesn't split the night.
* **All-nighter fallback.** If you don't sleep (naps don't count), a new day starts at the
  fallback time (default 5:00 AM), but only once you've been awake at least 6 hours.
* **You have the final say.** Tap any day in History, or *Adjust* on Today, to:
  * call the sleep that started the day a nap (merges it into the day before),
  * count a nap as sleep (starts a new day when you woke from it),
  * keep a day going through an all-nighter reset,
  * start a new day at a time you pick (for when your watch didn't track sleep).

  Your calls beat the automatic rules, can each be undone, and the widget follows them.

The logic is in [`DayCalculator.kt`](app/src/main/java/app/wakesteps/core/DayCalculator.kt) and
covered by unit tests.

## The widget

Works on the **home screen** and the **lock screen**. On Android 16+ Pixels, turn on lock screen
widgets under Settings → Display → Lock screen, then swipe over on the lock screen to add one.

* Resizable, laid out for its exact size: a compact strip (2×1, 4×1), a ring card (2×2), a
  side-by-side ring and metrics card (4×2), and a big ring with metrics underneath (4×3, the
  lock-screen size).
* Shows a goal ring with your steps, calories burned, distance, and how many steps are left
  to your goal. It deliberately doesn't show your wake-up time.
* A deep card in a dark tone of your wallpaper color (Material You), with a glowing goal ring
  that changes color when you hit your goal. It looks the same in light and dark mode.
* Tap it to open the app.

**How it stays live** (no notification anywhere):

| When | What happens |
|---|---|
| Screen turns on | Immediate refresh, so it's current when you look |
| Screen stays on | Refresh every 60 s |
| Screen off | Refresh every 15 min (the Android minimum) |
| You open the app | Immediate refresh |

The minute-by-minute part ("Keep the widget live" in Settings, on by default) runs as a
background service. Android requires services like this to have a notification. The app never
asks for notification permission, so on Android 13+ that notification is never shown. You'll
only see Awake Steps listed under "active apps" in quick settings.

The widget can only be as fresh as the data in Health Connect. The Fitbit app writes to Health
Connect whenever it syncs with your tracker. To keep that frequent, turn on **All-day sync** in
the Fitbit app and leave Fitbit unrestricted in battery settings.

## Calories and distance

Both are counted over the same wake-to-sleep window as steps. Calories are **total burned**
by default (resting + active, like Fitbit), or **active only** (set in Settings). Because sleep
hours are excluded, the total is lower than Fitbit's midnight-to-midnight number. Distance is
shown in km or miles based on your region, or whichever you choose.

## Setup

1. Install the APK. Get it from the **Actions** tab → latest *Build APK* run → `awake-steps-apk`,
   or build it yourself (below).
2. In the Fitbit app, connect **Health Connect** and allow it to write steps and sleep.
3. Open Awake Steps and allow access to steps, sleep, calories and distance.
4. Allow **"access data in the background"** when asked. Without it the widget only updates
   while the app is open.
5. Tap **Add widget** in the app, or long-press the home screen → Widgets → Awake Steps.
6. Optional: in Settings, set the step source to **Fitbit** if your phone also counts steps
   and the totals look inflated.

## Build

Requires JDK 17+ and the Android SDK (API 36).

```sh
./gradlew testDebugUnitTest assembleRelease
# → app/build/outputs/apk/release/awake-steps-<version>-release.apk (debug-signed, for sideloading)
```

`WidgetRenderTest` renders the real widget at every size (light, dark, goal reached) to
`app/build/widget-renders/*.png`, so layout changes can be checked without a phone. The goal
ring's arc drawables are generated by `tools/gen_rings.py`.

Kotlin, Jetpack Compose, Glance (widget), WorkManager, DataStore and Health Connect.
