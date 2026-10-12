# Changelog

Builds before 1.3 were briefly labelled 1.0 and 2.0–2.3. They were renumbered as below; the
internal `versionCode` kept increasing throughout, so each build installs over the previous one.

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
