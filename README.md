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
* A goal ring with the footprints icon over your step count, plus calories and distance.
  No percentages or "to go" (the ring already shows progress), and no wake-up time.
* A solid card in a deep tone of your wallpaper colour (Android 12+). Until you hit your goal the
  ring is a bright tone of it, even (Soft) or fading in (Fade), or aqua to blue with coral
  calories and sky-blue distance (Vivid); then it turns gold. Pick in Settings → Ring.
* Past your goal the ring keeps going: the next lap runs over the full ring, with a shadow at
  its tip.
* A small refresh button in the corner (at the end of the 4×1 strip; the 2×1 has no room for it)
  re-reads your numbers on the spot, showing a spinner until they're in. Tap anywhere else to
  open the app.

**How it stays live** (no notification anywhere):

| When | What happens |
|---|---|
| Screen turns on | Immediate refresh, so it's current when you look |
| Screen stays on | Checks Health Connect for new data every 15 s, and redraws the moment any arrives |
| Screen off | Refresh every 15 min (the Android minimum) |
| You open the app | Immediate refresh |
| You tap the widget's refresh button | Immediate refresh |
| Between syncs, once you're walking | The phone's step counter fills in the steps since the band's newest data; the band's count takes over when it arrives (a few steps around the house add nothing) |

The widget is drawn directly through `AppWidgetManager` (`WidgetPusher`), not through Glance's
`update()`, which queues the redraw as a WorkManager job that Android can delay for minutes
while the app is in the background.

The 15-second check is Health Connect's change log: one cheap call that reports whether anything
was written. Totals are only re-read when it says something changed, which keeps well clear of
Health Connect's rate limits.

The widget can only be as fresh as the data in Health Connect, and the Fitbit / Google Health
app decides when to write there. **Settings → Sync log** records each time new data actually
arrived, and how far behind it was, so you can see Fitbit's real cadence on your phone.
**Settings → Diagnostics** goes further: it reads Health Connect and Google Health live, side by
side, with the newest step each one has, and gives you the whole report to copy or share. To keep
it frequent, turn on all-day sync in the Fitbit app and leave it unrestricted in battery
settings.

The live part runs as a background service. Android requires services like this to have a notification, but the app
never asks for notification permission, so on Android 13+ that notification is never shown.
You'll only see Awake Steps listed under "active apps" in quick settings.

## Steps minute by minute

Tap *Hour by hour*, on Today or on any day's page, to open the day's steps up: bars across the
day that get finer as you zoom in (pinch, double-tap, or − and +) until each is a single minute,
the steps and time of the bar under your finger, and the whole day below to move around in. Under
it are the day's **walks**: stretches at 60 steps a minute or more lasting 5 minutes or more
(pauses of up to 2 minutes don't end one), each with its times, length, pace and steps. Tap one to
see it in the chart. With Google Health connected the minutes come straight from the band; from
Health Connect they're only as fine as the records the Fitbit app writes there. The walks are in
[`Walks.kt`](app/src/main/java/app/wakesteps/core/Walks.kt).

## Sleep

The Sleep tab shows each night the app starts a day from (the sleep you woke from, your own
calls included): its sleep score, what went into it, time in each stage, a chart of the night you
can scrub, and the last 7 nights. Stages come from Google Health when it's connected, otherwise
from Health Connect.

* **Sleep score.** Google Health doesn't share its sleep score with other apps, so the app
  estimates it from the six things Google says it weighs (duration, time to sound sleep, sound
  sleep, restlessness, full awakenings, interruptions) and rates it the same way (Excellent 90+,
  Good 80+, Fair 60+, Poor). Tap the score to enter the one Google Health gave a night; entered
  scores are shown as they are, and the estimates learn from them (with fewer than 4 just an
  offset, then a line held close to 1:1, from the latest 30). Like Google, no score for a night
  without stages.
* **Sound sleep** is measured the way Google describes it, from heart rate: the stretches of
  light, deep or REM sleep with a low, steady heart rate. Time to sound sleep runs to the first
  of them. Google doesn't publish its thresholds, so the app learns them from nights where it has
  Google's own figures (sent over from Google Health's Coach), starting from: at or under the
  day's resting rate and the night's own middle rate, within 4 beats a minute across five
  minutes, lasting five minutes or more. Without heart rate (Google Health not connected, or heart rate not
  allowed) the two show "needs heart rate", and the estimate uses deep and REM in their place.
* **Restless moments** are stretches of being awake of 5 minutes or less (a stretch taken whole,
  however many pieces the band recorded it in), counted as asleep the way Google
  counts them, and shown as their own category. Longer wake-ups are full awakenings. Settings →
  Restless moments → Count as awake counts them as awake time instead (the score is unchanged:
  it's Google's, so it uses Google's rules).

## Google Health (optional, faster data)

Health Connect only gets your steps when the Fitbit / Google Health app decides to write them.
Connecting Google Health (Settings → Google Health) reads them straight from Google's servers
instead, through the [Google Health API](https://developers.google.com/health), and falls back to
Health Connect whenever that doesn't work. It still isn't every second: the live count in the
Google Health app comes over Bluetooth from the watch, and the API only has what that app has
uploaded.

It needs a one-time setup in your own (free) Google Cloud project. The setup sheet in the app
links each page and has every value ready to copy:

1. Create a project, and turn on the Google Health API in it.
2. Set up the sign-in screen (Google Auth Platform): app name "Awake Steps", audience External.
3. Add the scopes `…/auth/googlehealth.activity_and_fitness.readonly`,
   `…/auth/googlehealth.sleep.readonly` and
   `…/auth/googlehealth.health_metrics_and_measurements.readonly` (heart rate, for sound sleep).
4. Add your Google account as a test user, then publish the app (otherwise sign-in lapses every
   7 days).
5. Create an OAuth client of type Android: package `app.wakesteps` and the SHA-1 of the signing
   key (`keystore/wakesteps.keystore`, shown in the sheet).
6. Make sure the Google Health app is signed in with the same account, then tap Connect.
7. Tap Allow heart rate. Heart rate is asked for on its own, so steps and sleep work without it.

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

`WidgetRenderTest` renders the real widget in every style and size, in dark mode and past the
goal, to `app/build/widget-renders/*.png`, and `ScreenRenderTest` renders the app's screens
(animations snapped to their end) to `app/build/screen-renders/*.png`, so design changes can be
checked without a phone.
The ring's arc and shadow drawables are generated by `tools/gen_rings.py`.

Kotlin, Jetpack Compose, Glance (widget), WorkManager, DataStore and Health Connect.
