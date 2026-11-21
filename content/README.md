# Tio Minuter content

This folder is the single source of the app's content. The Android app bundles it in the APK
(so it works offline), and the same files are published to Firebase Hosting, where the Android
app picks up changes without a release and the Flutter web app (`web/`, served at the site root)
reads the names it shows (see "How the apps consume it"):

```
https://tiominuter-e43f7.web.app/content/v1/<file>
```

```
content/
  README.md                  this guide (not published)
  v1/
    exercises.json           exercise catalog of the daily and gym flows
    routines.json            daily routines
    routines_for_gym.jsonc   gym routines (comments and trailing commas allowed)
    program_exercises.json   catalog of the Program feature
    program_templates.json   program splits, day templates, prescriptions...
    animations/<id>_<name>.gif
    manifest.json            generated, never committed (see "Publishing")
```

The APK sees `content/v1` as its assets folder: `content/v1/exercises.json` is the asset
`exercises.json` and `content/v1/animations/2_squat.gif` is
`file:///android_asset/animations/2_squat.gif`.

## Rules

- **Never change or replace a published GIF; to change an animation, publish it under a new file
  name.** GIFs are cached for a year (`immutable`), and the Android app never downloads an
  animation it already bundles, so a changed file under the same name would not reach clients
  that already have it. To fix an animation, add `animations/<id>_<name>_v2.gif` and point the
  exercise to it.
- `v1/` holds only the five JSON files above and `animations/<name>.gif` (letters, digits, `_`,
  `-`, `.`). The app rejects a whole manifest that lists any other file (and keeps its current
  content), so a new kind of file goes to `content/v2/`.
- Keep ids stable: progress and history refer to `exerciseId` and `routineId`.
- Never modify an existing gym routine's exercises (its saved progress is cleared when its slots
  change); add a new routine with a free `routineId` instead.
- Every name has an English `name` / `routineName` and a Spanish `nameEs` / `routineNameEs`
  (a daily routine's optional `title` has `titleEs`)
  (neutral wording: see "Language guidelines" in CLAUDE.md). An id shared with
  `program_exercises.json` uses exactly the same `nameEs`.
- **Every daily routine lasts at least 10 minutes** of active time for a person who does every
  slot, so it counts toward the weekly goal (the app counts a workout from 10 minutes). Aim at
  11 minutes of *conservative minimum*; the validation fails under 10.5. See "Daily routine
  duration" below.
- `v1` is the format the current app reads. A change that older apps can't read goes to a new
  folder (`content/v2/`) instead of breaking `v1`.

## Common edits

**Add an exercise.** Add the GIF to `v1/animations/` as `<id>_<snake_case>.gif`, then an entry to
`v1/exercises.json`:

```json
{ "exerciseId": 118, "name": "Cable Fly", "nameEs": "Aperturas en polea", "animation": "118_cable_fly.gif" }
```

Exercises used by the Program feature also need an entry in `v1/program_exercises.json`
(muscles, equipment, mechanic, impact, locations, `perSide`, `duplicateOf`).

**Add a gym routine.** Append to `v1/routines_for_gym.jsonc` with a free `routineId`; `"set"`
must equal the number of values in `"reps"` (for `TIME`, each value is seconds); `"rest"` is in
seconds. Don't use exercises marked `perSide` in `program_exercises.json` (the gym screens have
no per-side support).

```json
{
  "routineId": 76,
  "routineName": "Upper Body",
  "routineNameEs": "Tren superior",
  "exercises": [
    { "exerciseId": 116, "type": "REPS", "set": 3, "reps": [10,10,10], "rest": 90 },
    { "exerciseId": 4, "type": "TIME", "set": 3, "reps": [30,30,30], "rest": 30 }
  ]
}
```

**Add a daily routine.** Add a routine to `v1/routines.json` with the next `routineId` and a
`routineDay` of exactly `"Day <routineId>"` (the app names the day itself, "Day 5" / "Día 5",
from the id: the validation checks it); each slot is `{ "exerciseId", "type": "TIME" | "REPS",
"amount" }` (seconds or reps). Rest is exercise 13.

Optional names: `title` (English) and `titleEs` (Spanish), shown after the day ("Day 5 · Core" /
"Día 5 · Abdomen"). A `titleEs` needs a `title`, and neither may be blank. A Spanish device shows
only `titleEs` (never the English title), so give every `title` a `titleEs`. No routine has a title
today. The app already reads and validates `titleEs`, but doesn't store it yet: showing it needs a
new database version in the app, so add the first `titleEs` together with that app change.

```json
{ "routineId": 31, "routineDay": "Day 31", "title": "Core", "titleEs": "Abdomen", "exercises": [ ... ] }
```

**Daily routine duration.** The app counts a daily workout's *active time*: a running timer
(the 5-second Get Ready, a TIME exercise, a rest) and a REPS exercise while it's on screen. The
conservative minimum of a routine is:

- TIME exercise: `amount` + 5 s (Get Ready);
- rest (exercise 13): `amount` (it starts by itself, no Get Ready);
- REPS: 2 s per rep;
- nothing for transitions (waiting before tapping Get Ready doesn't count).

It must be at least **630 s (10.5 min)**; aim at **660 s (11 min)**. Example: `1 TIME 60` = 65 s,
`2 REPS 15` = 30 s, `13 TIME 30` = 30 s. To lengthen a short day, prefer (in this order) a short
warm-up at the start, slightly longer TIME holds, an extra rest slot, or a short second round,
with exercises the daily routines already use (no new animations), keeping the early days the
easiest. `DailyDataValidationTest` checks every day with the same formula
(`DailyDurationEstimate`) and lists the days that are too short.

**Add or replace an animation.** Add the new GIF under a new name (see "Rules") and update the
`animation` of the exercises that use it. Never overwrite an existing GIF.

## Checking a change

```bash
./gradlew :app:testDebugUnitTest --tests "*ValidationTest" --tests "com.javargasp.tiominutermvi.domain.program.*"
```

This is the gate CI runs on every pull request that touches `content/**`: unique ids, known
exercises, valid types and values, existing animation files, Spanish names, the daily routines'
minimum duration, the program catalog
and templates, and the program generator on the real templates (the BALANCED programs must not
change).

## How the apps consume it

### Android


- The APK bundles `v1` as its assets; with no network the app uses them exactly as before.
- Once per launch, in the background, the app downloads `manifest.json` from
  `https://tiominuter-e43f7.web.app/content/v1/`. If its `contentVersion` is not the one the device
  already has, the app downloads only:
  - JSON files whose sha256 differs from the copy bundled in that APK;
  - animations the APK doesn't bundle (new file names).
- Every file is checked (size, sha256, at most 5 MB) and every JSON file must be readable by the
  app's own parsers. If anything fails, nothing changes on the device. Otherwise the new version
  is activated atomically and **used from the next launch**.
- The device keeps the files in its private storage (`filesDir/content/`, not backed up). Each
  file is read from there when the active version has it, otherwise from the APK.
- What this means when editing: a change merged to `main` reaches installed apps after the
  deploy, on their second launch with network. Changes must stay readable by the apps already
  installed (`v1` format); a breaking change goes to `content/v2/`.

### Web (second, read-only client)

- The web app has no bundled copy. Once per page load it reads `manifest.json` (no-cache) from its
  own origin, checks it as Android does (schemaVersion 1, known paths, sizes, hashes, no
  duplicates), then downloads only `routines_for_gym.jsonc` and `routines.json` (with
  `?v=<sha256>` so a copy cached under an older manifest is never used) and checks each one's size
  and SHA-256 before using it.
- It uses them only for names: a gym routine's `routineName` / `routineNameEs` (from a training
  entry's `gym:<id>` or a post's `gym/<id>`) and a daily routine's optional `title` / `titleEs`
  ("Day 5 · Core" / "Día 5 · Abdomen"). Anything missing or unverified falls back to the stored
  title, as on Android.
- So a renamed gym routine shows its new name on the web from the next page load after the deploy.
  Animations and exercises are not used on the web yet.

## Publishing

Nothing to do by hand: a push to `main` that touches `content/**`, `web/**`, `shared/**` or
`firebase.json` runs the content gate and the web gate, builds the web app, generates
`v1/manifest.json` (`python3 scripts/build_manifest.py`: schemaVersion, contentVersion = UTC
`yyyyMMddHHmmss`, and every file's path, size and sha256), stages the Hosting folder
(`scripts/stage_hosting.py`: the web build at the root, the content under `/content/`) and deploys
to Firebase Hosting (`.github/workflows/content-deploy.yml`). Other code changes never deploy.
A content-only change also redeploys the web app (same build), which is harmless.

Caching: `manifest.json` no-cache; JSON files 5 minutes; GIFs one year, immutable. All of
`/content` allows any origin (CORS). The web app's single-page rewrite never covers `/content/**`:
a missing content file is a real 404, never the app's `index.html`.
