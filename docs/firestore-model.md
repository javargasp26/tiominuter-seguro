# Firestore data model (schemaVersion 1)

How each person's data lives in Cloud Firestore and the rules that lock it down (Phase 2a), as the app syncs it since Phase 2b (TECHNICAL_SUMMARY §21), the shared group documents (Phase 3a, see "Groups") and each group's feed (Phase 4a, see "Feed"). The model is the contract of two clients: the Android app (reads and writes) and the web app (reads and, since Phase 5b, writes its training; see "The web client"). Room (`tiominuter.db`, `programs.db`) stays the only source of truth for the UI; Firestore is the backup and sync target, and this model mirrors Room.

- Project `tiominuter-e43f7`, database `(default)`, Standard edition, region `southamerica-east1`, created in production mode (everything denied).
- Rules: [`firebase/firestore.rules`](../firebase/firestore.rules). Tests: [`firebase/rules-tests/`](../firebase/rules-tests/). Run them with `scripts/test_rules.sh`.

## Tree

```
users/{uid}                                   profile (no Room table)
users/{uid}/trainingLog/{entryId}             training_log (one doc per entry, entryId = uuid)
users/{uid}/weeklyGoals/{effectiveFromWeek}   weekly_goal (one doc per row)
users/{uid}/dailyProgress/state               daily_progress (one doc: the daily routines' unlock)
users/{uid}/programs/{programId}              programs (programId = a new per-program uuid)
users/{uid}/programs/{programId}/workouts/{workoutId}      program_workouts (workoutId = "{week}-{dayIndex}")
users/{uid}/programs/{programId}/completions/{workoutId}   program_workout_progress
users/{uid}/groups/{groupId}                  membership index (Groups)
users/{uid}/meta/groups                       group counter (Groups)
groups/{groupId}                              a group (Groups)
groups/{groupId}/members/{uid}                a member's public weekly summary (Groups)
groups/{groupId}/posts/{postId}               a counted workout shared with the group (Feed; postId = the entry's uuid)
inviteCodes/{code}                            invite code → group (Groups)
```

Nothing else is stored. The rules deny every other path. The "Fields every synced document carries", tombstones and the conflict policy apply to the synced Room mirrors only; the group and feed documents follow their own rules (see "Groups", "Feed").

## Fields every synced document carries

| Field | Type | Rule |
|---|---|---|
| `updatedAt` | timestamp | Must equal `request.time`. Clients write `FieldValue.serverTimestamp()`, never a device clock. |
| `deleted` | bool | `false` on a live doc. A deletion is a **tombstone** (below). |
| `schemaVersion` | int | Exactly `1`. A model change bumps it, and the rules accept the new version only once the app writes it. |

Every document that mirrors a Room row also stores its id as a field (`entryId`, `effectiveFromWeek`, `programId`, `workoutId`). The rules check that the field equals the id in the path.

### Tombstones

A deletion replaces the document (a `set` without merge) with **only** its id field, `deleted: true`, `updatedAt` and `schemaVersion`. For example, `{entryId, deleted: true, updatedAt, schemaVersion: 1}`. This way other devices learn about the deletion, and the server no longer holds the deleted data (notes, durations, profile values).

- A tombstone may be created directly, for a row deleted before it was ever uploaded.
- **Random ids (UUIDs: `trainingLog`, `programs`): tombstones are final.** The rules only accept another tombstone, so a late edit from an offline device can't bring a deleted entry or program back. Their ids are never reused, so finality can't block the user.
- **Natural ids (`weeklyGoals/{week}`, `workouts` / `completions/{week}-{dayIndex}`): a tombstone can be replaced by a full valid doc** (owner only, same validation as any write). The same id comes back by design: a week can get a goal again after its goal was deleted. The app never deletes a single goal today (`WeeklyGoalDao` only upserts, plus `deleteAll` in the local wipe), and workouts and completions only go away with their program. But a future reset, or a wipe followed by a new goal in the same week, must not be locked out. Last write wins, as for any update.
- Hard delete (`delete()`) by the owner is allowed. Delete account will use it to remove the whole `users/{uid}` tree from the client, because Cloud Functions need the Blaze plan. A hard delete erases the tombstone, so other devices only see it disappear. The app uses hard deletes only for the whole-account removal (Delete account: every document under `users/{uid}`, subcollections included, in batches, before the Firebase user).
- Children of a program (`workouts`, `completions`) are not tombstoned one by one. A tombstoned program means its children are dead, and clients ignore and clean them up.

## Conflict policy

**Last write wins, by `updatedAt`** (server time). Each document is the unit of conflict: the version that reached the server last is the truth, and every device converges to it.

- Device clocks never decide anything, because `updatedAt` is always the server's `request.time`.
- An offline device's write wins over an earlier online one when it arrives later. For a training entry, that means a later edit replaces an earlier edit as a whole: there is no field-level merge.
- **Exception: deletion wins for random ids.** The rules reject reviving a tombstoned entry or program (above). Natural-id docs follow plain last-write-wins.
- The sync pulls per collection with `updatedAt >= watermark`, ordered by (`updatedAt`, document id) with a cursor (every document of one batch has the same server time, so a page boundary can fall inside a batch; the inclusive watermark re-applies the last ones harmlessly), and applies remote docs to Room by id. Single-field ordering: no composite index.
- A pending local change wins over the pulled version (it is pushed afterwards and gets a newer server time), except a uuid tombstone (deletion wins); weekly goals follow the same rule. No device clock is ever compared with a server time.
- **Exception: the daily progress is merged, not last-write-wins** (see "`daily_progress`" below): the more advanced state is kept — a higher `cycle` (a restart) wins, then, in the same cycle, the higher `lastCompletedDay`.

## Room → Firestore mapping

### `users/{uid}` (no Room table)

| Field | Type | Rule |
|---|---|---|
| `displayName` | string or null | ≤ 500 chars, from the Google profile |
| `photoUrl` | string or null | `https://…`, ≤ 2048 chars |
| `createdAt` | timestamp | `request.time` on create; unchanged on update |
| `updatedAt`, `deleted` (must be `false`), `schemaVersion` | | common fields |

The email is **not** stored: Firebase Auth already has it, and nothing needs it in Firestore. The user doc has no tombstone, because deleting the account removes the whole tree.

### `training_log` → `users/{uid}/trainingLog/{entryId}`

| Room column | Firestore | Type and rule |
|---|---|---|
| `id` (autoincrement) | not synced | device-local key |
| `uuid` | doc id and `entryId` | lowercase UUID, equal to the path id |
| `dateLocal` | `dateLocal` | `"YYYY-MM-DD"` (regex; month 01–12, day 01–31; 4 or more year digits), the local date at completion |
| `completedAt` | `completedAt` | int epoch millis, any value (device clock). Kept as Room's millis for an exact round trip |
| `source` | `source` | `DAILY` / `GYM` / `PROGRAM` / `ACTIVITY` |
| `refId` | `refId` | string ≤ 200 or null/absent (`daily:3`, `gym:64`, `program:<programId>:<week>:<day>`) |
| `title` | `title` | string ≤ 500, may be empty. ACTIVITY: ≤ 40, the custom name or empty |
| `durationSeconds` | `durationSeconds` | int 0–2147483647 (a Kotlin Int) or null/absent. ACTIVITY: required, 60–36000 (1–600 min) |
| `activityType` | `activityType` | one of the 14 `ActivityType` codes. Required for ACTIVITY, null/absent otherwise |
| `intensity` | `intensity` | `EASY` / `MODERATE` / `HARD`. Required for ACTIVITY, null/absent otherwise |
| `note` | `note` | string ≤ 200 or null/absent |
| `photoPath` | **not synced** | a device-local file path. Photos are Phase 4 |
| `createdAt` | `createdAt` | int epoch millis (any value), as Room |
| `updatedAt` | `updatedAt` | **server timestamp** (Room's local `updatedAt` stays local) |
| — | `deleted`, `schemaVersion` | common |

`refId` of a PROGRAM entry: in Room `program:<localId>:<week>:<dayIndex>` (the device's program id), in Firestore `program:<programUuid>:<week>:<dayIndex>`. The sync rewrites it both ways (`ProgramRefIds` with the program feature's `ProgramRefMapper`); programs are pulled before the training log so the uuid is known. A refId whose program is unknown on the other side (deleted) is kept as it is.

### `weekly_goal` → `users/{uid}/weeklyGoals/{effectiveFromWeek}`

One document per Room row, so the goal history survives a restore. Past weeks keep the goal in force at the time, and the streak and the history headers depend on that.

| Room column | Firestore | Type and rule |
|---|---|---|
| `effectiveFromWeek` (PK) | doc id and `effectiveFromWeek` | `"YYYY-MM-DD"`, equal to the path id (a Monday; the rules don't check the weekday) |
| `workoutsPerWeek` | `workoutsPerWeek` | int 1–14 |
| `updatedAt` | `updatedAt` | server timestamp |
| — | `deleted`, `schemaVersion` | common |

The brief suggested a single `settings/weeklyGoal` document. That can't hold the history and still validate every value in the rules (rules can't loop over a list or map), so the history is a collection. The goal in force is the latest live doc whose `effectiveFromWeek` ≤ this week's Monday, as in Room. `settings/` stays free for future per-user settings.

### `daily_progress` → `users/{uid}/dailyProgress/state`

The daily routines unlock one by one (TECHNICAL_SUMMARY §28): Day 1 is always available, day N + 1 unlocks when day N is finished (its completion screen), unlocked days stay available, and "Restart days" locks every day again in a new cycle. One document per person.

| Room column | Firestore | Type and rule |
|---|---|---|
| `id` (always 0, one row) | not synced; the doc id is always `state` | the rules refuse any other id |
| `lastCompletedDay` | `lastCompletedDay` | int 0–999 (every day up to it was finished in this cycle; 0 = none) |
| `cycle` | `cycle` | int 0–2147483647 (a Kotlin Int), +1 per restart |
| `updatedAt` | `updatedAt` | server timestamp (Room keeps the local write time, or the pulled server time) |
| — | `schemaVersion` | exactly 1 |

- **Exactly these four fields:** no id field (the id is fixed) and **no `deleted`**: the document is never tombstoned (a reset is a new cycle, not a deletion). Owner read, write and hard delete (Delete account erases it with the rest of `users/{uid}`).
- **Merged by the clients, not last-write-wins:** `DailyUnlockRules.merge` (Kotlin) / `daily_progress.dart` (Dart), checked by `shared/golden/daily_unlock.json`: the higher `cycle` wins, then the higher `lastCompletedDay`. Android merges on every pull (`DailyProgressSync.reconcile` in `AppSyncStore`: the server ahead → applied, the device ahead → pushed again, equal → nothing); the web writes in a **transaction** over the server's current state (finish = `afterFinishing(server)`, restart = `restart(server)`), so it never overwrites a newer state it hasn't seen; offline it applies the change to its cached copy and queues it.
- **The rules don't enforce the order** (they can't refuse a regression without blocking Android's whole sync batch); the clients do. The only regression left is a web write landing between Android's pull and push of one sync (milliseconds); the next pull from the device that is ahead pushes its state again.
- **First state from the history:** a device (or the web) with no state yet starts at the highest day among its DAILY entries (`daily:<N>`), cycle 0, so nobody who trained before the unlock loses a day. On Android, the account's first sync with no server document raises a cycle-0 local state to that history before uploading it (a fresh install that read its progress before the entries arrived). A session closed for inactivity never unlocks a day.
- Pull: always in full (one document), after the training log.
- **Rules tests** (`firebase/rules-tests/test/dailyProgress.test.js`, 12, plus the generic owner / other user / anonymous cases of `rules.test.js`): every value the app produces (0, 30, 999; cycles 0 to 2147483647), a write going back accepted, owner list; refused: out of range, wrong types (string, fraction, null, bool, list), each missing field, extra keys, a `deleted` field or a tombstone, a client `updatedAt`, another schemaVersion, any other doc id, another user, nobody. **19 rule mutations, 18 killed**; the survivor (required keys not checked) is equivalent: every required field is read by a later check, so a missing one is already refused. 167 rules tests in total.

### `programs.db`

Room's program id is a device-local autoincrement, which collides across devices, so `programs.uuid` (ProgramDatabase v4; random lowercase, created with the program; older programs got one in the `Migration3To4` backfill) is the Firestore `programId`. Local ids never leave the device: a program pulled on another phone gets that phone's own id, and its workouts and completions are attached to it by the parent uuid in their path.

`programs` → `users/{uid}/programs/{programId}`

| Room column | Firestore | Type and rule |
|---|---|---|
| `id` | not synced | device-local key |
| — (new `uuid`) | doc id and `programId` | lowercase UUID, equal to the path id |
| `goal` | `goal` | `GAIN_MUSCLE` / `LOSE_FAT` |
| `level` | `level` | `BEGINNER` / `INTERMEDIATE` / `ADVANCED` |
| `location` | `location` | `HOME` / `GYM` |
| `focus` | `focus` | `BALANCED` / `LOWER_BODY` / `UPPER_BODY` / `CORE` |
| `weeks` | `weeks` | int 4–12 (`ProgramProfile.WEEKS_RANGE`) |
| `daysPerWeek` | `daysPerWeek` | int 2–5 |
| `age` | `age` | int 16–90 |
| `weightKg` | `weightKg` | number 30–250 |
| `heightCm` | `heightCm` | int 120–230 |
| `createdAt` | `createdAt` | int epoch millis (any value) |
| — | `updatedAt`, `deleted`, `schemaVersion` | common |

Age, weight and height are health-related personal data. They are readable only by their owner (rules), and a tombstone or a hard delete removes them.

`program_workouts` → `…/programs/{programId}/workouts/{workoutId}`

A generated program never changes when the templates change, so its workouts are synced as generated. Another device can't regenerate them, because the templates may have changed since.

| Room column | Firestore | Type and rule |
|---|---|---|
| `programId` | the parent path | |
| `week`, `dayIndex` (PK) | `week` (int 1–12), `dayIndex` (int 0–4), and `workoutId = "{week}-{dayIndex}"` | the rules check `workoutId` = path id = `"{week}-{dayIndex}"` |
| `kind` | `kind` | `STRENGTH` / `OUTDOOR_RUN` / `TREADMILL` / `HOME_CIRCUIT` |
| `isDeload` | `isDeload` | bool |
| `payload` | `payload` | string of 2–200000 chars: the same JSON as Room (`programPayloadJson`, discriminator `kind`). Opaque to the rules; the app decodes and validates it, as it does for Room rows |
| — | `updatedAt`, `deleted`, `schemaVersion` | common |

`program_workout_progress` → `…/programs/{programId}/completions/{workoutId}`

| Room column | Firestore | Type and rule |
|---|---|---|
| `programId`, `week`, `dayIndex` (PK) | parent path, `week`, `dayIndex`, `workoutId` | same id rule as workouts |
| `completedAt` | `completedAt` | int epoch millis (any value) |
| — | `updatedAt`, `deleted`, `schemaVersion` | common |

Completions are a separate collection, so the workout docs stay write-once. The rules don't check that the parent program or workout exists, which would cost an extra read per write and complicate batched uploads. Clients ignore completions without a live workout.

`program_slot_progress`: **not synced** (see the next section).

## App constraints vs rules

**The rules must accept every value the app can produce, today or in any earlier version.** Each rule is equal to the app's constraint or looser, never stricter. Check this table before tightening a rule, and update it with every model change. Sources: the domain rules (`ActivityEntryRules`, `WeeklyGoal`, `ProgramProfile`), the Room schemas (`app/schemas/`, v3–v6 and programs v1–v2) and git history (none of these constants ever changed).

| Field | What the app can produce | Rule | Proposed app limit for 2b |
|---|---|---|---|
| `trainingLog.durationSeconds`, ACTIVITY | 1–600 min (`DURATION_MINUTES`) → 60–36000 s; required | int 60–36000, required | — (already limited) |
| `trainingLog.durationSeconds`, DAILY / GYM / PROGRAM (see the note below the table) | ≥ 0 (a Kotlin `Int`); null on older entries (fewer than two completion times) and on a program strength completion without reported sets. Since the session work (TECHNICAL_SUMMARY §20) bounded by the 2-hour inactivity close: gym and program strength = first → last set, daily and program cardio = active time. Older versions could record days (a routine finished days later, a workout left open) | int 0–2147483647 or null/absent | — (bounded by the sessions; older entries keep the generous rule) |
| `trainingLog.title`, ACTIVITY | `""` for every type except OTHER; OTHER: the trimmed custom name, 1–40 UTF-16 units (`NAME_MAX_LENGTH`) | string ≤ 40 (rules count characters, not bytes: 40 accented letters or 20 emoji pass) | — |
| `trainingLog.title`, others | daily `routineDay` (+ " · " + title), the gym routine's English name, `ProgramWorkoutTitles`: content-driven, no limit, can be empty | string ≤ 500, empty allowed | A content validation test: names ≤ 100 characters |
| `trainingLog.note` | trimmed, blank → null, ≤ 200 (`NOTE_MAX_LENGTH`) | string ≤ 200 or null/absent, empty allowed | — |
| `trainingLog.refId` | `daily:<id>`, `gym:<id>`, `program:<id>:<week>:<day>` (≈ 20 chars; 49 with a program uuid); null for ACTIVITY | string ≤ 200 or null | — |
| `trainingLog.activityType`, `intensity` | the 14 / 3 codes, only on ACTIVITY (never changed); null on other sources and on every v3 row | enum on ACTIVITY, null/absent otherwise | — |
| `trainingLog` optional fields on old rows | v3–v6 have the same nullable columns: an old DAILY / GYM / PROGRAM entry may have null `refId`, `durationSeconds`, `activityType`, `intensity`, `note` | every optional field may be absent or null | — |
| `trainingLog.completedAt`, `createdAt`, program and completion times | `WallClock` millis: whatever the device clock says | any int | — |
| `trainingLog.dateLocal`, `weeklyGoals.effectiveFromWeek` | `SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)`: ASCII digits, 4-digit year (more past 9999) | regex with 4+ year digits | — |
| `weeklyGoals.workoutsPerWeek` | 1–14 (`WORKOUTS_RANGE`, enforced by the repository); the v4 `daysPerWeek` column held the same values, renamed in v5 | int 1–14 | — |
| `programs.age` / `weightKg` / `heightCm` | 16–90 / 30.0–250.0 (decimal, comma or dot) / 120–230 | same ranges, weight any number | — |
| `programs.weeks` / `daysPerWeek` | 4–12 / 2–5 | same | — |
| `programs.focus` | 4 codes; programs from v1 read `BALANCED` (migration default) | enum, required | — |
| `workouts.week` / `dayIndex` | 1–`weeks` (≤ 12) / 0–`daysPerWeek − 1` (≤ 4) | int 1–12 / 0–4 | — |
| `workouts.payload` | the generator's JSON; largest in the real backup 1455 chars, bounded by the session size | string 2–200000 | — (generator-bounded) |
| `users.displayName` | the Google profile name; no app limit | string ≤ 500 or null | Truncate to 500 when writing |
| `users.photoUrl` | Firebase Auth's Google photo URL (https) | https URL ≤ 2048 or null | Write null when not https |
| `dailyProgress.lastCompletedDay` | 0 … the last routine (30 today); `afterFinishing` and `fromHistory` cap at 999 (`MAX_DAY`) | int 0–999 | — (capped) |
| `dailyProgress.cycle` | 0, +1 per restart (a Kotlin Int) | int 0–2147483647 | — |

Rules tests (`edge values the app allows`) write each limit of this table and must stay green.

**Durations are now real and bounded in every source** (TECHNICAL_SUMMARY §20): every workout runs in a session that closes after 2 hours without activity, recording only the time done. Gym and program strength record first set → last set; daily and program cardio record active time (on screen and running; the outdoor run also in the background, pauses excluded). So a new entry can only be long when the activity really kept going with gaps of at most 2 hours. Entries recorded by older versions can still be long, so **the rules stay as they are** (any non-negative Kotlin Int).

## Validation against a real backup

`node scripts/validate_backup_against_rules.js <backup.tar>` takes a phone backup (`databases/tiominuter.db`, `programs.db` + `-wal`/`-shm`). It works as follows:
- It extracts only `databases/` into a temp dir outside the repo and checkpoints the WAL in that copy.
- It reads the databases read-only and maps every row as this document says. Each program gets a new UUID, as the v4 backfill does, and PROGRAM `refId`s are rewritten to it.
- It writes each doc as its owner into the Firestore emulator, through `firebase emulators:exec`.
- It prints counts per collection, accepted and rejected docs, and for each rejection the field and its rule. It never prints a value.
- It deletes the temp dir and exits with 1 on any rejection.

Last run (backup of 2026-10-20): 31 docs, 31 accepted, 0 rejected.

## Sync implementation (Phase 2b)

| Room | Firestore | Written by the app when | Pulled into |
|---|---|---|---|
| `training_log` row (by `uuid`) | `trainingLog/{uuid}` | an entry is recorded, an activity is added or edited; a deletion → tombstone | `AppSyncDao` insert / update by uuid (the local id and `photoPath` stay), delete on a tombstone |
| `weekly_goal` row (by week) | `weeklyGoals/{effectiveFromWeek}` | a goal is set | upsert / delete by week |
| `programs` row (by `uuid`) | `programs/{uuid}` | a program is created; deleted → tombstone | insert (new local id) / update by uuid; a tombstone deletes the program and, by CASCADE, its workouts, completions and sessions |
| `program_workouts` row | `programs/{uuid}/workouts/{week}-{dayIndex}` | with its program | upsert under the local program with that uuid |
| `program_workout_progress` row | `programs/{uuid}/completions/{week}-{dayIndex}` | every completion | upsert, when the workout exists locally |
| `daily_progress` (one row) | `dailyProgress/state` | a day finished (the next one unlocks), a restart, the first read (from the history) | merged (`DailyProgressSync`): the server ahead → the row takes it; the device ahead → enqueued again; no server doc → uploaded (cycle 0 raised to the history) |
| — | `users/{uid}` | every signed-in session (name ≤ 500, https photo or null) | not pulled |

- Every synced write enqueues its change (`sync_outbox`, in the row's database) in the same transaction; a change leaves the outbox only after its batch (≤ 500 writes) commits.
- Pulled documents are written without enqueueing (no ping-pong).
- A device whose local data belongs to another account wipes it before downloading; guest data meeting an account is uploaded and merged by id.

## Not synced: gym progress and program slot progress

`gym_exercise_progress` (gym routine slot markers) and `program_slot_progress` (strength workout slot markers) stay on the device. The reasons:

- **Per-session state.** They track "which exercises of the routine I'm doing now are done". Gym markers are cleared when the routine-completed dialog is dismissed or a routine's slots change. Program slots are cleared by Reset.
- **The result is already synced.** A completed gym routine or program workout is a `trainingLog` entry (GYM / PROGRAM), and a completed program workout is also a `completions` doc. Those carry everything a second device or a restore needs: the history, the weekly goal and the program progress percentage.
- **Cost and churn.** Every tapped set would be a write, and conflicts between two devices mid-session have no good answer.
- **Catalog coupling.** Gym markers point at catalog routine ids and slot positions, which a content update can change.

Losing a half-done session when changing phones is acceptable. If a later phase wants "resume on another device", it can add a doc per in-progress session.

## Excluded fields and device-local data

Not synced: Room autoincrement ids, `photoPath` and every photo file (Phase 4), Room's local `updatedAt`, `AuthPreferences` (start choice, banner dismissal), the content cache, and the catalogs (`exercises`, `gym_routines`, `daily_routines`…), which come from Hosting.

## Groups (Phase 3a)

Friends compare their week in small groups, joined only with an invite code. Phase 3a is the model, the rules and their tests; the app (3b) writes exactly the batches below. **Spark plan: no Cloud Functions.** Every write comes from a client, so each operation is one batched write whose documents the rules check against each other (`get` = before the batch, `getAfter` / `existsAfter` = after it). A batch that leaves out one of its writes is denied as a whole.

### Product rules

- Only signed-in accounts (Google). Guests and anonymous Firebase users are denied everything under groups (`isSignedIn()`: `request.auth != null` and `sign_in_provider != 'anonymous'`).
- Join by invite code only: 8 characters, uppercase, alphabet without `0 O 1 I L` (`2-9`, `A-H`, `J`, `K`, `M`, `N`, `P-Z`: 31 symbols, ≈ 8.5 × 10¹¹ codes). The owner can regenerate it; the old code stops working at once. The app picks a random code and, if the batch is denied because the code is taken, retries with another one.
- At most **20 members** per group and **5 groups** per person.
- The owner renames (1–40 characters, not blank, one line), regenerates the code, removes members, transfers ownership and deletes the group.
- An owner leaves only by transferring ownership in the same batch; an owner alone leaves by deleting the group.

### Documents

**`groups/{groupId}`** (`groupId`: a Firestore auto id, 20 alphanumeric characters)

| Field | Type | Rule |
|---|---|---|
| `name` | string | 1–40 characters, not only spaces, one line |
| `ownerUid` | string | the owner; always a current member |
| `inviteCode` | string | the current code; `inviteCodes/{inviteCode}` points back to this group |
| `memberCount` | int | 1–20, always equal to the number of member docs |
| `memberChangeUid` | string | the member who joined, left or was removed by the write that last changed `memberCount` (see below) |
| `createdAt` | timestamp | `request.time` at creation, never changes |
| `updatedAt` | timestamp | `request.time` on every write |
| `schemaVersion` | int | 1 |

**`groups/{groupId}/members/{uid}`**: written only by that member (the owner may delete it).

| Field | Type | Rule |
|---|---|---|
| `displayName` | string | 1–100 characters. The app writes the Google name truncated to 100, or a generic fallback ("Member" in the current language) when the account has none |
| `photoUrl` | string or null | https URL ≤ 2048, else null (as `users/{uid}`) |
| `joinedAt` | timestamp | `request.time` at the join, never changes |
| `inviteCode` | string | the code used to join (the join's proof); never changes, also after a regeneration |
| `weekStartLocal` | string | `YYYY-MM-DD` of the Monday of the member's current week; **the rules check it is a Monday** (`timestamp.date(y, m, d).dayOfWeek() == 1`; an impossible date such as `2026-02-30` is denied). 4-digit years only |
| `workoutsThisWeek` | int | 0–100: workouts of that week that count toward the goal |
| `weeklyGoal` | int | 1–14: the goal in force that week (the app asks for a goal before creating or joining a group) |
| `streakWeeks` | int | 0–1000: `WeeklyProgressCalculator.streak` |
| `daysThisWeek` | map | keys `"1"` (Monday) … `"7"` (Sunday), each optional; value = list of distinct sources (`DAILY`, `GYM`, `PROGRAM`, `ACTIVITY`), at most 4; a day without workouts is absent or `[]` |
| `lastTrainedLocal` | string or null | `YYYY-MM-DD` of the member's latest counted workout, null when none |
| `updatedAt` | timestamp | `request.time` |
| `schemaVersion` | int | 1 |

No other field is accepted (`hasOnly`), so durations, notes, titles, activity types, intensities, measurements, program data and photos can never reach a group.

**`inviteCodes/{code}`**: `groupId`, `groupName` (1–40, kept equal to the group's name), `createdAt` (`request.time`), `schemaVersion` 1. Any signed-in user can `get` one exact code; nobody can `list` them.

**`users/{uid}/groups/{groupId}`**: the membership index the app reads to know its groups: `groupId` (= the path id), `joinedAt` (`request.time`), `schemaVersion` 1. Created and deleted only, never updated.

**`users/{uid}/meta/groups`**: the group counter: `groupCount` (0–5), `lastGroupId` (the group of the last index change), `updatedAt` (`request.time`), `schemaVersion` 1. The app writes it as a merge with `FieldValue.increment(±1)`, so it never needs to read it first.

Changes to the suggested shape (and why):
- **`memberChangeUid` on the group.** The rules can't see which documents a batch touches, only look up documents by path. Naming the member whose join, leave or removal changes `memberCount` lets the group rule check that exactly that member doc appears or disappears in the same batch, and the member rule check the matching ±1. Without it, an owner could decrement `memberCount` without removing anyone and grow the group past 20. Every member can see who that was anyway.
- **The counter is a separate document (`users/{uid}/meta/groups`) with `lastGroupId`**, not a field on `users/{uid}`. The profile is written by the sync with its own validation (`isValidUser`, `updatedAt` = server time on every write), so a counter there would make every group batch rewrite the profile and every profile write carry the counter. `lastGroupId` pairs each counter step with one index entry, which keeps `groupCount` equal to the number of index entries; the limit of 5 is then a range check on the counter.
- **`schemaVersion` on `inviteCodes` and the index**, like every other document.
- **No `deleted` field on group documents.** They are not part of the outbox sync: they are written and deleted live (the app is online for every group operation) and read directly from Firestore, so there are no tombstones.

### Who reads what

| Document | Read |
|---|---|
| `groups/{groupId}` | `get` by its members only (`exists(members/{auth.uid})`); no `list` (the app knows its groups from its index) |
| `groups/{groupId}/members/{uid}` | `get` and `list` by the group's members, while the group exists; each person can also `get` their own member doc, also after being removed ("not found" then tells the app it is no longer a member) |
| `inviteCodes/{code}` | `get` by any signed-in user; no `list` |
| `users/{uid}/groups/…`, `users/{uid}/meta/groups` | the owner only |

### Batched operations

Every operation is ONE batch, as the app (3b) will send it; `firebase/rules-tests/test/groupBatches.js` implements each one. **One group per batch**: the counter moves by exactly 1, so cleaning up several groups takes several batches. `updatedAt` is `serverTimestamp()` everywhere.

| Operation | Batch | What the rules enforce |
|---|---|---|
| **Create** | `set groups/{g}` (`ownerUid` = me, `memberCount` 1, `memberChangeUid` = me) · `set groups/{g}/members/{me}` (with the code) · `set inviteCodes/{code}` · `set users/{me}/groups/{g}` · counter `+1`, `lastGroupId` = g | owner = creator, count 1, my member doc and the code doc (pointing to g, same name) exist after; the code doc must be new (an existing code is an update, denied); index + counter step; counter ≤ 5 |
| **Join** (after `get inviteCodes/{code}` → g) | `update groups/{g}`: `memberCount` `increment(1)`, `memberChangeUid` = me · `set groups/{g}/members/{me}` with `inviteCode` = code · index · counter `+1` | only those group fields change; count + 1 ≤ 20; I'm not a member before and am after; the member's code = the group's current code and `inviteCodes/{code}` points to g; index + counter ≤ 5. The joiner can't read the group, hence the increment |
| **Leave** (member) | `update groups/{g}`: `increment(-1)`, `memberChangeUid` = me · `delete` my member doc · `delete` my index entry · counter `−1` | count − 1; my member doc gone after; index gone; counter step. A non-owner can't change `ownerUid` |
| **Leave** (owner, others remain) | as Leave + `ownerUid` = another member, in the same `update` | the new owner is another member that still exists after the batch; without it the owner's leave is denied |
| **Leave** (owner alone) | = **Delete group** | a count of 0 is invalid, so only the delete works |
| **Remove member** (owner) | `update groups/{g}`: `increment(-1)`, `memberChangeUid` = them · `delete groups/{g}/members/{them}` | owner only, not themselves; that member existed and is gone after; count − 1. Their index entry stays (nobody can write another user's `users/` docs): see reconciliation |
| **Transfer** (owner) | `update groups/{g}`: `ownerUid` | owner only; the target is another current member |
| **Regenerate code** (owner) | `update groups/{g}`: `inviteCode` = new · `delete inviteCodes/{old}` · `set inviteCodes/{new}` | owner only; old doc gone, new doc points to g with the group's name; existing members keep their (old) `inviteCode` |
| **Rename** (owner) | `update groups/{g}`: `name` · `update inviteCodes/{code}`: `groupName` | owner only; 1–40; the code doc gets the same name in the batch (and changes nothing else) |
| **Delete group** (owner) | `delete` every member doc · `delete groups/{g}` · `delete inviteCodes/{code}` · `delete` my index entry · counter `−1` | owner only; code doc and the owner's member doc gone; other members' docs may be deleted by the owner because the group is deleted in the batch. A full group is 24 writes |

The rules can't count documents, so they can't prove that Delete group removed **every** member doc. A member doc left behind is harmless: nobody else can read it once the group is gone, its user can't update it, and its user deletes it (with the index entry) when reconciling.

The emulator accepts a full 20-member Delete group. Production documents a limit of 20 document lookups (`get` / `exists` / `getAfter`) per batched write; every member delete looks up the same group document, but whether repeated lookups count once there can only be checked in production. If 3b sees that batch denied, it removes members in smaller batches first (Remove member), then deletes the group. Join, the heaviest of the other operations, makes about a dozen lookups.

### Reconciliation of the index (done by each member's app)

A member's index entry and counter go stale when someone else changes their membership: the owner removed them, or deleted the group. Nobody can write another user's `users/{uid}` documents, so the member's own app cleans up, when it opens the groups screen and before any group write:

1. For each `users/{me}/groups/{g}`: `get groups/{g}/members/{me}` (always allowed for one's own doc).
2. Found: still a member. Not found: removed, or the group is gone. Then one batch: `delete users/{me}/groups/{g}` · counter `−1` with `lastGroupId` = g (the rules allow it only when my member doc doesn't exist after the batch). If the group is gone but my member doc was left behind (a Delete group that missed it), the same batch also deletes my member doc.

Rejoining a group after being removed needs that cleanup first (the index entry would otherwise already exist and the join is denied).

### Delete account (3b)

Before `users/{uid}` is erased (§21), the app runs, one batch per group: the reconciliation of stale entries; Leave for each group where the person is a member; for each owned group with other members, Leave with `ownerUid` = **the oldest other member by `joinedAt`** (the rules accept any current member: "oldest" is the app's choice and can't be checked without a query); Delete group for each owned group where the person is alone. Last, `delete users/{me}/meta/groups`, which the rules allow only at `groupCount` 0. The cleanup sequence test covers this exact flow.

### Summaries: privacy, "today", counting

- Each member's app writes only their own summary, from their local training history, whenever it changes and at least when the week changes: `weekStartLocal` = `LocalDateProvider.currentWeekStart()` (the phone's own date and zone), `workoutsThisWeek` and `daysThisWeek` from the entries that count toward the goal (`countsTowardGoal`: ≥ 10 minutes or unknown duration), the sources of each day (no counts, durations or titles), `streakWeeks` from `WeeklyProgressCalculator.streak`, `lastTrainedLocal` = the latest counted entry's `dateLocal`.
- Weeks run Monday to Sunday. "Today" is each member's own local date; readers never convert zones.

### Ranking (computed by readers, never stored)

For each member doc, relative to the reader's current week (`currentWeekStart()`):
- `workouts` = `workoutsThisWeek` when `weekStartLocal` is the reader's current Monday, else **0** (a summary from a previous week counts as 0);
- `compliance = min(workouts / weeklyGoal, 1)`.

Order: `compliance` descending, then `streakWeeks` descending, then `displayName` (case-insensitive, locale collation), then uid (so the order is stable).

### Banner input

The Home banner ("X trained today", "Y hasn't trained in N days") uses each member's `lastTrainedLocal` compared with the reader's local date, and `daysThisWeek` for this week's dots when `weekStartLocal` is current. Nothing else.

### How the app uses it (Phase 3b)

- `FirestoreGroupRepository` sends exactly the batches above (`groupBatches.js` is the reference); counts move with `increment(±1)`; the counter is a merge; summaries are an `update` of my member doc (a removed member gets NOT_FOUND instead of re-creating it, which the rules would deny anyway). Reads that decide something use `Source.SERVER`.
- `displayName`: the Google name truncated to 100 characters, or "Member" / "Miembro" when the account has none; `photoUrl`: only https. `weeklyGoal` is required (the app asks for a goal before creating or joining).
- Summaries are published only when they change (a hash per account), debounced 5 s, from the local history: see TECHNICAL_SUMMARY §22.3.
- **Delete group always in chunks:** the other members are removed one batch each (Remove member), then the final batch deletes the group, its code and the owner's membership. The single-batch Delete group above is still allowed by the rules (and tested), but the app never relies on it, because of the production limit on lookups per batch.
- Reconciliation runs on every list load, before create / join, and at app start and on Home (at most every 6 hours); Home reads members with one-shot queries cached 10 minutes, and only the detail screen keeps a listener (while visible).
- Delete account runs the group cleanup right after the re-authentication and before erasing `users/{uid}`; a failure stops the deletion.
- No rule or model change was needed.

### Rules tests

`firebase/rules-tests/test/groups.test.js` (40 tests, next to the 74 of `rules.test.js`): create (5-group limit, missing writes, bad owner / count / code / name / id, guests and anonymous accounts, a taken code); join (valid code, the old code after a regeneration, a wrong code and another group's code, the 20th and 21st member, a 6th group, partial or wrong batches, joining twice, writing someone else's member doc); reads (non-members, listing groups and codes, someone else's index, a removed member); summaries (own update, someone else's summary, every invalid field, non-Monday and impossible dates, private fields, the join proof); leave (member, owner with members denied, owner with transfer, owner alone); remove (owner, non-owner, the owner themselves, skipping the count, reconciliation); counter integrity; transfer, rename, regenerate, multi-field updates; delete (a full 20-member group, a member denied, keeping the code denied, a member doc left behind); and the whole Delete account cleanup sequence. The key checks were mutation-tested (31 mutations): removing one from the rules (both copies, where two rules check the same thing) makes a test fail.

## Feed (Phase 4a)

Each group has a feed of its members' counted workouts. Phase 4a is the model, the rules and their tests; the app (4b) writes exactly the operations below (`firebase/rules-tests/test/feedOps.js` is the reference). **Spark plan: no Cloud Functions, no Cloud Storage**, so the thumbnail is bytes inside the post, and expired posts are deleted by clients.

### Product rules

- When a member finishes a workout that counts toward the goal (`countsTowardGoal`: ≥ 10 minutes or unknown duration; any source), the app posts it to **every group the member is in** (≤ 5), unless the member turns sharing off for that workout. The author deletes their posts at any time.
- Only the group's members read the feed and react. Reactions: 💪 🔥 👏 🙌, at most one per member per post, changeable and removable. No comments.
- Moderation: the author deletes their own posts; the group's owner deletes any post of the group. "Hide for me" is local (no server data).
- The feed shows the current week (Monday to Sunday). A post lives **8 days** after its creation.
- Deleting an entry in the app deletes its posts; a changed photo may update or remove the thumbnail. Leaving a group deletes my posts in it; a member removed by the owner has their posts deleted by the owner; Delete account deletes my posts in every group before leaving them.

### `groups/{groupId}/posts/{postId}`

`postId` = **the training entry's `uuid`** (lowercase UUID, checked). The same entry is posted once per group (a second create of the same id is an update and is denied), and deleting the entry finds its posts by path, without a query.

| Field | Type | Rule |
|---|---|---|
| `authorUid` | string | `request.auth.uid` on create; never changes |
| `authorName` | string | 1–100 characters (the same name the app writes in the member doc: the Google name truncated to 100, or "Member" / "Miembro") |
| `authorPhotoUrl` | string, null or absent | https URL ≤ 2048 |
| `source` | string | `DAILY` / `GYM` / `PROGRAM` / `ACTIVITY` |
| `title` | string | ≤ 500 characters, may be empty (as `trainingLog.title`). The entry's stored title, in the recording device's language (the gym routine's English name, the program workout title, "Day N"), or an activity's custom name (empty for an activity of a named type). Readers show it only when `titleKey` can't name the workout |
| `activityType` | string, null or absent | one of the 14 `ActivityType` codes, **only on ACTIVITY posts**, so each reader shows "Running" / "Correr" in their own language and the activity's icon |
| `titleKey` | string, null or absent | the workout's **locale-free name**, so each reader names it in their own language without the author's data (see "Title keys"). ≤ 80 characters, strict format, **only for the post's own source**; never on ACTIVITY posts; **never changes** after creation |
| `durationSeconds` | int, null or absent | 0–2147483647, as `trainingLog` |
| `dateLocal` | string | `YYYY-MM-DD` of the entry (its `dateLocal`); **inside the week of `weekStartLocal`** |
| `weekStartLocal` | string | the Monday of `dateLocal`'s week (`LocalDateProvider.mondayOf(dateLocal)`); the rules check it is a Monday (as member docs) |
| `createdAt` | timestamp | `request.time` (server time) on create; never changes |
| `reactions` | map | `{uid: code}`, codes `strong` 💪, `fire` 🔥, `clap` 👏, `raised` 🙌; **empty on create** |
| `thumb` | bytes, null or absent | the JPEG thumbnail, ≤ **65,536 bytes** (hard cap; the app aims for ≤ 60 KB and ~400 px on the long side, no EXIF, which `TrainingPhotoProcessor` already strips) |
| `schemaVersion` | int | 1 |

No other field is accepted: never the note, intensity, `refId`, `photoPath`, a measurement or a program detail (`titleKey` names a catalog routine, a day number or a program workout *kind*, never the author's program or entry). No `updatedAt` and no `deleted`: posts are not outbox-synced, they are written live and hard-deleted.

`weekStartLocal` is the Monday of the **workout's** week, not of the day it was posted: an activity logged on Monday for the Saturday before belongs to last week's feed. That makes it a property of the entry the rules can check against `dateLocal` (the "current week" is each reader's own `currentWeekStart()`).

### Title keys (`titleKey`, added after 4b)

`title` is the text of the author's device, in the author's language (an English gym routine name, "Day 5", a Spanish program title…), so a reader in another language saw it untranslated. `titleKey` tells each reader *what* the workout was; the reader's app names it from its own catalogs and strings (`WorkoutNameResolver.nameOfPost`), and falls back to `title` when the key is absent (posts of older apps), unknown (a newer app's program code) or names something the reader doesn't have (a gym routine removed from the catalog).

| Source | Format | Example | Rules regex |
|---|---|---|---|
| GYM | `gym/<routineId>` (the routine of the entry's `refId` `gym:<id>`) | `gym/64` | `^gym/[1-9][0-9]{0,8}$` |
| DAILY | `daily/<dayNumber>` (the daily `routineId`, which is its day number) | `daily/5` | `^daily/[1-9][0-9]{0,8}$` |
| PROGRAM | `program/<CODE>`, a `ProgramTitleCode` name: a strength day title (`FULL_BODY_A` … `LOWER_C`) or a cardio kind (`OUTDOOR_RUN`, `BRISK_WALK`, `TREADMILL`, `HOME_CIRCUIT`) | `program/UPPER_A` | `^program/[A-Z][A-Z0-9_]*$` |
| ACTIVITY | none (`activityType` names it) | — | — |

- **Strict:** ids of 1–9 digits without a leading zero, codes of uppercase letters, digits and `_` starting with a letter, the whole key ≤ 80 characters, and only on a post of its own source (`gym/…` only on GYM, etc.). Optional: absent or null.
- **Immutable:** the only updates a post accepts (thumbnail by the author, a reaction by a member) may change nothing else (`affectedKeys().hasOnly(...)`), so a key can't be added, changed or removed after the create.
- **Stable codes:** `ProgramTitleCode` values are never renamed or removed (published posts use them); a new program title adds a value, and older apps show the post's `title` for it.
- The app never sends a key the rules would refuse (it validates it with the same format, `WorkoutTitleKey`), because a refused create is never retried; when nothing is known (a program deleted before the post was sent) the post goes without a key.
- Backward compatible: no `schemaVersion` change; older apps ignore the field when reading and never send it.

### Changes to the suggested shape (and why)

- **No `expiresAt` field. A post expires at `createdAt + 8 days`, computed by the rules from the server time.** The client can't write "server time + 8 days" (`serverTimestamp()` has no offset), so an `expiresAt` would come from the device clock, and the rules would have to compare it with `request.time` within a window. That breaks two rules of this project: no device clock is ever compared with a server time (a phone's clock can be wrong), and the rules accept everything the app produces (a wrong clock, or a post the Firestore SDK queued offline and sent days later, would be denied forever). `createdAt` is already the server time, so `createdAt + duration.value(8, 'd')` is exact. TTL would not need the field either (see "Expiration"). An `expiresAt` sent anyway is an unknown field and is denied.
- **`activityType` (optional, ACTIVITY only)** was not in the suggestion: an activity of a named type has an empty title, so without the code readers couldn't show what it was in their language.
- **A member may delete a post that doesn't exist** (a no-op): deleting an entry deletes `posts/{uuid}` in each group in my index, without knowing where it was posted or reading first.

### Reactions: a map on the post

`reactions` is a map on the post, not a subcollection:
- **Read cost:** a feed page of 50 posts is 50 reads with the reactions included; a subcollection would add one read per reaction (or a query per post).
- **Enforceable:** the rules check `request.resource.data.diff(resource.data).affectedKeys().hasOnly(['reactions'])` and `reactions.diff(resource.reactions).affectedKeys().hasOnly([request.auth.uid])`, with the value one of the 4 codes or absent. One key per member makes "at most one reaction per member" structural.
- **No races:** the app writes `update(FieldPath.of("reactions", myUid), code)` (or `FieldValue.delete()`), never the whole map, so concurrent reactions of different members don't overwrite each other and nothing is read first.
- **Size:** at most one key per person who was a member while the post lived (8 days); far from the 1 MiB document limit even with a full thumbnail. A removed member's key stays (harmless; the app can show only current members).
- Contention: Firestore sustains about one write per second per document, more than a 20-member group can react.

### Who reads and writes what

| Operation | Who | What the rules enforce |
|---|---|---|
| Read (`get`, `list`) | members of an existing group | `exists(members/{auth.uid})` and the group exists (a member doc left behind by a Delete group opens nothing) |
| Create | a member, as themselves | `authorUid == auth.uid`; every field validated; `createdAt == request.time`; `reactions` empty; thumb ≤ 65,536 bytes |
| Update: thumbnail | the author, while a member | only `thumb` changes (set, replace, or removed with `FieldValue.delete()` / null); ≤ 65,536 bytes |
| Update: reaction | any member (the author too) | only `reactions`, only the writer's key, a valid code or removed. The two updates can't be combined in one write |
| Delete | the author (also after leaving or being removed); the group's owner (while the group exists: `get()` reads it before the batch); **any member once `request.time > createdAt + 8 days`**; any member for a post that doesn't exist | — |

Guests and anonymous Firebase users are denied everything (`isSignedIn()`).

### Operations (Phase 4b)

| Operation | Writes | Notes |
|---|---|---|
| **Post a workout** | one `set groups/{g}/posts/{uuid}` **per group** (not one batch) | a group that rejects it (I was removed, the group is gone) doesn't block the others; `createdAt: serverTimestamp()`. Send it online with the values computed then: a post queued for days still gets 8 days from its server `createdAt` |
| **React** | `update` of `reactions.{myUid}` | `FieldPath.of("reactions", uid)` (uids are safe as keys, but a field path never parses) |
| **Change / remove the thumbnail** | `update {thumb}` per group where the entry was posted | the author only |
| **Delete an entry** | `delete posts/{uuid}` in every group of my index, one delete each | a group where it was never posted is a no-op the rules allow |
| **Leave** | query `posts where authorUid == me`, delete each, then the Leave batch | after leaving I can't list the group any more (I can still delete a post by id) |
| **Remove member** (owner) | the Remove member batch, then query `posts where authorUid == them` and delete each | the owner can delete them at any time; a crash in between leaves posts the owner (or the expiry) cleans later |
| **Delete group** (owner) | delete every post (query, in chunks), then every other member (Remove member, one batch each), then the final batch | posts first: once the group is gone the owner rule has no group to read. A post missed is unreadable and its author (or TTL, if ever enabled) deletes it |
| **Delete account** | per group, before the group cleanup of "Groups": delete my posts (as Leave); a solo owned group: delete all its posts, then Delete group | in the existing order: re-auth → posts + groups → `users/{uid}` → the auth user |
| **Cleanup of expired posts** | when a member opens a group's feed: query `posts where createdAt < now − 8 days orderBy createdAt limit 20`, delete each on its own | `now` is the device clock, so the server may refuse a post that isn't expired on its clock: deleting one by one (not a batch) skips it without blocking the others |
| **Author cleanup** | at app start (at most every 6 hours, like the group reconciliation): the same expired query for each group of my index | covers my own expired posts and anyone else's; no collection-group query, no extra index |

Owner deletions of many posts: send them one write each or in batches of at most 10 (each owner delete looks up the group document; production limits a batch to 20 lookups, and whether repeated lookups of one document count once is not documented).

### Expiration

- **TTL policies are not available on Spark:** Firestore's billing page lists "TTL deletes" among the features that "don't include free usage. You must enable billing to use these features" ([pricing](https://firebase.google.com/docs/firestore/pricing), [TTL](https://firebase.google.com/docs/firestore/ttl)). So on this project, the client cleanup above is the mechanism, and the rules' `createdAt + 8 days` is what makes a post deletable by any member.
- **If the project moves to Blaze**, one manual command enables TTL on `createdAt` with an 8-day offset (no new field, no rules change; TTL deletes usually happen within 24 hours of expiry and are billed as deletes):
  `gcloud firestore fields ttls update createdAt --collection-group=posts --enable-ttl --expiration-offset=8d --project=tiominuter-e43f7`
  (or the console: Firestore → Time-to-live → Create policy, collection group `posts`, field `createdAt`, offset 8 days). Keep the client cleanup anyway: TTL can lag by a day.
- The feed shows only this week (`weekStartLocal` == the reader's current Monday), so a post is hidden once its week ends and deleted later; 8 days is longer than any week a post can be shown in (a workout backdated to Monday and posted on Sunday is still shown that day).

### Indexes (`firebase/firestore.indexes.json`)

- **Composite `posts`: `weekStartLocal` ASC, `createdAt` DESC**, for the feed query `where weekStartLocal == <my Monday> orderBy createdAt desc limit 50` (equality plus order on another field needs one). The other queries use automatic single-field indexes: `authorUid ==` (leave, remove), `createdAt <` (cleanup).
- **Exemptions:** `posts.thumb` and `posts.reactions` are not indexed (`indexes: []`). Nothing queries them, and indexing 60 KB of bytes and every reaction key would only cost index storage and write latency.

### Budget

| Action | Reads | Writes |
|---|---|---|
| Post a workout | 0 | 1 per group (≤ 5) |
| React / change / remove | 0 | 1 |
| Open a feed (one-shot query) | 1 per post of this week (pages of 50; an empty result is 1) + the cleanup query (1 when nothing expired) | 0, or 1 per expired post deleted (≤ 20) |
| A listener while the feed is visible (as the group detail) | 1 per post added or changed (a reaction re-reads its post) | — |
| Delete an entry | 0 | 1 per group in my index |
| Leave / remove / delete group | 1 per post found | 1 per post deleted, plus the group batch |

Bandwidth: a post with a full thumbnail is about 61 KB, so a week of a busy 20-member group (≈ 100 posts) is about 6 MB per full feed load. That's inside the free 10 GiB/month, but 4b should page (50) and let the Firestore cache serve unchanged posts to listeners.

### Rules tests

`firebase/rules-tests/test/feed.test.js` (38 tests; operations in `test/feedOps.js`): create (with and without a thumbnail, every value the app can produce, optional fields absent; a non-member, guest, anonymous account, another uid, a group that doesn't exist; every invalid field: source, title 501, negative / too big / non-integer duration, name, http photo, an activityType on another source or unknown, schemaVersion, reactions not a map; bad dates: format, impossible, not a Monday, outside the week; a client `createdAt`, initial reactions, an `expiresAt`, private fields, `updatedAt`, bad ids; a 65,537-byte or string/array thumbnail; posting the same entry twice; one write per group); reads (members, the week query; non-members, guests, a removed member, a group deleted); reactions (add, change, remove; another member's key, the whole map, an invalid code, removing the map, other fields while reacting; non-member, removed member, guest); thumbnail (set, replace, remove; other fields, too big, wrong type, another member, the owner, a removed author); delete (author, owner, another member before expiry, non-member, guest; any member after 8 days and not before; the cleanup with a device clock 2 hours ahead; the no-op delete; an entry's posts in every group); and the sequences: leave, remove member, delete group (posts, members, group), a post the delete missed, Delete account across a member group, a shared owned group and a solo one. **32 rule mutations** (each check of the feed rules loosened, one at a time) each made a test fail. The 114 earlier tests are unchanged and green: **147 in total** at 4a. **`titleKey`** added 5 tests (each kind on its own source, the 80-character limit, absent / null, an activity without one; bad formats; a key of another source and any key on an activity; 81 characters; never changed by the author, a member, the owner, with a thumbnail or a reaction) and 16 mutations (the field allowed, the check called, the length, each source binding, each regex loosened or unanchored, immutability through the thumbnail and reaction updates): all **48 feed mutations** make a test fail, **152 tests in total**. (`titleKey is string` is not mutation-tested: a non-string fails `size()` / `matches()` anyway, which the rules evaluate as a denial.)

### How the app uses it (Phase 4b)

- `FirestoreFeedRepository` sends exactly the operations of `feedOps.js`: `set` of a new post (`createdAt: serverTimestamp()`, `reactions: {}`, `activityType` and `thumb` left out when empty), `update(FieldPath.of("reactions", uid), code | FieldValue.delete())`, `update({thumb: Blob | FieldValue.delete()})`, `delete` per post. One-shot queries come from the server.
- **Posting is one write per group** from a local queue (`feed_share`, Room v10, written in the entry's own transaction), so a workout finished offline is posted later; the queue is flushed by `FeedPublisher` (debounced 5 s; app start, queue changes, a successful sync). A group that refuses (PERMISSION_DENIED / NOT_FOUND) is never retried; a create refused because the post already exists and is mine (a lost answer) counts as posted. Posts carry `createdAt` from the server at the time they are actually written, so a post sent days later still lives 8 days.
- Nothing is posted until the person has seen a one-time explanation of what members see; the switch is per workout and ON by default. Only counted workouts of the current week, recorded while signed in, are posted, to the groups of the first post.
- **The title** is the entry's stored title (the history's text: the daily `routineDay`, the gym routine's English name, the program title in the author's language at recording time), kept as the fallback; the post also carries its **`titleKey`** (see "Title keys"), which readers resolve in their own language. An ACTIVITY with an empty title has `activityType`, which readers name in their own language.
- **Thumbnail:** the local 400 px thumbnail re-encoded to ≤ 60 KB (quality 60 → 30, then smaller sizes), never more than 65,536 bytes.
- **Reading:** a listener on `where weekStartLocal == <my Monday> orderBy createdAt desc limit 20 × pages`, only while the Feed tab is visible. **It needs the composite index `posts (weekStartLocal ASC, createdAt DESC)` of `firebase/firestore.indexes.json`, deployed with 4a; no new index was added in 4b.** The other queries (`authorUid ==`, `createdAt <` with `orderBy createdAt`, a plain `limit`) use single-field indexes. Hidden posts are filtered on the device.
- **Cleanup:** the Feed tab's first open and app start (every 6 hours) delete up to 20 posts older than 8 days per group, one by one, skipping refusals.
- **Sequences:** leave = my posts, then the Leave batch; remove member = the batch, then their posts; delete group = all posts, then members, then the final batch; Delete account = my posts in every group before the group cleanup, then `users/{uid}`, then the auth user.
- No rule or model change was needed for 4b; `titleKey` came later (rules + tests above; **deploy the rules after merging**). TECHNICAL_SUMMARY §23 has the app's architecture, screens and read/write budget, §24 the workout names.

## The web client (Phase 5a: reads; Phase 5b: a writer)

A second client: the Flutter web app (`web/`, served by Hosting at the site root). It reads this model directly with Dart models that mirror it (`web/lib/data/firestore_mappers.dart`: schemaVersion 1 only, unknown fields ignored, tombstones and documents missing a required field skipped) and has no guest mode. **No rule, index or field changed for it**: the rules check documents, not clients, so the web's writes are accepted exactly when Android's would be.

**The web as a writer (Phase 5b).** It writes the documents of this model with Dart mappers that match Android's field for field (`web/lib/data/write_mappers.dart`, tested against `shared/fixtures/`), and Android receives them through its normal pull by `updatedAt`:

| Document | What the web writes | As Android's |
|---|---|---|
| `users/{uid}/trainingLog/{entryId}` | a finished daily / gym / program workout, an abandoned session (dated on its start), a logged activity (create; an edit replaces it keeping `entryId` and `createdAt`); a deletion is the tombstone | `TrainingLogDocMapper` + `FirestoreRemoteUserData` (every field, nulls included; `updatedAt` = server time; `deleted`; `schemaVersion` 1; a `set` without merge) |
| `users/{uid}/weeklyGoals/{effectiveFromWeek}` | this week's goal (the natural id: this Monday) | `WeeklyGoalDocMapper` |
| `users/{uid}/programs/{programId}` + `workouts/{week}-{dayIndex}` | a new program (a lowercase UUID v4) and every workout, one batch (≤ 61 writes); the payloads come from the web's port of the generator, byte for byte Android's (shared/golden/program_generator.json) | `ProgramDocMapper`, `ProgramWorkoutDocMapper` |
| `…/completions/{week}-{dayIndex}` | a completed strength / treadmill / circuit workout | `ProgramCompletionDocMapper` |
| `users/{uid}/dailyProgress/state` | the daily unlock: created from my DAILY entries on the first read, a finished day, a restart; each in a transaction over the server's state (offline: the cached state, queued) | `DailyProgressDocMapper` (the same four fields; merged, never tombstoned) |
| `groups/{groupId}/members/{uid}` | my weekly summary, an `update` of the summary fields + name and photo (never `joinedAt` / `inviteCode`), only when it changed since this browser's last publish | `memberFields`, `GroupSummaryPublisher` |
| `groups/{groupId}/posts/{postId}` | my post of a counted workout of the current week (postId = the entry's uuid, `titleKey` `daily/<N>` / `gym/<routineId>` / `program/<CODE>`, `thumb` when this browser has the entry's photo: a JPEG ≤ 400 px and ≤ 60 KB (never above 65,536 bytes), no Exif); a photo added, changed or removed later updates or removes `thumb` on my posts of the entry (one `update` per group, the author-only update; Android's `setThumbnail`); deleting an entry deletes it in every group of my index; turning sharing off deletes the posts made | `postFields`, `FeedPublisher` |
| `reactions.<myUid>` on a post | my reaction (a field-path `update`) | `setReaction` |

**Since Phase 6 the web is a full groups client** with Android's batches, field for field (`FirestoreGroupsRepository`, the reference is `firebase/rules-tests/test/groupBatches.js` and `feedOps.js`): create, join, leave (with a transfer), remove, transfer, rename, regenerate the code, delete in chunks (posts, then one member per batch, then the final batch), reconciliation of my stale index entries (counter − 1), the counter's deletion at 0, post deletes (mine, the owner's, expired) — no rule, index or model change. It writes `users/{uid}` on sign-in as Android's sync (name ≤ 500, an https photo or null, `createdAt` kept) and erases everything under `users/{uid}` for Delete account (programs with workouts and completions, the log, the goals, the profile doc), after leaving every group as `GroupAccountCleaner` does. Offline, the persistent web cache queues the writes and sends them later (they carry `serverTimestamp()`, resolved at the server, so `updatedAt == request.time` holds); **no browser clock is ever compared with a server time**. Gym and program slot progress and sessions stay in the browser (localStorage), as Android keeps them on the device: only the finished workout (its entry) is synced, so a gym routine started on one device continues on that device. Cross-client check: `scripts/test_web_train_emulator.sh` (web writes → an Android pull into a fresh Room, checked against Android's own generator and summary calculator → an Android push → the web reads it).

Reads per screen (listeners only while the screen and the browser tab are visible):

| Screen | Reads | Listener |
|---|---|---|
| Landing / Account | none (Firebase Auth only) | — |
| My week | `trainingLog where dateLocal >= <my Monday>` (this week's docs, then 1 per change) and every `weeklyGoals` doc; the streak reads past weeks with `trainingLog where dateLocal >= A and < B`, 8 weeks per query, only as far back as the streak goes (`streakReadingBack`, never before the first goal's week), once per visit | both, while visible |
| My history | `trainingLog orderBy completedAt desc limit 20` (+ `startAfter` per "Load more"); a tombstone has no `completedAt`, so it never appears. Per distinct PROGRAM refId of the loaded entries: `get programs/{uuid}` (cached per program; tombstoned → the stored title) + `get programs/{uuid}/workouts/{week}-{day}` (its payload's title code), cached for the page load | — (one-shot) |
| Groups | `users/{uid}/groups` (≤ 5) + `get groups/{g}` each (a refused one is skipped) | — |
| Group ranking | `groups/{g}` and `groups/{g}/members` (≤ 20) | while visible |
| Group feed | `posts where weekStartLocal == <my Monday> orderBy createdAt desc limit 20 × pages` (the existing composite index) | while the Feed tab is visible |

The daily unlock adds: `get dailyProgress/state` on the Train hub and the daily selection (every visit) and when a daily workout opens; `trainingLog where source == "DAILY"` once, only while there is no state yet; a transaction (1 read + at most 1 write) per finished day or restart. Training adds: `trainingLog where dateLocal < <Monday> orderBy dateLocal desc limit 25` (the last trained date, only when this week has none; ≤ 4 pages), `users/{uid}/groups` before a publish or a post, `users/{uid}/programs` (+ `count()` of each program's `completions where deleted == false`, one read per program) on the Train hub, and one program's `workouts` and `completions` once per page load. Writes per action: TECHNICAL_SUMMARY §27.2.

Every query uses an automatic single-field index or the feed's existing composite index. Content names (gym routines, daily titles) come from `/content/v1/` over HTTP (`manifest.json`, then `routines_for_gym.jsonc` and `routines.json`, each checked by size and SHA-256), not from Firestore.

**Test data is shared:** the valid documents of every type live in `shared/fixtures/firestore_documents.json`, generated by `node scripts/export_fixtures.mjs` from `firebase/rules-tests/test/fixtureData.js` (committed; CI fails with `--check` when it is out of date). The rules tests (`fixtures.js`) and the web's Dart mapper tests both read that JSON, so the documents the rules accept are the ones the web maps. A model change updates `fixtureData.js`, re-exports, and both sides' tests.

## Future collections

- **Full-size photos:** the feed's thumbnail lives in the post (Spark). Full-size photos stay on the device; Cloud Storage (Blaze) would be a later, separate decision, with a `photo` field on the entry doc and schemaVersion 2.

Each of these needs its own rules, rules tests and a model update here.
