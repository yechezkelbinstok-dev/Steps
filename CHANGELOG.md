# Changelog

Builds before 1.3 were briefly labelled 1.0 and 2.0–2.3. They were renumbered as below. The
internal `versionCode` kept increasing throughout (1–6), so each build installs over the previous
one. From 1.4 on it's the version without the dot: 1.4 → 104, 1.5 → 105, 1.10 → 110. Small updates
get a patch number from 1.12.1 on, with two digits each for minor and patch: 1.12.1 → 11201,
1.13 → 11300, 1.13.1 → 11301, 1.13.2 → 11302, 1.14 → 11400, 1.14.1 → 11401, 1.15 → 11500, 1.15.1 → 11501. Builds that
were sent but never installed are folded into the next number (1.8 includes two builds briefly
labelled 1.8 and 1.9; 1.11 includes four, two of them briefly labelled 1.12, not the 1.12 below).

## 1.15.1
- Steps minute by minute opens hour by hour, the same as the Hour by hour chart you tapped. As you
  zoom in it gets finer a step at a time (every 30, 20, 15, 10, 5, 3 and 2 minutes), and the bars
  fade into the finer ones. With 40 minutes or less across it's minute by minute, which is as fine
  as steps go: the band keeps them a minute at a time.
- Hour by hour runs on the clock's hours: the first from when you woke to the next o'clock, then
  8–9, 9–10 and so on. Each hour used to run from the minute you woke (7:12–8:12) while labelled
  7–8 AM.
- With steps from Health Connect, an hour without any steps used to be left out of Hour by hour,
  which moved every later hour one place to the left. It's there now, as nothing.

## 1.15
- Steps minute by minute. Tap Hour by hour, on Today or on any day's page, to open the day up:
  bars across the day get finer as you zoom in (pinch, double-tap, or − and +) until each one is
  a single minute, down to ten minutes across. Drag a finger along to read each bar's steps and
  time; the whole day sits below with the part in view framed (drag along it to move, tap to
  jump). Bars from the minute you passed your goal are gold.
- Under it, the day's walks: every stretch at walking pace (60 steps a minute or more) lasting
  five minutes or more, with its times, how long, its pace and its steps, and Goal on the walk
  where you passed it. Pauses of up to two minutes (a crossing, a door) don't end a walk. Tap a
  walk to see it in the chart.
- On a day's page the night before is just its card now. Tap it for that night's own page (score,
  stages and all) instead of a second copy of the night chart underneath.
- The night chart's panel is called Sleep stages (it was Through the night). Opened up, the panel
  under it with the time in each stage is called Breakdown.

## 1.14.1
- Restlessness and interruptions are counted the way Google counts them: a stretch of being awake
  is taken whole, so a 4-minute wake-up running straight into a 2-minute stir is one 6-minute
  interruption rather than two restless moments. (On Oct 18 the app had 45 minutes all restless;
  Google split the same 45 into 35 restless and 10 of interruptions.)
- What counts as sound sleep is learnt from Google's own figures: the 13 nights you sent from
  Google Health's Coach (Sep 14 to Oct 19). Once heart rate is allowed, the app reads those nights'
  heart rate once and picks the thresholds that come closest to Google's sound sleep and time to
  sound sleep, then uses them for every night.
- For those 13 nights, Behind the score shows Google's own time to sound sleep, sound sleep,
  restlessness and interruptions.
- Oct 19's score (72) is added to the ones the estimates learn from, and the estimates kept for
  older nights are worked out again with the new measures.

## 1.14
- Sound sleep is measured from your heart rate, the way Google measures it, instead of counting
  every minute of light, deep and REM (which came out hours more than Google's). It's the
  stretches of light, deep or REM sleep with a low, steady heart rate; time to sound sleep runs
  from going to bed to the first of them. Google doesn't publish its exact thresholds, so the
  app's are its own: at or under the day's resting heart rate, steady within 4 beats a minute,
  for five minutes or more.
- Heart rate needs one more permission: add the third scope in step 4 of the Google Health setup
  (Settings → Google Health), then tap Allow heart rate. It's asked for on its own, so steps and
  sleep carry on without it; until then, sound sleep and time to sound sleep show "needs heart
  rate" rather than a number the app can't get right.
- Each night's sound sleep is worked out once and kept, so only the newest night is read again
  (for its first half day, while its heart rate may still be arriving).

## 1.13.2
- The night chart runs the full width of its panel, here and opened up: each stage's name and
  time sit small above its own row instead of in columns at the sides, which had squeezed the
  night into the middle.
- Brief stirs are slim ticks without a glow, so a night with dozens of them reads as a row of fine
  marks rather than a solid amber band.
- The times along the bottom of the zoomed night are spaced for the room there is, and never run
  into each other.

## 1.13.1
- The night panel's rows are back to just the stage's name; each stage's time sits at the end of
  its row instead of under its name, in plain type.

## 1.13
- The Hebrew date, in Hebrew, beside the date: on Today ("SATURDAY, OCT 17 · ו׳ חשון") and at the
  top of each day's and night's page. It's the Hebrew date of the day itself.
- The night and its stages are one panel now, right under the score: a row for each stage, named
  with its time that night, and the night drawn across them.
- Tap it to open the night up full screen and zoom in: pinch, double-tap, or use − and +, down to
  a quarter of an hour across, with the times marked along the bottom. Drag a finger along it to
  read the moment and stage under it. The whole night sits below it with the part in view framed;
  drag along it to move, or tap to jump. Each row's time, and the stages ring under it, count just
  what's in view.
- What went into the score comes after, redesigned: one bar made of the six factors, each part as
  wide as that factor counts and filled as far as it went well, then a tile for each with how it
  went and a small ring for how well.
- Fixed: sleep never actually came from Google Health. The app asked for it by when it started,
  but Google only finds sleep by when it ended, so every sleep read failed (the "Invalid data point
  filter" error under Settings → Google Health) and the app quietly used Health Connect's instead.
  Sleep now comes from Google Health when it's connected, for the Sleep tab and for where your
  days start and end, including the brief wake-ups Google counts as restlessness (which the app
  would also have dropped, as they come without a stage type).
- The Sleep tab no longer waits for two weeks of step totals before reading your nights: they load
  side by side, so the nights show up much sooner.

## 1.12.1
- The rest of the sleep scores you sent: Sep 14, 15, 16, 17 and 18, Oct 9, 11 and 12. With the
  first four, the estimates now learn from 12 of Google Health's scores.
- Scores for nights older than the two weeks the app shows count too: the app reads those nights
  once to see what it would have estimated for them.
- A night still shown is estimated afresh each time, rather than by the estimate kept from when
  its score was entered, so an improvement to the estimates doesn't leave stale numbers behind.

## 1.12
- Sleep score. Google Health doesn't share its sleep score with other apps, so the app estimates
  it from the six things Google says it weighs: duration, time to sound sleep, sound sleep,
  restlessness, full awakenings and interruptions, and rates it the way Google Health does
  (Excellent 90+, Good 80+, Fair 60+, Poor below). Tap "Estimate" under the score to enter the one
  Google Health gave that night: entered scores show as they are ("Google Health ✓"), and every
  one you enter teaches the estimates to match Google's. The four you sent (Oct 13, 14, 15 and 18)
  are already in. Like Google, no score for a night without stages.
- Behind the score: each of the six, with how it went and a bar for how well. It replaces the
  efficiency, time to fall asleep and wake-up figures.
- Restless moments now count the way Google counts them: brief stirs (5 minutes or less) count as
  asleep, in a Restless category of their own, and only longer wake-ups count as full awakenings.
  Settings → Restless moments → Count as awake counts them as awake time instead, each one a
  wake-up.
- The Sleep tab, redesigned under its own night sky: the score as the big ring, then what went
  into it, the stages (a ring beside the time and share of each, and how much of the night you
  were asleep), and the night chart, where brief stirs are small marks that don't break up the
  sleep around them. The last 7 nights show each night's score above its bar and the week's
  average; earlier nights show their score and rating, and each night's page lets you enter its
  score too.
- Today's last-night card is a small night sky with the night's score.
- A design pass over the whole app: panels softly lit from above, and a faint glow low on the
  page so the bottom of a screen isn't flat black.

## 1.11
- Sleep, with its own tab (Today · Sleep · History · Settings):
  - Last night in full: a ring showing what the night was made of (deep, light, REM and awake),
    the time asleep in the middle, and when you went to bed and woke up.
  - Efficiency, how long it took to fall asleep, how often you woke up, and how often you were
    restless (brief wake-ups, from Google Health).
  - Time in each stage, with its share of the night.
  - Through the night: the night stage by stage. Touch or drag along it to read the time and
    stage, with a tick each time the stage changes. Otherwise it shows the longest deep sleep.
  - The last 7 nights as stacked bars, scrubbable like the step charts, and earlier nights in a
    list, each opening its own page.
- Today shows last night as a card that opens the Sleep tab, and each day's page ends with the
  night before it.
- The night is the one the app starts your day from, your own calls included, with its stages
  from Google Health when it's connected, otherwise Health Connect. Fitbit's sleep score isn't
  available to other apps, so it isn't shown.
- A night your band recorded without stages shows just when you slept, rather than a made-up
  efficiency or wake-up count.
- Live steps between syncs. Your band only syncs now and then, so the phone's own step counter
  fills the gap: the number shown is the band's total plus the steps the phone counted since the
  band's newest data. When the band's next batch arrives, the count corrects itself to the band's.
  The widget and Today tick up as you walk while the screen is on, and Today's freshness pill
  shows the part from the phone ("6 min ago · +312"). Nothing is added once the day has ended,
  and past days are always the band's own numbers.
- The band comes first: a few steps around the house (to the bathroom, say) add nothing, and the
  phone's count only kicks in once you're clearly walking (30 steps within a minute and a half).
  It then stays up until the band's next sync takes over, rather than vanishing when you stop.
- Android asks once for "physical activity" access, from a card on Today. Say no and the card
  goes away for good.
- Settings → Diagnostics: a troubleshooting report to copy or share. It reads Health Connect and
  Google Health live, side by side (steps, calories and distance, plus the newest step each one
  has and how far behind now that is), along with the phone's step counter and the app's state:
  version, permissions, the day window, what the widget shows, and the recent sync log. Each read
  gives up after 10 seconds, so a source that doesn't answer can't hold up the rest.
- Fixed widget updates sometimes being dropped when two arrived at once, which could leave the
  widget showing older numbers until the next one.

## 1.10 (briefly labelled 2.0)
- A refresh button on the widget, in the top-right corner (at the end of the 4×1 strip; the 2×1
  has no room for it). Tap it and the widget re-reads your steps, calories and distance right
  away, from Google Health when it's connected and Health Connect otherwise, with a spinner until
  they're in. It brings in whatever your tracker has synced so far; it can't make the band sync.
  Tapping anywhere else still opens the app.

## 1.9
- Fixed a red "StandaloneCoroutine was cancelled" message on Today. It appeared when two
  refreshes overlapped (for example coming back from Google's sign-in screen); the one that got
  replaced wrongly reported itself as an error. The widget's background refresh had the same
  flaw when the screen turned off mid-refresh.

## 1.8
- Four new ring styles to try in Settings → Ring (swipe the row): Platinum (steel to bright
  platinum, so it's silver until the goal and then gold), Amethyst (violet to orchid), Rose
  (raspberry to blush) and Emerald (emerald to mint). Like the others they turn gold at the goal.
- Google Health: connect it in Settings and the app reads your steps, calories, distance, hour by
  hour and sleep straight from Google, falling back to Health Connect when it can't. While the
  screen is on it checks Google Health for new steps every 15 seconds. A setup sheet walks
  through the one-time Google Cloud setup, with each page a tap away and every value ready to
  copy (including the app's own signing fingerprint).
- Redesigned the app again, for a premium feel:
  - Everything moves: the ring sweeps round and numbers count up when a screen opens, sections
    drift in one after another, tabs slide, and buttons and cards give under your finger.
  - A slowly drifting glow in your wallpaper's tones behind every screen, glass-edged panels,
    small spaced-capital labels and big display numbers.
  - Hour by hour: tap or drag across the bars to read any hour (with a haptic tick per bar).
    The hours after you passed your goal are gold, and it says when you got there.
  - History: the last 7 days chart is scrubbable too, goal days in gold against a goal line.
    Tap any day for its own page: ring, calories and distance, hour by hour, a timeline from
    waking to sleep with naps on it, and Adjust.
  - Settings: drag a ruler to set your goal, ticking every 500 steps.
  - Today's header shows the date and how fresh your tracker data is, with a breathing dot.
  - A met goal gets a slow light passing round the gold ring.
- Vivid (from 1.6) is back as a third ring style: aqua to blue up to the goal, with coral
  calories and sky-blue distance, then the same gold as the other styles once the goal is met.

## 1.7
- New app design to match the widget: always dark in your wallpaper's tones, Google Sans
  bundled (so it looks the same on Samsung), a big ring with footprints over the count, calorie
  and distance tiles, an hour-by-hour chart and a floating tab bar. Pull down to refresh.
- Today shows the stretch being counted as one small tag ("Since 7:12 AM"; tap to adjust)
  instead of a card of text, and "Synced 6 min ago" at the bottom opens the sync log.
- History: a last-7-days chart with your average and a goal line, and each day with its own
  ring. Settings: goal with − / + buttons, ring style, Health Connect and the sync log.
- The ring is your wallpaper colour until the goal and gold once you hit it, in both styles
  (Soft and Fade), on the widget and in the app. The aqua-to-blue Vivid style is gone.

## 1.6
- Widget card is solid again, in a deep tone of your wallpaper colour with a soft light from
  the top-left (the 1.4 look), instead of see-through glass.
- Three ring styles, picked in Settings → Widget: Soft (one wallpaper tone), Fade (wallpaper
  tone fading in toward the tip) and Vivid (aqua-to-blue with coral calories and sky-blue
  distance, the default). The widget redraws as soon as you pick.
- Past the goal the ring keeps going: the next lap runs over the full ring with a shadow at its
  tip, so you can see how far past the goal you are. The Today gauge in the app does the same.
- Thicker ring with a faint glow inside, numbers in Google Sans (on Pixels).

## 1.5
- Widget redesign: smoked-glass card that lets the wallpaper show through, an aqua-to-blue
  gradient goal ring (gold when the goal is met), the footprints icon over the step count,
  coral calories and sky-blue distance. Fixed palette instead of wallpaper-derived colours,
  which came out muddy.
- Dropped redundant text: no percentage, no "steps to go", no "steps"/"calories" words. The
  ring shows progress and the icons and units say what each number is.

## 1.4
- Settings trimmed to the daily goal, Health Connect access and the sync log. Sleep/nap rules,
  the all-nighter cutoff, data source, units and live refresh are tuned in code instead.
- Removed explanatory text from Today, History, Settings and the Adjust sheet.

## 1.3 (built as 2.3)
- All-nighters are decided in hindsight: going to bed within 6 h of the 5 AM cutoff is a late
  night, not an all-nighter. Only staying up past that starts a new day, back-dated to 5 AM.
- After 18+ h awake, any sleep of 1 h or more is the night's sleep, whatever the time.

## 1.2 (built as 2.2)
- Widget redraws immediately instead of waiting in Android's background job queue.
- While the screen is on, checks Health Connect for new data every 15 s.
- History: "Midnight to midnight" view to compare with the Fitbit / Google Health app.
- Settings: sync log showing how often Fitbit data actually arrives.

## 1.1 (built as 2.1)
- Sleep vs. nap uses time of day and a learned night window, not just length.
- Adjust any day: call a sleep a nap or vice versa, skip an all-nighter reset, or set when the
  day started.

## 1.0 (built as 2.0)
- Redesigned widget: deep wallpaper-colored card, goal ring, layouts for every size, works on
  the lock screen.
- Calories and distance, steps to go; no wake time on the widget.
- Live refresh without a visible notification.

## 0.1 beta (built as 1.0)
- First version: wake-to-sleep step counting from Health Connect, all-nighter fallback, basic
  widget, Today / History / Settings.
