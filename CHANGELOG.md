# Changelog

Builds before 1.3 were briefly labelled 1.0 and 2.0–2.3. They were renumbered as below. The
internal `versionCode` kept increasing throughout (1–6), so each build installs over the previous
one. From 1.4 on it's the version without the dot: 1.4 → 104, 1.5 → 105, 2.0 → 200. Builds that
were sent but never installed are folded into the next number (1.8 includes two builds briefly
labelled 1.8 and 1.9).

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
