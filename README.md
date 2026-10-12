# Awake Steps

An Android app and home/lock-screen widget that count your steps, calories and distance **from when you wake up to when
you go to sleep**, not midnight to midnight like Fitbit does.

It reads your Fitbit data (steps and sleep) from **Health Connect**, so it works alongside the
Fitbit / Google Health app. It never writes anything back.

## How a "day" is defined

These rules are tuned in code (`DayRules` and `AppSettings`), not exposed as settings. The app
only lets you set your daily goal.

* **A day starts when you wake up** from your main sleep, and **ends when you go to sleep**.
  Steps at 1 AM before bed still count toward the day you woke up on.
* **Sleep or nap? Time of day and how long you've been up matter, not just length.**
  * Sleep mostly during **your night** starts a new day even if it's short: 1 h or more.
    4–6 AM is sleep.
  * After being up **18 hours or more**, any sleep of 1 h or more is your night's sleep,
    whatever the time. Going to bed at 6 AM and sleeping until 9 is a late night, not a nap.
  * Other **daytime** sleep is a nap unless it's long: 5 h or more. 4–6 PM after a normal
    morning is a nap.
  * **Your night is learned** from your last two weeks of sleep: 4 hours either side of your
    usual mid-sleep time, plus midnight–6 AM, which always counts. If you usually sleep
    3 AM–11 AM, a short 9–11 AM sleep counts as sleep for you.
  * Waking up for up to 3 hours in the middle of the night doesn't split the night.
* **All-nighters are decided in hindsight.** Being awake at the cutoff (default 5:00 AM) only
  marks a *possible* all-nighter. Go to sleep within 6 hours of it and it was just a late night:
  your day runs until you fall asleep. Still up 6 hours later (or only dozed briefly)? Then it
  was an all-nighter, and a new day starts back at 5:00 AM. The cutoff also needs you to have
  been awake 6+ hours, so waking at 4:30 isn't cut off at 5.
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
| Screen stays on | Checks Health Connect for new data every 15 s, and redraws the moment any arrives |
| Screen off | Refresh every 15 min (the Android minimum) |
| You open the app | Immediate refresh |

The widget is drawn directly through `AppWidgetManager` (`WidgetPusher`), not through Glance's
`update()`, which queues the redraw as a WorkManager job that Android can delay for minutes
while the app is in the background.

The 15-second check is Health Connect's change log: one cheap call that reports whether anything
was written. Totals are only re-read when it says something changed, which keeps well clear of
Health Connect's rate limits.

The widget can only be as fresh as the data in Health Connect, and the Fitbit / Google Health
app decides when to write there. **Settings → Sync log** records each time new data actually
arrived, and how far behind it was, so you can see Fitbit's real cadence on your phone. To keep
it frequent, turn on all-day sync in the Fitbit app and leave it unrestricted in battery
settings.

The live part runs as a background service. Android requires services like this to have a notification, but the app
never asks for notification permission, so on Android 13+ that notification is never shown.
You'll only see Awake Steps listed under "active apps" in quick settings.

## Comparing with the Fitbit / Google app

History has a **Midnight to midnight** toggle that shows plain calendar-day totals (steps,
calories, distance), the way the Fitbit / Google Health app counts them, so you can compare.

## Calories and distance

Both are counted over the same wake-to-sleep window as steps. Calories are **total burned**
(resting + active, like Fitbit). Because sleep
hours are excluded, the total is lower than Fitbit's midnight-to-midnight number. Distance is
shown in km or miles based on your region.

## Setup

1. Install the APK. Get it from the **Actions** tab → latest *Build APK* run → `awake-steps-apk`,
   or build it yourself (below).
2. In the Fitbit app, connect **Health Connect** and allow it to write steps and sleep.
3. Open Awake Steps and allow access to steps, sleep, calories and distance.
4. Allow **"access data in the background"** when asked. Without it the widget only updates
   while the app is open.
5. Tap **Add widget** in the app, or long-press the home screen → Widgets → Awake Steps.

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
