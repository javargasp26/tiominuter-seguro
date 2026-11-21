# TioMinuterMVI — Technical Summary

> Hand-off document for a developer/AI adding new features. Factual: only what exists in the code. Items that are planned but absent are marked **NOT IMPLEMENTED**.
>
> App: Android workout app (Kotlin + Jetpack Compose + Material 3). Walks a user through a daily routine of TIME- and REPS-based exercises with GIF animations, a countdown timer, and audio/haptic feedback. Also has Gym Routines (§4c, §5.4–5.6), a **Flutter web app** to view the week, history and groups (§25), an isolated **Program** feature: multi-week programs generated from the user's profile (§12), a unified **training history** of every completed workout (§13), a **weekly goal** (workouts per week) with progress on Home (§14), **logging an activity** done outside the routines (§15), and **training photos** attached to history entries, local only (§16).

---

## 0. Content (single source, `content/`)

- `content/v1/` at the repository root holds `exercises.json`, `routines.json`, `routines_for_gym.jsonc`, `program_exercises.json`, `program_templates.json` and `animations/*.gif`, moved from `app/src/main/assets` with `git mv` (history kept). The unused `routines.jsonc` is archived in `docs/archive/` (not bundled, not published). Editing guide: `content/README.md`.
- APK: `android.sourceSets["main"].assets` = `../content/v1` in `app/build.gradle.kts`; same asset paths as before (`exercises.json`, `file:///android_asset/animations/<name>`), verified byte-for-byte against the pre-move APK (104 assets; only `routines.jsonc` removed). `ignoreAssetsPatterns += "!manifest.json"` keeps the generated manifest out (verified: without it, it is bundled).
- The app reads it only through `ContentSource` / `AnimationLocator`, which prefer a verified download from Hosting over the bundled copy (§17).
- Tests read content through `testutil.CONTENT_DIR` (`../content/v1`).
- Validation gate: `.github/workflows/content-validate.yml` (PRs and non-main pushes touching `content/**`; reusable), JDK 17 launcher (daemon Java 21 per `gradle/gradle-daemon-jvm.properties`), `./gradlew :app:testDebugUnitTest --tests "*ValidationTest" --tests "com.javargasp.tiominutermvi.domain.program.*"` (85 tests).
- **Daily minimum duration** (a workout counts toward the goal from 10 minutes, §14.2): every daily routine's **conservative minimum active time** must be ≥ 10.5 min, the content aims at ≥ 11. Estimate (`DailyDurationEstimate.conservativeMinimumSeconds`, unit-test source, the only definition, following what `WorkoutViewModel` counts, §5.3): TIME = `amount` + 5 s Get Ready (waiting before Get Ready doesn't count), rest = `amount` (starts on load), REPS = 2 s per rep, no transitions, no skipped slots. Checked by `DailyDataValidationTest`. The "max" below is only informative: REPS at 3 s per rep + 5 s per REPS slot. Days changed (all others unchanged, day 8 = 11.2 min, days 10–30 ≥ 12.8 min):

  | Day | Before min / max | After min / max | Slots | Change |
  |---|---|---|---|---|
  | 1 | 6.5 / 8.8 min | 11.3 / 14.9 min | 8 → 16 | Jumping jacks 60 → 90 s; rest 30 s after push-ups; second round: rest 45 s, squats 15, push-ups 8, plank 30 s, rest 30 s, bicycle crunches 20, leg raises 15 |
  | 2 | 7.2 / 9.8 min | 11.6 / 15.2 min | 8 → 16 | Warm-up jumping jacks 30 s first; rest 30 s after push-ups; second round: rest 45 s, squats 15, plank 30 s, rest 30 s, bridge 15, leg raises 15 |
  | 3 | 6.7 / 8.5 min | 11.2 / 14.1 min | 8 → 15 | Jumping jacks 70 → 100 s; rest 30 s after squats; second round: rest 45 s, squats 15, bicycle crunches 20, plank 30 s, rest 30 s, leg raises 15 |
  | 4 | 8.2 / 11.2 min | 11.5 / 15.1 min | 8 → 14 | Warm-up jumping jacks 30 s first; rest 30 s after push-ups; second round: rest 45 s, squats 15, push-ups 10, plank 30 s |
  | 5 | 8.2 / 10.6 min | 11.8 / 14.8 min | 8 → 13 | Jumping jacks 80 → 110 s; rest 30 s after squats; second round: rest 45 s, squats 15, bicycle crunches 20, plank 30 s |
  | 6 | 9.5 / 12.9 min | 11.1 / 14.5 min | 8 → 11 | Warm-up jumping jacks 30 s first; rest 30 s after push-ups; rest 30 s before leg raises |
  | 7 | 9.5 / 12.2 min | 11.1 / 13.8 min | 8 → 10 | Jumping jacks 90 → 120 s; rest 30 s after squats; plank 90 → 95 s; rest 30 s before leg raises |
  | 9 | 10.8 / 13.8 min | 11.1 / 14.2 min | 8 → 8 | Jumping jacks 100 → 120 s |

  Only exercises the daily routines already used (1 jumping jacks, 2 squats, 3 push-ups, 4 plank, 6 bicycle crunches, 7 leg raises, 8 bridge, 13 rest); no new animation; routineIds and routineDays unchanged. The early days keep the lightest work (their extra time is mostly rest and short repeats). `routines.json` is in no parity golden (only the program golden hashes `program_exercises.json` / `program_templates.json`), so no golden changed.
- Manifest: `scripts/build_manifest.py` → `content/v1/manifest.json` (`schemaVersion` 1, `contentVersion` UTC `yyyyMMddHHmmss`, files sorted by path with `path`, `size`, `sha256`; deterministic for a given version). Generated in CI, gitignored.
- Publishing: `.github/workflows/content-deploy.yml` ("Hosting deploy") on push to `main` touching `content/**`, `web/**`, `shared/**`, `firebase.json` or `scripts/stage_hosting.py`: content gate + web gate → `flutter build web` → `build_manifest.py` → `scripts/stage_hosting.py` → `FirebaseExtended/action-hosting-deploy@v0` (`channelId: live`, `projectId: tiominuter-e43f7`, secret `FIREBASE_SERVICE_ACCOUNT_TIOMINUTER_E43F7`). Other code changes never deploy.
- Hosting (`firebase.json`, `.firebaserc`): public folder `hosting/public/` (generated, gitignored), filled by `stage_hosting.py` (also the `predeploy` step) with the web build at the root and `content/v<N>/` → `/content/v<N>/` (§25.5). URLs `https://tiominuter-e43f7.web.app/` (the web app) and `/content/v1/<file>`.
- Headers: `manifest.json` no-cache; other JSON/JSONC `public, max-age=300` (JSONC served as `application/json`); GIFs `public, max-age=31536000, immutable`; `Access-Control-Allow-Origin: *` on `/content`. Checked with the local Hosting emulator. **Never overwrite a GIF: add a new file name.**

---

## 1. Build Configuration

Single-module Gradle project (`:app`). Namespace / applicationId: `com.javargasp.tiominutermvi`.

| Item | Value |
|------|-------|
| minSdk | 24 |
| targetSdk | 37 |
| compileSdk | 37 |
| versionCode / versionName | 1 / "1.0" |
| Java source/target | 11 |
| Kotlin | 2.2.10 |
| AGP (com.android.application) | 9.1.1 |
| Compose BOM | 2026.09.00 |
| Compose compiler | via `org.jetbrains.kotlin.plugin.compose` (Kotlin 2.2.10) |
| Navigation Compose | 2.10.2 |
| Koin (android + androidx-compose) | 3.5.6 |
| Coil (coil-compose) | 2.6.0 |
| Coil GIF (coil-gif) | 2.6.0 |
| AndroidX ExifInterface | 1.3.7 (direct dependency for the training photos; the same version Coil already brought in) |
| material-icons-core | BOM (added: material3 no longer pulls it transitively) |
| kotlinx-serialization-json | 1.8.1 (minimum required by `room-testing`; was 1.7.3) |
| Room (runtime / ktx / compiler + Gradle plugin) | 2.8.4 |
| room-testing (androidTest) | 2.8.4 |
| KSP | 2.3.10 |
| lifecycle-runtime-ktx | 2.11.0 |
| lifecycle-viewmodel-compose | 2.11.0 |
| activity-compose | 1.13.0 |
| core-ktx | 1.19.0 |
| JUnit (test) | 4.13.2 |
| MockK (test) | 1.13.10 |
| kotlinx-coroutines-test | 1.8.1 |
| androidx.test:core (test) | 1.7.0 |
| androidx.test.ext:junit (test) | 1.3.0 |
| androidx.test.ext:junit (androidTest) | 1.3.0 |
| espresso-core (androidTest) | 3.7.0 |
| Firebase BoM | 34.19.0 (`firebase-auth` 24.2.0); BoM 35.x has firebase-auth 25.0.0 with Kotlin 2.4 metadata, which Kotlin 2.2.10 can't read |
| com.google.gms.google-services (plugin) | 4.5.0 (reads the committed `app/google-services.json`, generates `default_web_client_id`) |
| androidx.credentials + credentials-play-services-auth | 1.6.0 |
| googleid (`com.google.android.libraries.identity.googleid`) | 1.2.0 (1.2.1 has Kotlin 2.4 metadata) |
| firebase-firestore | 26.6.0, from the same BoM 34.19.0 (Kotlin metadata 1.9); the sync (§21) |
| WorkManager (`androidx.work:work-runtime`) | 2.12.0 (Kotlin metadata 2.1, minSdk 24, needs compileSdk ≥ 35; its Room 2.7.0 resolves to 2.8.4): the network-constrained sync retry |

Notes:
- Rule: the Compose BOM must cover the Compose version resolved at runtime; after any dependency upgrade check both `debugCompileClasspath` and `debugRuntimeClasspath` (`./gradlew :app:dependencies --configuration …`) and the Kotlin metadata version of new libraries (≤ 2.3 for Kotlin 2.2.10). Adding the account dependencies left Compose UI 1.12.1 / Material3 1.4.0 / kotlin-stdlib 2.2.10 on both classpaths.
- `buildFeatures { compose = true }`; no `kotlinOptions`/`jvmTarget` block beyond Java 11.
- `release` build type: `isMinifyEnabled = false`.
- minSdk 24 and **no core library desugaring**: `java.time` APIs (API 26+) must not be used. Timestamps go through `domain/time/WallClock` (`System.currentTimeMillis()`), countdowns through `MonotonicTimeSource` (`SystemClock.elapsedRealtime()`).
- `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS` and the foojay-resolver-convention plugin (1.0.0).
- `gradle/gradle-daemon-jvm.properties` present (toolchain config).

---

## 2. MVI Foundation

All in `ui/base/`. The pattern: `User Action → Intent → ViewModel.reduce() → State (StateFlow) / Effect (Channel) → Compose UI`.

### Signatures

```kotlin
// MviIntent.kt
interface MviIntent

// MviState.kt
interface MviState

// MviEffect.kt
interface MviEffect

// BaseViewModel.kt
abstract class BaseViewModel<I : MviIntent, S : MviState, E : MviEffect>(
    initialState: S
) : ViewModel() {
    val state: StateFlow<S>
    val effect: Flow<E>
    fun handleIntent(intent: I)
    protected abstract suspend fun reduce(intent: I, currentState: S)
    protected fun setState(newState: S)
    protected fun sendEffect(effect: E)
}
```

Implementation details:
- `_state` is a `MutableStateFlow(initialState)`; `state` is its `asStateFlow()`.
- `_effect` is a `Channel<E>` (unbounded); `effect` is `_effect.receiveAsFlow()`.
- `handleIntent` launches in `viewModelScope` and calls `reduce(intent, _state.value)`.
- `sendEffect` launches in `viewModelScope` and sends to the channel.

```kotlin
// MviComposable.kt
@Composable
fun <E : MviEffect> CollectEffects(
    effectFlow: Flow<E>,
    lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: (E) -> Unit
)

@Composable
fun <I : MviIntent, S : MviState, E : MviEffect> MviComposable(
    viewModel: BaseViewModel<I, S, E>,
    onEffect: (E) -> Unit,
    content: @Composable (state: S, onIntent: (I) -> Unit) -> Unit
)
```

- `CollectEffects` uses `LocalLifecycleOwner` + `repeatOnLifecycle(lifecycleState)` inside a `LaunchedEffect`.
- `MviComposable` collects `state` via `collectAsState()`, calls `CollectEffects`, then invokes `content(state, viewModel::handleIntent)`.

### How a screen wires it together — Home (complete example)

`HomeScreen.kt` does **not** use `MviComposable`; it wires the pieces manually (a second, inconsistent pattern — see Tech Debt) and renders the stateless `HomeContent(state, onIntent)` (simplified below):

```kotlin
@Composable
fun HomeScreen(
    onNavigateToDailyRoutineSelection: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Handle effects
    CollectEffects(viewModel.effect) { effect ->
        when (effect) {
            is HomeEffect.NavigateToDailyRoutineSelection -> onNavigateToDailyRoutineSelection()
        }
    }

    // Load data on first composition
    LaunchedEffect(Unit) {
        viewModel.handleIntent(HomeIntent.OnStart)
    }
    HomeContent(state = state, onIntent = viewModel::handleIntent)
}
```

By contrast, `DailyRoutineSelectionScreen` and `WorkoutScreen` use the `MviComposable(viewModel, onEffect) { state, onIntent -> ... }` helper. Both patterns coexist in the codebase.

---

## 3. Package / Folder Structure

Root package: `com.javargasp.tiominutermvi`. Tree (3 levels deep, source only):

```
app/src/main/java/com/javargasp/tiominutermvi/
├── TioMinuterApplication.kt        # Application: starts Koin, configures Coil (GIF), photo cleanup + content sync at start
├── MainActivity.kt                 # setContent { AppNavigation() } with LocalAnimationLocator from Koin
├── di/
│   ├── AuthModule.kt               # Koin module of the accounts (§18)
│   ├── ContentModule.kt            # Koin module of the content read path and sync (§17)
│   ├── WorkoutModule.kt            # Koin module (remote sources, repos, Room, training log, ViewModels)
│   └── ProgramModule.kt            # Koin module of the Program feature (§12)
├── data/
│   ├── auth/                       # FirebaseAuthRepository, FakeAuthRepository, auth preferences, DefaultLocalUserDataWiper (§18)
│   ├── content/                    # ContentSource, AnimationLocator, ContentSyncer, ContentStore… (§17)
│   ├── datasource/
│   │   └── AssetDataSource.kt      # reads + parses JSON from assets (no longer used by app code)
│   ├── remote/                     # simulated backend (gym + daily routines)
│   │   ├── RemoteAssets.kt         # shared remoteJson, EXERCISES_FILE, fetchEndpoint, fetchExerciseCatalog
│   │   ├── GymRemoteDataSource.kt  # interface
│   │   ├── AssetGymRemoteDataSource.kt
│   │   ├── DailyRemoteDataSource.kt  # interface
│   │   ├── AssetDailyRemoteDataSource.kt
│   │   └── dto/                    # GymRoutineDto, GymRoutineExerciseDto, DailyRoutineDto, DailyRoutineExerciseDto, ExerciseDto
│   ├── local/                      # Room (gym routines + daily catalog + training log + weekly goal + gym and daily sessions + sync outbox + feed queue + daily progress), version 11
│   │   ├── TioMinuterDatabase.kt
│   │   ├── GymRoutineWithExercises.kt
│   │   ├── DailyRoutineWithExercises.kt
│   │   ├── entity/                 # ExerciseEntity, GymRoutineEntity, GymRoutineExerciseEntity, GymExerciseProgressEntity, DailyRoutineEntity, DailyRoutineExerciseEntity, TrainingLogEntity, WeeklyGoalEntity
│   │   └── dao/                    # GymRoutineDao, ExerciseDao, GymProgressDao, DailyRoutineDao, TrainingLogDao, WeeklyGoalDao
│   ├── mapper/
│   │   ├── GymMappers.kt           # DTO -> Entity -> Domain pure functions (gym + shared exercise mappers)
│   │   └── DailyMappers.kt         # DTO -> Entity -> Domain pure functions (daily routines)
│   ├── feedback/
│   │   └── AndroidWorkoutFeedback.kt # ToneGenerator + Vibrator
│   ├── repository/
│   │   ├── WorkoutRepositoryImpl.kt  # offline-first daily repository (Room + DailyRemoteDataSource)
│   │   ├── GymRoutineRepositoryImpl.kt
│   │   └── GymProgressRepositoryImpl.kt
│   ├── traininglog/                # TrainingLogMappers, TrainingLogRepositoryImpl (also the TrainingLogRecorder) (§13)
│   ├── goal/                       # WeeklyGoalRepositoryImpl (§14)
│   └── program/                    # Program feature (§12)
│       ├── remote/                 # ProgramRemoteDataSource, AssetProgramRemoteDataSource, dto/ (ProgramExerciseDto, ProgramTemplatesDto…)
│       ├── mapper/                 # ProgramMappers (DTO -> domain), ProgramEntityMappers (payload JSON, entities)
│       ├── local/                  # ProgramDatabase (programs.db, v1), ProgramSummaryRow, entity/, dao/
│       └── repository/             # ProgramRepositoryImpl
├── domain/
│   ├── auth/                       # AuthSession, AuthRepository, LocalUserDataWiper, AuthPreferences, GuestBanner (§18)
│   ├── feedback/
│   │   └── WorkoutFeedback.kt      # interface: playCompletionSound(), vibrate()
│   ├── time/
│   │   ├── MonotonicTimeSource.kt  # fun interface: nowMillis() (app: SystemClock.elapsedRealtime)
│   │   └── WallClock.kt            # fun interface: nowMillis() (app: System.currentTimeMillis)
│   ├── model/
│   │   ├── Routine.kt
│   │   ├── RoutineExercise.kt
│   │   ├── Exercise.kt
│   │   ├── ExerciseType.kt
│   │   ├── GymRoutine.kt
│   │   └── GymRoutineExercise.kt
│   ├── repository/
│   │   ├── WorkoutRepository.kt    # interface (reads + refresh)
│   │   ├── GymRoutineRepository.kt # interface
│   │   └── GymProgressRepository.kt # interface
│   ├── serialization/
│   │   └── ExerciseTypeSerializer.kt
│   ├── traininglog/                # TrainingLogEntry, TrainingSource, TrainingLogRecorder (+ recordSafely), TrainingLogRepository, LocalDateProvider, CalendarDates, ActivityType + Intensity, ActivityEntryRules (§13–§15); photo/: PhotoSizing, ExifTransform, TrainingPhotoPaths, TrainingPhotoRepository, TrainingPhotoProcessor, TrainingPhotoFiles, PhotoResult (§16)
│   ├── goal/                       # WeeklyGoal, WeeklyProgress, CurrentWeekSummary, WeeklyProgressCalculator, WeeklyGoalRepository, ObserveCurrentWeek (§14)
│   └── program/                    # Program feature (§12), pure Kotlin
│       ├── model/                  # enums, ProgramExercise, ProgramTemplates, ProgramProfile, GeneratedProgram, ProgramDetail
│       ├── generator/              # ProgramRules (computeBmi, isLowImpact, isEligible), ProgramGenerator
│       └── repository/             # ProgramRepository (interface), ProgramWorkoutTitles
└── ui/
    ├── base/
    │   ├── BaseViewModel.kt
    │   ├── MviIntent.kt
    │   ├── MviState.kt
    │   ├── MviEffect.kt
    │   └── MviComposable.kt        # MviComposable + CollectEffects
    ├── navigation/
    │   ├── AppNavigation.kt
    │   └── Routes.kt               # route constants + builders
    ├── components/                 # ExerciseAnimation (+ REST_ANIMATION), formatSeconds, TimerText, exerciseSummary, KeepScreenOn
    ├── theme/
    │   ├── Color.kt
    │   ├── Theme.kt
    │   └── Type.kt
    ├── example/                    # reference MVI example (Intent/State/Effect/ViewModel)
    ├── auth/                       # GoogleIdTokenRequester (Credential Manager), AuthMessage, AccountAvatar (§18)
    ├── welcome/                    # Welcome screen: Google or guest (§18)
    ├── account/                    # Account screen: sign in / sign out / delete (§18)
    ├── home/
    │   ├── HomeScreen.kt           # entry point (effects, OnStart) + stateless HomeContent
    │   ├── HomePreviews.kt         # 360x640 / 411x891, light/dark: with goal, without goal, goal met, guest banner
    │   ├── GuestProfileBanner.kt   # guest banner (§18)
    │   ├── WeeklyGoalCard.kt       # WeeklyGoalCompactCard (+ WeeklyGoalCardPreviews.kt)
    │   ├── HomeViewModel.kt
    │   ├── HomeState.kt
    │   ├── HomeIntent.kt
    │   └── HomeEffect.kt
    ├── dailyroutineselection/
    │   ├── DailyRoutineSelectionScreen.kt
    │   ├── DailyRoutineSelectionViewModel.kt
    │   ├── DailyRoutineSelectionState.kt
    │   ├── DailyRoutineSelectionIntent.kt
    │   └── DailyRoutineSelectionEffect.kt
    ├── gymroutineselection/        # GymRoutineSelection{Screen,ViewModel,State,Intent,Effect} + GymRoutineSelectionPreviews
    ├── gymroutine/                 # GymRoutine{Screen,ViewModel,State,Intent,Effect} + GymRoutinePreviews
    ├── gymexercise/
    │   ├── GymExerciseScreen.kt    # entry point (BackHandler, keep screen on) + GymExerciseContent
    │   ├── GymExercisePreviews.kt
    │   ├── GymExerciseViewModel.kt # set/rest flow + single countdown job
    │   ├── GymExerciseState.kt
    │   ├── GymExercisePhase.kt     # sealed phases
    │   ├── GymExerciseIntent.kt
    │   └── GymExerciseEffect.kt
    └── workout/
        ├── WorkoutScreen.kt        # entry point + WorkoutContent
        ├── WorkoutContent.kt       # ExerciseContentOnly
        ├── WorkoutButtons.kt       # ExerciseBottomButtons + button variants
        ├── WorkoutPreviews.kt      # mock data + previews
        ├── WorkoutViewModel.kt
        ├── WorkoutState.kt
        ├── WorkoutIntent.kt
        ├── WorkoutEffect.kt
        └── WorkoutCompletionScreen.kt
    ├── traininglog/                # TrainingHistory{Screen,ViewModel,State,Intent,Effect} + TrainingHistoryPreviews (§13)
    ├── weeklygoal/                 # WeeklyGoal{Screen,ViewModel,State,Intent,Effect} + WeeklyGoalPreviews (§14)
    ├── logactivity/                # LogActivity{Screen,ViewModel,State,Intent,Effect} + LogActivityPreviews, ActivityLabels (§15)
    └── program/                    # Program feature (§12)
        ├── ProgramNavGraph.kt      # NavGraphBuilder.programNavGraph(), called by AppNavigation
        ├── common/                 # ProgramFormat (formatPercent…), ProgramCountdown, ProgramUi (labels, dialogs, KeepScreenOn…), ResourceProgramWorkoutTitles, ProgramPreviewData
        ├── list/  create/  detail/  strength/  strengthexercise/  treadmill/  outdoorrun/  circuit/
        │                           # each: *Screen (+ stateless *Content), *ViewModel, *State, *Intent, *Effect, *Previews
```

Resources: `res/values/strings.xml` (English, default) and `res/values-es/strings.xml` (Spanish). `res/drawable/uncle.png` (home mascot). Assets (from `content/v1`, §0): `routines.json`, `exercises.json`, `assets/routines_for_gym.jsonc` (gym catalog), `assets/program_exercises.json` and `assets/program_templates.json` (Program feature, §12), `assets/animations/` (92 files, all GIF). Room schemas: `app/schemas/<database class>/<version>.json` for `TioMinuterDatabase` (1–5) and `ProgramDatabase` (1).

---

## 4. Data Layer

### Domain models (all `@Serializable`, in `domain/model/`)

```kotlin
data class Routine(
    val routineId: Int,
    val routineDay: String,
    val title: String? = null,
    val exercises: List<RoutineExercise>
)

data class RoutineExercise(
    val exerciseId: Int,
    @Serializable(with = ExerciseTypeSerializer::class)
    val type: ExerciseType,
    val amount: Int,
    val isRest: Boolean = false   // set only by DailyMappers (see TIME vs REPS)
)

data class Exercise(
    val exerciseId: Int,
    val name: String,
    val animation: String
)

enum class ExerciseType { TIME, REPS }
```

The domain models are still `@Serializable`, and `ExerciseTypeSerializer` maps the enum to/from its `name` string (`"TIME"` / `"REPS"`). Only `SerializationTest` uses that now: the app decodes the assets into DTOs (`data/remote/dto/`), stores them in Room, and maps the Room entities to these domain models (§4d).

### Sample entries

`routines.json` (array of `DailyRoutineDto`):
```json
{
  "routineId": 1,
  "routineDay": "Day 1",
  "exercises": [
    { "exerciseId": 1, "type": "TIME", "amount": 60 },
    { "exerciseId": 2, "type": "REPS", "amount": 15 },
    { "exerciseId": 13, "type": "TIME", "amount": 30 }
  ]
}
```

`exercises.json` (array of `ExerciseDto`; `nameEs` is optional in the DTO, present in every entry of the asset):
```json
{ "exerciseId": 1, "name": "Jumping Jacks", "nameEs": "Saltos de tijera", "animation": "1_jumping_jack.gif" }
{ "exerciseId": 13, "name": "Rest", "nameEs": "Descanso", "animation": "13_rest.gif" }
```

**Catalog names (shared convention of `exercises.json`, `routines_for_gym.jsonc` and `program_exercises.json`).** The English name is `name` / `routineName` (required); the Spanish one sits next to it as `nameEs` / `routineNameEs`.
- `exercises.json`: all 97 entries have `nameEs`. For the 84 ids also in `program_exercises.json` it is exactly that catalog's `nameEs` (checked by `GymDataValidationTest`); the other 13 (13 rest "Descanso", 26, 27 and the basketball drills 33, 35–37, 39, 41–45) were written for this catalog, starting from the old `ex_*` strings. Names usually said in English in Spanish gyms were kept: "Burpees", "Hip thrust", "Face pull en polea", "Contractor de pecho (pec deck)", "Skipping (rodillas altas)", "Sentadilla hack", "Pullover con mancuerna", "Curl …" and "Press …".
- `routines_for_gym.jsonc`: every routine has `routineNameEs` ("Día de tirón", "Día de empuje", "Día de pierna", "Día de brazos", "Hombro y pierna", "Espalda", "Pecho", "Glúteo y pierna", "Pierna y core", "Cuerpo completo A", "Cuerpo completo B"; "tirón"/"empuje" as in the Program titles).
- Code: `ExerciseDto.nameEs` / `GymRoutineDto.routineNameEs` (default null, older JSON decodes) → `ExerciseEntity.nameEs` / `GymRoutineEntity.routineNameEs` (nullable columns, v6) → `Exercise.nameEs` / `GymRoutine.routineNameEs` (default null; `SerializationTest` unchanged). The gym and daily refreshes upsert `exercises` from the same file, so both fill `nameEs`.
- **Display rule:** `localizedName(name, nameEs, language)` (`ui/components/LocalizedName.kt`) is the single choice: language `"es"` (any region) → `nameEs` when not blank, else `name`; any other language → `name`. Composable overloads read the language from `LocalConfiguration` (`currentLanguage()`), plus `Exercise.localizedName()`. Used directly for exercise names (the workout screen, the gym screens' exercise rows). **Routine names go through `WorkoutNameResolver` (§24)**, whose rendering applies this rule: Gym Routine Selection and Gym Routine (`GymRoutine.workoutName()`), Gym Exercise (title from `state.routineName` / `state.routineNameEs`) and the training history. Daily Routine Selection shows "Day X" / "Día X" titles (§24) and counts, no exercise names. The Program feature is unchanged (its own `displayName(spanish)`).
- The `ex_*` exercise-name strings (94) and `incline_dumbbell_curl` were deleted from `values` and `values-es` (nothing referenced them; 95 `UnusedResources` warnings gone).

### How the daily repository loads them

- `WorkoutRepository` interface: `getExercises()`, `getRoutines()`, `getExercise(id)`, `getRoutine(id)` (unchanged) and `refresh(): Result<Unit>`. All are `suspend`.
- `WorkoutRepositoryImpl(database, dailyRemoteDataSource)` reads only from Room and fills it from the simulated remote source. See §4d for the refresh rules.
- `ContentSource.readText(path)` (§17) is the only content read the app uses, through the remote sources. `AssetDataSource` is no longer in Koin nor used by app code (the Koin `Json` was removed); only `AssetDataSourceTest` uses it.

### Simulated remote source (daily flow)

- `DailyRemoteDataSource` (`data/remote/`): `fetchDailyRoutines(): List<DailyRoutineDto>` and `fetchExercises(): List<ExerciseDto>`. Implemented by `AssetDailyRemoteDataSource(contentSource, simulatedLatencyMs = 0)`, which reads `routines.json` / `exercises.json` through `ContentSource` (`DAILY_ROUTINES_CONTENT`, `EXERCISES_CONTENT`). Registered in Koin as `single<DailyRemoteDataSource> { AssetDailyRemoteDataSource(get()) }`.
- DTOs (`data/remote/dto/`), separate from the domain models and mirroring `routines.json`: `DailyRoutineDto(routineId, routineDay, title: String? = null, exercises)` and `DailyRoutineExerciseDto(exerciseId, type: String, amount)`. `type` stays a raw string until `DailyMappers` converts it. Exercises reuse the gym `ExerciseDto`.
- Shared with the gym source (`data/remote/RemoteAssets.kt`):
  - `remoteJson` (comments, trailing commas, unknown keys).
  - `EXERCISES_FILE`.
  - `EXERCISES_CONTENT` (`ContentJsonFile` of `exercises.json` with `remoteJson`).
  - `fetchEndpoint(file: ContentJsonFile, latency)`: latency, then `ContentSource.read(file)`, on `Dispatchers.IO`.
  - `fetchExerciseCatalog`: the single `fetchExercises()` implementation.
- Used only by `WorkoutRepositoryImpl.refresh()` (§4d).
- `DailyDataValidationTest` validates the real `routines.json`/`exercises.json`: unique routineIds, every routine has exercises, every exerciseId exists, type REPS/TIME, amount > 0, and every referenced animation file exists, and every routine's conservative minimum active time is ≥ 10.5 min (§0). All checks pass.

### TIME vs REPS

Defined by `RoutineExercise.type` (`ExerciseType.TIME` or `ExerciseType.REPS`) plus `amount`:
- **TIME** → `amount` is seconds. Drives the countdown timer + "Get Ready" pre-countdown.
- **REPS** → `amount` is the target rep count. Driven by a "Complete Reps" button (no timer).
- `WorkoutState` exposes derived flags `isTimeExercise` / `isRepsExercise`.
- **Rest** is not a distinct type. It is a TIME slot with `RoutineExercise.isRest = true`. The flag is set only in `DailyMappers.kt`, for slots whose `exerciseId` equals `DAILY_REST_EXERCISE_ID` (13, the "Rest" entry of `exercises.json`); that constant is the only place in the app code that knows the rest id. The default `false` keeps serialization and existing tests unchanged (`SerializationTest` still passes).

---

## 4b. Local Database — Room

Room is the **local source of truth for both flows**: the gym repositories (§4c) and the daily `WorkoutRepositoryImpl` (§4d) read only from it. Since version 2 it also holds the **daily-routine catalog** (catalog only; there is no daily progress), since version 3 the **training history** (`training_log`, §13), and since version 4 the **weekly goals** (`weekly_goal`, §14).

### Dependencies (version catalog `gradle/libs.versions.toml`)

- `androidx.room:room-runtime` / `room-ktx` / `room-compiler` — **Room 2.8.4** (stable).
- KSP plugin `com.google.devtools.ksp` — **2.3.10** (required: versions < 2.3.4 fail under AGP 9 built-in Kotlin with "kotlin.sourceSets DSL is not allowed"; KSP 2.2.10-2.0.2 does NOT work with AGP 9).
- Room Gradle plugin `androidx.room` 2.8.4, configured with `room { schemaDirectory("$projectDir/schemas") }` in `app/build.gradle.kts`; the database class sets `exportSchema = true`, so schema JSON is committed under `app/schemas/com.javargasp.tiominutermvi.data.local.TioMinuterDatabase/<version>.json` (`1.json` … `11.json`). The plugin also makes these schemas available to androidTest, which `MigrationTest` relies on; no extra `sourceSets` setup is needed.
- `androidx.room:room-testing` 2.8.4 (`androidTestImplementation`, `libs.room.testing`) for `MigrationTestHelper`. It depends on kotlinx-serialization 1.8.1, and AGP pins the androidTest classpath to the app's version, so the catalog's `kotlinxSerialization` was raised from 1.7.3 to 1.8.1. With 1.7.3, `MigrationTestHelper` fails with `AbstractMethodError` while reading the schema.

### Database (`data/local/TioMinuterDatabase.kt`)

`@Database(version = 6, exportSchema = true, autoMigrations = [AutoMigration(1, 2), AutoMigration(2, 3), AutoMigration(3, 4), AutoMigration(4, 5, spec = Migration4To5::class), AutoMigration(5, 6)])`, `@TypeConverters(RepsListConverter::class)`, exposes `gymRoutineDao()`, `exerciseDao()`, `gymProgressDao()`, `dailyRoutineDao()`, `trainingLogDao()`, `weeklyGoalDao()`. Name: `tiominuter.db`.

| Version | Change | Migration |
|---------|--------|-----------|
| 1 | `exercises`, `gym_routines`, `gym_routine_exercises`, `gym_exercise_progress` | — |
| 2 | adds `daily_routines`, `daily_routine_exercises` (gym tables unchanged) | `AutoMigration(1, 2)`; verified by `MigrationTest` |
| 3 | adds `training_log` (other tables unchanged) | `AutoMigration(2, 3)`; verified by `MigrationTest` (2 → 3 and 1 → 3) |
| 4 | adds `weekly_goal` (other tables unchanged) | `AutoMigration(3, 4)`; verified by `MigrationTest` (3 → 4 and 1 → 4) |
| 5 | renames `weekly_goal.daysPerWeek` to `workoutsPerWeek` (values kept: a 4-day goal becomes 4 workouts) | `AutoMigration(4, 5)` with `TioMinuterDatabase.Migration4To5` (`@RenameColumn` `AutoMigrationSpec`); verified by `MigrationTest` (4 → 5 and 1 → 5) |
| 6 | adds nullable `exercises.nameEs` and `gym_routines.routineNameEs` (null until the next catalog refresh fills them; gym progress kept since the slots don't change) | `AutoMigration(5, 6)`; verified by `MigrationTest` (5 → 6 and 1 → 6) and `GymRepositoriesTest` (a refresh fills the names and keeps progress) |

No `fallbackToDestructiveMigration`: a missing migration fails instead of wiping gym progress.

### Entities (`data/local/entity/`)

| Entity | Table | Key | Notes |
|--------|-------|-----|-------|
| `ExerciseEntity` | `exercises` | PK `exerciseId` | name + animation asset name |
| `GymRoutineEntity` | `gym_routines` | PK `routineId` | deleting a row cascades to exercises + progress |
| `GymRoutineExerciseEntity` | `gym_routine_exercises` | composite PK (`routineId`, `position`) | FK → `gym_routines` CASCADE; index on `exerciseId` (no FK to `exercises`); `reps: List<Int>` stored via `RepsListConverter` (comma-separated string); sets = `reps.size`, not stored separately; `type` is a raw `String` |
| `GymExerciseProgressEntity` | `gym_exercise_progress` | PK (`routineId`, `position`) | FK → `gym_routines` **only** (CASCADE), so progress survives exercise refreshes but is removed with the routine |
| `DailyRoutineEntity` | `daily_routines` | PK `routineId` | `routineDay`, nullable `title`; deleting a row cascades to its slots |
| `DailyRoutineExerciseEntity` | `daily_routine_exercises` | composite PK (`routineId`, `position`) | FK → `daily_routines` CASCADE; index on `exerciseId` (no FK to `exercises`); `type` is a raw `String`; `amount` = seconds (TIME) or reps (REPS); `position` lets the same exercise (e.g. rest) appear more than once |
| `TrainingLogEntity` | `training_log` | PK `id` (autoGenerate) | unique index on `uuid`, index on `dateLocal`; `source` = `TrainingSource` name; nullable `refId`, `durationSeconds`, `activityType`, `intensity`, `note`, `photoPath`; no foreign keys (an entry outlives its routine or program) |
| `WeeklyGoalEntity` | `weekly_goal` | PK `effectiveFromWeek` (a Monday, "yyyy-MM-dd") | `workoutsPerWeek` (1..14; the column was `daysPerWeek` until v5), `updatedAt`; one row per week, setting the goal again that week replaces it |

The daily catalog has no exercise table of its own: exercise data lives in the shared `exercises` table.

`RepsListConverter` encodes `List<Int>` as a comma-separated string (e.g. `30,45,60`).

### DAOs (`data/local/dao/`)

- **`GymRoutineDao`**
  - `observeRoutinesWithExercises(): Flow<List<GymRoutineWithExercises>>` — `@Transaction` + `@Relation` (ordering by `position` happens in the mapper).
  - `observeRoutineWithExercises(routineId): Flow<GymRoutineWithExercises?>`.
  - `replaceCatalog(routines, exercisesByRoutine)` — `@Transaction` that replaces the gym catalog: upserts routines (rows are **never** delete-and-reinserted, so progress is not cascaded away), deletes routines whose ids are no longer present (CASCADE removes their exercises + progress), then per routine deletes its exercises and inserts the new ones.
- **`ExerciseDao`**: `upsertAll`, `observeAll(): Flow<List<ExerciseEntity>>`, `getByIds(ids)`, plus `getAll()` (ordered by `exerciseId`) and `getById(exerciseId)` used by the daily repository. Both refreshes upsert the same `exercises.json` catalog into this table, and rows are never deleted.
- **`GymProgressDao`**: `observeProgress(routineId): Flow<List<GymExerciseProgressEntity>>`, `getCompletionTimes(routineId)`, `markCompleted(entity)` (REPLACE), `clearRoutine(routineId)`.
- **`TrainingLogDao`**: `insert(entity): Long` (ABORT: a duplicate uuid throws), `observeAll()` and `observeBetween(fromDate, toDate)` (inclusive on `dateLocal`), both `ORDER BY completedAt DESC, id DESC`, `getById(id)`, `update(entity): Int` (`@Update`), `delete(id)`.
- **`WeeklyGoalDao`**: `upsert(entity)` (`@Upsert`), `observeAll()` ordered by `effectiveFromWeek`.
- **`DailyRoutineDao`** (used by `WorkoutRepositoryImpl`)
  - `observeRoutinesWithExercises(): Flow<List<DailyRoutineWithExercises>>` and `getRoutinesWithExercises()` (both `ORDER BY routineId`), `getRoutineWithExercises(routineId)` (null if missing), `countRoutines()`.
  - `replaceCatalog(routines, exercisesByRoutine)` — `@Transaction` with the same semantics as the gym one: upsert routines (never delete-and-reinsert), delete routines no longer present (CASCADE removes their slots), then replace each routine's slots.

`GymRoutineWithExercises` and `DailyRoutineWithExercises` (in `data/local/`) are the `@Embedded`/`@Relation` POJOs returned by the routine DAOs; ordering by `position` is left to the caller.

### Koin (`di/WorkoutModule.kt`)

`single<GymRemoteDataSource> { AssetGymRemoteDataSource(get()) }` and `single<DailyRemoteDataSource> { AssetDailyRemoteDataSource(get()) }`. `single<TioMinuterDatabase>` via `Room.databaseBuilder(...)` (no `fallbackToDestructiveMigration`), plus one `single` per DAO (`GymRoutineDao`, `ExerciseDao`, `GymProgressDao`, `DailyRoutineDao`, `TrainingLogDao`) resolved from the database. Training history (§13): `single { LocalDateProvider(get()) }` and one `TrainingLogRepositoryImpl` `single` bound as both `TrainingLogRepository` and `TrainingLogRecorder`. Training photos (§16): `single<TrainingPhotoFiles> { TrainingPhotoFileStore(filesDir, cacheDir) }`, `single<TrainingPhotoProcessor> { AndroidTrainingPhotoProcessor(contentResolver) }`, `single<TrainingPhotoRepository> { TrainingPhotoRepositoryImpl(get(), get(), get(), get()) }` (also used by `programModule`). Weekly goal (§14): `single<WeeklyGoalRepository> { WeeklyGoalRepositoryImpl(get(), get(), get()) }` and `factory { ObserveCurrentWeek(get(), get(), get()) }`. `single<WorkoutRepository> { WorkoutRepositoryImpl(get(), get()) }` (database + `DailyRemoteDataSource`) replaces the old asset-reading registration.

### Tests

`src/androidTest/.../data/local/GymDaoTest.kt` — in-memory database, 8 tests: replaceCatalog insert/update/remove semantics (incl. progress kept for surviving routines and cascaded for removed ones), reps converter round-trip (unit + through the DB), progress mark/observe/clear, and ExerciseDao upsert/observe/getByIds.

`src/androidTest/.../data/local/DailyDaoTest.kt` — in-memory database, 8 tests: replaceCatalog inserts, updates (routine fields and slots), removes disappeared routines with their slots cascaded, an empty catalog removes everything, the same exercise twice in one routine, `countRoutines`, unknown routine → null, and the daily catalog does not touch the gym tables.

`src/androidTest/.../data/local/MigrationTest.kt` — `MigrationTestHelper` against the exported schemas, 7 tests: 1 → 2 (gym data kept, empty daily tables), 2 → 3 (gym data, progress and daily catalog kept, empty `training_log`), 1 → 3, 3 → 4 (two `training_log` entries, with nulls and a note, and gym progress kept; empty `weekly_goal`), 1 → 4, 4 → 5 (two goals keep their values under `workoutsPerWeek`, the `training_log` entry survives), 1 → 5; each validated against the target schema.

`src/androidTest/.../data/local/TrainingLogDaoTest.kt` — in-memory database, 7 tests: insert returns the id and keeps every field, newest first (ties by id), inclusive date range, `getById`, `update` replaces the row with the same id (0 for an unknown id), delete, duplicate uuid rejected.

`src/androidTest/.../data/local/WeeklyGoalDaoTest.kt` — in-memory database, 3 tests: empty at first, ordered by week, upsert in the same week replaces the row.

All pass on an emulator (`connectedDebugAndroidTest`).

---

## 4c. Gym Routines data flow (offline-first)

`assets/routines_for_gym.jsonc` + `assets/exercises.json` (fake API) → `AssetGymRemoteDataSource` (DTOs, shared `remoteJson` with comments/trailing commas and `fetchEndpoint`/`fetchExerciseCatalog` helpers from `RemoteAssets.kt`, optional `simulatedLatencyMs`) → mappers (`data/mapper/GymMappers.kt`) → Room → repositories → `Flow` of domain models → ViewModels.

- **`GymRoutineRepositoryImpl.refresh()`** (`Result<Unit>`, guarded by a `Mutex`): fetches routines + exercises, then in one `database.withTransaction` upserts exercises and calls `replaceCatalog`. For each surviving routine whose slots changed (exerciseId, type, reps, restSeconds, in order) its progress is cleared in the same transaction; unchanged routines keep their progress. On failure the database is untouched. Called by Gym Routine Selection on first load and on Retry.
- **Reads** come only from Room: `observeGymRoutines()` / `observeGymRoutine(id)` map entities to `GymRoutine` (slots sorted by position; a slot with an unknown type string is skipped; a slot whose exercise is missing from the catalog maps to `exercise = null` and the UI shows "Unknown exercise").
- **`GymProgressRepositoryImpl`**: `observeCompletedPositions`, `getCompletionTimes(routineId)` (for the training-history duration), `markCompleted(routineId, position)` (stores `completedAt` from the injected `WallClock`), `resetRoutine`. Progress rows have a FK to the routine only: `markCompleted` for a routine that no longer exists throws (constraint), which `GymExerciseViewModel` handles by showing not-found.
- **Data validation** (`GymDataValidationTest`): all gym data checks pass (unique routineIds, every exerciseId exists, sets == reps.size, reps > 0, rest ≥ 0, type REPS/TIME, every referenced animation file exists). The two previously missing files (`63_tricep_dip.gif`, used by exercises 63 and 85, and `98_incline_dumbbell_curl.gif`) are now in `assets/animations/`.
- **Gym catalog** (`routines_for_gym.jsonc`, 11 routines): 64 Pull Day, 65 Push Day, 66 Leg Day, 67 Arms Day, 68 Shoulder and Leg Day, 69 Back, 71 Chest, and the balanced routines 72 Glutes & Legs (hip thrust, Romanian deadlift, leg press, abductor, lying leg curl, glute bridge, standing calf raise), 73 Legs & Core (squat, knee extension, leg curl, adductor, seated calf raise, plank TIME 30/40/45 s, hanging crunches), 74 Full Body A (squat, barbell bench press, lat pulldown, hip thrust, dumbbell shoulder press, plank TIME 3 × 30 s) and 75 Full Body B (Romanian deadlift, incline dumbbell press, low pulley row, leg press, lateral raise, leg raises). The new routines use no per-side exercise (the gym screen has no per-side support); the older 64 (75, 80) and 66 (20) do use exercises the program catalog marks per side.
- `exercises.json` now also has 116 Barbell Bench Press (`116_barbell_bench_press.gif`) and 117 Romanian Deadlift (`117_romanian_deadlift.gif`), with the same ids and files as `program_exercises.json`.
- Gym routine and exercise names have Spanish versions (`routineNameEs`, `nameEs`; §4 "Catalog names"); `GymDataValidationTest` requires them for every routine and every exercise a routine uses.

---

## 4d. Daily routines data flow (offline-first)

`assets/routines.json` + `assets/exercises.json` (fake API) → `AssetDailyRemoteDataSource` (DTOs, shared `remoteJson` and `fetchEndpoint`/`fetchExerciseCatalog` helpers) → mappers (`data/mapper/DailyMappers.kt`) → Room (`daily_routines`, `daily_routine_exercises`, shared `exercises`) → `WorkoutRepositoryImpl` → `HomeViewModel`, `DailyRoutineSelectionViewModel`, `WorkoutViewModel` (unchanged).

- **`WorkoutRepositoryImpl.refresh()`** (`Result<Unit>`, guarded by a `Mutex`): fetches routines and exercises, then in one `database.withTransaction` upserts exercises and calls `DailyRoutineDao.replaceCatalog`. On failure the database is untouched.
- **Refresh once per process.** The repository is a Koin `single`. The first call to any read method runs `refresh()` once, behind a second `Mutex` with a double-checked flag, so concurrent first readers trigger a single refresh. After that, reads go straight to Room.
- **If the first refresh fails:**
  - With routines already in Room, it logs `Log.w("WorkoutRepository", …)` and serves the stored data.
  - With an empty Room, the read throws, so the ViewModels keep their existing behaviour: Home falls back to "Day 1", and selection and workout show their error. The next read retries the refresh.
- **Reads** come only from Room:
  - `getRoutines()` returns the routines ordered by routineId; `getRoutine(id)` and `getExercise(id)` read single rows and return null when missing.
  - `getExercises()` returns the whole `exercises` table ordered by exerciseId. That is the `exercises.json` catalog, which the gym refresh writes too.
- **Mappers** (`DailyMappers.kt`, pure functions):
  - DTO → entity with position = index in the routine's list.
  - Entity → `Routine`/`RoutineExercise`, with slots sorted by position and the type string mapped to `ExerciseType` case-insensitively. Slots with `exerciseId == DAILY_REST_EXERCISE_ID` get `isRest = true`.
  - A slot with an unknown type is skipped and logged with `println`, as in `GymMappers`.
  - Exercise mapping (`ExerciseDto.toEntity`, `ExerciseEntity.toDomain`) and `toExerciseType` are reused from `GymMappers.kt`.
- Unlike the gym flow there is no daily progress, so a refresh needs no progress handling.
- The remote source is still the APK's assets, so the daily flow works offline (verified in airplane mode). The "stored data on failure" path only matters once a real backend replaces the assets.
- **Animations outside the active routines:** `exercises.json` still names 12 animation files that don't exist (exercises 26 Tibialis Raise, 27 A-Crunches, 33 Defensive Slides, 35 Suicide Sprints, 36 Lateral Bounds, 37 Sprint-In-Place, 39 Free Throw Shoot, 41 14 Shoots, 42/43 lay-ups, 44 Box Jumps, 45 3 point shoots). None of them is used by `routines.json` or `routines_for_gym.jsonc`; only the unused `routines.jsonc` references them. Every file in `assets/animations/` is referenced by at least one exercise.

---

## 5. Screens

### 5.1 Home (`ui/home/`)

- **State** `HomeState`: `isLoading: Boolean = false`, `currentDay: Int? = null`, `week: CurrentWeekSummary? = null`; derived `weeklyMessage: WeeklyGoalMessage?` (`GoalMet`, `WorkoutsLeft(n)`; null without a goal or before loading).
- **Intents** `HomeIntent`: `OnStart`, `OnStartRoutineClicked`, `OnGymRoutinesClicked`, `OnProgramsClicked`, `OnHistoryClicked`, `OnWeeklyGoalClicked`, `OnLogActivityClicked`.
- **Effects** `HomeEffect`: `NavigateToDailyRoutineSelection`, `NavigateToGymRoutineSelection`, `NavigateToProgramList`, `NavigateToTrainingHistory`, `NavigateToWeeklyGoal`, `NavigateToLogActivity`.
- **ViewModel** `HomeViewModel(workoutRepository, observeCurrentWeek, authRepository, guestBanner, gymSessions)` (`OnStart` first closes inactive gym sessions, §20):
  - `init` observes `ObserveCurrentWeek` through `flatMapLatest` on a reload counter; every `OnStart` (sent each time Home is shown) increments it, so the week is recomputed with a fresh "today" (§14).
  - `OnStart` → sets `isLoading=true`, calls `loadCurrentDay()`, sets `isLoading=false` (both from the latest state, so a week or day that arrived meanwhile is not overwritten), then reloads the week.
  - `loadCurrentDay()` launches a **separate** `viewModelScope.launch` that reads `getRoutines()`, sets `currentDay = maxOfOrNull { it.routineId } ?: 0`; on exception falls back to `currentDay = 1`.
  - `OnStartRoutineClicked` → `sendEffect(NavigateToDailyRoutineSelection)`.
  - `OnGymRoutinesClicked` → `sendEffect(NavigateToGymRoutineSelection)`.
  - `OnProgramsClicked` → `sendEffect(NavigateToProgramList)`.
  - `OnHistoryClicked` → `sendEffect(NavigateToTrainingHistory)`.
  - `OnWeeklyGoalClicked` → `sendEffect(NavigateToWeeklyGoal)`.
  - `OnLogActivityClicked` → `sendEffect(NavigateToLogActivity)`.
- **Layout** (`HomeContent`, top to bottom): mascot (88dp), title and tagline → the compact "This week" card (`WeeklyGoalCompactCard`, §14.4; it opens the goal screen and contains the "Log activity" (§15) and "See history" text button, the way to the Training history, §13) → "Day X available" / "Día X disponible" (`home_day_available`, the day named through `WorkoutNameResolver`, §24) → Start button (loading spinner) → "Gym routines" `OutlinedButton` → Gym Routine Selection (§5.4) → "Programs" / "Programas" `OutlinedButton` → My programs (§12.5). There is no separate History button. The column scrolls when the content is taller than the screen (`BoxWithConstraints` + `verticalScroll` + `heightIn(min = maxHeight)`) and stays vertically centered otherwise.
- **Previews** (`HomePreviews.kt`): with a goal, without a goal and goal met, each at 360x640 and 411x891 dp in light and dark.
- **Caveats:** `loadCurrentDay()` is fire-and-forget (a nested `launch`), so `isLoading` is set to `false` *before* the day is actually loaded — the spinner is effectively never visible. `currentDay` is the **max routineId**, not a "next day to do" (see §7).

### 5.2 Routine Selection (`ui/dailyroutineselection/`)

- **State** `DailyRoutineSelectionState`: `isLoading: Boolean = false`, `routines: List<Routine> = emptyList()`, `error: String? = null`.
- **Intents** `DailyRoutineSelectionIntent`: `OnLoad`, `OnRoutineSelected(routineId)`, `OnBackPressed`.
- **Effects** `DailyRoutineSelectionEffect`: `NavigateToWorkout(routineId)`, `NavigateBack`.
- **ViewModel** `DailyRoutineSelectionViewModel(repository)`: `OnLoad` → `loadRoutines()` (sets loading, `getRoutines()`, sets list or error); `OnRoutineSelected` → `NavigateToWorkout`; `OnBackPressed` → `NavigateBack`.
- **Working features:** loading spinner, error text, empty state, `LazyColumn` of `RoutineCard` (title or `routineDay` + exercise count), back navigation, tap → workout.
- **Card title:** `RoutineCard` shows `WorkoutNameResolver.dailyName(routine).text()` (§24): "Day 3" / "Día 3", plus the routine's title in the viewer's language when the content has one (no routine has a title today).

### 5.3 Workout (`ui/workout/WorkoutScreen.kt` + `WorkoutViewModel.kt`)

- **State** `WorkoutState`:
  - `isLoading`, `routine: Routine?`, `currentExerciseIndex: Int`, `currentExercise: Exercise?`, `currentRoutineExercise: RoutineExercise?`, `completedReps: Int`, `isWorkoutComplete: Boolean`, `hasNavigatedToCompletion: Boolean`, `error: String?`, `timerDurationSeconds: Int`, `timerRemainingSeconds: Int`, `isTimerRunning: Boolean`, `isReadyCountdown: Boolean`, `readyCountdownSeconds: Int`.
  - Derived: `currentExerciseNumber`, `totalExercises`, `isLastExercise`, `isRepsExercise`, `isTimeExercise`.
- **Intents** `WorkoutIntent`: `OnLoad`, `OnNextExercise`, `OnCompleteReps`, `OnSkipExercise`, `OnFinishWorkout`, `OnBackPressed`, `OnTimerFinished`, `OnStartTimerWithReady`.
- **Effects** `WorkoutEffect`: `NavigateBack`, `NavigateToHome`, `NavigateToCompletion`, `PlayCompletionSound`, `TriggerHapticFeedback`.
- **ViewModel** `WorkoutViewModel(repository, routineId, trainingLogRecorder, timeSource)` — holds `timerJob` and `readyCountdownJob`; cancels both in `onCleared()`. Stores the `MonotonicTimeSource` time of the first `OnLoad`; `finishWorkout`, after sending `NavigateToCompletion`, records a DAILY entry in its own coroutine (refId `daily:<routineId>`, title `routineDay` + " · title" when present, duration since the first `OnLoad`), through `recordSafely` (§13).
  - `OnLoad` → `loadWorkout` → `getRoutine(routineId)` → `loadCurrentExercise` (sets current exercise, resets timer fields; if index out of range → complete). For a rest TIME slot (`isRest`) `loadCurrentExercise` then starts the timer directly. `loadWorkout` ignores `OnLoad` while a rest timer is running (the screen re-sends `OnLoad` after a rotation).
  - `OnNextExercise` / `OnSkipExercise` → `moveToNextExercise` (cancels timer+countdown, advances index, reloads; if past last → `finishWorkout`).
  - `OnCompleteReps` → sets `completedReps = amount`, then auto-advances from that updated state (finishing the workout keeps it).
  - `OnTimerFinished` → if TIME, emits `PlayCompletionSound` + `TriggerHapticFeedback`, then advances.
  - `OnStartTimerWithReady` → `startReadyCountdown` (5s), then `startTimer`. Does nothing until `currentExercise` has loaded. On a rest slot it skips the countdown and starts the timer only if none is running (a fallback: the UI never sends it for rest).
  - `OnFinishWorkout` → `finishWorkout` (guarded by `hasNavigatedToCompletion`), emits `NavigateToCompletion`.
  - `OnBackPressed` → cancels the timer and the ready countdown, then `NavigateBack`.
- **Working features:** exercise name + GIF (Coil `AsyncImage` of the model from `AnimationLocator`: bundled asset, else downloaded file), TIME countdown with "GET READY" pre-countdown, REPS completion, rest-exercise handling, progress "Exercise X of Y", completion sound + haptics, bottom-aligned buttons, scrollable content, in-screen "Workout Complete" content.
- **Caveats:**
  - The screen stays on while `WorkoutScreen` is visible (exercises, rests, Get Ready and the in-screen completion content): it calls the shared `KeepScreenOn()` (`ui/components/`). Leaving the screen (back, the completion route, home) releases it.
  - Non-rest TIME slots do **not** auto-start on load. `loadCurrentExercise` sets `isTimerRunning=false`; the user must tap "Get Ready" (`OnStartTimerWithReady`) to begin.
  - **Rest slots auto-start** on load with their `amount` in seconds and no Get Ready. The screen shows the countdown, "Timer running…" and "Skip Exercise" (the existing "timer running" branch of `TimeExerciseButtons`; no UI change was needed). At the end: `OnTimerFinished` → sound + haptic → next exercise, or the completion screen if the rest is the last slot. Skip, Back and `onCleared()` cancel it. Verified on the emulator (Day 1, exercise 5): the rest counted down by itself, kept counting across two rotations, then vibrated, played the tone and moved to exercise 6.
  - `isRestExercise` comes from `state.currentRoutineExercise.isRest` (`WorkoutButtons`); it hides "Get Ready" for a rest that is not running. The rest animation is the exercise's own `13_rest.gif` from the catalog.
  - **Buttons** (`WorkoutButtons.kt`, no fixed widths, same layout for REPS and TIME): primary (filled: Complete Reps / Get Ready / Next Exercise) + "Skip Exercise" (outlined) in a full-width `Row`, 12.dp apart, each `Modifier.weight(1f)`; a single skip button (Get Ready countdown, timer running, rest, reps completed) is full width. Every button is at least 48dp tall with 12dp horizontal content padding. Labels: `maxLines = 1`, centered; a label that does not fit shrinks by 10 % steps down to 11sp (hidden until settled) and is ellipsized only if it still does not fit, so long Spanish labels never wrap. Previews in `WorkoutPreviews.kt`: TIME idle, Get Ready, timer running and REPS, each in English and Spanish (`locale = "es"`) at 360dp.
  - `WorkoutScreen.kt` was split into `WorkoutScreen.kt` (entry point + `WorkoutContent`), `WorkoutContent.kt` (`ExerciseContentOnly`), `WorkoutButtons.kt` (`ExerciseBottomButtons` + `RepsExerciseButtons`/`TimeExerciseButtons`) and `WorkoutPreviews.kt` (mock data + previews). The unused `TimeExerciseContent` variant and the old painter-based `AnimationLoader.kt` were deleted.

### 5.4 Gym Routine Selection (`ui/gymroutineselection/`)

- **State** `GymRoutineSelectionState`: `isLoading = true` (only until the first emission), `routines: List<GymRoutine>`, `expandedRoutineIds: Set<Int>` (several cards can be open), `isRefreshing`, `errorMessage: String?` (only when a refresh failed and there is no data).
- **Intents**: `OnLoad`, `OnToggleExpanded(routineId)`, `OnStartRoutine(routineId)`, `OnRetry`, `OnBackPressed`. **Effects**: `NavigateToGymRoutine(routineId)`, `NavigateBack`, `ShowRefreshError`.
- **ViewModel** `GymRoutineSelectionViewModel(gymRoutineRepository)`: observes `observeGymRoutines()` exactly once (in `init`). `OnLoad` calls `refresh()` **only the first time** (the screen re-sends it after every configuration change); `OnRetry` always refreshes. Refresh failure with data → `ShowRefreshError` (snackbar) and the list stays; without data → `errorMessage` (Retry state).
- **UI:** TopAppBar (back) + thin `LinearProgressIndicator` while refreshing with data; loading / error (Retry) / empty-catalog states; `LazyColumn` of cards: routine name, a "See routine" toggle row (≥ 48dp, `Role.Button`, click label "Show/Hide exercises", `stateDescription` "Expanded/Collapsed"; the rotating chevron is decorative), `AnimatedVisibility` exercise rows (name fades out only when it overflows; right side = shared `exerciseSummary`), "This routine has no exercises yet." for an empty routine, full-width "Start routine" button. Stateless `GymRoutineSelectionContent`; previews in `GymRoutineSelectionPreviews.kt` (collapsed, expanded, long name, TIME, no exercises, loading, empty, error, dark theme).
- **Koin:** `viewModel { GymRoutineSelectionViewModel(get()) }`.

### 5.5 Gym Routine (`ui/gymroutine/`)

- **State** `GymRoutineState`: `isLoading: Boolean = true`, `routine: GymRoutine? = null`, `completedPositions: Set<Int>`, `isNotFound: Boolean = false`, `showResetConfirmation: Boolean = false`, `showRoutineCompleted: Boolean = false`. Derived: `validCompletedPositions` (completed ∩ routine positions), `completedCount`, `totalCount`, `isAllCompleted`, `canReset` (completedCount > 0).
- **Intents** `GymRoutineIntent`: `OnLoad`, `OnExerciseClicked(position)`, `OnResetClicked`, `OnConfirmReset`, `OnDismissReset`, `OnRoutineCompletedDismissed`, `OnBackPressed`.
- **Effects** `GymRoutineEffect`: `NavigateToExercise(routineId, position)`, `NavigateBack`.
- **ViewModel** `GymRoutineViewModel(routineId, gymRoutineRepository, gymProgressRepository, trainingLogRecorder)`:
  - `init` combines `observeGymRoutine(routineId)` + `observeCompletedPositions(routineId)` and observes them **exactly once per instance** (re-sending `OnLoad` does not re-subscribe), always updating from the latest state.
  - Routine null (also when it is removed from the catalog while the screen is open) → `isNotFound`, and any open reset / completed dialog is closed. Completed positions outside the routine are ignored when counting.
  - Non-empty routine with every position completed → `showRoutineCompleted = true`; the actual `resetRoutine(routineId)` runs only on `OnRoutineCompletedDismissed` (never automatically).
  - When `showRoutineCompleted` turns true, `GymSessionRepository.completeSession` records a GYM entry (refId `gym:<routineId>`, routine name, the session's first → last set), before any reset. It records only while the session exists and removes it in the same transaction, so re-sent `OnLoad`, rotations, later emissions and a dialog shown again after process death don't record again (§13, §20).
  - `OnResume` closes inactive gym sessions (§20) and emits `ShowSessionSaved(durationSeconds)` for each; a confirmed manual reset also discards the session.
  - `OnResetClicked` → confirmation only if `canReset`; `OnConfirmReset` → `resetRoutine` + hide; `OnDismissReset` → hide only.
  - `OnExerciseClicked` → `NavigateToExercise` for any position, including completed ones. `OnBackPressed` → `NavigateBack`.
- **Working features:** TopAppBar (back arrow, routine name, reset action disabled when `canReset` is false), "N of M exercises completed" (plurals) + `LinearProgressIndicator`, `LazyColumn` of clickable `Card(onClick)`s (name with "Unknown exercise" fallback, shared `exerciseSummary` e.g. "Sets: 4 · Reps: 10, 10, 10, 10" / "Sets: 2 · Time: 30s, 45s"; completed cards get `successContainer` background + a decorative `CheckCircle`, and every card exposes `stateDescription` "Completed" / "Not completed" for TalkBack), "This routine has no exercises yet." for an empty routine (the completed dialog never shows for it), reset confirmation dialog, routine-completed celebration dialog, loading and not-found states. Previews in `GymRoutinePreviews.kt` (incl. empty routine and dark theme).
- **Koin:** `viewModel { (routineId: Int) -> GymRoutineViewModel(routineId, get(), get(), get()) }`, obtained with `koinViewModel { parametersOf(routineId) }`.

### 5.6 Gym Exercise (`ui/gymexercise/`)

Guides the user through every set of one exercise slot (`position`) of a gym routine, then marks the slot completed.

- **Phases** `GymExercisePhase` (sealed; set indexes 0-based, shown 1-based):
  - `PerformingReps(setIndex)` → "Set 2 of 4", "10 reps"; button **Set done**.
  - `ReadyToStartTimed(setIndex)` → set duration (`mm:ss`); button **Start**.
  - `GetReady(setIndex, remainingSeconds)` → 5-second "Get ready" countdown before a TIME set; no button.
  - `PerformingTimed(setIndex, remainingSeconds, totalSeconds)` → set countdown + `LinearProgressIndicator`; no button.
  - `Resting(nextSetIndex, remainingSeconds, totalSeconds)` → "Rest", `mm:ss` inside a `CircularProgressIndicator` ring (remaining/total), "Next: set 3 of 4"; button **Skip rest**. Shows the rest animation.
- **Transitions:**
  - Finishing a set that is not the last → `Resting(next)` if `restSeconds > 0`, otherwise straight to the next set.
  - Rest ends or is skipped → next set (`PerformingReps` or `ReadyToStartTimed` by type).
  - Finishing the last set → no rest: `markCompleted(routineId, position)`, sound + vibration, `NavigateBack`. Guarded so it runs once.
  - Sound + vibration also at the natural end of a TIME set (not the last one; that set gets the completion feedback once) and at the natural end of a rest. Skipping a rest plays nothing.
- **State** `GymExerciseState`: `isLoading = true`, `routineName`, `exercise: GymRoutineExercise?`, `position`, `exerciseNumber` (1-based place in the routine's list, for "Exercise 2 of 11"; positions can have gaps when the mapper skips a slot), `totalExercises`, `phase: GymExercisePhase?` (null while loading / not found), `showExitConfirmation`, `isNotFound`. Derived: `completedSets` (the set index of the current phase, `nextSetIndex` while resting), `totalSets`.
- **Intents** `GymExerciseIntent`: `OnLoad`, `OnSetDone`, `OnStartTimedSet`, `OnSkipRest`, `OnBackPressed`, `OnConfirmExit`, `OnDismissExit`. Intents that don't match the current phase are ignored.
- **Effects** `GymExerciseEffect`: `NavigateBack`.
- **ViewModel** `GymExerciseViewModel(routineId, position, gymRoutineRepository, gymProgressRepository, workoutFeedback, timeSource)`:
  - `OnLoad` (first one only) starts observing `observeGymRoutine(routineId)` for the life of the screen. The first emission picks the slot whose `position` matches and starts set 1; later emissions only refresh the routine name / numbering and never restart the exercise. A missing routine, a missing position or an exercise with 0 sets — at load **or later** (removed from the catalog by a refresh) — cancels the timer and shows `isNotFound` (`phase = null`).
  - `markCompleted` failing (e.g. the routine was deleted between the last set and the write, so the progress FK fails) shows `isNotFound` instead of crashing; no feedback, no `NavigateBack`.
  - Timers: see §6. Calls `workoutFeedback` directly (domain interface), not through effects.
  - `OnBackPressed`: `completedSets > 0` → `showExitConfirmation = true`; otherwise cancels the timer and emits `NavigateBack`. Ignored once the exercise is being completed. `OnConfirmExit` cancels the timer and emits `NavigateBack` **without** `markCompleted`; `OnDismissExit` only hides the dialog (the rest timer keeps running behind it).
- **UI:** TopAppBar (back arrow → `OnBackPressed`, routine name); system back also sends `OnBackPressed` (`BackHandler`). "Exercise 2 of 11", exercise name ("Unknown exercise" fallback), large centered `ExerciseAnimation` (the exercise's animation, or `REST_ANIMATION` while resting), then the phase block and a fixed-height bottom slot for the primary button. The screen stays on while visible (the shared `KeepScreenOn()` from `ui/components/`). Exit `AlertDialog` "Leave exercise?" ("Progress: 2 of 4 sets completed…", plurals) explains the progress will not be saved. The exercise name falls back to "Unknown exercise" and the animation area stays empty when the exercise is missing from the catalog. Stateless `GymExerciseContent` + `MviComposable` wiring; previews in `GymExercisePreviews.kt` (reps set, timed ready, get ready, timed running, resting, last set, dark theme). Strings (`gym_exercise_*`, "reps" as a plurals resource) in `values` and `values-es`.
- **Koin:** `viewModel { (routineId: Int, position: Int) -> GymExerciseViewModel(routineId, position, get(), get(), get(), get()) }`, obtained with `koinViewModel { parametersOf(routineId, position) }`; `single<MonotonicTimeSource> { MonotonicTimeSource { SystemClock.elapsedRealtime() } }`.

### 5.7 Workout Completion (`ui/workout/WorkoutCompletionScreen.kt`)

- **No State / Intents / Effects / ViewModel** — it is a plain stateless composable: `WorkoutCompletionScreen(onNavigateToHome, modifier)`.
- **Working features:** "Workout Complete!" header, "Great job!" message, "Back to Home" button.
- **Caveat:** It is a separate navigation destination (`workout_completion`) and is **not** MVI-based. The Workout screen also renders its own in-screen `WorkoutCompleteContent` when `isWorkoutComplete` is true, so there are two completion presentations.

---

## 6. Timer, Sound, and Haptics

### Timer — lives in the **ViewModel** (`WorkoutViewModel`)
- `startTimer(durationSeconds)`: cancels any previous `timerJob` (never two timers), then launches a `viewModelScope` job; sets `isTimerRunning=true`, then loops `delay(1000)` decrementing `timerRemainingSeconds` until 0; on finish sets `isTimerRunning=false` and calls `handleIntent(OnTimerFinished)`.
- `startReadyCountdown(currentState)`: 5-second pre-countdown (`readyCountdownSeconds` 5→0), then calls `startTimer`. On a rest slot it is skipped and the timer starts directly, only if no timer is running.
- `cancelTimer()` / `cancelReadyCountdown()`: cancel the jobs and reset the related state fields. Both are called in `onCleared()`, on exercise change, and on finish.
- Non-rest TIME slots: triggered by the user tapping "Get Ready" (`OnStartTimerWithReady`); **not** auto-started on load.
- Rest slots (`isRest`): `loadCurrentExercise` calls `startTimer(amount)` as soon as the slot loads. A re-sent `OnLoad` during the rest is ignored, so the countdown continues. The end goes through `OnTimerFinished` (sound + haptic, then advance).

### Gym exercise timer — lives in the **ViewModel** (`GymExerciseViewModel`)
- One `timerJob` at a time. Every phase change goes through `setPhase()`, which cancels the current job first; the job is also cancelled on exit, on completion and in `onCleared()`.
- Countdowns (get ready, timed set, rest) store an **end timestamp** from the injected `MonotonicTimeSource` (`SystemClock.elapsedRealtime()` in the app) and recompute the remaining seconds from it on every tick, rounding up. The next tick is scheduled for when the displayed second changes (about once per second). If the app spends time in the background, the countdown jumps to the correct value on the next tick instead of drifting.
- Unlike the daily flow, the gym ViewModel calls `WorkoutFeedback` directly (no sound/haptic effects).

### Sound — behind the `WorkoutFeedback` interface
- `WorkoutFeedback` (`domain/feedback/`) declares `playCompletionSound()` and `vibrate()`. Implemented by `AndroidWorkoutFeedback` (`data/feedback/`), registered in Koin as a `single`; `WorkoutScreen` obtains it with `koinInject()`.
- `AndroidWorkoutFeedback.playCompletionSound()`: `ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(TONE_PROP_BEEP, 200)` on a background thread, with `release()` guaranteed in a `finally` block (the previous inline code created one per call and never released it — leaked). Wrapped in try/catch (silent failure).
- Triggered by the `WorkoutEffect.PlayCompletionSound` effect, which the ViewModel emits on `OnTimerFinished` for TIME exercises. The composable guards against double-play with a `hasPlayedSound` flag (reset per exercise via `LaunchedEffect(state.currentExerciseIndex)`).

### Haptics — behind the `WorkoutFeedback` interface
- `AndroidWorkoutFeedback.vibrate()`: resolves `Vibrator` (via `VibratorManager` on S+, deprecated `VIBRATOR_SERVICE` below), `VibrationEffect.createOneShot(200, DEFAULT_AMPLITUDE)` on O+, deprecated `vibrate(200)` below. All failures caught silently.
- Triggered by the `WorkoutEffect.TriggerHapticFeedback` effect (same timing as sound), guarded by a `hasTriggeredHaptic` flag.
- The `android.permission.VIBRATE` permission is declared in `AndroidManifest.xml`.

> Note: the *decision* to play sound/haptics is in the ViewModel (as effects); the *actual Android API calls* live behind the `WorkoutFeedback` interface (`AndroidWorkoutFeedback`). The `VIBRATE` permission **is declared** in `AndroidManifest.xml`.

---

## 7. Persistence

**The daily-routine flow stores its catalog, but no progress.** The daily catalog lives in Room (`daily_routines`, `daily_routine_exercises` and the shared `exercises` table, §4b/§4d). It is refreshed from the simulated remote source once per process and read only from Room. There is no DataStore, SharedPreferences, or file-based progress store anywhere in the codebase.

- **"Day X available"** (Home) is computed in `HomeViewModel.loadCurrentDay()` from the routine with the **highest routineId in the stored daily catalog** (filled from `routines.json`), not a "next day to do" or a saved progress marker: `currentDay` and `currentDayName` (`WorkoutNameResolver.dailyName`, rendered with `home_day_available`; it used to be a hardcoded English literal). On any load error it falls back to day `1`.
- Completing a workout does **not** advance a day or unlock the next routine; every launch starts from the same catalog. It **is** recorded in the training history (§13).
- **NOT IMPLEMENTED (daily flow):** progress tracking within a routine, or user settings persistence. (Weekly workout goals and streaks exist, based on the training history, §14.)
- **Gym Routines is the exception:** Room (`data/local/TioMinuterDatabase`, §4b) persists gym routines, their exercise slots and completion progress between sessions. The gym repositories read only from Room; the remote source fills it via `refresh()`.
- **Training history** (`training_log` in `TioMinuterDatabase`, §13): one entry per completed daily, gym or program workout.
- **Weekly goals** (`weekly_goal` in `TioMinuterDatabase`, §14): the goal history, one row per week in which it was set.
- **Programs** have their own Room database, `programs.db` (`ProgramDatabase`, §12.3): every created program, its generated workouts and exercise/workout completion persist between sessions. A running timer or stopwatch (treadmill, circuit, outdoor run, sets) is **not** saved; leaving the screen or process death loses it.

---

## 8. Navigation Graph

`AppNavigation(startDestination)` — `NavHost` whose start is `"welcome"` until a start choice is stored, else `"home"` (`MainActivity` reads `AuthPreferences` synchronously before `setContent`, so there is no flicker). Routes:

| Route | Destination | Arguments |
|-------|-------------|-----------|
| `welcome` | `WelcomeScreen` (first launch; navigating to `home` pops it) | — |
| `home` | `HomeScreen` | — |
| `account` | `AccountScreen` (from Home's top bar or the guest banner) | — |
| `daily_routine_selection` | `DailyRoutineSelectionScreen` | — |
| `workout/{routineId}` | `WorkoutScreen` | `routineId: Int` (parsed via `toIntOrNull()`) |
| `workout_completion` | `WorkoutCompletionScreen` | — |
| `training_history` | `TrainingHistoryScreen` | — |
| `weekly_goal` | `WeeklyGoalScreen` | — |
| `log_activity` | `LogActivityScreen` (new activity) | — |
| `log_activity/{entryId}` | `LogActivityScreen` (edit) | `entryId: Long` (`NavType.LongType`) |
| `gym_routine_selection` | `GymRoutineSelectionScreen` | — |
| `gym_routine/{routineId}` | `GymRoutineScreen` | `routineId: Int` (`navArgument` + `NavType.IntType`) |
| `gym_exercise/{routineId}/{position}` | `GymExerciseScreen` | `routineId: Int`, `position: Int` (typed) |
| `program_list` | `ProgramListScreen` | — |
| `program_create` | `ProgramCreateScreen` | — |
| `program_detail/{programId}` | `ProgramDetailScreen` | `programId: Long` (`NavType.LongType`) |
| `program_strength/{programId}/{week}/{day}` | `ProgramStrengthScreen` | `programId: Long`, `week: Int`, `day: Int` |
| `program_strength_exercise/{programId}/{week}/{day}/{position}` | `ProgramStrengthExerciseScreen` | as above + `position: Int` |
| `program_treadmill/{programId}/{week}/{day}` | `ProgramTreadmillScreen` | `programId: Long`, `week: Int`, `day: Int` |
| `program_outdoor_run/{programId}/{week}/{day}` | `ProgramOutdoorRunScreen` | same |
| `program_circuit/{programId}/{week}/{day}` | `ProgramCircuitScreen` | same |

Transitions:
- `home` → `daily_routine_selection` (on Start).
- `daily_routine_selection` → `workout/{routineId}` (on routine tap); back → `popBackStack()`.
- `workout/{routineId}` → `workout_completion` (on finish); back → `popBackStack()`; "home" → `navigate("home") { popUpTo("home") { inclusive = true } }`.
- `workout_completion` → `home` (same `popUpTo` reset).
- `home` → `training_history` ("See history" in the weekly goal card → `OnHistoryClicked` → `NavigateToTrainingHistory`); back → `popBackStack()`.
- `home` → `weekly_goal` (tapping the "This week" card or its "Set goal" button → `OnWeeklyGoalClicked` → `NavigateToWeeklyGoal`); Save or back → `popBackStack()`.
- `home` → `log_activity` ("Log activity" in the weekly goal card → `OnLogActivityClicked` → `NavigateToLogActivity`); `training_history` → `log_activity` (FAB) and `training_history` → `log_activity/{entryId}` (tap on an ACTIVITY entry); Save, Delete or back → `popBackStack()`.
- `home` → `gym_routine_selection` (secondary "Gym routines" button → `OnGymRoutinesClicked` → `NavigateToGymRoutineSelection`).
- `gym_routine_selection` → `gym_routine/{routineId}` ("Start routine" → `OnStartRoutine` → `NavigateToGymRoutine`); back → `popBackStack()`.
- `gym_routine/{routineId}` → `gym_exercise/{routineId}/{position}` (card click → `OnExerciseClicked` → `NavigateToExercise`); back → `popBackStack()`.
- `gym_exercise/{routineId}/{position}` → back (`NavigateBack` → `popBackStack()`): after the last set, on back with no sets done, or on confirmed exit.
- `home` → `program_list` ("Programs" button). `program_list` → `program_create` (FAB) or `program_detail/{id}` (card tap).
- `program_create` → `program_detail/{id}` with `popUpTo(program_create) { inclusive = true }`, so Back from the new program's detail returns to the list.
- `program_detail/{id}` → `program_strength` / `program_treadmill` / `program_outdoor_run` / `program_circuit` by the workout's kind. `program_strength` → `program_strength_exercise` (card tap). Every program screen goes back with `popBackStack()`.
- The program destinations are registered by `programNavGraph(navController)` (`ui/program/ProgramNavGraph.kt`); `AppNavigation` only calls it. Route constants and builders (`Routes.programDetail(id)`, `Routes.programStrength(id, week, day)`…) live in `Routes`.

> Note: `WorkoutScreen` also has an in-screen `WorkoutCompleteContent` shown when `isWorkoutComplete` is true, which calls `OnFinishWorkout` → `NavigateToCompletion` → the separate `workout_completion` route. Both paths exist.
>
> Note: all three gym destinations are MVI screens: §5.4, §5.5, §5.6.

---

## 9. Tests

Run with `./gradlew test` (unit tests, `testDebugUnitTest`). **Result: 927 tests, 0 failed, 0 skipped** (incl. the 5 parity golden checks, §26; the daily unlock added 41, §28). The suite must stay green; new code must not introduce failures. `WorkoutRepositoryImplTest` (6, 5 failing) and `JsonParsingIntegrationTest` (5, 4 failing) were **deleted** with the asset-reading repository; `DailyDataValidationTest`, `DailyMappersTest` and the instrumented `DailyRepositoryTest` cover what they checked.

| Test class | Tests | Skipped | Failed | Covers |
|------------|:-----:|:-------:|:------:|--------|
| `ExampleUnitTest` | 1 | 0 | 0 | Trivial `2+2=4` sanity check. |
| `domain.serialization.SerializationTest` | 7 | 0 | 0 | (De)serialization of `Exercise`, `Routine`, `RoutineExercise`, `ExerciseType` (incl. custom serializer). **Passing.** |
| `data.datasource.AssetDataSourceTest` | 2 | 0 | 0 | `loadFromAssets` with a mocked `Context`/`AssetManager` (success + error). **Passing.** |
| `ui.home.HomeViewModelTest` | 25 | 0 | 0 | Private fakes, `MainDispatcherRule`, fixed clock: initial state, gym and weekly-goal navigation effects; "This week" card: no goal (no message), workouts left, two workouts on one day count as two, below / exactly at the minimum and unknown duration, goal met with this week in the streak, exceeding the goal keeps the real count, late in the week still "N workouts left" (no unreachable note), past-week streak, a new workout updates the card, `OnStart` picks up a new week, `OnStart` keeps the week and loads the day, the day named in English and Spanish ("Day 2 available" / "Día 2 disponible"). **Passing.** Also: `OnStart` closes inactive gym sessions (§20). |
| `domain.traininglog.LocalDateProviderTest` | 10 | 0 | 0 | Same instant in UTC / Madrid / Bogotá / Kiritimati, local midnight edge, today and yesterday from a fixed clock, yesterday across a DST change and a year boundary, zone read on every call. Week helpers (Monday start, 7 dates, previous/next week) and the current week around Sunday midnight in UTC vs Madrid. Local times to instants and back in Madrid/UTC, start of day across a DST change. **Passing.** |
| `domain.traininglog.CalendarDatesTest` | 5 | 0 | 0 | `mondayOf` for every weekday, month/year/leap-day boundaries, a DST week still has 7 dates, `plusWeeks` from any day, an invalid date → `IllegalArgumentException`. **Passing.** |
| `domain.goal.WorkoutCountingTest` | 4 | 0 | 0 | The minimum is 600 s; below / exactly at / above it and 0; null duration counts; workouts per day with several entries in one day and short entries left out. **Passing.** |
| `domain.goal.WeeklyProgressCalculatorTest` | 18 | 0 | 0 | Goal history lookup (none, before the first, latest on or before in any order), goal changed mid-history, several workouts on one day, only the week's dates, exceeding the goal, Sunday→Monday boundary, device zone at recording time near midnight, no goal never met; streak: no goal, consecutive met weeks, two workouts in one day meet a goal of two, a gap, unfinished current week doesn't break and adds one once met, weeks before the first goal, a past week against its own higher goal; workouts needed. **Passing.** |
| `domain.traininglog.photo.PhotoSizingTest` | 8 | 0 | 0 | 1080/70 and 400/60 specs; longest side and aspect ratio; no upscaling; at least 1 px; sample size (largest power of two keeping the limit); the 8 EXIF orientations; quarter turns swap sides. **Passing.** |
| `domain.traininglog.photo.TrainingPhotoPathsTest` | 5 | 0 | 0 | `<uuid>-<version>.jpg` naming, derived `_thumb`, valid paths (no "..", subfolders, other dirs, thumbnails), orphan selection (unreferenced, replaced, `.tmp`, thumbnails without photo). **Passing.** |
| `data.traininglog.photo.TrainingPhotoFileStoreTest` | 8 | 0 | 0 | Temporary folder: paths, staging and capture in the cache, deleting photo + thumbnail, invalid paths never delete outside the directory, listing, age-based temporary cleanup, capture deletion. **Passing.** |
| `data.traininglog.photo.TrainingPhotoRepositoryImplTest` | 12 | 0 | 0 | Real file store + fake processor and training log: attach (uuid name, photoPath), replace deletes old files, remove, stage/commit/discard, camera file deleted (also on failure), processing failure keeps the entry and current photo and leaves no files, ENOSPC → NO_SPACE, unexpected → FAILED, commit to a missing entry, photoPath write failure removes the new files, cleanup of orphans and old temporaries only. **Passing.** |
| `domain.goal.ObserveCurrentWeekTest` | 3 | 0 | 0 | Combines goals and counted workouts from the entries, leaves out short entries, reads today on every collection. **Passing.** |
| `data.goal.WeeklyGoalRepositoryImplTest` | 3 | 0 | 0 | Fake DAO: the goal applies from this week's Monday, same week replaces and past weeks keep theirs, only 1..14 accepted. **Passing.** |
| `ui.weeklygoal.WeeklyGoalViewModelTest` | 8 | 0 | 0 | Goal in force preselected, default without a goal, + / − one at a time, the stepper stops at 1 and 14, save stores and navigates back, the limits can be saved, save failure shown, back without saving. **Passing.** |
| `domain.traininglog.TrainingLogRecorderTest` | 4 | 0 | 0 | `durationBetweenCompletions` (fewer than two → null), `recordSafely` passes values, swallows failures, propagates cancellation. `recordSafely` returns the new entry id, null on failure. **Passing.** |
| `data.traininglog.TrainingLogMappersTest` | 3 | 0 | 0 | Domain → entity (source by name, every field), round trip for every source with null optionals, unknown stored source skipped. **Passing.** |
| `data.traininglog.TrainingLogRepositoryImplTest` | 15 | 0 | 0 | Fake DAO: `record` stores uuid, local date (Madrid), timestamps; unknown sources skipped on read; delete; `addActivityEntry` stores codes, seconds, trimmed note and the local date of a backdated `completedAt`; OTHER keeps its name as title; an invalid activity is rejected and nothing stored; `updateActivityEntry` keeps uuid and createdAt and sets updatedAt; an older entry's own day stays allowed when editing; only ACTIVITY entries can be updated; `getEntry`. Photos: `record` returns the id; `setPhotoPath` stores the path, updates updatedAt, returns the previous one and throws for a missing entry; `getPhotoPaths`; deleting an entry deletes its photo files (none without photo); editing an activity keeps its photo. **Passing.** |
| `domain.traininglog.ActivityEntryRulesTest` | 10 | 0 | 0 | Valid entry, duration 1..600 (null, 0, 1, 600, 601), type and intensity required, OTHER name (blank, 40, 41, trimmed), note ≤ 200, now allowed but not the future, window start at midnight six days ago, window in another zone, stored title and note, codes round trip. **Passing.** |
| `domain.traininglog.TrainingSourceRenameTest` | 3 | 0 | 0 | Sources are DAILY, GYM, PROGRAM, ACTIVITY; ACTIVITY stored by name and an old FREE row skipped; no `src/main` Kotlin or XML file refers to FREE. **Passing.** |
| `domain.goal.ActivityWeeklyGoalTest` | 2 | 0 | 0 | Real repository (fake DAO) + `ObserveCurrentWeek`: activities of 10 and 90 minutes count, 9 doesn't; a backdated Sunday activity counts in the previous week (and its streak). **Passing.** |
| `ui.logactivity.LogActivityViewModelTest` | 23 | 0 | 0 | Real repository over a fake DAO: defaults (today, now, 7 day options), 18:00 for a past day unless a time was chosen, save stores and goes back, errors after Save then live, future time rejected, filtering and caps, under-10 hint, edit loads and keeps uuid/createdAt, older entry editable on its own day, automatic/missing ids not found, delete confirm/dismiss, no delete when creating, back with/without changes, undoing a change. Photo: a pick is staged at once (unsaved change) and committed on save with the new id; an unreadable pick is a message; picking again / removing discards the staged photo; `onCleared` (ViewModelStore) discards it; edit shows the stored photo, replacing commits, removing removes on save, untouched photo left alone; a photo failure on save keeps the entry and shows the error before going back. **Passing.** |
| `ui.traininglog.TrainingHistoryViewModelTest` | 28 | 0 | 0 | Loading, empty state, grouping by day newest first, Today/Yesterday/date labels from a fixed clock and zone, delete confirm / cancel / last entry, pending delete closed when the entry disappears, delete failure, back. Week headers: weeks start on Monday newest first, each week with the goal it had and every counted workout of a day (no goal before the first), short entries stay listed but aren't counted, a goal change updates the headers. Logged activities: tap opens edit only for ACTIVITY entries, the FAB logs a new one, row data (type, intensity, first note line), activities count in the week headers. Workout names (§24, real EN / ES strings): the gym catalog is observed (a rename shows at once); every source in the viewer's language whatever language recorded it (a Spanish-recorded program entry reads "Upper A" in English), one program lookup per entry; unknown routines, a bad refId, deleted programs and old daily data keep the stored title; an unreadable daily catalog still names "Día 3". Photos: attach for an automatic entry; logged activities and unknown ids are ignored; remove asks first (dismiss / confirm); an entry without photo has nothing to remove; failures are messages and keep the entry. **Passing.** |
| `ui.workout.WorkoutViewModelTrainingLogTest` | 8 | 0 | 0 | Virtual time: one DAILY entry with refId/title and the duration since the first `OnLoad` (REPS and natural timer end), skipping everything records, records once, re-sent `OnLoad` keeps the start, title with routine title, leaving early records nothing, a recording failure keeps the completion. **Passing.** |
| `data.mapper.GymMappersTest` | 9 | 0 | 0 | Gym mappers: position = index, sorting by position, `sets = reps.size`, unknown type skipped, missing exercise -> `exercise = null`, type-string mapping, Spanish names DTO → entity → domain (exercise and routine), missing Spanish names stay null. **Passing.** |
| `data.remote.AssetGymRemoteDataSourceTest` | 4 | 0 | 0 | Simulated gym endpoints (mocked `ContentSource`) decode the `.jsonc`/JSON content. **Passing.** |
| `data.content.ContentRulesTest` | 6 | 0 | 0 | Manifest rules: allowed paths (known JSON, `animations/<name>.gif`), rejected `../x`, `/abs`, `other/file.txt`, unknown JSON, nested or non-GIF animations; schemaVersion, contentVersion, size ≤ 5 MB, sha256 format, duplicates; `readAtMost`; sha256 digest. **Passing.** |
| `data.content.ContentSyncerTest` | 17 | 0 | 0 | Real bundle (`content/v1`), fake `ContentHttpClient`, temp folder; a new `ContentProcess` = restart (§17.6). **Passing.** |
| `data.remote.dto.GymDtoParsingTest` | 6 | 0 | 0 | DTO decoding (incl. `"set"`/`"rest"` aliases); exercise entry with and without `nameEs`; routines with and without `routineNameEs`. **Passing.** |
| `data.remote.GymDataValidationTest` | 7 | 0 | 0 | Validates the real gym assets (plain JVM, relative paths): unique routineIds, exerciseIds exist, sets/reps/rest/type, animation files exist, every routine has a non-blank `routineNameEs`, every exercise a routine uses has a non-blank `nameEs`, `exercises.json` Spanish names equal `program_exercises.json`'s for the same id (read as raw JSON). **Passing.** |
| `data.remote.AssetDailyRemoteDataSourceTest` | 4 | 0 | 0 | Simulated daily endpoints with a mocked `ContentSource`: parsed routines (with/without `title`) and exercises; read errors propagate. **Passing.** |
| `data.remote.dto.DailyDtoParsingTest` | 4 | 0 | 0 | Daily DTO decoding: routine with and without `title`, REPS and TIME slots in order, `type` kept as the raw string. **Passing.** |
| `data.remote.DailyDataValidationTest` | 9 | 0 | 0 | Validates the real daily assets (plain JVM, relative paths): unique routineIds, every routine has exercises, exerciseIds exist, type REPS/TIME and amount > 0, animation files exist, every exercise a routine uses has a non-blank `nameEs`, `routineDay` is "Day <routineId>" (the app names the day from the id, §24), a `titleEs` needs a `title` and neither is blank, every routine's conservative minimum active time is ≥ 10.5 min (`DailyDurationEstimate`, §0). **Passing.** |
| `data.mapper.DailyMappersTest` | 7 | 0 | 0 | Daily mappers: position = index, slots sorted by position, unknown type skipped, nullable title, same exercise twice in a routine, case-insensitive type, only `DAILY_REST_EXERCISE_ID` slots get `isRest = true`. **Passing.** |
| `ui.workout.WorkoutViewModelTest` | 8 | 0 | 0 | Private fake `WorkoutRepository`, `testutil.MainDispatcherRule`, virtual time, effects collected in `backgroundScope`: skip to the next exercise, skip the last one completes (`NavigateToCompletion`), `OnCompleteReps` advances with reps reset, the last one completes keeping `completedReps = target`, completion navigation sent once (further finish/skip/complete ignored), a TIME exercise ending naturally after Get Ready emits sound + haptic then completion, REPS completion emits no sound/haptic, `OnFinishWorkout` clears exercise and timer state. **Passing.** |
| `ui.workout.WorkoutViewModelRestTest` | 8 | 0 | 0 | With a private fake `WorkoutRepository`, `testutil.MainDispatcherRule` and virtual time; the rest slot uses a non-13 id with `isRest = true`: loading a rest starts its countdown immediately without Get Ready; it counts down, then emits `PlayCompletionSound` + `TriggerHapticFeedback` and moves on; a final rest finishes the workout; Skip and Back cancel it (no further ticks); a normal TIME slot does not auto-start and shows the 5 s Get Ready first; a REPS slot starts no timer; re-sending `OnLoad` (and `OnStartTimerWithReady`) during a rest does not create a second timer. **Passing.** |
| `ui.workout.WorkoutViewModelTimerTest` | 9 | 0 | 0 | Normal (non-rest) TIME slots, same setup as `WorkoutViewModelTest`: on load the duration is set but nothing runs (also after 60 s); a REPS slot starts no timer; Get Ready ticks 5 → 1, then the timer 3 → 1, and the natural end emits sound + haptic + completion; the natural end of a non-last slot sends `OnTimerFinished` and advances; the timer is cancelled by next/skip (also during Get Ready), finish, Back and `onCleared()` (ViewModel created through a `ViewModelStore` and cleared), with no later ticks or effects. Rest slots are in `WorkoutViewModelRestTest`. **Passing.** |
| `ui.components.LocalizedNameTest` | 4 | 0 | 0 | The display rule: Spanish → `nameEs`; other languages → `name`; Spanish with a missing or blank `nameEs` → `name`; every Spanish region ("es-MX", "es-ES") counts as Spanish. **Passing.** |
| `domain.traininglog.GymRefIdTest` | 2 | 0 | 0 | `gymRefId` / `gymRoutineIdOf` round trip; null for daily, program, empty and non-numeric refIds. **Passing.** |
| `ui.components.ExerciseSummaryTest` | 6 | 0 | 0 | Shared summary: REPS, TIME, single set (both types), uneven reps `[12,10,8]`, Spanish labels. **Passing.** |
| `ui.components.FormatSecondsTest` | 7 | 0 | 0 | `mm:ss` formatting. **Passing.** |
| `ui.gymroutineselection.GymRoutineSelectionViewModelTest` | 12 | 0 | 0 | Observation once, refresh once on repeated `OnLoad` (rotation), expanded cards survive rotation, multi-expand, start/back effects, refresh failure with/without data, retry, zero-exercise routine. **Passing.** Also: `OnResume` closes inactive gym sessions and announces each once (§20). |
| `ui.gymexercise.GymExerciseViewModelTest` | 28 | 0 | 0 | Gym Exercise screen with fake repositories, a fake `WorkoutFeedback`, `testutil.MainDispatcherRule` and a `MonotonicTimeSource` backed by the test scheduler's virtual time: REPS set → rest → next set, skip rest, rest 0, last set (`markCompleted(routineId, position)`, feedback, `NavigateBack`, no rest, once only), TIME get ready → countdown → feedback → rest, last TIME set, rest-end feedback, countdown from the time source (simulated background), back / confirm / dismiss, single timer after skipping rest, loading and not-found; edge cases: routine or slot removed while open (not found, timer stopped), `markCompleted` failure, routine update keeps the exercise, exercise missing from the catalog, rest countdown unchanged after a rotation's `OnLoad`; the state carries the routine's Spanish name (`routineNameEs`), also after an update. **Passing.** Also: every set noted in the gym session, the last one before the marker, a late set saves the abandoned session (§20). |
| `data.program.remote.ProgramCatalogValidationTest` | 12 | 0 | 0 | Real `program_exercises.json` (plain JVM, relative paths): unique ids, rest/no-animation exercises left out, `duplicateOf` points to an existing canonical entry, valid enum values, canonical entries have a location and a muscle, Spanish names, every animation file exists (ids 100–127 included) except the `PENDING_ANIMATIONS` (now empty), pending animations are used by an entry and still missing (fails once a GIF arrives, so it gets removed from the list), animations of ids ≥ 121 are named `<id>_<snake_case>.gif`; every entry has a boolean `perSide` (read from the raw JSON, since the DTO defaults it), exactly the `PER_SIDE_IDS` are per side, duplicates match their canonical entry. **Passing.** |
| `data.program.remote.ProgramTemplatesValidationTest` | 13 | 0 | 0 | Real `program_templates.json` against the real catalog: a split exists for every goal × 2–5 days × focus (exactly one each), BALANCED and focused splits match the requested layouts, every goal × days × location × level × focus × impact has a split whose patterns all have an eligible candidate, focused splits keep the balanced cardio days, CORE days end with two required CORE holds and contain an anti-rotation pattern, referenced strength days exist, candidates exist/are canonical/train the pattern's muscle, every slot pattern has an eligible candidate for HOME/GYM × normal/low impact × every level (uses the generator's `isEligible`), home circuits have enough exercises (with `isCircuitEligible`) and no per-side candidate, prescriptions and cardio templates for every goal/level/location. **Passing.** |
| `domain.program.ProgramRulesTest` | 7 | 0 | 0 | `computeBmi`, low impact at BMI 30 / age 60, senior rule, `isEligible` (duplicates, home equipment, location, HIGH impact, exercise 87). **Passing.** |
| `domain.program.ProgramGeneratorTest` | 29 | 0 | 0 | `ProgramGenerator` with the real assets: weeks/workouts for every goal × days × location × level (4–12 weeks), session size, splits, cardio days, no exercise twice in a workout, no duplicates/out-of-location exercises, A/B/C vary, beginner never gets 87 (advanced gym can, low impact cannot), prescriptions per goal/level, TIME holds, +15 s rest at 60+, rep/set progression and deload weeks, sets 2–5, hold progression, outdoor run +10 %/week and deload −30 %, treadmill intervals per week, low impact (BMI and age) removes HIGH impact and switches cardio, gym = treadmill and home alternates run/circuit, slots carry the catalog's `perSide` and it changes nothing else (sets, reps, rest), home circuits never use per-side exercises (also when a candidate is marked per side), determinism, a pattern without candidates is skipped. **Passing.** |
| `domain.program.ProgramFocusTest` | 8 | 0 | 0 | Focus checks for every goal × days × location × level, normal and low impact (age 65): LOWER_BODY ≥ 60 % of each week's strength slots on QUADS/HAMSTRINGS/GLUTES/CALVES, UPPER_BODY ≥ 60 % on CHEST/BACK/SHOULDERS/BICEPS/TRICEPS, CORE ≥ 2 CORE slots per strength workout ending with two TIME holds and the same session size as BALANCED; focused workouts never repeat an exercise or skip a pattern; cardio identical to BALANCED; same exercise → same sets/reps/rest/perSide whatever the focus; default focus is BALANCED. **Passing.** |
| `domain.program.ProgramBalancedRegressionTest` | 2 | 0 | 0 | BALANCED output equals the golden files captured before the focus feature (`src/test/resources/program_golden/<goal>_<location>.json`: 144 profiles = every goal × location × days × level × {normal, low impact by BMI, senior}, 4/6/8/12 weeks in turn); the files hold exactly that profile list. **Passing.** |
| `data.program.mapper.ProgramEntityMappersTest` | 8 | 0 | 0 | Payload JSON round trip for every workout kind (incl. a per-side slot), a payload stored without `perSide` decodes as not per side, `kind` discriminator, unreadable payload → null, entity numbering by week/day, profile ↔ entity, focus round trip for every focus (stored by code), unknown focus reads as BALANCED. **Passing.** |
| `ui.program.common.ProgramFormatTest` | 7 | 0 | 0 | `formatPercent`: 0/3 "0.0", 1/3 "33.3", 2/3 "66.7", 3/3 "100.0", Spanish "33,3", total 0; one-decimal formatting; `programExerciseSummary`, also per side in English ("Sets: 3 · Time: 40s, 40s, 40s per side") and Spanish ("… por lado"). **Passing.** |
| `ui.program.list.ProgramListViewModelTest` | 6 | 0 | 0 | Loading, empty state, cards in order, delete confirm / cancel, navigation effects. **Passing.** |
| `ui.program.create.ProgramCreateViewModelTest` | 13 | 0 | 0 | Defaults (focus BALANCED), required/out-of-range errors, input filtering and decimal comma, BMI, low-impact note (BMI and age), underweight warning, ranges for weeks/days, focus selection (state and profile), preview generated with the selected focus (changes when the focus changes), preview, create (with the focus) → `NavigateToDetail`, create failure. **Passing.** |
| `ui.program.detail.ProgramDetailViewModelTest` | 6 | 0 | 0 | Weeks shown, week percentages with one decimal, deload flag, completion updates, navigation by workout kind, deleted program → not found. **Passing.** |
| `ui.program.strength.ProgramStrengthViewModelTest` | 11 | 0 | 0 | Progress, "Workout completed!" when the last exercise completes during the visit (not when opening a completed one), reset confirm/dismiss, navigation, not found. Completion photo: an entry recorded before the visit is not offered; the dialog attaches to the entry just recorded; failure is a message and the dialog stays; nothing before the entry is known. **Passing.** |
| `ui.program.strengthexercise.ProgramStrengthExerciseViewModelTest` | 18 | 0 | 0 | Virtual time: reps → rest → next set with feedback, skip rest, rest 0, last set marks the slot (feedback once, back, no rest), TIME get ready → countdown → rest, last TIME set, exit confirmation, missing slot, workout removed while open, marking failure; per side: TIME left → switch (feedback) → right → rest, total time left + 5 s + right, last per-side set completes after the right side, back during the switch stops the timer, REPS per side is one tap, non-per-side TIME has no side. **Passing.** |
| `ui.program.strengthexercise.ProgramPerSideTextTest` | 4 | 0 | 0 | `repsPlural` picks `program_reps_per_side` for per-side slots; reads the real `strings.xml` files: "10 reps per side" / "1 rep per side", "10 repeticiones por lado" / "1 repetición por lado" (with `many`), side and switch labels in both languages. **Passing.** |
| `ui.program.treadmill.ProgramTreadmillViewModelTest` | 11 | 0 | 0 | Segments and total remaining, progression with feedback, pause/resume, skip (also while paused), completion at the end and by skipping, exit confirmation, wrong kind → not found. The completion passes the time since Start (780 s). Completion photo on the finished view (attached to this run's entry, not an earlier one); failure is a message. **Passing.** |
| `ui.program.outdoorrun.ProgramOutdoorRunViewModelTest` | 7 | 0 | 0 | Target distance, stopwatch start/pause/resume, background time counted, finish confirmation → completed, dismiss, exit confirmation, brisk walk flag. The completion passes the stopwatch time (600 s). **Passing.** |
| `ui.program.circuit.ProgramCircuitViewModelTest` | 7 | 0 | 0 | Work → rest (next exercise) → work, rest between rounds, completion after the last round (feedback count), skip, exit confirmation, wrong kind → not found. The completion passes the time since Start (300 s). **Passing.** |
| `ui.gymroutine.GymRoutineViewModelTest` | 29 | 0 | 0 | Gym Routine screen: state reflection, single-shot observation, out-of-routine positions ignored, celebration + dismiss-only reset, manual reset confirmation, navigation effects, not-found, routine removed while open, zero exercises (no completed dialog), state kept across a rotation's `OnLoad`.; training history: one GYM entry with the duration between completions (null with one), not again after a re-sent `OnLoad` or later emissions, a new entry after dismiss and a new completion, nothing for an empty routine or a partial completion, a recording failure keeps the dialog and the reset. Completion photo: the dialog knows the recorded entry; attach; a second photo replaces; failure is a message and keeps the completion; no entry (recording failed) → nothing to add; a new completion starts without the previous photo. **Passing.** Recording now goes through the gym session (§20): no second entry without a session, reset discards, `OnResume` closes and announces. |
| `domain.gymsession.GymSessionTrackerTest` | 19 | 0 | 0 | Gym session with a fake `WallClock` and an in-memory store: inactivity close at 2 h (2 h 39 / 2 h 41 / exactly 2 h), real duration, a late set closes and restarts, 0-s single set, clock moved back, double and concurrent checks, rollback, `fromCompletionTimes` (§20). |
| `ui.components.GymSessionSavedMessageTest` | 2 | 0 | 0 | "1 h 05 min" / "40 min", at least 1 min like the history (§20). |

**Instrumented tests (`connectedDebugAndroidTest`, run only on an emulator with `ANDROID_SERIAL` set — the task uninstalls the app and deletes its data on every connected device): 108 tests (105 run, `FirestoreSyncEmulatorTest` and `GroupsEmulatorTest` (2) skipped unless run by `scripts/test_sync_emulator.sh` / `scripts/test_groups_emulator.sh`), 0 failures.** (`LocalUserDataWiperTest` added with the accounts, §18.)
- `ExampleInstrumentedTest` (1): package name.
- `data.local.GymDaoTest` (8): in-memory Room — replaceCatalog semantics, reps converter round-trip, progress mark/observe/clear, ExerciseDao upsert/observe/getByIds.
- `data.local.DailyDaoTest` (8): in-memory Room — daily replaceCatalog insert/update/remove (cascade), empty catalog, repeated exercise in one routine, `countRoutines`, unknown routine, gym tables untouched.
- `data.local.MigrationTest` (9): 1 → 2, 2 → 3, 1 → 3, 3 → 4 (training_log entries and gym progress survive, `weekly_goal` empty), 1 → 4, 4 → 5 (goal values survive the `daysPerWeek` → `workoutsPerWeek` rename, training_log survives), 1 → 5, 5 → 6 (exercises, gym routines, slots, progress, daily routines and goals survive; `nameEs` / `routineNameEs` are null and can then be written), 1 → 6; each validated against the exported schema.
- `data.local.TrainingLogDaoTest` (7): insert/fields, newest first, inclusive date range, `getById`, `update`, delete, unique uuid.
- `data.local.WeeklyGoalDaoTest` (3): empty, ordered by week, same-week upsert replaces.
- `data.goal.WeeklyProgressFlowTest` (2): real in-memory database; the current-week Flow emits when a workout is recorded (two the same day count as two, a 5-minute one doesn't; the goal becomes met with the streak) and when the goal changes.
- `data.repository.GymRepositoriesTest` (7): offline-first repositories with a fake `GymRemoteDataSource` — a refresh after one without Spanish names fills `routineNameEs` / `nameEs` and keeps the routine's progress (slots unchanged), refresh fills the DB and emits mapped routines in order, refresh failure keeps previous data, progress kept when a routine is unchanged / cleared when its content changed, routine removed remotely disappears with its progress, and mark/observe/reset progress.
- `data.repository.DailyRepositoryTest` (9): offline-first `WorkoutRepositoryImpl` with an in-memory database and a fake `DailyRemoteDataSource`:
  - The first read refreshes and returns mapped routines in order (the rest slot mapped with `isRest = true`); later reads don't call the remote.
  - Concurrent first reads trigger a single refresh.
  - When the refresh fails, stored data is served; with an empty database the read throws, and the next read retries.
  - A failed explicit refresh leaves the database untouched.
  - `getRoutine`/`getExercise` return the item or null.
  - A refresh that removes a routine removes its slots.
  - Daily and gym refreshes share the `exercises` table without breaking each other.
- `data.program.ProgramDaoTest` (6): in-memory `ProgramDatabase` — insert/observe a program with workouts in order, summaries with counts newest first, delete cascades to workouts and progress, slot progress per workout and reset, workout completion upsert, progress for a missing workout rejected (FK).
- `data.program.ProgramMigrationTest` (2): `ProgramDatabase` 1 → 2 validated against `2.json` — a v1 program with two workouts, slot progress and a workout completion survives unchanged and gets `focus` 'BALANCED'; the same v1 file opened through Room (AutoMigration) reads the program as BALANCED with its payload and progress.
- `data.traininglog.photo.AndroidTrainingPhotoProcessorTest` (4): a 3000×2000 JPEG with GPS, camera and date EXIF → photo 1080×720 and thumbnail 400×267, no EXIF block and no location in either; orientation 6 → 720×1080 with the pixels rotated (red left half on top) and no orientation tag; 600×400 is not upscaled; a text file is UNREADABLE_IMAGE and leaves no output.
- `data.traininglog.photo.TrainingPhotoLifecycleTest` (5): in-memory Room + real processor + files in a test folder: attach stores both files and the path; replace deletes the old files; remove clears the path and deletes; deleting the entry deletes its files; cleanup keeps referenced photos and deletes orphans.
- `data.traininglog.photo.TrainingPhotoFileProviderTest` (2): the capture file gets a writable `content://<app>.trainingphotos` uri; stored photos can't be shared through the FileProvider.
- `data.program.ProgramRepositoryTest` (15): each completion exposes its training-history entry (`observeCompletionEntryId`; null when recording fails); `ProgramRepositoryImpl` with an in-memory database and the real program assets — create stores every week/workout (deload week 4 of 6), create stores the focus (LOWER_BODY → Lower A / Upper / Lower B, equal to the preview), catalog and templates loaded once, stored program unchanged when templates change, several programs and cascade delete, completing every slot completes the workout, reset keeps the completion, cardio completed explicitly, missing program/workout → null, Spanish names and animations; with a fake `TrainingLogRecorder`: strength auto-completion records once with the slot duration (not again when re-marking a slot), reset + redo records again, every cardio completion records with the screen duration, a recording failure doesn't fail the completion.

> There are **no** Compose UI tests and **no** Robolectric dependency. `androidx.test:core` and `androidx.test.ext:junit` are still declared as `testImplementation`, but no unit test uses them since `JsonParsingIntegrationTest` was deleted.

---

## 10. Tech Debt and Risks

**Build / dependencies**
- ~~The Compose BOM is 2024.09.00 (Compose 1.7), but the debug-only `ui-tooling` / `ui-test-manifest` dependencies bring newer AndroidX artifacts whose runtime-only constraints raise the **debug runtime** classpath to Compose UI 1.11 / foundation 1.10 (release stays on 1.7.2). Debug code used to be compiled against 1.7 and run on 1.10/1.11, which crashed with `NoSuchMethodError` in `FlowRow` (Home card, log-activity screen). Fixed in `app/build.gradle.kts`: in `afterEvaluate`, every non-androidTest `*CompileClasspath` calls `shouldResolveConsistentlyWith` on its `*RuntimeClasspath`, so debug compiles against 1.10/1.11 and release against 1.7.2. Debug and release therefore run different Compose versions; raising the BOM to match the tooling would remove that gap (not done). The newer compile classpath surfaces two existing deprecation warnings (`LocalLifecycleOwner` in `MviComposable`, `Icons.Filled.ArrowBack` in the daily screens).~~ **Resolved:** the BOM was raised to 2026.09.00 (Compose UI 1.12.1, Material3 1.4.0), so compile and runtime classpaths agree on their own and the `afterEvaluate` `shouldResolveConsistentlyWith` workaround was removed. `material-icons-core` is now a direct dependency (material3 no longer pulls it transitively), and the `LocalLifecycleOwner` / `ArrowBack` deprecations were fixed (`androidx.lifecycle.compose.LocalLifecycleOwner`, `Icons.AutoMirrored.Filled.ArrowBack`).

**Gym assets**
- ~~Gym routine and exercise names (`routines_for_gym.jsonc`, `exercises.json`) are English only.~~ **Resolved:** both catalogs have Spanish names and the screens pick them with `localizedName` (§4).
- Copied from `program_exercises.json`, eight Spanish exercise names use a participle about the user's position ("colgado", "tumbado", "sentado": ids 28, 31, 50, 51, 58, 59, 86, 91), which the neutral-language rule avoids. They match the program catalog on purpose; fixing them means changing both catalogs together.
- Daily "Day X" titles (`routineDay`) are not localized.

**Training photos**
- Upload is a later phase: photos exist only on the device (and are excluded from backups), so uninstalling or changing phones loses them until the group sync uploads them.
- A program completion's entry id lives in `ProgramRepositoryImpl`'s memory: after process death the completion screen can't attach a photo, but the history can (long press → Add photo).
- The log-activity form keeps a staged photo only in the ViewModel: after process death it is gone (its file is removed by the hourly-age cleanup at the next start).

**Program assets**
- ~~The seven focus exercises (ids 121–127) have no GIF yet (`ProgramCatalogValidationTest.PENDING_ANIMATIONS`).~~ **Resolved:** their GIFs are in `content/v1/animations/` and `PENDING_ANIMATIONS` is empty.
- There is no bodyweight calf exercise, so no slot pattern targets CALVES (every pattern must have a home candidate).

**Oversized / duplicated files**
- ~~`WorkoutScreen.kt` is ~800 lines and mixes UI, sound, haptics, mock data, and 4 previews.~~ **Resolved:** the file was split into `WorkoutScreen.kt`, `WorkoutContent.kt`, `WorkoutButtons.kt` and `WorkoutPreviews.kt`; the unused `TimeExerciseContent` variant was deleted; sound/haptics moved behind the `WorkoutFeedback` interface (`AndroidWorkoutFeedback`), fixing the `ToneGenerator` leak.

**Side effects / architecture**
- `HomeViewModel.loadCurrentDay()` spawns a nested `viewModelScope.launch` inside `reduce()` (fire-and-forget), so `isLoading` is cleared before the data loads — the loading state is effectively dead.
- Two MVI wiring styles coexist: `HomeScreen` wires `state`/`CollectEffects`/`LaunchedEffect` manually; `DailyRoutineSelectionScreen`/`WorkoutScreen` use `MviComposable`. Inconsistent.
- `WorkoutCompletionScreen` is not MVI-based (no State/Intent/Effect/ViewModel), unlike the other screens.
- ~~Sound/haptic Android API calls live in the composable file rather than an injectable abstraction — hard to unit test.~~ **Resolved:** moved behind the injectable `WorkoutFeedback` interface (`AndroidWorkoutFeedback`, Koin `single`).

**Hardcoded values / strings**
- Rotating the screen during a normal (non-rest) TIME exercise stops its timer and resets it to the full duration, showing "Get Ready" again: the screen re-sends `OnLoad` and `loadWorkout` reloads the exercise. Verified on the emulator (Day 1, Jumping Jacks: 00:55 → 01:00 after rotating). Rest slots are protected by the `OnLoad` guard; non-rest slots were left unchanged on purpose.
- Hardcoded UI strings not in `strings.xml`: `"Day ${state.currentDay} Available"` (`HomeScreen`), `"Routine not found"` (`WorkoutViewModel` error), and the Home mascot's `contentDescription = "Uncle"` (decorative image; should be `null`). These are **not** localized. (The workout-screen strings `"Get Ready"`, `"GET READY"`, `"Prepare yourself!"` were extracted to `strings.xml` as `get_ready`/`get_ready_banner`/`prepare_yourself`.)
- ~~`coil-gif:2.5.0`, `androidx.test:core:1.5.0`, `androidx.test.ext:junit:1.1.5` are hardcoded in `app/build.gradle.kts` instead of the version catalog.~~ **Resolved:** all three moved to `gradle/libs.versions.toml` (`coil-gif` 2.6.0, `androidx.test:core` 1.7.0, `androidx.test.ext:junit` 1.3.0).

**Missing / risky**
- ~~`VIBRATE` permission is referenced (`@RequiresPermission`) but **NOT IMPLEMENTED** in `AndroidManifest.xml`.~~ **Resolved:** `<uses-permission android:name="android.permission.VIBRATE" />` is now declared in `AndroidManifest.xml`.
- **No persistence in the daily flow** (see §7) — "Day X" is just the max routineId. (Gym Routines persists via Room, §4b.)
- `AssetDataSource` is no longer used by app code nor registered in Koin (only `AssetDataSourceTest` uses it); the remote sources keep their `Asset*` names although they read through `ContentSource`.
- Content sync (§17): the remote manifest lists every file of `v1`; a file this app doesn't know rejects the whole manifest (by design), so a new kind of file must go to `content/v2/`. A new version is used only from the next launch. The sync runs once per process at start (no periodic sync). The `androidx.test:core`/`androidx.test.ext:junit` unit-test dependencies are unused too.
- `ui/example/` reference MVI files remain in the production source set.

**Resources / lint (outside the gym feature, not fixed)**
- `./gradlew lint`: 0 errors, 86 warnings (2 `SimilarGradleDependency` besides the list below; 85 before the daily unlock, which added none in its code — the extra one is a library-update notice), none in gym, `data/` (`data/content` included), program or photo code (the photo feature added only a `GradleDependency` notice for `androidx.exifinterface` 1.3.7, kept to match Coil's). Remaining: 50 `UnusedResources` (41 legacy strings such as `gym_routines`, `start_gym_routine`, `reps_gym`, `go_to_gym`, `week`, `set_x_of_y`…, 8 template colors, the `uncle` mipmap), 17 `NewerVersionAvailable`/`GradleDependency`/`AndroidGradlePluginVersion` (library and AGP updates), 5 `PluralsCandidate` in unused legacy strings, launcher-icon warnings (`IconLauncherShape` ×5, `IconDipSize` ×2, `IconLocation`, `IconDuplicates`), `RedundantLabel` (manifest). Deleting the exercise-name strings removed 95 `UnusedResources` warnings (145 → 50).
- Spanish strings identical to English (untranslated), all outside the gym screens: `week`, `set`, `set_completed`, `set_x_of_y`, `reps_gym`, `reps_format`, `error`, routine-category names. Both files define the same keys.

**Program feature (known limitations)**
- No GPS: the outdoor run only measures time; the distance is the target, not a measurement.
- Timers/stopwatches are kept only in the ViewModel: leaving the screen or process death loses them (completion is stored only when a workout or exercise ends).
- Resetting a strength workout clears its exercises but keeps the workout's completion (a completed workout can be done again).
- No Compose UI tests; the screens were checked manually on an emulator (create, preview, strength exercise, brisk walk, circuit, treadmill, delete, Spanish).
- Exercise names are chosen from the locale's language at display time (`nameEs` for Spanish); the program stores only exercise ids.

**Gym Routines (known limitations, not fixed)**
- If a refresh changes the content of the slot being performed (same position, different reps/type), `GymExerciseScreen` keeps the version it loaded; only removal is handled (not-found).
- `observeGymRoutine` reads the exercise catalog as a one-shot snapshot, so a change to exercises alone (without the routine rows changing) is not re-emitted. Today `refresh()` always rewrites both in one transaction.
- `GymMappers` logs skipped slots with `println` (kept JVM-testable; not visible in Logcat).
- No Compose UI / screenshot tests; accessibility (touch targets, `stateDescription`, content descriptions) was verified by code review, not with TalkBack on a device.

---

## 11. Git Status

**The project is a git repository**, hosted on GitHub at `https://github.com/javargasp26/TioMinuterMVI.git` (remote `origin`), branch `main`.

---

## 12. Program feature (isolated)

Multi-week training programs generated from the user's profile and a **focus** (BALANCED, LOWER_BODY, UPPER_BODY, CORE). **Isolated** from the rest of the app: new code only under `data/program/`, `domain/program/`, `ui/program/` and `di/ProgramModule.kt`; its own database, Koin module and assets. It uses, read-only, `ui/components` (`ExerciseAnimation`, `REST_ANIMATION`, `formatSeconds`, `TimerText`), `ui/theme` (success colors), `ui/base`, `WorkoutFeedback`, `WallClock`, `MonotonicTimeSource`, the core content read path `ContentSource` (§17) and `TrainingLogRecorder` (the only dependency on the training history, §13). Edits outside these packages: the Home "Programs" entry point, the program routes in `Routes`/`AppNavigation`, `programModule` in `TioMinuterApplication`, and the `program_*` strings.

### 12.1 Exercise catalog (`assets/program_exercises.json`)
- 110 entries derived from `exercises.json` (which is unchanged): every exercise with an existing animation, without rest (13) and without 26, 27, 33, 35–37, 39, 41–45; plus 21 new exercises (ids 100–120) and 7 home-friendly lower-body exercises for the focus (ids 121–127).
- Fields: `exerciseId`, `name`, `nameEs`, `animation`, `primaryMuscles`, `equipment`, `mechanic`, `impact`, `locations`, `perSide` (boolean, present in every entry; `false` in the DTO/domain when absent), `duplicateOf`. Enums are in `domain/program/model/ProgramEnums.kt`.
- **Duplicates** (`duplicateOf` → canonical): 71→18, 72→29, 82→65, 14→65, 67→32, 46→16, 88→16, 89→61, 91→62, 73→68, 75→70, 94→64, 48→30, 85→63, 59→7, 60→8, 25→50, 51→50. 14/65, 63/85, 7/59 and 8/60 were confirmed by looking at the GIFs; `25_calf_raises.gif` shows a seated calf machine and is byte-identical to `51_seated_tibialis_raise.gif`, so 25 and 51 point to 50 (Seated Calf Raise).
- **Per side** (unilateral, one side at a time; every set covers both sides): 9 cross crunches, 20 lunges (split stance), 49 standing leg curl, 70 dumbbell row (single arm) and its duplicate 75, 80 dumbbell preacher curl (single arm), 112 Bulgarian split squat, 114 step-up, 120 side plank, 123 single-leg glute bridge, 124 donkey kick, 125 fire hydrant, 126 side-lying leg raise, 127 lateral lunge. Checked on the GIFs; alternating moves (6 bicycle crunches, 119 dead bug) and two-arm moves are not per side.
- **HIGH impact:** 1 jumping jacks, 5 mountain climbers, 12 burpees, 21 jump rope, 23 skipping, 100 outdoor run, 103 treadmill run.
- **New exercises (GIFs in `content/v1/animations/`):** `100_outdoor_run.gif`, `101_outdoor_brisk_walk.gif`, `102_treadmill_walk.gif`, `103_treadmill_run.gif`, `104_stationary_bike.gif`, `105_march_in_place.gif`, `106_superman.gif`, `107_inverted_row.gif`, `108_reverse_snow_angel.gif`, `109_incline_push_up.gif`, `110_decline_push_up.gif`, `111_pike_push_up.gif`, `112_bulgarian_split_squat.gif`, `113_goblet_squat.gif`, `114_step_up.gif`, `115_wall_sit.gif`, `116_barbell_bench_press.gif`, `117_romanian_deadlift.gif`, `118_cable_fly.gif`, `119_dead_bug.gif`, `120_side_plank.gif`. A seated cable row was not added: 69 Low Pulley Row is that exercise.
- **Focus exercises (ids 121–127):** all BODYWEIGHT, LOW impact, HOME and GYM. 121 Sumo Squat / Sentadilla sumo (QUADS, GLUTES; COMPOUND), 122 Curtsy Lunge / Zancada cruzada (QUADS, GLUTES; COMPOUND; alternating legs, so not per side), 123 Single-Leg Glute Bridge / Puente de glúteos a una pierna (GLUTES, HAMSTRINGS; ISOLATION), 124 Donkey Kick / Patada de glúteo en cuadrupedia (GLUTES; ISOLATION), 125 Fire Hydrant / Abducción de cadera en cuadrupedia (GLUTES; ISOLATION), 126 Side-Lying Leg Raise / Elevación lateral de pierna de costado (GLUTES; ISOLATION), 127 Lateral Lunge / Zancada lateral (QUADS, GLUTES; COMPOUND). Animations `121_sumo_squat.gif`, `122_curtsy_lunge.gif`, `123_single_leg_glute_bridge.gif`, `124_donkey_kick.gif`, `125_fire_hydrant.gif`, `126_side_lying_leg_raise.gif`, `127_lateral_lunge.gif` are in `assets/animations/` (they were in `PENDING_ANIMATIONS` until delivered). They are not in the home circuit lists (BALANCED output must not change).
- Equipment notes: dips (63) use `PULL_UP_BAR` (bar station, gym only); calves have only machine exercises, so no slot pattern targets calves.

### 12.2 Templates (`assets/program_templates.json`) and generator
- Read by `AssetProgramRemoteDataSource` (`ProgramRemoteDataSource`: `fetchExercises()`, `fetchTemplates()`) through the core `ContentSource` (§17), decoded with its own lenient `programJson` (`EXERCISES_CONTENT`, `TEMPLATES_CONTENT`, also registered in `programModule` so the sync checks program downloads with them) on `Dispatchers.IO`. DTOs keep enum values as strings; `ProgramMappers` converts them (catalog entries with an unknown value are skipped and logged; invalid templates throw).
- **Splits** are picked by goal, days per week and focus (`ProgramTemplates.split(goal, days, focus)`; a split without `focus` in the JSON is BALANCED). Exactly one split per goal × 2–5 days × focus.
  - **BALANCED** (unchanged since before the focus): GAIN 2 = Full body A/B, 3 = A/B/C, 4 = Upper A / Lower A / Upper B / Lower B, 5 = Push / Pull / Legs / Upper A / Lower A. LOSE 2 = A/B, 3 = A, Cardio, B; 4 = A, Cardio, B, C; 5 = A, Cardio, B, Cardio, C.
  - **LOWER_BODY:** GAIN 2 = Full body A/B with extra lower-body patterns (`FULL_BODY_LOWER_A/B`), 3 = Lower A / Upper / Lower B, 4 = Lower A / Upper / Lower B / Full body, 5 = Lower A / Upper A / Lower B / Upper B / Lower C. LOSE keeps its cardio days: 2 = Full body A/B, 3 = A, Cardio, B (the lower full-body days); 4 = Lower A, Cardio, Upper, Lower B; 5 = Lower A, Cardio, Upper, Cardio, Lower B. Lower A = `LOWER_GLUTE` (hip thrust/bridge, RDL/single-leg bridge, Bulgarian split squat/step-up, abductor/fire hydrant, sumo squat, leg press/lateral lunge, donkey kick, then a plank), Lower B = `LOWER_QUAD` (squat, lunge/curtsy lunge, leg press/bench squat, leg curl/bridge, step-up, wall sit hold, abductor/side-lying leg raise, then a side plank), Lower C = `LOWER_POSTERIOR`; Upper = `UPPER` (same patterns as Upper A); Upper A/B in the 5-day split are the BALANCED days.
  - **UPPER_BODY** (the mirror): GAIN 2 = `FULL_BODY_UPPER_A/B`, 3 = Upper A / Lower / Upper B, 4 = + Full body, 5 = Upper A / Lower A / Upper B / Lower B / Upper C. LOSE 2/3 = the upper full-body days; 4 = Upper A, Cardio, Lower, Upper B; 5 = Upper A, Cardio, Lower, Cardio, Upper B. Upper A and the 5-day Lower A/B are the BALANCED days; Upper B = `UPPER_FOCUS_B`, Upper C = `UPPER_FOCUS_C`, Lower = `LOWER` (same patterns as Lower A).
  - **CORE:** the BALANCED layout, every strength day replaced by `CORE_<day>`: the day's non-core patterns, an anti-rotation pattern (dead bug 119, the only one in the catalog) third, and two `required` CORE TIME holds (plank, side plank) at the end.
  - The focused days put the focus muscles first and fill all 7 sizes without skipping, so LOWER_BODY / UPPER_BODY reach ≥ 60 % of each week's strength slots (exactly 60 % in the 5-day splits); the core pattern at the end of the lower/upper days is a fallback that the session size never reaches.
- **Strength days:** 7–10 ordered slot patterns each (7 in the BALANCED days) (`muscle`, `mechanic`, optional `type` TIME for holds, optional `required`, `candidates`; each pattern lists gym candidates first and home ones after). For each pattern the generator takes the first candidate that is eligible, not used yet in the workout and trains the muscle, preferring the pattern's mechanic; a pattern with no candidate is skipped (logged with `println`). **Required** patterns are picked first and always keep their place; the other patterns fill the rest of the session in order; the workout keeps the template order (so CORE workouts end with their two holds whatever the session size). Without required patterns (every BALANCED day) the result is identical to before. Exercises are chosen once per strength day, so the same day keeps its exercises every week.
- New day titles (`StrengthDayTitle`, stored by name in payloads, only ever added): FULL_BODY "Full body" / "Cuerpo completo", UPPER "Upper" / "Tren superior", LOWER "Lower" / "Tren inferior", UPPER_C, LOWER_C.
- **Eligibility** (`ProgramRules.isEligible`): not a duplicate; the location is in `locations`; HOME only with BODYWEIGHT, DUMBBELL, BENCH or NONE; no HIGH impact when low impact; 87 (behind-the-neck press) excluded for beginners and low impact.
- **Session size:** the top of the level's range (4–5 / 5–6 / 6–7) for 2–3 days a week, the bottom for 4–5 days.
- **Prescription:** GAIN sets 3/3 (beginner), 4 compound + 3 isolation (intermediate), 4/4 (advanced); reps 6–10 compound, 8–12 isolation; rest 90/90/120 s compound, 60 s isolation. LOSE sets 2/3/3, reps 12–15, rest 45 s compound, 30 s isolation. Holds 30/40/50 s. Age ≥ 60 adds 15 s to every rest (strength and circuit).
- **Per side:** the slot copies `perSide` from the catalog (`StrengthSlot.perSide`, default `false` so payloads stored before the field still decode); the number of sets, reps/seconds and rest are unchanged and the values are per side.
- **Progression:** a step counter advances on every non-deload week. Reps +1 per week to the top, then +1 set (max 5) with reps back to the bottom; holds +5 s per week. Deload every 4th week when the program has ≥ 6 weeks: sets −1 (min 2) from the last trained week, reps at the bottom / base hold, cardio × 0.7.
- **Cardio:** GYM → treadmill; HOME alternates outdoor run and home circuit (program-wide counter).
  - Outdoor run: 1.5 / 3 / 5 km, +10 % per week, rounded to 0.1 km. Low impact: brisk walk from 2 / 3 / 4 km.
  - Treadmill: 5 min warm-up, `base + week step` intervals of jog + walk (beginner 7 / 5 km/h, 4–8 intervals of 60 s + 120 s; intermediate 8.5 / 5.5, 5–10 of 120 s + 90 s; advanced 10 / 6, 6–12 of 180 s + 60 s; jog inclines 1–8 %), 5 min cool-down. Low impact: walking at 5 km/h, alternating 8 % and 2 % incline for 120 s each.
  - Home circuit: rounds 3/4/5, work 30/40/45 s, rest 30/20/15 s, 5/6/6 HOME exercises (`isCircuitEligible`: CARDIO, COMPOUND or CORE and **not per side**), 60 s between rounds; low impact uses a LOW-impact list. Per-side exercises are excluded rather than split, because a 30–45 s station split in two would give 15–22 s per side with no time to switch; lunges (20) and the Bulgarian split squat (112) were removed from the candidate lists (low impact now uses 58 knees to chest and 7 leg raises instead). Consecutive circuits rotate the start of the list.
- **Low impact** (`isLowImpact`): BMI ≥ 30 or age ≥ 60. `ProgramGenerator.generate(profile, catalog, templates)` is pure and deterministic.
- **Focus** (`ProgramFocus(code)`: BALANCED, LOWER_BODY, UPPER_BODY, CORE; `fromCode`, `DEFAULT` = BALANCED) is `ProgramProfile.focus` (default BALANCED). It only picks the split: prescriptions, progression, deload, impact rules, per side, cardio and "no exercise twice in a workout" are the same for every focus. BALANCED output is pinned by `ProgramBalancedRegressionTest` (golden files).

### 12.3 Persistence (`ProgramDatabase`, file `programs.db`, version 4, exportSchema)
- Separate from `TioMinuterDatabase`; schemas in `app/schemas/com.javargasp.tiominutermvi.data.program.local.ProgramDatabase/` (`1.json` … `4.json`; never edit an exported one).
- Migrations: v1 → v2 is an `AutoMigration` that adds `programs.focus` (TEXT NOT NULL DEFAULT 'BALANCED'); every other table is unchanged and existing programs read as BALANCED. v2 → v3 (`AutoMigration` + `Migration2To3`) adds `program_strength_session` and `program_cardio_session` and derives strength sessions in flight (§20). v3 → v4 (`AutoMigration` + `Migration3To4`) adds `programs.uuid` (indexed, backfilled with random lowercase uuids) and `sync_outbox` (§21). `ProgramMigrationTest` (instrumented) covers them.
- Tables: `programs` (id autoGenerate, goal, level, location, weeks, daysPerWeek, age, weightKg, heightCm, createdAt from `WallClock`, focus as its `ProgramFocus` code; an unknown stored focus reads as BALANCED and is logged); `program_workouts` (PK programId+week+dayIndex, `kind`, `isDeload`, `payload` = the domain `ProgramWorkout` as JSON with a `kind` discriminator; FK → programs CASCADE); `program_slot_progress` (PK programId+week+dayIndex+position, completedAt; FK → program_workouts CASCADE); `program_workout_progress` (PK programId+week+dayIndex, completedAt; FK → program_workouts CASCADE).
- `ProgramRepository` / `ProgramRepositoryImpl(database, remote, wallClock, trainingLogRecorder, workoutTitles, sessionRecorder)` (Koin `single`): catalog and templates are fetched once and cached (Mutex). `createProgram` generates and inserts in one transaction; stored payloads are never regenerated, so template changes don't alter existing programs. `observePrograms` (with completed/total workouts), `observeProgram`, `observeWorkout`, `previewProgram`, `deleteProgram`, `markSlotCompleted` (completes the strength workout in the same transaction when every slot is done), `resetWorkout` (clears slots, keeps the workout's completion), `markWorkoutCompleted(…, durationSeconds)`, `getExercises`, `getImpactRules`.
- Completion photo (§16): `observeCompletionEntryId(programId, week, dayIndex)` exposes the entry id of the workout's latest recorded completion (in memory; set to null when a new completion starts, then to the recorded id, or left null when recording fails). The interface default emits null.
- Training history (§13): after the progress is stored, a PROGRAM entry is recorded (refId `program:<programId>:<week>:<dayIndex>`, title from `ProgramWorkoutTitles` = `ResourceProgramWorkoutTitles`, the same strings as `workoutTitle`). Strength: only when `markSlotCompleted` is the call that completes the workout, duration from the slot timestamps (`ProgramProgressDao.getSlotCompletionTimes`). Cardio: every `markWorkoutCompleted`, with the screen's duration (outdoor run: stopwatch time without pauses; treadmill and circuit: time since Start). Failures are logged by `recordSafely`.
- Koin `programModule` (`di/ProgramModule.kt`): remote source, database, repository and every program ViewModel (`parametersOf` for ids). It also binds `ProgramWorkoutTitles` and uses `ContentSource` from `contentModule` and `WorkoutFeedback`, `WallClock`, `MonotonicTimeSource` and `TrainingLogRecorder` from `workoutModule`.

### 12.4 Shared program UI (`ui/program/common/`)
- `formatPercent(completed, total, locale)` (one decimal, locale decimal separator; total 0 → "0.0"), `formatOneDecimal`, `programExerciseSummary` ("Sets: 4 · Reps: 10, 10, 10, 10" / "Sets: 2 · Time: 30s, 45s"; per-side slots end with " per side" / " por lado", e.g. "Sets: 3 · Time: 40s, 40s, 40s per side"). It is the only slot summary and appears on the strength workout cards; the detail cards ("6 exercises") and the create preview (exercise names) show no slot summary.
- `ProgramCountdown`: one countdown job computed from an end timestamp of `MonotonicTimeSource`; starting a new one cancels the previous one.
- `ProgramUi.kt`: `currentLocale()`/`isSpanish()` (exercise names use `nameEs` in Spanish), labels for goal/focus (`focusLabel`, `focusDescription`)/level/location/workout titles/segments, `workoutSummary`, `ProgramTopBar`, `ProgramConfirmDialog`, `ProgramLoading`, `ProgramNotFound`, `ProgramWorkoutFinished`, `ProgramCircularCountdown`, `KeepScreenOn`.

### 12.5 Screens (all MVI: `BaseViewModel` + `MviComposable` + stateless `*Content` + light/dark `*Previews.kt`)
- **List** (`list/`, "My programs"): State `isLoading`, `programs`, `pendingDeleteId`. Intents `OnProgramClicked`, `OnCreateClicked`, `OnDeleteRequested`, `OnConfirmDelete`, `OnDismissDelete`, `OnBackPressed`. Effects `NavigateToCreate`, `NavigateToDetail`, `NavigateBack`. Observes the programs once in `init`. Empty state with explanation; FAB "+" (`contentDescription` "Create program"); cards with goal, "Focus: Lower body", "6 weeks · 3 days per week", location · level, progress bar and "Progress: 33.3 %". Long press → delete dialog; the delete action is also exposed with `onLongClickLabel` and a semantics custom action.
- **Create** (`create/`): State holds the typed text (age, weight, height), level, goal, focus (default BALANCED; `OnFocusSelected`), location, weeks (default 6), days (default 3), `showAllErrors`, `impactRules`, `preview`, `exercises`, `isWorking`, `createFailed`; derived parsed values, field errors (REQUIRED / OUT_OF_RANGE), `profile`, `bmi`, `isLowImpact`, `showUnderweightWarning`. Inline validation (age 16–90, weight 30–250 kg with decimal comma or point, height 120–230 cm). BMI with one decimal, low-impact note, warning for BMI < 18.5 with "Lose fat" (goal labels: "Gain strength and muscle" / "Ganar fuerza y músculo" and "Lose fat" / "Perder grasa"; stored codes unchanged), health disclaimer. **Focus** selector between goal and location: "Focus" / "Enfoque", a radio group (`selectableGroup`, each row `selectable` with `Role.RadioButton`, at least 48dp, so TalkBack reads the selected state) of Balanced / Equilibrado "The whole body, evenly" / "Todo el cuerpo por igual", Lower body / Tren inferior "More glutes and legs" / "Más glúteo y pierna", Upper body / Tren superior "More chest, back, shoulders and arms" / "Más pecho, espalda, hombros y brazos", Core and stability / Core y estabilidad "More core and balance work" / "Más trabajo de core y equilibrio". Preview shows week 1 (titles and exercise names) generated with the selected focus, under a "Focus: …" line. Create → `NavigateToDetail(id)`, replacing the create screen in the back stack.
- **Detail** (`detail/`): observes the program once; top bar with goal and duration, then "Focus: Lower body" as the first line of the list; per week "Week 2 · 66.7 %" and a "Deload" label; workout cards with a summary, success colors + check icon and `stateDescription` Completed / Not completed. Tap → `NavigateToWorkout(kind, week, day)` (completed workouts can be opened again).
- **Strength workout** (`strength/`): exercise cards (name, summary, green when done), "N of M exercises completed" (plurals) + progress bar, Reset with confirmation (enabled when something is done). When the last exercise becomes completed while the screen is in the back stack, "Workout completed!" → dismiss goes back to the detail.
- **Strength exercise** (`strengthexercise/`): phases `PerformingReps` → `Resting` (circular countdown, `REST_ANIMATION`, Skip rest) → next set; TIME sets `ReadyToStartTimed` → `GetReady` (5 s) → `PerformingTimed`. Per-side TIME sets: `PerformingTimed(side = LEFT)` → `SwitchingSides` (5 s, "Switch sides", sound + vibration when it starts) → `PerformingTimed(side = RIGHT)` → rest, with "Left side" / "Right side" above the countdown and "Per side: left first, then right" before Start. Per-side REPS sets show "10 reps per side" (plurals) and are still one "Set done" tap after both sides. No rest after the last set; the last set calls `markSlotCompleted`, plays sound + vibration and goes back. Feedback also at the natural end of a TIME set or rest. Single countdown, `BackHandler` with exit confirmation when sets are done, keeps the screen on; not-found if the slot disappears or marking fails.
- **Treadmill** (`treadmill/`): current segment large (label, countdown, km/h, incline), next segment, total remaining, Start / Pause / Resume, Skip segment, full segment list. Sound + vibration at every segment change; after the last segment `markWorkoutCompleted` and a "Workout completed!" view. Exit confirmation once started; keeps the screen on.
- **Outdoor run** (`outdoorrun/`): target ("Run 3.3 km" or "Brisk walk: 2.0 km"), stopwatch from `MonotonicTimeSource` with Start / Pause / Resume, Finish with confirmation → `markWorkoutCompleted`. No GPS. Exit confirmation once started; keeps the screen on.
- **Circuit** (`circuit/`): "Round X of N", the exercise with its animation and a work countdown, rest with the next exercise, rest between rounds, Skip. Sound + vibration at every change; after the last exercise of the last round `markWorkoutCompleted`. Exit confirmation once started; keeps the screen on.
- An exercise whose animation file is missing shows an empty area; nothing crashes.

---

## 13. Training history

Every completed workout, wherever it comes from, becomes one entry in a single local history. Foundation for weekly goals, logged activities and the future Firebase sync (none of these exist yet).

### 13.1 Domain (`domain/traininglog/`)
- `TrainingLogEntry(id, uuid, dateLocal, completedAt, source, refId?, title, durationSeconds?, activityType?, intensity?, note?, photoPath?, createdAt, updatedAt)`; `enum TrainingSource { DAILY, GYM, PROGRAM, ACTIVITY }`. ACTIVITY entries are logged by hand (§15) and fill `activityType` / `intensity` (codes) and `note`. **NOT IMPLEMENTED yet:** `photoPath` (the column exists so a later story needs no migration). The source was `FREE` until story 3 and was renamed because "free" reads as "free of charge"; no FREE row was ever written and nothing stored, serialized or tested depended on the string (`TrainingSourceRenameTest`).
- `uuid`: random `UUID` created with the entry (future sync id). `dateLocal`: "yyyy-MM-dd" of `completedAt` in the device zone at that moment. Timestamps are epoch millis from `WallClock`.
- `LocalDateProvider(clock: WallClock, timeZoneProvider: () -> TimeZone)`: `dateOf(millis)`, `today()`, `todayPlusDays(days)` (via `Calendar`, so yesterday is a calendar day across DST changes), and week helpers `mondayOf`, `currentWeekStart`, `weekDates`, `previousWeek`, `nextWeek` that delegate to the pure `CalendarDates` (§14.1). No `java.time` (minSdk 24, no desugaring). The zone is read on every call.
- `TrainingLogRecorder.record(source, refId, title, durationSeconds): Long` (the new entry's id): the only API other features use. `recordSafely(...): Long?` catches and logs (`println`) any failure except cancellation and returns null then. `durationBetweenCompletions(times)`: last − first in seconds, null with fewer than two.
- `TrainingLogRepository`: `observeEntries()` (newest first), `observeEntriesBetween(fromDate, toDate)` (inclusive; not used by a screen yet), `getEntry(id)`, `addActivityEntry(input)`, `updateActivityEntry(id, input)` (§15, keeps the photo), `deleteEntry(id)` (deletes the photo files too), `setPhotoPath(id, path?)` (returns the previous path; only the photo repository calls it) and `getPhotoPaths()` (§16).

### 13.2 Data
- `training_log` in `TioMinuterDatabase` v3 (§4b), `TrainingLogDao`, mappers in `data/traininglog/TrainingLogMappers.kt` (an unknown stored source is skipped and logged).
- `TrainingLogRepositoryImpl(dao, clock, localDateProvider, photoFiles, newUuid)` implements both interfaces. DAO additions for photos: `updatePhotoPath(id, photoPath, updatedAt)`, `getPhotoPaths()`. `record` sets uuid, `dateLocal`, `completedAt = createdAt = updatedAt = now` and inserts in `NonCancellable`, so a ViewModel cleared right after a workout doesn't lose the entry.

### 13.3 Recording points

| Source | Where | refId | Title | Duration |
|--------|-------|-------|-------|----------|
| DAILY | `WorkoutViewModel.finishWorkout` (the single `NavigateToCompletion`, also when every exercise was skipped), after the effect, in its own coroutine | `daily:<routineId>` | `routineDay` (+ " · title") | active time: on screen from the first loaded exercise to the finish, background excluded (§20.8); an unfinished workout is recorded after 2 h of inactivity |
| GYM | `GymRoutineViewModel` → `completeSession` when `showRoutineCompleted` turns true (before the dismiss reset); or the inactivity close of an abandoned session (§20) | `gym:<routineId>` | routine name | the session's last set − first set (0 for a single set); an abandoned session is dated on its start |
| PROGRAM strength | `ProgramRepositoryImpl.markSlotCompleted`, when this call completes the workout | `program:<programId>:<week>:<dayIndex>` | `ProgramWorkoutTitles` (current language) | the strength session's first → last set (null without reported sets); an abandoned session is recorded after 2 h, the workout left resumable (§20.7) |
| PROGRAM cardio | `ProgramRepositoryImpl.markWorkoutCompleted` (every call) | same | same | active time from the screen: treadmill and circuit running and visible; outdoor run stopwatch (pauses excluded, background counted) (§20.8) |

- Recording never blocks or breaks a workout: failures are caught by `recordSafely`; the daily and gym ViewModels record in a separate coroutine.
- Doing a completed program workout again records a new entry. Re-marking an exercise of an already completed strength workout does not.
- Titles are stored as text in the language active when the workout was completed; they don't change with a later language switch.
- Gym (fixed in §20): the guard used to be the ViewModel's dialog flag, so process death while the "Routine completed!" dialog was pending recorded a second entry. The entry is now tied to the session, which the recording removes, so the dialog shown again records nothing.
- Every source now records a real, bounded duration (§20).

### 13.4 History screen (`ui/traininglog/`, route `training_history`)
- Entry point: the "See history" / "Ver historial" text button in Home's weekly goal card (§5.1, §14.4). A FAB (`Icons.Filled.Add`, content description "Log activity") logs a new activity (§15). Title "Training history" / "Historial de entrenos".
- **State** `TrainingHistoryState(isLoading = true, weeks: List<TrainingWeek>, pendingDeleteId: Long?, names: WorkoutNameCatalog, …)`, derived `days` (all day groups), `isEmpty`, `pendingDeleteEntry`, `nameOf(entry)` (§24). `TrainingWeek(weekStart, progress: WeeklyProgress, days)`. `TrainingDay(dateLocal, label, entries)` with `DayLabel.Today` / `Yesterday` / `Date(dateLocal)`.
- **Intents** `OnDeleteRequested(entryId)`, `OnConfirmDelete`, `OnDismissDelete`, `OnBackPressed`, `OnEntryClicked(entryId)` (only an ACTIVITY entry opens, in edit mode; automatic entries are not editable), `OnLogActivityClicked`. **Effects** `NavigateBack`, `NavigateToLogActivity`, `NavigateToEditActivity(entryId)`.
- **ViewModel** `TrainingHistoryViewModel(repository, weeklyGoalRepository, localDateProvider, names: WorkoutNameCatalogs, photos)`: combines `observeEntries()`, `observeGoals()` and `names.observeCatalog()` once in `init` (then `withProgramsOf` looks up each PROGRAM entry's workout, `mapLatest`), groups by `dateLocal` (newest day first, entries newest first) and then by week (`groupByWeek`, progress from `WeeklyProgressCalculator` with the goal of each week) and labels Today / Yesterday from `LocalDateProvider` on every emission. A pending delete closes when its entry disappears; a delete failure is logged and the screen keeps working.
- **UI:** `TopAppBar` with back; FAB; list bottom padding so the FAB doesn't cover the last entry; a week header above each week's days (`secondaryContainer` surface, `heading()`): "Week of 13 October · 3 of 4 workouts ✓" / "Semana del 13 de octubre · 3 de 4 entrenos ✓" (counted workouts) (date with the `dMMMM` skeleton; "· 3 workouts" without a goal that week; TalkBack reads ", goal met" instead of ✓); day headers (`heading()` semantics) formatted with `DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")` ("Monday, 19 October" / "lunes, 19 de octubre"); rows (`Card`, ≥ 56dp) with a decorative source icon (DateRange / Build / List / Create), title, "Gym routine · 32 min" (`training_history_duration_minutes` plurals, rounded down, at least 1, so a 9:50 entry shows "9 min"; only the source when unknown), "Doesn't count toward the goal" / "No cuenta para la meta" under entries shorter than 10 minutes (§14.2) and the time in the device 12/24-hour format. Empty state text. Long press → "Delete this entry?" dialog; the same action is exposed as `onLongClick` semantics and a custom accessibility action ("Delete entry"). Previews: light, dark, Spanish, empty, delete dialog.
- **ACTIVITY rows:** the activity's emoji icon (decorative), the custom name (OTHER) or the type label as title, "45 min · Moderate", the first non-blank line of the note (one line, ellipsized). Tap (`combinedClickable`, click label "Edit activity") → edit; long press and the custom accessibility action still delete. Automatic rows keep long-press delete only. The delete dialog uses the same display title.
- **Titles:** stored titles are only the fallback (GYM: the English `routineName`; DAILY: "Day N"; PROGRAM: the title in the language active at completion). Every row and the delete dialog show `state.nameOf(entry).text()`, resolved at display time in the viewer's language by `WorkoutNameResolver` (§24).
- **Photos (§16):** a 48dp thumbnail on entries with a photo (tap: full screen). Automatic entries: long press opens a menu (Add / Change photo, Remove photo with "Remove the photo?" confirmation, Delete), also as accessibility custom actions; ACTIVITY entries keep long press = delete and edit their photo in the form. Intents `OnPhotoSelected(entryId, source)`, `OnRemovePhotoRequested`, `OnConfirmRemovePhoto`, `OnDismissRemovePhoto`; state `processingPhotoEntryIds`, `pendingPhotoRemovalId` (+ derived `pendingPhotoRemovalEntry`); effect `ShowPhotoError`. The chooser target and the viewed photo are UI-only `rememberSaveable` state.
- Koin: `viewModel { TrainingHistoryViewModel(get(), get(), get(), get(), get()) }` in `workoutModule` (the fifth is `TrainingPhotoRepository`).

### 13.5 Tests
JVM: `LocalDateProviderTest`, `TrainingLogRecorderTest`, `TrainingLogMappersTest`, `TrainingLogRepositoryImplTest`, `TrainingHistoryViewModelTest`, `WorkoutViewModelTrainingLogTest`, the recording cases of `GymRoutineViewModelTest` and the durations in the cardio ViewModel tests (§9). Instrumented: `TrainingLogDaoTest`, `MigrationTest` 2 → 3 and 1 → 3, the recording cases of `ProgramRepositoryTest`.

---

## 14. Weekly goal

Each person sets how many **workouts** per week to do; Home shows the current week's progress, computed from the training history (§13). Two workouts on the same day (gym in the morning, basketball in the evening) count as two. Until database v5 the goal counted days; the v4 → v5 migration keeps the stored number (a 4-day goal becomes 4 workouts).

### 14.1 Dates and weeks
- Weeks run **Monday to Sunday**. A workout's day is its `training_log.dateLocal`, fixed in the device zone when it was recorded; "today" and the current week come from `LocalDateProvider` (device zone, read on every call).
- `CalendarDates` (`domain/traininglog/`, pure): `plusDays`, `mondayOf`, `weekDates`, `plusWeeks`. Date strings have no zone, so the arithmetic runs in UTC (every day 24 h, unaffected by DST). Non-lenient parsing; an invalid date throws `IllegalArgumentException`.

### 14.2 Counting rule (`domain/goal/WorkoutCounting.kt`)
- `MIN_COUNTED_DURATION_SECONDS = 10 * 60` is the only definition of the minimum.
- `countsTowardGoal(entry)`: true when `durationSeconds >= MIN_COUNTED_DURATION_SECONDS` or `durationSeconds == null` (unknown — e.g. a gym routine finished with a single completion, or a program strength workout with one exercise). Shorter entries (e.g. a daily routine with every exercise skipped) stay in the history but don't count.
- `countedWorkoutsPerDay(entries)`: date → number of counted entries; dates without one are absent.

### 14.3 Model and rules (`domain/goal/`)
- `WeeklyGoal(workoutsPerWeek: 1..14, effectiveFromWeek)` — `WORKOUTS_RANGE = 1..14`; `effectiveFromWeek` is the Monday of the week where it starts.
- **Goal of a week:** the latest goal whose `effectiveFromWeek` ≤ that week's Monday (`WeeklyProgressCalculator.goalForWeek`); null before the first goal. A change applies from the current week; past weeks keep the goal they had.
- `WeeklyProgress(weekStart, goal?, workoutsPerDay)`: `workoutCount` (sum), `workoutsOn(date)`, `trainedDates`; `isGoalMet` = goal != null && workoutCount ≥ goal (going over the goal stays met).
- **Streak** (rules unchanged, counted in workouts): consecutive completed weeks before the current one with their goal met, counted back from last week until a missed week or the first goal's week (weeks before the first goal don't count). The current week adds 1 only once its goal is met; while unfinished it never breaks the streak. No goal → 0.
- `CurrentWeekSummary(today, progress, streak)` derives `workoutsNeeded` (0 when met or without goal). The former "no longer reachable this week" state was removed: with several workouts per day it isn't meaningful.
- `WeeklyGoalRepository`: `observeGoals()` (oldest first), `setGoal(workoutsPerWeek)` (current week; 1..14 or `IllegalArgumentException`).
- `ObserveCurrentWeek(weeklyGoalRepository, trainingLogRepository, localDateProvider)`: `combine(observeGoals(), observeEntries())` → `countedWorkoutsPerDay` → `WeeklyProgressCalculator.currentWeek(today, …)`. "Today" is read on each emission; Home re-collects on every `OnStart`.

### 14.4 Home card (`ui/home/WeeklyGoalCard.kt`, `WeeklyGoalCompactCard`)
- "This week" / "Esta semana" card between the title and the buttons (§5.1), shown once the first summary arrives. A `Card` made `clickable` (role button, click label "Open weekly goal" / "Abrir meta semanal") → goal screen.
- **With a goal:** the title and "3 of 4 workouts" / "3 de 4 entrenos" on one line (`weekly_goal_progress`, plurals by the goal; it can read "5 of 4 workouts"), `LinearProgressIndicator` capped at 100 %, seven day circles Mon–Sun with letters `weekly_day_short_*` (M T W T F S S / L M X J V S D): filled on days with counted workouts, showing the number when 2 or more and a check for 1; today outlined. TalkBack reads the row as one sentence: "Monday 1 workout, Wednesday 2 workouts. Today, Friday: no workouts yet." (weekday names from `SimpleDateFormat("EEEE", locale)`, `weekly_goal_a11y_*` plurals).
- **One message line** (`HomeState.weeklyMessage` + streak): goal met → "Goal met this week!" / "¡Meta de la semana cumplida!" (success color); otherwise "N workouts left to reach your goal" / "Te faltan N entrenos para tu meta" (plurals); followed by " · Streak: 3 weeks" / " · Racha: 3 semanas" (plurals) when the streak ≥ 1.
- **Without a goal:** "Set your weekly goal" / "Define tu meta semanal" and a "Set goal" / "Definir meta" button.
- **"Log activity"** / "Registrar actividad" and **"See history"** / "Ver historial" `TextButton`s (≥ 48dp, a `FlowRow` that wraps when they don't fit) at the bottom of the card, with or without a goal → `OnLogActivityClicked` (§15) / `OnHistoryClicked` (Training history). Without a goal, "Set goal" sits on its own line above them.
- Tapping the card or "Set goal" → goal screen. Previews of the card alone in `WeeklyGoalCardPreviews.kt` (workouts left light/dark, goal exceeded in Spanish, no goal); of the whole Home in `HomePreviews.kt`.

### 14.5 Goal screen (`ui/weeklygoal/`, route `weekly_goal`)
- **State** `WeeklyGoalState(isLoading = true, currentGoal?, selectedWorkouts = DEFAULT_WORKOUTS (3), isSaving, saveFailed)`, derived `canDecrement` / `canIncrement`. **Intents** `OnIncrement`, `OnDecrement`, `OnSave`, `OnBackPressed`. **Effects** `NavigateBack`.
- **ViewModel** `WeeklyGoalViewModel(weeklyGoalRepository, localDateProvider)`: loads the goal in force this week once and starts from it (3 without a goal). + / − move by one and stop at 14 and 1 (ignored while loading). Save → `setGoal`, `NavigateBack`; failure → `saveFailed` (logged), the screen stays.
- **UI:** question "How many workouts a week do you want to do?", a − [N workouts per week] + stepper (56dp `FilledTonalIconButton`s, labelled "Fewer workouts" / "More workouts", disabled at the limits; the value is a polite live region), "Any workout of at least 10 minutes counts: daily routines, gym routines, program workouts and the activities you log. Two workouts on the same day count as two." (minutes from the constant, plurals), "The change applies from this week; past weeks keep their goal.", Save. Previews: light, dark, Spanish at the maximum with the save error.
- Koin: `viewModel { WeeklyGoalViewModel(get(), get()) }`.

### 14.6 Persistence
`weekly_goal` in `TioMinuterDatabase` (§4b): PK `effectiveFromWeek`, `workoutsPerWeek`, `updatedAt` (`WallClock`). Added in v4 as `daysPerWeek`; v5 renames the column with `@RenameColumn` (`TioMinuterDatabase.Migration4To5`), keeping the values. `4.json` still describes v4. `WeeklyGoalRepositoryImpl` (`data/goal/`) upserts under the current week's Monday.

### 14.7 Tests
JVM: `CalendarDatesTest`, the week cases of `LocalDateProviderTest`, `WorkoutCountingTest`, `WeeklyProgressCalculatorTest`, `ObserveCurrentWeekTest`, `WeeklyGoalRepositoryImplTest`, `HomeViewModelTest`, `WeeklyGoalViewModelTest`, the week-header and uncounted-entry cases of `TrainingHistoryViewModelTest` (§9). Instrumented (emulator only, `ANDROID_SERIAL`): `WeeklyGoalDaoTest`, `MigrationTest` 3 → 4, 1 → 4, 4 → 5, 1 → 5, `WeeklyProgressFlowTest`.

---

## 15. Log an activity

Anyone can log a workout done outside the app's routines (own gym routine, running, yoga, basketball…). It counts toward the weekly goal like any other entry through `countsTowardGoal` (≥ 10 minutes); the goal logic is unchanged. No migration: `training_log` already had `activityType`, `intensity`, `note` and `photoPath`.

### 15.1 Naming
- "Free" is not used anywhere (it reads as "free of charge"): the source is `TrainingSource.ACTIVITY` (renamed from `FREE`, see §13.1).
- Code: `LogActivityScreen` / `LogActivityViewModel` / `LogActivityState` / `LogActivityIntent` / `LogActivityEffect` / `LogActivityPreviews` in `ui/logactivity/`; never a class called just `ActivityScreen` / `ActivityViewModel` (confusion with `android.app.Activity`). Repository methods `addActivityEntry` / `updateActivityEntry`.
- Texts: "Log activity" (buttons), "Log an activity" / "Edit activity" (titles); "Registrar actividad", "Registrar una actividad" / "Editar actividad".

### 15.2 Domain (`domain/traininglog/`)
- `ActivityType(code)`: OWN_GYM_ROUTINE, RUNNING, WALKING, CYCLING, SWIMMING, YOGA, PILATES, DANCE, TEAM_SPORT, RACKET_SPORT, MARTIAL_ARTS, HIIT, STRETCHING, OTHER. `Intensity(code)`: EASY, MODERATE, HARD. Both stored by code in `training_log` (`fromCode`; an unknown code reads as null). Labels are resolved from strings at display time (`ActivityLabels.kt`), never stored.
- **Entry rules:** source ACTIVITY; `activityType`, `durationSeconds` (1–600 minutes) and `intensity` required; optional `note` (≤ 200 characters, trimmed, blank → null); `photoPath` null (story 4); `refId` null. OTHER needs a custom name (≤ 40, trimmed) stored as the `title`; the other types keep `title` empty.
- **Date window:** `completedAt` from 00:00 of six days ago (device zone) up to now, never in the future; `dateLocal` is computed from `completedAt` by `LocalDateProvider`. Editing also allows the entry's own day when it has left the window.
- `ActivityEntryRules` (pure): `DURATION_MINUTES`, `NAME_MAX_LENGTH`, `NOTE_MAX_LENGTH`, `DAYS_BACK`, `windowStart`, `validate(input, now, earliest)` → `Set<ActivityEntryError>`, `titleFor`, `noteFor`. `InvalidActivityEntryException(errors)` when the repository gets an invalid input.
- `LocalDateProvider` additions: `nowMillis()`, `startOfDay(date)`, `epochMillisAt(date, hour, minute)`, `timeOfDay(millis)` → `TimeOfDay`.
- `TrainingLogRepositoryImpl`: `addActivityEntry(input)` (new uuid, createdAt = updatedAt = now, `NonCancellable` insert), `updateActivityEntry(id, input)` (ACTIVITY entries only, else `IllegalStateException`; keeps uuid and createdAt; updatedAt = now; `dateLocal` recomputed), `getEntry(id)`. `TrainingLogDao.getById` / `update`.

### 15.3 Screen (`ui/logactivity/`, routes `log_activity` and `log_activity/{entryId}`)
- **ViewModel** `LogActivityViewModel(entryId?, repository, localDateProvider)`; Koin `viewModel { params -> LogActivityViewModel(params.getOrNull<Long>(), get(), get()) }`; the screen passes no parameter to create and the id to edit (`koinViewModel(key = …)`).
- **State** `LogActivityState`: `form` / `initialForm` (`LogActivityForm`: type, customName, durationText, intensity, date, hour, minute, note), `dayOptions` (`DayOption(date, DayLabel)`), `showErrors` / `errors`, `timeEdited`, `isSaving`, `saveFailed`, `isNotFound`, dialogs; derived `isEditMode`, `hasUnsavedChanges`, `showsShortDurationHint`.
- **Intents:** type, custom name (capped at 40), duration text (digits, ≤ 3), quick duration, intensity, date, time, note (capped at 200), Save, Delete / confirm / dismiss, Back / confirm / dismiss discard. **Effect** `NavigateBack`.
- **UI:**
  - Activity type: `FilterChip`s in a `FlowRow`, decorative emoji icon (the app only has the core Material icons, which have no sport icons) and the label as text; OTHER adds a name field with a counter.
  - Duration: quick chips 15 / 30 / 45 / 60 / 90 min and a numeric field; under 10 minutes: "It won't count toward your weekly goal (minimum 10 minutes)" / "No contará para tu meta semanal (mínimo 10 minutos)" (from `MIN_COUNTED_DURATION_SECONDS`, plurals).
  - Intensity: three `SegmentedButton`s (≥ 48dp, each described as "Easy: you can talk normally" / "Moderate: you can talk with effort" / "Hard: hard to talk"), with the three descriptions listed below (the selected one in bold).
  - When: chips Today, Yesterday and the 5 earlier days ("Mon, 13 Oct" / "lun, 13 oct", `getBestDateTimePattern(locale, "EEEdMMM")`), plus the edited entry's older day; a time button opening a Material 3 `TimePicker` dialog (device 12/24 h); default now for today and 18:00 for a past day, a chosen time is kept when the day changes; future times are rejected.
  - Note: optional multi-line field with "n/200".
  - Errors per field (polite live regions) after the first Save, then live. Save → store → back. Edit mode: Delete action in the top bar with confirmation. Back (top bar or `BackHandler`) with unsaved changes → "Discard changes?" (Discard / Keep editing).
  - Missing or non-ACTIVITY id → "This activity no longer exists."
- Previews (`LogActivityPreviews.kt`): new (light), edit (dark), OTHER with errors and the under-10 hint (Spanish), discard dialog.

### 15.3b Photo
- Optional photo in create and edit: `LogActivityForm.photo: FormPhoto` (None / Stored(photoPath) / Staged(StagedPhoto)), so it counts as an unsaved change. A pick is processed at once (`stage`, preview from the staged thumbnail); Save commits it to the saved entry (replacing the old photo) or removes a stored photo that was removed; Back / Discard / `onCleared` discard a staged photo. Save waits while `isProcessingPhoto`. A photo failure on Save never loses the entry: the entry is saved, `ShowPhotoError` (Toast) then `NavigateBack`.

### 15.4 Entry points and history
- Home weekly card: "Log activity" `TextButton` next to "See history" (§14.4).
- History: FAB "Log activity"; tapping an ACTIVITY entry opens it in edit mode; automatic entries are not editable and keep long-press delete; ACTIVITY rows show the activity icon and label (or custom name), duration, intensity and the first line of the note (§13.4).

### 15.5 Tests
JVM: `ActivityEntryRulesTest`, the new `LocalDateProviderTest` cases, `TrainingLogRepositoryImplTest`, `LogActivityViewModelTest`, the activity cases of `TrainingHistoryViewModelTest`, `ActivityWeeklyGoalTest`, `TrainingSourceRenameTest` (§9). Instrumented (emulator only, `ANDROID_SERIAL`): `getById` / `update` in `TrainingLogDaoTest`.

---

## 16. Training photos (local only)

A photo attached to a training-history entry as light evidence of a workout. Nothing is uploaded: the later group sync will upload the files this feature produces (photo + thumbnail per entry).

### 16.1 Where
| Place | How |
|-------|-----|
| Gym "Routine completed!" dialog | `CompletionPhotoSection`; the entry id is what `recordSafely` returned (`GymRoutineState.completionPhoto`). A new completion starts without photo. |
| Program strength "Workout completed!" dialog, cardio finished view (treadmill, outdoor run, circuit) | `CompletionPhotoSection` in the dialog / `ProgramWorkoutFinished`; the entry id comes from `ProgramRepository.observeCompletionEntryId` (first value dropped: it predates this visit). |
| Log activity (create, edit) | Photo field in the form (§15.3b). |
| History | Thumbnail + full-screen viewer for every entry with a photo; automatic entries add / change / remove from the long-press menu (§13.4). |
| Daily workout | Not offered. |

The section stays hidden until the entry id is known (or forever when recording failed). Errors are a Toast (`showTrainingPhotoError`), so they show over dialogs and after navigating back; the workout flow and the entry are never affected.

### 16.2 Capture (no dangerous permissions)
- `rememberTrainingPhotoPicker(onPhotoSelected)` (`ui/components/photo/TrainingPhotoPicker.kt`): gallery = `ActivityResultContracts.PickVisualMedia` (ImageOnly; falls back to the system picker on old devices); camera = `ActivityResultContracts.TakePicture` into `cache/training_photo_capture/capture.jpg` through the FileProvider `${applicationId}.trainingphotos` (`res/xml/training_photo_paths.xml`: that cache folder only). A cancelled capture deletes the file.
- No CAMERA permission is declared or requested (checked in the merged manifest). `<queries>` for `android.media.action.IMAGE_CAPTURE` lets the app see whether a camera app exists (API 30+); without one the camera row is hidden.
- `TrainingPhotoChooser`: the bottom sheet "Add a photo" (Take photo / Choose from gallery, privacy note) plus the launchers, registered even while the sheet is hidden so a result after recreation is delivered. The capture uri is `rememberSaveable`.

### 16.3 Processing (`AndroidTrainingPhotoProcessor`, `Dispatchers.IO`)
1. Bounds only (`inJustDecodeBounds`).
2. Decode with `inSampleSize = PhotoSizing.sampleSize(bounds, 1080)`: the largest power of two that keeps the longest side ≥ 1080, so the full-resolution bitmap is never in memory.
3. Exact scale to `PhotoSizing.targetSize` (longest side 1080, aspect kept, never upscaled) and the EXIF orientation (`ExifTransform.fromExifOrientation`, all 8 values; AndroidX `ExifInterface` on the stream).
4. Encode the photo as JPEG quality 70, then the thumbnail from it: longest side 400, quality 60 (about 40 KB).
5. `Bitmap.compress` writes a bare JFIF file: no EXIF block, so location, camera, date and every other metadata field are gone (verified on the emulator).
Writes go to `<name>.tmp` and are renamed; on failure nothing is left. Errors: `UNREADABLE_IMAGE` (no decodable image), `NO_SPACE` (ENOSPC), `FAILED` (anything else, `OutOfMemoryError` included).

### 16.4 Storage and naming (`TrainingPhotoPaths`, `TrainingPhotoFileStore`)
- `files/training_photos/<entry uuid>-<version>.jpg` and `<entry uuid>-<version>_thumb.jpg`; `version` = attach time (epoch millis). `training_log.photoPath` stores the relative photo path; the thumbnail path is derived. **No schema change.**
- Why a version in the name: a replacement is written next to the old photo and only then are the old files deleted (a failed replace keeps the old photo), and Coil's caches never show an old picture under a reused name.
- `isValidPhotoPath` (a plain `.jpg` directly in `training_photos/`, no "..", not a thumbnail) guards every path read back before a file is touched.
- Staged photos: `cache/training_photo_staging/<random>.jpg` (+ `_thumb`). Camera capture: `cache/training_photo_capture/capture.jpg`.

### 16.5 Lifecycle (`TrainingPhotoRepository` / `TrainingPhotoRepositoryImpl(trainingLog, processor, files, wallClock)`)
- `stage(source)` → `StagedPhoto` (processed into staging; the camera file is deleted afterwards, also on failure). `commit(entryId, staged)` → photoPath (moves the files into place, `setPhotoPath`, then deletes the previous photo's files; on failure the new files go and the old photo stays). `discard(staged)`. `attach` = stage + commit. `remove(entryId)` (clears photoPath, deletes the files).
- `TrainingLogRepository.deleteEntry` deletes the entry's files after the row.
- `cleanUp()`: deletes files in `training_photos/` that no `photoPath` references (photo or derived thumbnail; `.tmp` leftovers included) and staged / camera files older than one hour. `TioMinuterApplication` runs it at start on an application `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and logs the count (checked on the emulator: two seeded orphans were deleted at launch).
- Commit, remove and cleanup share one `Mutex` (the cleanup never sees a file whose path isn't stored yet) and run in `NonCancellable` (a dialog dismissed mid-way never leaves files and database disagreeing).
- ViewModels call it through `photoAction { }` → `PhotoResult.Success(value)` / `Failure(error)`.

### 16.6 Backup and privacy
- `files/training_photos/` is excluded from auto backup (`res/xml/backup_rules.xml`, Android 11 and lower) and from cloud backup and device transfer (`res/xml/data_extraction_rules.xml`, Android 12+). Everything else keeps the default backup.
- Note shown wherever a photo is added: "Photos stay on this device for now. Location data is removed." / "Por ahora las fotos se guardan solo en este dispositivo. Se elimina la ubicación."
- Accessibility: thumbnails have `contentDescription` "Workout photo, 19 Oct 2026" / "Foto del entreno, …" (`photoContentDescription`) and the click label "Open photo"; the viewer's close button is labelled "Close photo"; buttons and sheet rows are at least 48dp; "Saving photo…" / "Photo added" are polite live regions.

### 16.7 Shared UI (`ui/components/photo/`)
`TrainingPhotoPicker` / `rememberTrainingPhotoPicker`, `rememberTrainingPhotoFiles` (built from the context, so previews work), `TrainingPhotoSourceSheet`, `TrainingPhotoChooser`, `PhotoPrivacyNote`, `TrainingPhotoThumbnail`, `TrainingPhotoViewer` (full screen, black, Fit), `CompletionPhotoState` + `CompletionPhotoSection`, `photoErrorMessage`, `showTrainingPhotoError`, `photoContentDescription`. The Program feature uses them read-only.

### 16.8 Tests
JVM: `PhotoSizingTest`, `TrainingPhotoPathsTest`, `TrainingPhotoFileStoreTest`, `TrainingPhotoRepositoryImplTest`, and photo cases in `TrainingLogRepositoryImplTest`, `TrainingLogRecorderTest`, `GymRoutineViewModelTest`, `ProgramStrengthViewModelTest`, `ProgramTreadmillViewModelTest`, `TrainingHistoryViewModelTest`, `LogActivityViewModelTest` (§9). Instrumented (emulator only): `AndroidTrainingPhotoProcessorTest`, `TrainingPhotoLifecycleTest`, `TrainingPhotoFileProviderTest`, the completion-entry case of `ProgramRepositoryTest`.

## 17. Remote content (Firebase Hosting, offline-first, verified cache)

The app keeps working exactly as before with no network (bundled `content/v1`). With network, it checks the published manifest at start, downloads only what the bundle can't serve, verifies it and activates it atomically. No Room schema or user data changes. Package `data/content/`, Koin `contentModule` (`di/ContentModule.kt`, started before `workoutModule` and `programModule`). Permission: `INTERNET` only. `BuildConfig.CONTENT_BASE_URL` = `https://tiominuter-e43f7.web.app/content/v1/` (`buildFeatures.buildConfig = true`).

### 17.1 Flow

```
manifest.json ──GET──▶ validate ──▶ same version? ─yes─▶ UpToDate
                                     │no
                                     ▼
              pick files the bundle can't serve (JSON sha ≠ bundled; animation not bundled)
                                     ▼
              download → staging/ (size + sha256) → decode JSON with the app's decoders
                                     ▼  any failure: delete staging, keep the active version (Failed)
              move into objects/<sha256> → write active.json LAST (tmp + fsync + rename)
                                     ▼
              next launch: ContentSource / AnimationLocator serve the new version
```

### 17.2 Components (`data/content/`)
- `ContentManifest` / `ContentFileEntry` (published manifest), `LocalContentManifest(contentVersion, bundleFingerprint, files)` (active version, cached files only), `ContentRules` (schemaVersion 1, 5 MB per file, 1 MB manifest, path rules, `rejectionReason`).
- `ContentHttpClient` (`get(url, maxBytes)`), `HttpUrlConnectionContentHttpClient` (HTTPS only, 10 s connect/read, status 200, body capped with `readAtMost`). No HTTP library was added.
- `BundledContent` (`open(path)`, `animationNames()`), `AssetBundledContent(AssetManager)`; `BundledContentHashes` (sha256 of the bundled JSON computed at runtime once per process, plus a `fingerprint` over all of them).
- `ContentJsonFile<T>(path, deserializer, json)`: a content file and the app's decoder for it. Registered in Koin with `named(path)`: `exercises.json`, `routines.json`, `routines_for_gym.jsonc` (`contentModule`), `program_exercises.json`, `program_templates.json` (`programModule`). `getAll<ContentJsonFile<*>>()` gives the syncer its decoders and the list of allowed top-level JSON files.
- `ContentStore(filesDir/content)`: `objects/<sha256>` (verified files, content-addressed: an activation never overwrites a file in use), `staging/`, `active.json` (+ `.tmp`). `promote`, `writeActive` (atomic), `cleanUp` (staging leftovers + objects the active manifest doesn't list; only at start).
- `ActiveContent`: the local manifest read once per process.
- `ContentSource` / `CachedContentSource`: the single JSON read path (`readText`, `read(ContentJsonFile)`). Cached file when the active version lists it, it is stored with its size and the `bundleFingerprint` matches this APK; otherwise the bundled asset.
- `AnimationLocator` / `ContentAnimationLocator`: bundled asset URL first, then the cached `File`, else null. `ExerciseAnimation` reads it from `LocalAnimationLocator` (provided by `MainActivity`; `AnimationLocator.BundledOnly` in previews).
- `ContentSyncer` (`syncOnce()` / `sync()` → `ContentSyncResult.UpToDate | Updated(contentVersion, downloadedFiles, cachedFiles) | Failed(reason, cause)`).

### 17.3 Rules
- Read path: content JSON only through `ContentSource`, animations only through `AnimationLocator`; never `assets.open` or `file:///android_asset/...` elsewhere.
- Bundled animations are never downloaded; changing an animation requires a new file name (also a Hosting cache rule, §0).
- A manifest listing anything other than the known JSON files and `animations/<name>.gif` is rejected as a whole.
- A new version is used from the next launch: `ActiveContent` is fixed before the sync changes anything, and the repositories keep their refresh-once-per-process behaviour.
- App update: cached JSON is ignored when the bundled JSON changed (`bundleFingerprint`); the next sync re-activates the published version against the new bundle (even with the same `contentVersion`) and drops cached files equal to the bundle.
- Every JSON served from the cache (new or reused) must decode with the current app's decoders before activation.

### 17.4 Start-up
`TioMinuterApplication.syncContent()` launches `ContentSyncer.syncOnce()` on the existing `applicationScope` (`SupervisorJob + Dispatchers.IO`), next to the photo cleanup; it never blocks start or the UI. Results are logged (`Log.i` for UpToDate/Updated, `Log.w` for Failed, tag `TioMinuterApplication`); no user-facing error.

### 17.5 Cache location and backup
`filesDir/content/` is excluded from auto backup (`backup_rules.xml`) and from cloud backup and device transfer (`data_extraction_rules.xml`), like `training_photos/`.

### 17.6 Tests
- `ContentRulesTest` (6) and `ContentSyncerTest` (17), with `ContentTestDoubles.kt` (`DirectoryBundledContent`, `FakeContentHttpClient` with `publish`, `CrashingContentStore`, `ContentProcess`). Cases: offline → every file from the bundle and bundled GIF located; data sources decode the bundle through `ContentSource`; version equal to the bundle → nothing downloaded; same `contentVersion` → only the manifest requested; changed JSON downloaded, served from the next launch (the running process keeps its version); sha256 mismatch, longer body, > 5 MB entry, undecodable JSON → previous version stays and staging is empty; `../x`, `/abs`, `other/file.txt`, `animations/../exercises.json`, `unknown.json`, `animations/a.png` reject the manifest; schemaVersion 2 / missing field / non-JSON rejected; new animation downloaded and located as a `File`, bundled animation never requested; a file stored with the same hash is not downloaded again; crash before `active.json` → previous version on restart, orphan removed by the next cleanup; files dropped by a new version removed at the next start; app update with other bundled JSON ignores the cache and re-syncs; `syncOnce` runs once.
- Instrumented: `ProgramRepositoryTest` reads the bundled content through a `ContentSource` over `AssetBundledContent`.
- Verified on the emulator: fresh install in airplane mode (sync `Failed: UnknownHostException`, workout and GIFs load from the bundle); online, first launch `Content 20261020154542 activated (0 files downloaded…)`, second launch `is up to date`; with a locally altered bundled `routines.json`, the published one was downloaded (1 file), served on the next launch, and dropped by cleanup after reinstalling the normal APK.

## 18. Accounts (Phase 1: identity only)

Sign in with Google through Firebase Authentication, or guest mode. **No user data leaves the device** (no Firestore, no sync, no upload; Phase 2). Firebase project `tiominuter-e43f7`; `app/google-services.json` is committed (not a secret; CI builds with the google-services plugin) and its API key must be restricted in Google Cloud Console before a public release.

### 18.1 Flow

```
Welcome / Account / guest banner
   │ RequestGoogleToken (effect)
   ▼
GoogleIdTokenRequester (ui/auth, Activity context)
   │ Credential Manager · GetSignInWithGoogleOption(default_web_client_id)
   ▼
GoogleTokenResult ── Cancelled → nothing shown · NoNetwork / NoGoogleAccount / Failed → snackbar (§18.1.1)
   │ Token(idToken) as OnGoogleTokenResult (intent)
   ▼
AuthRepository.signInWithGoogle(idToken)
   │ FirebaseAuthRepository: GoogleAuthProvider.getCredential → signInWithCredential
   ▼
session: Flow<AuthSession> (AuthStateListener) → Home avatar, Account screen, guest banner
```

- Credential Manager only; the deprecated `GoogleSignInClient` API is never used. The requester checks connectivity first (`ACCESS_NETWORK_STATE`) so airplane mode shows "No internet connection". With no Google account on the device, Google's own add-account flow opens.
- Repositories never hold an Activity; ViewModels never call Credential Manager.

### 18.1.1 Error mapping
**Only a user cancellation is silent.** Every other failure shows a snackbar and is logged once with `Log.w` (tags `GoogleIdTokenRequester` / `FirebaseAuthRepository`) as `describeAuthFailure`: exception class, Credential Manager `type`, message with anything that looks like an email or a JWT replaced; never ID tokens, emails or names. Failures carry the exception's short class name (`GoogleTokenResult.*.cause`, `AuthResult.Failure.cause`, `ShowMessage.cause`); `authMessageText` appends it to the snackbar in debug builds only (`BuildConfig.DEBUG`), e.g. "Couldn't sign in with Google. Please try again. (GetCredentialCustomException)".

| Step | Outcome | Result | Message (EN / ES) |
|---|---|---|---|
| Before the picker | No network (`ConnectivityManager` check) | `GoogleTokenResult.NoNetwork` | "No internet connection. Check it and try again." / "Sin conexión a internet. Revísala e inténtalo de nuevo." |
| Credential Manager | `GetCredentialCancellationException` (picker closed, back) | `Cancelled` | none, not logged |
| Credential Manager | `NoCredentialException` | `NoGoogleAccount` | "No Google account is available on this device. Add one in Settings and try again." / "No hay ninguna cuenta de Google disponible en este dispositivo. Añade una en Ajustes e inténtalo de nuevo." |
| Credential Manager | `IOException`, or any `GetCredentialException` while offline | `NoNetwork` | no internet (as above) |
| Credential Manager | Any other `GetCredentialException` (custom, provider configuration, interrupted, unknown…) | `Failed` | "Couldn't sign in with Google. Please try again." / "No se pudo iniciar sesión con Google. Inténtalo de nuevo." |
| Credential Manager | A credential that isn't a Google ID token, `GoogleIdTokenParsingException`, any other exception | `Failed` | generic sign-in (as above) |
| Firebase sign-in / re-auth | `FirebaseNetworkException`, `IOException` | `AuthResult.Failure(NETWORK)` | no internet |
| Firebase sign-in / re-auth | Any `FirebaseAuthException` or other exception | `Failure(SIGN_IN_FAILED)` | generic sign-in |
| Firebase delete | `FirebaseAuthRecentLoginRequiredException` (first time) | `RecentLoginRequired` | none: a new Google token is requested, then one retry |
| Firebase delete | Still recent-login-required after re-auth, or another exception | `Failure(FAILED)` | "Couldn't delete the account. Try again." / "No se pudo eliminar la cuenta. Inténtalo de nuevo." |
| Local wipe after deleting | Any exception | `Failure(LOCAL_WIPE_FAILED)` | "Account deleted, but some workouts couldn't be removed…" |

The mapping functions are pure and JVM-tested: `googleTokenFailure` / `googleTokenFrom` (`ui/auth/GoogleCredentialMapping.kt`) and `authFailureOf` (`FirebaseAuthRepository.kt`). The same mapping applies to the re-authentication before a deletion: a failed re-auth (no token, or Firebase refusing it) shows its message and deletes nothing (`reauthenticate` runs before `delete`, and the ViewModel only calls `deleteAccount` with a token).

**Troubleshooting.** The account picker appears but sign-in fails (debug snackbar "(GetCredentialCustomException)", logcat "GetCredentialResponse error returned from framework"): check the SHA-1 of the signing key / the Android OAuth client (`client_type` 1) in `google-services.json`. Add the SHA-1 in the Firebase console (Project settings → Your apps), download `google-services.json` again and rebuild. A missing Android client was the cause of the first failure seen on a real phone.

### 18.2 Session model and settings
- `AuthSession` = `Guest` | `SignedIn(uid, displayName, email, photoUrl)`; Firebase persists the signed-in session.
- `AuthPreferences` (`SharedPreferencesAuthPreferences`, file `auth_preferences`, synchronous reads): `startChoiceMade` (Welcome done), `installChecked` (existing-install check done), `guestBannerDismissedAt` (WallClock millis).
- `ExistingInstallMigration` runs in `TioMinuterApplication.onCreate` right after Koin, before anything opens a database: on the first launch of this version, an existing `tiominuter.db` or `programs.db` means an update → stored as guest (no Welcome, banner on Home). It runs once (`installChecked`), so a fresh install that closes Welcome without choosing sees it again.
- `GuestBannerRule`: guests only; after a dismissal it reappears 7 days later (`REAPPEAR_AFTER_MILLIS`), so at most once a week. `GuestBanner` combines the session and the last dismissal; Home re-subscribes on every `OnStart`.

### 18.3 Screens
- **Welcome** (`ui/welcome/`, `welcome`): "Continue with Google" / "Continue as guest". Guest → warning dialog (only on this device; uninstalling or changing phone loses it; groups and the activity feed, coming soon, need an account) with "Continue as guest" and "Sign in with Google". The choice is stored only on success.
- **Account** (`ui/account/`, `account`): guest → explanation + "Sign in with Google" (local data kept). Signed in → avatar (Coil, account icon as placeholder/fallback), name, email, "Sign out" (confirmation; data kept; back to guest without Welcome), "Delete account" in two steps (`DeleteDialog.Explain` with the unchecked "Also delete my workouts from this device" checkbox → `DeleteDialog.Confirm`). Confirming always asks for a fresh Google token first (`RequestGoogleToken`), then `deleteAccount(wipe, reauthIdToken)` (§18.4); cancelling the re-authentication keeps everything, silently; a failed one shows the sign-in message. After a deletion: guest mode + snackbar.
- **Home**: `TopAppBar` with the account action (`AccountAvatar`, labelled "Account"); `GuestProfileBanner` (outlined card, Sign in, labelled close button) at the top of the column.
- Spanish strings keep "invitado" agreeing with "modo" ("Continuar en modo invitado"); `NeutralLanguageTest` allowlists, per key and word, `welcome_title` → "bienvenida" ("Te damos la bienvenida", noun) and `home_screen_tagline` → "no excuses" / "sin excusas": the brand tagline "No excuses — just 10 minutes a day." / "Sin excusas — solo 10 minutos al día." is a documented exception kept by the product owner (CLAUDE.md "Language guidelines"; don't change it without their approval). "sin excusas" and "no excuses" are in the tone denylist, so the phrase fails in any other string (2 tests prove it: another string with it fails, and the tagline with another denylisted word still fails; 881 JVM tests). The web's `tagline` is the same text, allowlisted the same way in `test/ui/l10n_test.dart` (125 VM tests).

### 18.4 Account deletion and the local wipe
- `FirebaseAuthRepository.deleteAccount(wipe, reauthIdToken)` (the token is required), each step only if the previous one worked: 1. `reauthenticate` with the fresh Google credential (a failure → SIGN_IN_FAILED or NETWORK; nothing erased or deleted) → 2. `UserDataEraser.eraseServerData(uid)` (every server document, §21; a failure → `SERVER_DATA_FAILED`, the user is kept) → 3. `user.delete()` (any failure after the erase, rare since the user was just re-authenticated → `ACCOUNT_NOT_DELETED`: "Your backed-up data was deleted, but your account couldn't be deleted. Try Delete account again, or sign out.") → 4. if asked, `LocalUserDataWiper.wipe()` (a failure → `LOCAL_WIPE_FAILED`, "Account deleted, but some workouts couldn't be removed…").
- **Edge case `ACCOUNT_NOT_DELETED`:** the account exists without server data. The erase marked it (`SyncStateStore.erasedAccount`, persisted), so the engine skips every sync of that account (no automatic re-upload of its local data or pending changes, also after a restart; "Back up now" does nothing) until the person retries Delete account (re-auth → nothing left to erase → the user is deleted) or signs out (the coordinator clears the mark when the session becomes a guest; signing in to that account later is an explicit choice that merges the local data back).
- `DefaultLocalUserDataWiper`: one transaction of `TioMinuterDatabase` deleting `training_log`, `weekly_goal`, `gym_exercise_progress` (`deleteAll()` queries added to the three DAOs; no schema change), then `ProgramDataCleaner.deleteAllProgramData()` (`clearAllTables()` on `ProgramDatabase`, which holds only user data), then `TrainingPhotoRepository.cleanUp()` (every photo is now unreferenced). Never touched: `exercises`, gym and daily routine catalog tables, `filesDir/content/`, the assets, the database files and schemas.
- **Tables per database.** `tiominuter.db`: cleared `training_log`, `weekly_goal`, `gym_exercise_progress`; kept (content) `exercises`, `gym_routines`, `gym_routine_exercises`, `daily_routines`, `daily_routine_exercises`. `programs.db`: cleared all four tables (`programs`, `program_workouts`, `program_slot_progress`, `program_workout_progress`), all user data: the program catalog and templates are never stored there.
- **Content in the same process (no restart needed).** Nothing the wipe clears is refilled from `ContentSource`, so no catalog has to be restored: the daily and gym catalogs stay in Room, so the "refreshed once per process" flags of `WorkoutRepositoryImpl` / `GymRoutineSelectionViewModel` still point at valid data; the program catalog and templates live in `ProgramRepositoryImpl.cachedSources` (memory, read once from `ContentSource`), which the wipe doesn't touch (it only clears the tables and the completion-entry ids). After a deletion with the wipe, Programs still shows the catalog and creating a program works. `LocalUserDataWiperTest.after_the_wipe_the_same_repositories_still_serve_content_and_create_programs` checks it in one process with the real repositories and bundled content.
- Program isolation: `ProgramDataCleaner` is a separate interface implemented by `ProgramRepositoryImpl` (same Koin instance), so `ProgramRepository` and its fakes are unchanged.

### 18.5 Koin (`authModule`, `di/AuthModule.kt`)
`AuthPreferences`, `ExistingInstallMigration` (factory; the data check looks at the database files), `LocalUserDataWiper`, `AuthRepository` (`FirebaseAuthRepository(FirebaseAuth.getInstance(), wiper)`), `GuestBanner` (factory), `WelcomeViewModel`, `AccountViewModel`. `HomeViewModel` now takes `AuthRepository` and `GuestBanner` (in `workoutModule`).

### 18.6 Tests
- JVM: `AuthRulesTest` (7), `WelcomeViewModelTest` (9: each mapped error with its cause), `AccountViewModelTest` (15: each sign-in error; a failed re-auth token or a refused re-auth deletes nothing), `GoogleCredentialMappingTest` (11: one per Credential Manager outcome, cancellation silent and unlogged, unexpected credential type, log lines without tokens or emails, debug-only cause), `FirebaseAuthRepositoryTest` (11, mocked Firebase: sign-in errors, network, `authFailureOf`, a failed re-auth never calls `delete()` nor wipes, one retry only), 4 account/banner cases in `HomeViewModelTest` (7-day rule with a fake clock), `DefaultLocalUserDataWiperTest` (2). `FakeAuthRepository` and `InMemoryAuthPreferences` live in the main source set (tests and previews).
- Instrumented (emulator only): `LocalUserDataWiperTest` (2; in-memory Room: user tables, programs and photos emptied; catalog rows and `content/` kept; and one process with the real daily, gym and program repositories over the bundled content: fill content → user data → wipe → the daily/gym routines and the program catalog and templates are still served and a new program is created).
- Verified on the emulator (Play image, no Google account): an install updated from `main` skips Welcome, shows the guest banner and keeps its weekly goal; the banner hides on dismissal and after a relaunch; a fresh install shows Welcome; in airplane mode "Continue with Google" shows the no-network snackbar; the guest dialog then Home with the banner; Back from Home leaves the app; Daily, Gym, Programs and Log activity open. Signing in with a real Google account, signing out and deleting the account (with and without the wipe) need an account on the device and were not run here.


## 19. Firestore (Phase 2a: data model and rules; used by the sync, §21)

Cloud Firestore, project `tiominuter-e43f7`, database `(default)`, Standard edition, region `southamerica-east1`, created in production mode. Phase 2a defined the model and the security rules, tested on the emulator; the app syncs with it since Phase 2b (§21), Room staying the only store the UI reads. The full model is in [`docs/firestore-model.md`](../../docs/firestore-model.md).

### 19.1 Model summary
- Everything lives under `users/{uid}`: the profile doc (displayName, photoUrl, createdAt; never the email), `trainingLog/{entryId}` (entryId = the Room `uuid`), `weeklyGoals/{effectiveFromWeek}` (one doc per Room row, so the goal history survives), `programs/{programId}` with `workouts/{week}-{dayIndex}` (the generated payload JSON, as in Room) and `completions/{week}-{dayIndex}`.
- Not synced: Room ids, `photoPath` and photos (Phase 4), gym progress and program slot progress (per-session markers; the result is already a training entry and a program completion), catalogs, auth preferences.
- Program ids: `programs.uuid` (ProgramDatabase v4) is the Firestore `programId` (Room's autoincrement id collides across devices); PROGRAM entries' `refId` names the uuid in Firestore and the local id in Room (§21).
- Every doc: `updatedAt` = server time (`request.time`), `deleted` (bool), `schemaVersion` 1. Times copied from Room (`completedAt`, `createdAt`) stay int epoch millis.
- Deletion = tombstone: the doc is replaced by `{idField, deleted: true, updatedAt, schemaVersion}` (no data left). Tombstones of random ids (`trainingLog`, `programs`) are final; natural-id docs (`weeklyGoals/{week}`, `workouts` / `completions/{week}-{dayIndex}`) can be written again with a full valid doc, since the same id comes back by design (a week getting a goal again). Owner hard delete is allowed (Delete account will remove the tree from the client; no Cloud Functions on the Spark plan).
- Conflicts: last write wins by server `updatedAt`, per document; for random ids deletion wins (their tombstones can't be revived). The app never compares a device clock with a server time (§21.4).
- **Never stricter than the app:** every rule accepts every value the app can produce now or produced in an earlier version (table "App constraints vs rules" in `docs/firestore-model.md`: field → app constraint → rule → proposed 2b limit). Automatic entries' durations can exceed a day (gym progress kept across days, a workout left open), so they take any non-negative Kotlin Int; times take any int (device clock); titles ≤ 500 and may be empty; `refId` ≤ 200; payload ≤ 200000.
- Groups (Phase 3a, §19.5): `groups/{groupId}` + `members/{uid}`, `inviteCodes/{code}`, and per person `users/{uid}/groups/{groupId}` (index) + `users/{uid}/meta/groups` (counter).
- Feed (Phase 4a, §19.6): `groups/{groupId}/posts/{postId}` (postId = the entry's uuid), with reactions and a thumbnail inside the post. Full-size photos stay on the device (Cloud Storage would need Blaze).

### 19.2 Rules (`firebase/firestore.rules`)
- Deny by default; only `request.auth.uid == uid` reads or writes under `users/{uid}`. The group paths have their own rules (§19.5); any other path (a stray top-level collection, `users/{uid}/settings`…) is denied.
- One `isValid…` helper per doc type: `keys().hasAll(required)` + `hasOnly(required + optional)`, types, enums (the app's codes), string lengths, ranges (weekly goal 1–14; durations 0–2147483647 s, an ACTIVITY 60–36000 s; program profile ranges of `ProgramProfile`), `dateLocal` regex (4+ year digits), lowercase-UUID ids, path id = id field (`workoutId` = `"{week}-{dayIndex}"`), `updatedAt == request.time`, `deleted == false` on live docs, `schemaVersion == 1`. ACTIVITY entries need `activityType`, `intensity` and a duration; other sources must not have the first two.
- `canCreate`: a valid live doc or a tombstone. `canUpdateRandomId` (entries, programs): an update of a tombstone accepts only another tombstone. `canUpdateNaturalId` (weekly goals, workouts, completions): a valid doc or a tombstone, also over a tombstone. The user doc's `createdAt` is `request.time` on create and unchanged afterwards. Owner `delete` everywhere.
- `firebase/firestore.indexes.json`: the sync's queries (one field, `updatedAt`) use the automatic single-field indexes; the only composite index is the feed's (`posts`: `weekStartLocal` ASC, `createdAt` DESC), and `posts.thumb` / `posts.reactions` are exempt from indexing (§19.6).
- `firebase.json`: `firestore` (rules + indexes) and `emulators` (Firestore on 8080, UI off, `singleProjectMode`); the hosting block is unchanged.

### 19.3 Tests (`firebase/rules-tests/`)
- Node project (`package.json` + `package-lock.json`, ESM): `@firebase/rules-unit-testing` 5.0.2, `firebase` 12.19.0, `vitest` 4.1.12, all pinned. (rules-unit-testing 6.x needs Node ≥ 24.12, so it can't run on the Node 22 CI.) Fixtures in `test/fixtures.js` (a valid doc of every type and the tombstone builder; group, member, invite and index docs), cases in `test/rules.test.js` (74), `test/groups.test.js` (40, §19.5) and `test/feed.test.js` (38, §19.6): **152 in total**, all green.
- `scripts/test_rules.sh` installs the dependencies when missing, then runs `firebase emulators:exec --only firestore --project tiominuter-e43f7 "npm --prefix firebase/rules-tests test"`. Needs Java 21 and firebase-tools.
- 74 cases: unauthenticated (every type, list); another user (every type, list); owner create/read/update/delete of every type, optional fields absent or null, every source, goal limits; tombstones (write, final, create without a live doc, owner only, no data, the user doc has none); rejected writes (missing field, extra key, wrong type, bad enum, inconsistent activity fields, bad `dateLocal`, goal 0 and 15, negative or too long durations, profile ranges and workout keys, strings too long, `updatedAt` ≠ request.time, wrong `schemaVersion`, path id ≠ doc id, non-UUID ids, user `createdAt`); paths outside `users/{uid}`; natural ids come back after a deletion (set goal → delete → set again for the same week; workouts and completions; owner only, valid doc only; entries and programs stay deleted); edge values the app allows (activity 60 and 36000 s; a 40-character custom name with accents or 20 emoji; a 200-character note; every activity type and intensity; null durations on DAILY / GYM / PROGRAM; durations over a day up to the Int maximum; a v3 entry without optional fields; empty and 500-character titles; any clock value; a 5-digit year; goals 1–14; profile limits and decimal weights; week 12 day 4; every kind; a 200000-character payload; a 500-character display name). Green on Node 26 (local) and Node 22.23.3. Loosening a rule makes a case fail (checked).
- **Real-data check:** `node scripts/validate_backup_against_rules.js <backup.tar>` (logic in `firebase/rules-tests/backup/validateBackup.js`, `node:sqlite`, Node 22.13+) extracts only `databases/` to a temp dir outside the repo, checkpoints the WAL in that copy, opens the databases read-only, maps every row as `docs/firestore-model.md` says (a new UUID per program, PROGRAM `refId`s rewritten), writes each doc as the owner into the emulator (through `firebase emulators:exec`), prints counts and rejected field names only (never values), deletes the temp dir and exits 1 on any rejection. A rejected doc's fields are found by swapping each one for the fixture's valid value. The phone backup of 2026-10-20: 31 docs (1 program, 24 workouts, 1 completion, 3 entries, 1 weekly goal, the profile), 31 accepted; not synced by design: 5 program slot markers, 2 photo paths.

### 19.4 CI and deployment
- `.github/workflows/firestore-rules.yml`: pull requests and pushes to `main` touching `firebase/**`, `firebase.json`, `scripts/test_rules.sh` or the workflow. Node 22 (npm cache from the lockfile), Java 21 (Temurin), `npm ci`, `firebase-tools@15.34.0`, `scripts/test_rules.sh`. It never deploys (the CI service account only has Hosting admin).
- Rules and indexes are deployed **by hand** after the merge to `main`: `firebase deploy --only firestore:rules,firestore:indexes --project tiominuter-e43f7` (check first with `--dry-run`, which compiles the rules on the server without deploying).
- `content-deploy.yml` still deploys Hosting only (`action-hosting-deploy`). It also triggers on `firebase.json` changes, so merging this step redeploys the same Hosting content once.

### 19.5 Groups (Phase 3a: model, rules, tests; no app code yet)
Full model: `docs/firestore-model.md` "Groups". The app side is Phase 3b.
- **Model.** `groups/{groupId}` (auto id; `name` 1–40, `ownerUid`, `inviteCode`, `memberCount` 1–20, `memberChangeUid`, `createdAt`, `updatedAt`, `schemaVersion`). `members/{uid}`: `displayName` 1–100, `photoUrl` https or null, `joinedAt`, `inviteCode` (the join's proof, never changes), `weekStartLocal` (a Monday), `workoutsThisWeek` 0–100, `weeklyGoal` 1–14, `streakWeeks` 0–1000, `daysThisWeek` (`"1"`..`"7"` → distinct sources, ≤ 4), `lastTrainedLocal` or null, `updatedAt`, `schemaVersion`: nothing private (no durations, notes, titles, measurements, photos). `inviteCodes/{code}` (8 characters without `0 O 1 I L`): `groupId`, `groupName`, `createdAt`, `schemaVersion`. Per person: `users/{uid}/groups/{groupId}` (`groupId`, `joinedAt`, `schemaVersion`; create/delete only) and the counter `users/{uid}/meta/groups` (`groupCount` 0–5, `lastGroupId`, `updatedAt`, `schemaVersion`). Group docs have no `deleted` (not outbox-synced; written live).
- **Changes to the suggested shape.** `memberChangeUid` (names the member whose join / leave / removal moves `memberCount`, so the group and member rules check each other; otherwise an owner could decrement the count alone and exceed 20); the counter is its own doc with `lastGroupId` (not a field of the sync-written profile; pairs every ±1 with one index entry); `schemaVersion` on invites and index entries.
- **Rules strategy.** Spark plan, no Cloud Functions: every operation is one batch and each document's rule checks the others with `get` (before) / `getAfter` / `existsAfter` (after). Create: owner = creator, `memberCount` 1, own member doc, invite doc pointing back (same name), index + counter ≤ 5. Join: `increment(1)` (a joiner can't read the group) ≤ 20, member doc's `inviteCode` = the group's current code and `inviteCodes/{code}.groupId` = the group, index + counter. Leave: −1, own member doc and index deleted; an owner must set `ownerUid` to another member in the same write (alone: delete the group). Remove: owner, not self, −1 with that member's doc deleted. Transfer: owner → a current member. Regenerate: owner, old invite deleted, new one created. Rename: owner, invite `groupName` updated in the batch. Delete: owner, invite and own member doc deleted (other member docs too; a missed one is unreadable and its user deletes it). Reads: members only (`exists(members/{auth.uid})`), own member doc always, invite `get` only. `isSignedIn()` rejects anonymous Firebase users. The counter moves by exactly ±1 per batch, so one group per batch. Member summary: `hasExactly`, ranges, `isMonday` (`timestamp.date(y, m, d).dayOfWeek() == 1`, an impossible date is denied), sources enum + `toSet().size() == size()`, `updatedAt == request.time`, `schemaVersion == 1`; `joinedAt` and `inviteCode` never change; only while the group exists.
- **Reconciliation** (each app, for its own index): `get groups/{g}/members/{me}`; not found → one batch deleting the index entry with counter −1 (allowed only when the member doc doesn't exist after the batch), plus a left-behind member doc when the group is gone. Delete account (3b): reconcile, leave member groups, leave owned groups handing them to the oldest member by `joinedAt` (not checkable by rules), delete solo groups, then delete the counter (allowed at 0).
- **Ranking and banner** computed by readers: a summary whose `weekStartLocal` isn't the reader's current Monday counts as 0 workouts; compliance = min(workouts / weeklyGoal, 1), then `streakWeeks`, then name, then uid. Banner: `lastTrainedLocal`.
- **Tests** (`test/groups.test.js`, 40; batches in `test/groupBatches.js`): create (5-group limit, missing writes, bad fields, anonymous, taken code), join (valid, old code after regeneration, wrong / other group's code, 20th ok / 21st denied, 6th group, partial batches, twice), reads (non-member, list groups / codes, others' index, removed member), summaries (own, others', every invalid field and date, private fields), leave (member, owner with members denied, owner with transfer, owner alone), remove (owner, non-owner, self, reconciliation), counter integrity, transfer, rename, regenerate, delete (full 20-member group in one batch, member denied, invite kept denied, left-behind doc), the whole delete-account cleanup. 31 rule mutations each made a test fail. The 74 earlier cases are unchanged and green. `firebase deploy --only firestore:rules,firestore:indexes --dry-run` compiles; nothing deployed.
- **Open for 3b:** production may count the 20-member Delete group's lookups past the 20-per-batch limit (the emulator accepts it); then remove members in smaller batches first.

### 19.6 Feed (Phase 4a: model, rules, tests; no app code yet)
Full model: `docs/firestore-model.md` "Feed". The app side is Phase 4b.
- **Model.** `groups/{groupId}/posts/{postId}`, postId = the training entry's uuid (lowercase, checked): one post per counted workout and group, found by path when the entry is deleted. Fields: `authorUid` (= auth uid), `authorName` 1–100, `authorPhotoUrl` (https ≤ 2048, null or absent), `source` (4 codes), `title` ≤ 500 (may be empty; what the author's app shows, in the author's language), `activityType` (one of the 14 codes, ACTIVITY only, so readers localize it), `titleKey` (optional, ≤ 80, `gym/<id>` / `daily/<day>` / `program/<CODE>` only on its own source, immutable; §24), `durationSeconds` (0–2147483647, null or absent), `dateLocal` (inside the week of `weekStartLocal`), `weekStartLocal` (the Monday of `dateLocal`, `isMonday`), `createdAt` (`request.time`), `reactions` (map uid → `strong` / `fire` / `clap` / `raised`, empty on create), `thumb` (bytes ≤ 65,536, null or absent), `schemaVersion` 1. Nothing else (no note, intensity, `refId`, `photoPath`; no `updatedAt` / `deleted`: written live, hard-deleted).
- **Changes to the suggested shape.** No `expiresAt`: a post expires at `createdAt + duration.value(8, 'd')` computed in the rules, because a client-written expiry comes from the device clock and validating it against `request.time` would deny posts from a wrong clock or a write queued offline. `activityType` added. A member's delete of a post that doesn't exist is allowed (no-op), so deleting an entry needs no read.
- **Reactions = a map on the post** (1 read per post with its reactions, vs a read per reaction for a subcollection); the rules allow only `reactions` to change and only the writer's key (`diff().affectedKeys().hasOnly([auth.uid])`), with a valid code or removed; the app writes `FieldPath.of("reactions", uid)` so concurrent reactions never overwrite each other.
- **Rules.** Read: members of an existing group. Create: a member, as themselves, every field validated, server `createdAt`, empty reactions. Update: the author changes only `thumb`; any member changes only their reaction; never both in one write; members only. Delete: the author (also after leaving), the group owner (the group must still exist: `get()` before the batch), any member after 8 days, any member for a missing post.
- **Expiration on Spark.** TTL policies need billing ("TTL deletes" are listed among the features without free usage), so the client cleans up: opening a feed queries `createdAt < now − 8 days` (limit 20) and deletes each post on its own, skipping refusals (the device clock may run ahead); app start does it per group, throttled. If the project moves to Blaze: `gcloud firestore fields ttls update createdAt --collection-group=posts --enable-ttl --expiration-offset=8d` (manual), keeping the client cleanup.
- **Operations (4b).** One write per group to post (≤ 5); leave = delete my posts (query `authorUid == me`), then the Leave batch; remove member = the Remove batch, then the owner deletes their posts; delete group = posts, then members, then the final batch; Delete account = my posts in every group before the group cleanup of §22. Owner bulk deletes: one write each or batches of ≤ 10 (lookups per batch).
- **Indexes.** Composite `posts` (`weekStartLocal` ASC, `createdAt` DESC) for the week feed; `thumb` and `reactions` exempt.
- **Tests** (`test/feed.test.js`, 33; operations in `test/feedOps.js`): create (with / without thumb, every value the app produces, a non-member / guest / anonymous / other uid, every invalid field and date, client `createdAt`, initial reactions, `expiresAt`, private fields, ids, thumb 65,537 bytes and wrong types, twice, one write per group), reads (week query; non-member, removed member, deleted group), reactions (add / change / remove; another key, whole map, invalid codes, other fields, non-member, removed member), thumbnail (set / replace / remove; other fields, size, type, others), delete (author, owner, member before / after 8 days, non-member, the cleanup with a clock 2 h ahead, no-op, an entry across groups), and the leave / remove-member / delete-group / missed-post / Delete-account sequences. 32 rule mutations each made a test fail. The 114 earlier tests are unchanged and green: 147. `firebase deploy --only firestore:rules,firestore:indexes --dry-run` compiles; nothing deployed. **`titleKey`** (after 4b): 5 more tests (valid per kind, absent / null, bad formats, another source's key, 81 characters, immutable through every update) and 16 more mutations: 38 feed tests, 48 feed mutations all killed, **152 in total**.

## 20. Workout sessions (inactivity close, real and bounded durations)

A gym workout's duration is the real one: first completed set → last completed set. A session closes automatically after 2 hours without a new completed set.

### 20.1 Diagnosis (before this change)
- Room kept no per-set time: only `gym_exercise_progress.completedAt`, one row per **exercise slot**, written by `GymExerciseViewModel.completeExercise` after the slot's last set (the sets in between live only in the ViewModel).
- The entry was written only on full routine completion, by `GymRoutineViewModel` (when `showRoutineCompleted` turned true), with duration = max − min of the slot times (null below two). Progress is kept across days, so a routine half-done on Monday and finished on Wednesday recorded a two-day workout; the first exercise's own time was never counted. Abandoned routines were never recorded.
- Daily and Program: see §20.6.

### 20.2 Session model
- `GymSession(routineId, sessionStartedAt, lastSetAt)` (wall-clock millis, `WallClock`): the first completed set and the latest one, updated on **every** set. `durationSeconds = max(0, lastSetAt − sessionStartedAt) / 1000`.
- `SESSION_INACTIVITY_TIMEOUT` = 2 h (single constant, shared since §20.6). `GymSessionRules.isInactive(lastSetAt, now)` = `now ≥ lastSetAt && now − lastSetAt > timeout` (a clock moved back never closes). `afterSet` starts or extends a session (a set before the start moves the start, so the duration can't be negative).
- `GymSessionTracker` (implements `GymSessionRepository`, Koin `single`): `onSetCompleted(routineId)`, `completeSession(routineId, title): Long?`, `closeInactiveSessions()`, `discardSession(routineId)`, `takeClosedSessions()`. Each operation: `NonCancellable` + one `Mutex` + one `GymSessionStore.inTransaction`; failures are logged (`println`) and return a neutral value.
- Close = `SessionEntryRecorder.recordSession` (GYM, `gym:<routineId>`, the routine's English name, the duration, `completedAt` = `lastSetAt`, `dateLocal` from `sessionStartedAt`) + `clearProgress` + delete the session, all in one transaction. Normal completion records the same duration with today's date and time (as before), deletes the session and keeps the progress for the dialog; a manual reset deletes the session only.

### 20.3 Data (database version 7)
- `gym_session` (`GymSessionEntity`: `routineId` PK, FK → `gym_routines` CASCADE like the progress, `sessionStartedAt`, `lastSetAt`), `GymSessionDao` (`get`, `getAll`, `@Upsert upsert`, `delete`, `deleteAll`), `RoomGymSessionStore` (sessions, `gymProgressDao().clearRoutine`, the routine name, `database.withTransaction`). `training_log` is unchanged.
- v6 → v7: `AutoMigration` with `Migration6To7`, whose `onPostMigrate` gives each routine with progress in flight (some, not all, slots completed) a session from its slot times: the latest run with no gap over 2 h (`GymSessionRules.fromCompletionTimes`). Routines with every slot completed get none (their entry was recorded when they were completed). After an upgrade, a stale in-flight routine is therefore closed at the first check with the duration of its latest run, not of the whole span.
- The local wipe (§18.4) also clears `gym_session`.

### 20.4 Close triggers (lazy, no background work)
1. `HomeViewModel` on every `OnStart` (app start and returns to Home), before restarting the week observation.
2. `GymRoutineSelectionViewModel` and `GymRoutineViewModel` on `OnResume` (`LifecycleResumeEffect`: every open and return to the foreground); they then emit `ShowSessionSaved` for each session in `takeClosedSessions()`, shown as a Toast "Your unfinished workout was saved (1 h 05 min)." (`showSessionSaved`, `formatSessionDuration`: minutes rounded down, at least 1, like the history; strings `gym_session_saved`, `gym_session_duration_hours_minutes`, `gym_session_duration_minutes`).
3. `GymExerciseViewModel` on every completed set (`onSetCompleted`: an inactive previous session is closed first; then the set starts a new session). Non-last sets launch it without delaying the next phase; the last set awaits it in `completeExercise` before `markCompleted`.
No timers, services, alarms or WorkManager.

### 20.5 Tests
- JVM: `GymSessionTrackerTest` (19: 0/10/40 min checked at 2 h 41 → one 40-min entry dated on the start, completed at the last set, progress reset; 2 h 39 → nothing; exactly 2 h → nothing; 2 h 30 continuous; a set 3 h later closes and restarts; one set → 0 s, not counted; clock moved back; double and concurrent checks → one entry; a check racing a set; every routine on its own; discard; rollback and retry; a failure while noting a set; `fromCompletionTimes`; the timeout), `GymSessionSavedMessageTest` (2), `GymRoutineViewModelTest` (recording through the session, no second entry without a session, reset discards, `OnResume` closes, resets and announces once), `GymRoutineSelectionViewModelTest` (`OnResume`, a close done by Home announced later), `GymExerciseViewModelTest` (every set noted, the last one before the marker, a late set saves the abandoned session), `HomeViewModelTest` (`OnStart` closes), `DefaultLocalUserDataWiperTest` (sessions wiped). Doubles: `testutil/GymSessionTestDoubles.kt` (`InMemoryGymSessionStore` with rollback, `RecordingSessionEntryRecorder`, `FakeWallClock`, `GymSessionTestKit`).
- Instrumented (emulator only): `GymSessionStoreTest` (2: real Room and training log; an abandoned session becomes one entry dated on its start, a failed close rolls the inserted entry back), `MigrationTest` 6 → 7 (derived session, completed routine skipped, CASCADE) and 1 → 7, `LocalUserDataWiperTest` (sessions wiped).
- Emulator: Pull-ups (4 sets) completed, app stopped, clock +3 h (`cmd alarm set-time`, automatic time restored afterwards), app reopened → the history shows "Pull Day · Gym routine · 1 min · Doesn't count toward the goal" completed at the time of the last set; the gym screen showed the saved message; the routine was back to "0 of 11".

### 20.6 Shared session core (`domain/session/`)
The gym's generic parts were extracted and every source now uses them; the gym's behaviour and tests are unchanged (only `GYM_SESSION_INACTIVITY_TIMEOUT` became the shared `SESSION_INACTIVITY_TIMEOUT`).
- `Session.kt`: `SESSION_INACTIVITY_TIMEOUT` (2 h), `SessionRules` (`isInactive` — never when the clock moved back —, `durationSeconds` clamped at 0, `latestRun` for migrations), `SessionTransactions`, `SessionLock` (one `Mutex` + one transaction + `NonCancellable`; failures logged, default returned), `ClosedSession`, `ClosedSessionNotices`, `InactiveSessionCloser` (what Home runs at start).
- `ActiveSession.kt`: `ActiveSession(key = refId, startedAt, activeSeconds, lastActivityAt)`, `ActiveSessionStore`, `ActiveSessionTracker` (`start` records an open session of the kind first; `update` saves the active time, or — session gone, another workout or inactive — records the old one and starts a new one, returning false so the caller restarts its count; `finish` ends without recording; `closeInactive`; a close removes then records), `ActiveTimeMeter` (counts with `MonotonicTimeSource` only while running and visible; `countsInBackground` for the outdoor run).
- `ActiveWorkoutSession.kt`: the ViewModel helper: `start` (first real action; returns the closed sessions to announce), `setRunning`, `setVisible`, `onUserAction`, `finish` (returns the active seconds to record once), `close`; saves every 5 s while counting (the loop stops when the time didn't move, e.g. a frozen test clock) and on every change.
- `GymSessionTracker` uses `SessionRules`, `SessionLock` and `ClosedSessionNotices`; `GymSessionRepository` is an `InactiveSessionCloser`.

### 20.7 Program strength
- Gym model per workout: `ProgramStrengthSessions(StrengthSessionStore, WallClock)` in `domain/program/repository/` (`onSetCompleted`, `closeInactive`, `takeClosedSessions`); `ProgramStrengthSessionStore` on `program_strength_session` (PK programId+week+dayIndex, FK `program_workouts` CASCADE, `sessionStartedAt`, `lastActivityAt`).
- `ProgramStrengthExerciseViewModel` reports every set (`repository.onStrengthSetCompleted`): launched for non-last sets, awaited before `markSlotCompleted` for the last one.
- **Inactivity close leaves the workout resumable** (decision): one PROGRAM entry (real duration, dated on the start, title from `ProgramWorkoutTitles`), the session removed, the slots kept and the workout **not** marked completed. Closing "as on a normal completion" would mark an unfinished workout completed in the program (progress %, detail check marks) and clear nothing, so the next visit couldn't finish it as a workout. Finishing the remaining slots later is a new session and records its own entry.
- Normal completion: `markSlotCompleted`, when it completes the workout, reads and deletes the session in its own `programs.db` transaction and records its duration; re-marking a completed workout records nothing; without a session (sets never reported) the duration is null. `resetWorkout` deletes the session.

### 20.8 Daily and program cardio: active time
**Rule: active time matches what the workout does.** It counts while the workout's timer runs (countdown or stopwatch), in the foreground or the background, screen on or off, because those timers keep running there (coroutines in the ViewModel, computed from end timestamps of `MonotonicTimeSource`). Excluded: explicit pauses and states that wait for the person with no timer running. Daily REPS exercises (untimed, the person exercises before tapping "Done") count only while the screen is visible. `ActiveTimeMeter`: `setTimerRunning` (counts anywhere), `setActiveOnScreen` + `setVisible` (counts while visible). Each ViewModel maps its state to these inputs (`state.map { … }.distinctUntilChanged()`), so a timer that stops for any reason stops the count. After a process death the time up to the last 5-s save counts.

| Flow | Timer runs in the background (before and now)? | Waiting-for-input states (not counted) | Counted now |
|---|---|---|---|
| Daily | Yes: Get Ready (5 s), TIME exercise and rest timers are ViewModel coroutines; they stop only on Back, skip, next or the end | A TIME exercise loaded and waiting for "Get Ready"; the finished workout | Get Ready + TIME + rest timers, foreground or background; a REPS exercise only while the screen is visible |
| Treadmill | Yes: the segment countdown keeps running and segments advance | Before Start; Pause (explicit); after the end | Every running second, foreground or background; not paused time |
| Circuit | Yes: work, rest and rest between rounds run back to back | Before Start; after the end (no pause control) | Start → end, foreground or background (rests included); Leave stops it |
| Outdoor run | Yes: the stopwatch | Before Start; Pause; after Finish | The stopwatch (unchanged) |

- Daily: `WorkoutViewModel` owns an `ActiveWorkoutSession` (key `dailyRefId(routineId)`) over the daily tracker (`named(SessionQualifiers.DAILY)`, `DailyActiveSessionStore` on `daily_session`: routineId PK, startedAt, activeSeconds, lastActivityAt; no FK). Inputs from `WorkoutState`: timer = `isTimerRunning || isReadyCountdown`, REPS on screen = the current slot is REPS (both only while in progress); visibility from `LifecycleResumeEffect` (`OnScreenVisible` / `OnScreenHidden`, which matter only for REPS). The session is stored at the first action (`OnNextExercise`, `OnCompleteReps`, `OnSkipExercise`, `OnFinishWorkout`, `OnStartTimerWithReady`); Back stops the timers and marks the screen gone, so nothing counts and the session waits for the inactivity close. Daily has no pause control; leaving the screen is not a pause while a countdown keeps running. Finish: `session.finish()` then `recordSafely` in a `NonCancellable` launch (the navigation clears the ViewModel).
- Program cardio: the treadmill, circuit and outdoor-run ViewModels get the `PROGRAM_CARDIO` tracker (`ProgramRepositoryImpl.cardioSessions`, `ProgramCardioSessionStore` on `program_cardio_session`, at most one row for all cardio). Start stores the session (an open cardio session is recorded first and announced). Timer running = treadmill `isRunning`, circuit `phase != null && !isFinished` (and "Leave" stops it explicitly, since the phase stays in the state), outdoor run `isRunning`. No visibility input (no untimed steps). Finish records `session.finish()` through `markWorkoutCompleted`. An abandoned cardio session is recorded with its active time and the workout is not marked completed.
- `DailyRoutineSelection` (`OnResume`), program list / detail / strength (`OnScreenResumed`) close inactive sessions and show `ShowSessionSaved`; the workout and cardio screens announce what `start` closed.

### 20.9 Data (TioMinuterDatabase v8, ProgramDatabase v3)
- `TioMinuterDatabase` v7 → v8: `AutoMigration` adding `daily_session` (nothing to derive: daily time used to live in memory). The local wipe clears it.
- `ProgramDatabase` v2 → v3: `AutoMigration` + `Migration2To3` adding `program_strength_session` and `program_cardio_session` (FKs to `program_workouts`, CASCADE); `onPostMigrate` decodes each workout's payload and gives every strength workout with some (not all) slots done a session from the latest run of its slot times. `clearAllTables` (local wipe) covers both tables. Phase 2b's `programs.uuid` will be a later version.

### 20.10 Tests (sessions in every source)
- JVM: `ActiveSessionTrackerTest` (12), `ActiveWorkoutSessionTest` (9: a running timer counts in the background, an explicit pause doesn't, an untimed step only while visible and a waiting state not at all, saves, process death, restart after 2 h, finish once, start announces), `ProgramStrengthSessionsTest` (9: 0/10/40 min → one 40-min entry dated on the start; 2 h 39 → nothing; 2 h 30 continuous; a set 3 h later closes and restarts; one set → 0 s, not counted; clock moved back; double / concurrent checks; per workout; rollback), Daily cases in `WorkoutViewModelTrainingLogTest` (a running timer counts in the background; waiting for Get Ready is not counted, in the foreground or the background; a REPS exercise in the background is not counted; process death → the time up to the last save; nothing stored before acting; unfinished → recorded after 2 h; a new workout records the old and announces it; a normal finish records once), cardio cases (treadmill: the running countdown counts in the background, a pause doesn't, process death keeps the last save, leaving keeps the time for the close, starting records the old one; circuit: the whole run counts, leaving stops it; outdoor run counted in the background and saved meanwhile), `ProgramStrengthExerciseViewModelTest` (sets reported, the last before the slot), `GymSessionTrackerTest` unchanged. Doubles: `testutil/ActiveSessionTestDoubles.kt` (`InMemoryActiveSessionStore` with rollback and a save log, `ActiveSessionTestKit`).
- Instrumented (emulator only): `ProgramRepositoryTest` (completion from the session and nothing left to close; abandoned strength recorded once and resumable, finished later as a new session; abandoned cardio recorded with its active time and not completed; reset / delete discard sessions), `MigrationTest` 7 → 8 and 1 → 8, `ProgramMigrationTest` 2 → 3 (derived session, completed workout skipped, CASCADE) and 1 → 3, `LocalUserDataWiperTest` with the new DAO.

## 21. Sync with Firestore (Phase 2b, offline-first)

A signed-in account's training log, weekly goals and programs (workouts, completions) are backed up to and synced with Firestore. **Room stays the only source of truth for the UI**: the UI reads Room only and refreshes through its existing flows (weekly goal, history, programs). Guests sync nothing. The contract is `docs/firestore-model.md`; the rules are deployed.

### 21.1 Architecture
```
Repositories ──(row + outbox change, ONE transaction: ChangeTracker)──► Room (tiominuter.db, programs.db)
                                                                            │  sync_outbox in each database
SyncCoordinator (app scope) ── session / debounced outbox count ──► SyncEngine (Mutex)
                                                                            │  pull, then push
             LocalSyncStore: AppSyncStore (log, goals) · ProgramSyncStore (programs, workouts, completions)
                                                                            │
                                         RemoteUserData: FirestoreRemoteUserData ──► users/{uid}/…
```
- Domain (`domain/sync/`): `SyncCollection` (`path`, `idField`, `hasRandomIds`, `isProgramChild`), `DocKey` (collection, id, parentId = program uuid), `PendingChange`, `OutgoingDoc` (fields or null = tombstone), `IncomingDoc`, `SyncTime` (seconds + nanos), `PullQuery`, `PageCursor`, `RemotePage`; ports `ChangeTracker` (+ `None`), `LocalSyncStore`, `RemoteUserData`, `SyncStateStore`, `NetworkMonitor`, `SyncScheduler`; `SyncConflicts`, `SyncEngine`, `SyncCoordinator` (implements `SyncRepository` for the UI and `UserDataEraser` for Delete account), `ProgramRefMapper` / `ProgramRefIds`.
- Data (`data/sync/`): `SyncOutboxEntity` / `SyncOutboxDao` (in both databases), `RoomChangeTracker`, `AppSyncDao`, `TrainingLogDocMapper`, `WeeklyGoalDocMapper`, `AppSyncStore`, `FirestoreRemoteUserData`, `SharedPreferencesSyncStateStore` (file `sync_state`: `lastSyncedUid`, `wm|<uid>|<collection>`, `ok|<uid>`), `AndroidNetworkMonitor`, `WorkManagerSyncScheduler`, `SyncWorker`. Program side (`data/program/sync/`): `ProgramSyncStore` (+ `ProgramDocMapper`, `ProgramWorkoutDocMapper`, `ProgramCompletionDocMapper`), `ProgramSyncDao`. Program isolation: the sync code reaches the program feature only through `LocalSyncStore` and `ProgramRefMapper` (`programModule`, `named(SyncQualifiers.PROGRAM)`).
- Koin `syncModule`: Firestore with a memory-only cache, the remote, state, network, scheduler, the app `ChangeTracker` and store (`SyncQualifiers.APP`), the engine (program store first), the coordinator (its own `SupervisorJob` + `Dispatchers.Default` scope) as `SyncRepository` and `UserDataEraser`. `FirebaseAuthRepository` resolves its eraser lazily (a direct dependency is a Koin cycle with the coordinator, which needs the `AuthRepository`; it crashed the app start before the fix).

### 21.2 Change tracking: an outbox
**Decision: an outbox table, not per-row dirty flags.** Deletions must be remembered after the row is gone (a tombstone); dirty flags would need soft-deleted rows in every table and changes to every query. The outbox leaves the entity schemas untouched (only `programs.uuid` was needed), coalesces repeated writes into one row per document, and makes "pending changes" a single `COUNT(*)` (the debounce and the sign-out warning). Each database has its own `sync_outbox` so the change is in the same transaction as the row: tiominuter.db (training log, weekly goals) and programs.db (programs, workouts, completions).
- Row: `collection`, `parentId` ('' or the program uuid), `docId`, `isDelete`, `version` (+1 on every write of the document), `enqueuedAt`; PK (collection, parentId, docId).
- Writers: `TrainingLogRepositoryImpl` (`record`, `recordSession`, `addActivityEntry`, `updateActivityEntry`, `deleteEntry` → tombstone; `setPhotoPath` is not synced), `WeeklyGoalRepositoryImpl.setGoal`, `ProgramRepositoryImpl` (`createProgram`: the program + every workout; `markSlotCompleted` / `markWorkoutCompleted`: the completion; `deleteProgram`: the program's tombstone). Each through a `ChangeTracker` whose default is `None` (test fakes unchanged).
- Acknowledge = delete the row only if `version` is unchanged (a write during the upload stays pending). A deleted program's pending children are dropped (they are not uploaded under a tombstone). The local wipe clears both outboxes.

### 21.3 A sync run (`SyncEngine.sync(uid)`, one at a time)
1. Offline → schedule the WorkManager retry, return `OFFLINE`.
2. Account check: `lastSyncedUid` = uid → incremental; null (guest data) → `enqueueAll` on every store, watermarks forgotten (full pull) → a merge by id; another uid → `LocalUserDataWiper` (automatic), watermarks forgotten, full download. `lastSyncedUid` = uid.
3. Pull, store by store (programs first, so PROGRAM refIds resolve): every `pullPlan` query (`programs`, then `workouts` / `completions` of each local program; a program new on this device gets its children fetched in full) from the watermark (inclusive: re-applying is harmless), ordered by (updatedAt, document id), pages of 200 with a cursor; each page applied in one Room transaction with the conflict policy; watermarks saved when the store's pull completes.
4. Push, store by store: batches of ≤ 500 (`commit`), acknowledged after each commit.
5. Success → `lastSuccessAt`; failure → `failing`, the outbox kept, a retry scheduled.

### 21.4 Conflicts (`SyncConflicts`)
| Remote document | Local pending change? | Result |
|---|---|---|
| any | no | applied (upsert, or delete on a tombstone; a natural-id doc after a tombstone is written again) |
| uuid tombstone (entry, program) | yes | applied: deletion wins, the pending change is dropped (the rules would reject it anyway) |
| any other (weekly goals included) | yes | the local version is kept and pushed (it gets a newer server time) |

No device clock is ever compared with a server time: a phone's clock can be far in the past or the future (`SyncEngineTest` checks that absurd local times change nothing).

### 21.5 Room ↔ Firestore and program ids
- Documents exactly as docs/firestore-model.md (§19, tested by `DocMappersTest`); the remote adds `updatedAt = serverTimestamp()`, `deleted`, `schemaVersion` 1; a tombstone is a `set` of the id field + those three.
- `programs.uuid` (v4, random lowercase, backfilled by `Migration3To4`) is the program's Firestore id; local ids never leave the device. `ProgramRefIds.toRemote` / `toLocal` rewrite PROGRAM refIds ("program:<localId>:w:d" ↔ "program:<uuid>:w:d"); a program unknown to the other side keeps the refId as it is.
- Pulled rows keep the device's own id and `photoPath`; their local `updatedAt` is the server time.

### 21.6 Triggers, status, sign-out, deletion
- `SyncCoordinator.start()` (TioMinuterApplication): every signed-in session (sign-in and app start: profile `users/{uid}` — created with a server `createdAt`, then merged without it; name ≤ 500, https photo or null — then a sync), the outbox count debounced 5 s while signed in, WorkManager (`firestore-sync`, KEEP, `CONNECTED`, exponential backoff 30 s) when offline or failing; `SyncWorker` → `syncNow()`.
- Account screen: "Backed up · <relative time>", "X changes pending", "Couldn't back up, will retry", "Backing up…", "Back up now". Sign-out with pending changes: "You have X changes that aren't backed up yet." → "Back up now" (sync, then sign out; a failed backup keeps the session), "Sign out anyway", "Cancel".
- Delete account (§18.4): **a fresh re-authentication always first** → `UserDataEraser` (`SyncCoordinator.eraseServerData` → `SyncEngine.eraseServerData` under the sync lock: `RemoteUserData.deleteAll` deletes every program's workouts and completions, the programs, the log, the goals and the profile in batches; only once all is deleted it marks the account erased (`SyncStateStore.erasedAccount`), forgets its sync state and makes the local data guest data) → Firebase user → optional local wipe. A server-data failure aborts with `SERVER_DATA_FAILED` and marks nothing (a later sync restores what was already deleted). While an account is marked erased, `SyncEngine.sync` returns SKIPPED for it; `SyncCoordinator` clears the mark when the session becomes a guest (deleted or signed out).
- Firestore: memory cache only (Room is the local copy), reads with `Source.SERVER` (an offline cache can never move a watermark), 60 s timeouts.

### 21.7 Databases
- `TioMinuterDatabase` v8 → v9: `AutoMigration` adding `sync_outbox`.
- `ProgramDatabase` v3 → v4: `AutoMigration` + `Migration3To4`: `programs.uuid` (TEXT NOT NULL DEFAULT '', indexed, not unique because the backfill runs after the index) and `sync_outbox`; every program gets a random lowercase uuid (idempotent).

### 21.8 Tests
- JVM: `SyncEngineTest` (22: … weekly goals without clocks (3), no re-upload after the erase also after a restart, a failed erase marks nothing, the mark lifted), `SyncCoordinatorTest` (6: … no automatic upload after the erase, sign-out lifts it), `DocMappersTest` (6), Account (sign-out with and without pending changes, back up first, its failure, sign out anyway, back up now; delete: re-authentication asked first, cancelled → nothing and silent, failed token, Firebase refusing the re-auth, server-data failure, `ACCOUNT_NOT_DELETED`, network), `FirebaseAuthRepositoryTest` (delete order re-auth → erase → user → wipe, no wipe when not asked, failed re-auth or no network → nothing erased, erase failure keeps the user, user deletion failure after the erase → `ACCOUNT_NOT_DELETED`). Doubles: `testutil/SyncTestDoubles.kt` (`FakeRemoteUserData` — every doc of a batch gets the same server time —, `InMemorySyncStore` with the real conflict policy, `FakeSyncStateStore`, `FakeNetwork`, `RecordingScheduler`, `FakeSyncRepository`).
- Instrumented: `MigrationTest` 8 → 9, 1 → 9; `ProgramMigrationTest` 3 → 4 (backfill random, lowercase, distinct, idempotent), 1 → 4.
- **Emulator integration** (`FirestoreSyncEmulatorTest`, opt-in): `scripts/test_sync_emulator.sh` checks that emulator-5554 is the only device, starts the Firestore + Auth emulators (`firebase emulators:exec --only firestore,auth`, rules from `firebase/firestore.rules`) and runs the test with the instrumentation argument `syncEmulator=true` (otherwise it is skipped). A dedicated `FirebaseApp` uses the emulators at 10.0.2.2 and an anonymous Auth-emulator account: device A (in-memory Room, real repositories, stores, Firestore remote, page size 2) writes a goal, an activity, a gym entry, a program with a completed strength workout and a deleted entry, and syncs; device B (fresh Room) syncs and must hold identical rows (PROGRAM refId mapped to its own local program id); B's goal change and deletion reach A; the erase empties the account. Tightening a rule makes it fail (checked). Debug builds allow cleartext only to 10.0.2.2 / localhost for it (`app/src/debug`).

## 22. Groups (Phase 3b: Android)

Friends compare their week. The Firestore model, batches and rules are Phase 3a (§19.5, `docs/firestore-model.md` "Groups"); this is the app's side. Signed-in accounts only; no push, no WorkManager for groups, no Cloud Functions.

### 22.1 Architecture
- Isolated like Program: `domain/groups/`, `data/groups/`, `ui/groups/`, `di/GroupsModule.kt` (`groupsModule`, started after `syncModule`; its own `CoroutineScope(SupervisorJob() + Dispatchers.Default)` named `groupsScope`). Core dependencies, read-only: the auth session, the training log and weekly goal read APIs, `LocalDateProvider` / `WallClock`, the sync hooks (the shared `FirebaseFirestore`, `awaitTask`, `NetworkMonitor`, `SyncRepository.status`), `AccountAvatar`.
- Domain (pure where it can be): `GroupModels.kt` (`GroupInfo`, `MemberSummary`, `MemberProfile`, `GroupMember`, `Membership`, `InvitePreview`, `GroupSnapshot`, `GroupLimits`, `GroupError` / `GroupException`), `InviteCodes`, `GroupSummaryCalculator`, `GroupRanking` (`RankedMember`), `TrainedTodayRule` (`TrainedTodayBanner`, `GroupMembers`), `GroupRepository` + `GroupPreferences` + `GroupTexts`, `MemberProfiles`, `MySummarySource`, `GroupReconciler` + `GroupLog`, `GroupMembersCache`, `GroupSummaryPublisher` (`PublishOutcome`), `GroupOperations` (`MyGroup`, `CreatedGroup`), `GroupAccountCleaner` (implements `domain/auth/AccountGroupsCleaner`), `GroupsHome` (`HomeGroups`, `DefaultGroupsHome`, `GroupsHome.None`) and `GroupsCoordinator`.
- Data: `FirestoreGroupRepository` (every batch of the 3a model; `Source.SERVER` reads; `increment` for counts; a 20 s timeout per call; Firestore codes → `GroupError`: PERMISSION_DENIED, NOT_FOUND, UNAVAILABLE / DEADLINE_EXCEEDED / timeouts → NETWORK, the rest FAILED), `GroupsAndroid.kt` (`SharedPreferencesGroupPreferences` in `group_preferences`: per-account summary hash, dirty flag, last reconciliation; the banner dismissal date; `LogcatGroupLog` with tag `Groups`; `ResourceGroupTexts`).
- Delete account (`FirebaseAuthRepository`): re-auth → `AccountGroupsCleaner.leaveAllGroups(uid)` (resolved lazily in `AuthModule`, like the eraser) → erase → delete user → wipe. A cleanup failure → `AuthError.GROUPS_FAILED` (`AuthMessage.GROUPS_FAILED`), nothing erased.

### 22.2 Screens and navigation
- Home: a Groups icon (`ic_groups`, Material "group") in the top bar next to the account icon → `groups`. Chosen over a card: Home already stacks the guest banner, the weekly card and three buttons; the top bar keeps the entry quiet and always in the same place, and the banner is the visible nudge.
- `groups` (list), `group_create`, `group_join` (both replaced by `group_detail/{groupId}` once done), `group_detail/{groupId}` (`NavType.StringType`). Guests: an explanation + "Sign in with Google" → the Account screen (its existing flow), and the list reloads on return (`LifecycleStartEffect`).
- The weekly goal is required before creating or joining: a dialog opens the existing `weekly_goal` screen.
- Detail: ranking (position, avatar, name ("(You)" highlighted with `primaryContainer`), done/goal, a `LinearProgressIndicator` of the compliance, streak, Mon–Sun strip with one icon per source and one TalkBack sentence ("Monday: Gym routine. Tuesday: no workouts…")), invite code card (copy / share), Leave; owner top-bar menu (Rename, New invite code, Transfer ownership, Delete group) and a per-member menu (Make owner, Remove from group). Dialogs: `GroupDetailDialog` (Rename, RegenerateCode, RemoveMember, PickNewOwner(beforeLeaving), ConfirmNewOwner(andLeave), DeleteExplain → DeleteConfirm, Leave, LeaveAlone). Results are snackbars; losing access closes the screen with a toast.

### 22.3 Publish flow
- `GroupSummaryCalculator.summary(today, entries, goals)`: null without a goal this week; `weekStartLocal` = Monday of today, `workoutsThisWeek` = counted entries of the week (≤ 100), `weeklyGoal` = the goal in force, `streakWeeks` = `WeeklyProgressCalculator.streak` (≤ 1000), `daysThisWeek` = distinct sources per day in enum order, `lastTrainedLocal` = latest counted date not after today.
- `GroupSummaryPublisher.start()`: per signed-in session, `combine(training log + goals, requests)` → `debounce(5 s)` → `publish`; plus `SyncRepository.status.lastSuccessAt` changes → `request()` when dirty. `publish`: summary + profile → SHA-256 → unchanged and not dirty → nothing; offline → dirty; else an `update` of my member doc in every group of my index (NOT_FOUND / PERMISSION_DENIED for one group are logged and left to the reconciliation; any other failure marks dirty).

### 22.4 Banner
`GroupsHome.onHomeShown()` (every Home `OnStart`, in the background): `publisher.request()`, a throttled reconciliation, then my groups' members (index query + one members query per group), cached 10 minutes. `HomeState.trainedTodayBanner` applies `TrainedTodayRule` with Home's own week (my counted workouts today), the session uid and the dismissal date. Tap → `NavigateToGroupDetail` when one group has trainers, else `NavigateToGroups`; X → `dismissTrainedToday(today)`.

### 22.5 Reconciliation
`GroupReconciler.reconcile(uid)` (one `Mutex`): for each index entry, `ownMemberExists` (my own doc is always readable); missing → `removeStaleMembership` (entry + counter − 1); present but the group is gone → the same plus my left-behind member doc. Runs on every list load, before create / join, and via `reconcileIfDue` (6 h throttle) at app start / account change (`GroupsCoordinator`) and from Home. A failing removal is logged and retried next time.

### 22.6 Read / write budget (Spark: 50 000 reads and 20 000 writes per day)

| Action | Reads | Writes |
|---|---|---|
| Home shown (cached 10 min) | 0, or index (≤ 5) + members (≤ 5 × 20) ≈ ≤ 105 | 0 |
| Reconciliation (throttled 6 h; list load; before create / join) | index (≥ 1) + 2 per group (≤ 11) | 2 per stale entry |
| Publish (only when the summary changed) | index (≤ 5) | 1 per group (≤ 5) |
| Groups list | reconciliation + members per group (≤ 5 × 20) ≈ ≤ 111 | 0 |
| Create | reconciliation | 5 (one batch) |
| Look up a code / Join | 1 / reconciliation (+ 1 when denied) | 0 / 4 |
| Leave / Remove / Transfer / Rename / New code | 0 | 4 / 2 / 1 / 2 / 3 |
| Delete group (20 members) | members (≤ 20) | 19 batches × 2 + 5 = 43 |
| Detail open (listener while visible) | 1 + members (≤ 20), then 1 per changed doc | 0 |
| Delete account cleanup | reconciliation + members of owned groups + counter (1) | one batch per group (4–5) + 1 |

### 22.7 Tests
JVM (56 new): `GroupSummaryCalculatorTest` (7), `GroupRulesTest` (8), `GroupOperationsTest` (11), `GroupMaintenanceTest` (12), `GroupViewModelsTest` (12), Home banner cases in `HomeViewModelTest` (5), `FirebaseAuthRepositoryTest` (delete order with the groups step; a group failure erases nothing). Doubles: `testutil/GroupTestDoubles.kt` (`FakeGroupRepository` applying the rules' semantics, with `failOn` and `beforeOp`; `InMemoryGroupPreferences`, `RecordingGroupLog`, `FakeTrainingLog`, `FakeGoals`, `trainingEntry`, `GroupsTestKit`). Instrumented, opt-in: `GroupsEmulatorTest` (2) through `scripts/test_groups_emulator.sh` (emulator-5554 must be the only device; Firestore + Auth emulators; email/password test users, never the real project).

## 23. Group feed (Phase 4b: Android)

Each group's feed of its members' finished workouts. The Firestore model, rules and tests are Phase 4a (§19.6, `docs/firestore-model.md` "Feed"; `firebase/rules-tests/test/feedOps.js` is mirrored by `FirestoreFeedRepository`). Signed-in accounts in at least one group only. No push, no Cloud Functions, no Cloud Storage, no TTL (Spark).

### 23.1 Architecture
- **Core (small, outside the groups packages):** `feed_share` in `tiominuter.db` (**database v10**, `AutoMigration(9, 10)`, new table only): `FeedShareEntity` (entryUuid PK, ownerUid, share, entryDeleted, targetGroupIds?, postedGroupIds, refusedGroupIds, postedPhotoPath?, createdAt, version, dirty) and `FeedShareDao` (local changes bump `version` and set `dirty`; `saveProgress` clears `dirty` only when the version didn't change meanwhile, like the sync outbox). Ports in `domain/traininglog/WorkoutShares.kt`: `WorkoutShareQueue` (the history's hooks: `onRecorded` inside the insert transaction, `onRecordCommitted` after it, `onDeleted`, `onPhotoChanged`), `WorkoutShareStore` (the queue for the publisher and the switch), `WorkoutSharing` (the completion screens' port; `None` by default), `ShareOptions`, `RecordedEntry`. `RoomWorkoutShareStore` implements both queue and store; it queues only counted workouts (`countsTowardGoal`) recorded while signed in (`FirebaseAuth.currentUser`), so a guest's workouts are never posted. `TrainingLogRepositoryImpl(…, shares)` calls the hooks in its existing transactions; `getEntryByUuid` / `TrainingLogDao.getByUuid` were added (default `null` on the interface, so fakes are unchanged).
- **Groups feature:** `FeedModels.kt` (`Reaction` strong/fire/clap/raised, `FeedPost`, `FeedPage`, `NewPost`, `FeedLimits`, `ReactionRules`, `FeedRepository`, `FeedThumbnailSource`, `ThumbnailFitter`, `FeedPreferences`, `hiddenKey`), `FeedPublisher`, `FeedMaintenance.kt` (`FeedModeration`, `FeedCleaner`, `FeedShareController` = `WorkoutSharing`), `data/groups/FirestoreFeedRepository` (one-shot reads from the server; the week listener; `FieldPath.of("reactions", uid)`; `Blob` thumbnails; `ServerTimestampBehavior.ESTIMATE` for my pending posts), `AndroidFeedThumbnailSource`, `FirestoreCalls.kt` (`firestoreCall`: the timeout and error mapping now shared with `FirestoreGroupRepository`), `SharedPreferencesGroupPreferences` also implements `FeedPreferences` (intro seen, last cleanup, hidden posts with their time, last known group count, the detail tab). `GroupOperations(…, feed)`, `GroupAccountCleaner(…, feed)` and `GroupsCoordinator(…, feedPublisher, feedCleaner)` take the feed as optional parameters (null in older tests).

### 23.2 Publish flow
1. A workout is recorded (`record`, `recordSession`, `addActivityEntry`): if it counts and someone is signed in, `feed_share` gets a row (share ON, dirty) in the same transaction; after the commit the entry becomes the "latest recorded" of its source.
2. The completion screen shows "Share in my groups" (ON). The first time, the explanation dialog shows what members see (name, photo, title, duration, thumbnail); "OK" acknowledges it for the account, "Don't share this one" turns this workout off and acknowledges, closing it acknowledges nothing (nothing is posted until it is answered).
3. `FeedPublisher` (debounced 5 s; app start, queue changes, `request()` after the explanation, a successful sync) reads my index once (`memberships`, from the server; offline → nothing), then per dirty row: another account's row → dropped; entry deleted → `deletePost` in every group of my index plus the posted ones, row removed when done; switch off → the posts already made are deleted; otherwise, if the week is still current and the explanation was seen: (a) existing posts get the current thumbnail when the photo changed (`setThumbnail` bytes / delete), (b) a `createPost` per target group not yet posted or refused. PERMISSION_DENIED / NOT_FOUND → the group is remembered as refused and never retried (a create refused because the post already exists and is mine — a lost answer — counts as posted); NETWORK / FAILED → the row stays dirty. Rows done and older than 9 days are pruned.
4. The thumbnail: the stored 400 px thumbnail (else the photo, sampled down) decoded and re-encoded by `ThumbnailFitter`: qualities 60, 50, 40, 30 at 400 px, then × 0.75 per step down to 64 px, until ≤ 60 KB; nothing larger than 65,536 bytes is ever sent (the post goes without it).

### 23.3 Screens
- **Completion screens:** `ShareInGroupsSection(entryId)` next to `CompletionPhotoSection` in the gym "Routine completed!" dialog, the program strength dialog and the cardio finished view (`ProgramWorkoutFinished`); `LatestShareInGroupsSection(DAILY)` on `WorkoutCompletionScreen` (it doesn't know its entry: it takes the DAILY entry recorded within 2 minutes before it opened, or the next one). `ShareInGroupsViewModel(sharing, clock)` (MVI, `koinViewModel(key = "shareInGroups")`, nothing in previews). The switch row is one `toggleable` 48 dp element.
- **Log activity:** the same `ShareSwitchRow` in the form when `isAvailable()` (create) or when the workout has a row (edit, showing its own choice); not an unsaved change. After Save, the explanation shows if needed before going back.
- **Group detail:** a `PrimaryTabRow` with Ranking (the existing content, now `RankingTab`) and Feed; `GroupDetailState.tab` remembered per group (`FeedPreferences.detailTab`). Only the selected tab is composed.
- **Feed tab** (`ui/groups/feed/`: `GroupFeedState`, `GroupFeedIntent`, `GroupFeedEffect`, `GroupFeedViewModel(groupId, operations, feed, cleaner, preferences, network, dates, clock)`, `GroupFeedScreen.kt` with `GroupFeedTab` + stateless `GroupFeedContent`, `GroupFeedPreviews.kt`): `LifecycleStartEffect` → `OnVisible` / `OnHidden`; the listener of this week (`weekStartLocal ==` my Monday, `createdAt` desc, 20 × pages; "Load more"); hidden posts and other weeks filtered; the first visible time deletes up to 20 expired posts. Card: avatar, name ("(you)" for mine), source · duration (rounded down minutes, as the history) · relative time (`DateUtils`, "Just now" while pending), the title (an empty ACTIVITY title → the localized activity type), the thumbnail (decoded on `Dispatchers.Default`, LRU of 4 MB), and four 48 dp reaction chips with counts (mine selected; TalkBack: "On fire, 3 reactions" + "Your reaction"). Menu / long press / accessibility actions: Delete (mine, or any as the owner; confirmation dialog) and Hide for me (others' posts). Empty state; on Mondays "A new week starts today…".

### 23.4 Cleanup and membership sequences
- Spark has no TTL: the Feed tab's first open and app start (throttled 6 h, `GroupsCoordinator` → `FeedCleaner.cleanUpIfDue`) delete posts with `createdAt` < now − 8 days, ≤ 20 per group, one by one, skipping refusals (the device clock may run ahead of the server's).
- Leave: my posts (query `authorUid == me`) then the Leave batch; a failed delete stops the leave. Remove member: the batch, then their posts (a failure is only logged). Delete group (and an owner alone leaving): all posts (pages of 100 ids), then members one by one, then the final batch. Delete account: my posts in every group (every post of a group I own alone) before the group cleanup → erase → auth user (`GroupAccountCleaner` with `FeedModeration`).

### 23.5 Read / write budget
| Action | Reads | Writes |
|---|---|---|
| Finish a shared workout | 1 per group in my index (≤ 5, the index read of the flush) | 1 per group (≤ 5) |
| Completion screen switch | ≤ 5 (the index, to know I have groups; none offline) | 0 (local) |
| Photo added / changed / removed later | ≤ 5 (index) | 1 per group posted |
| Delete an entry | ≤ 5 (index) | 1 per group in my index |
| Open the Feed tab | 1 per post of this week (pages of 20) + 1 per change while visible + the cleanup query (1 when nothing expired) | ≤ 20 expired deletes |
| React / change / remove | 0 | 1 |
| Delete a post | 0 | 1 |
| App start cleanup (every 6 h) | ≤ 5 (index) + 1 query per group | ≤ 20 per group |
| Leave / remove member | 1 per post found | 1 per post + the batch |
| Delete group / account | 1 per post found | 1 per post + the batches |

A post with a full thumbnail is about 61 KB, so a week of a busy group (~100 posts) is about 6 MB per full read; pages of 20 and the listener's cache keep it lower.

### 23.6 Tests
JVM (64 new; **847 in total**): `FeedPublisherTest` (17), `FeedRulesTest` (11), `FeedMembershipTest` (9), `ShareInGroupsViewModelTest` (8), `GroupFeedViewModelTest` (11), `LogActivityShareTest` (7) and the tab case in `GroupViewModelsTest` (1); doubles in `testutil/FeedTestDoubles.kt` (`FakeFeedRepository` with the rules' semantics and a reaction gate, `FakeFeedShareDao` with the DAO's SQL semantics, `InMemoryFeedPreferences`, `InMemoryTrainingLogDao`, `FeedTestKit` = the real history + the real queue + the feed over `GroupsTestKit`). The share test found a real ordering bug, fixed: the "latest recorded entry" was announced inside the insert transaction, before the row was committed. Instrumented: `MigrationTest` 9 → 10, 1 → 10; `AndroidFeedThumbnailSourceTest` (3); opt-in `FeedEmulatorTest` (3, `scripts/test_feed_emulator.sh`; 4 with the `titleKey` case, §24). Lint: 0 errors, 85 warnings (none from the feed). `scripts/test_rules.sh`: 147 green (rules unchanged at 4b; 152 with `titleKey`).

## 24. Workout names (WorkoutNameResolver)

Every workout name is resolved **at display time, in the viewer's language**, by one resolver; no screen shows a stored title directly. The stored `trainingLog.title` (written once, in the recording device's language, synced as is) is only the fallback. No change to the user-data model (`title` + `refId` stay), no Room migration.

### 24.1 Before
A Spanish device showed English in: daily names everywhere ("Day N", from `routineDay`; the content has no Spanish), Home's hardcoded "Day X Available", gym posts in the feed (the English routine name, no `refId` on the post), and program entries/posts recorded in the other language (the title was resolved at recording time). History GYM titles were already localized through the refId.

### 24.2 Design
- **Core** (`domain/traininglog/WorkoutNames.kt`): `WorkoutName` (language-free: `Gym(name, nameEs)`, `Daily(dayNumber, title?, titleEs?)`, `Program(ProgramTitleCode)`, `Activity(type)`, `Stored(text)`, `SourceOnly(source)`); `WorkoutNameCatalog` (gym names and daily titles by routineId; program workouts by Room refId); `ProgramTitleCode` (FULL_BODY_A … LOWER_C, OUTDOOR_RUN, BRISK_WALK, TREADMILL, HOME_CIRCUIT; never renamed or removed); `WorkoutTitleKey` (the feed key, §24.4); `ProgramWorkoutTitleCodes` (port: refId → code); `WorkoutNameResolver` (`nameOf(entry, catalog)`, `nameOfPost(titleKey, source, activityType, title, catalog)`, `titleKeyOf(entry, code)`, `dailyName(routine)`, `gymName(…)`).
- **Catalog** (`WorkoutNameCatalogs.kt`): `WorkoutNameCatalogs(gymRoutineRepository, workoutRepository, programTitleCodes)` (Koin single in `workoutModule`, also `WorkoutNameCatalogSource`): `observeCatalog()` = the gym routines flow + the daily routines read once (a failure leaves it empty); `withProgramsOf(catalog, entries)` = one `titleCodeOf` per distinct PROGRAM refId; `ProgramWorkoutTitleCodes.titleKeyOf(entry)` for the publisher.
- **Program side:** `ProgramWorkout.titleCode()` (`domain/program/model/ProgramWorkoutTitleCode.kt`, exhaustive), `RoomProgramWorkoutTitleCodes(programDao)` (one row read: `programWorkoutOf(refId)` → `getWorkout` → payload → code; null when the program is gone), bound in `programModule`. `workoutTitleRes(workout)` now delegates to the core `programTitleRes(code)`, the one table of program titles (same strings as before, pinned by `ProgramTitleCodeTest`).
- **Rendering** (`ui/components/WorkoutNameText.kt`): `WorkoutName.text()` (composable: `LocalResources` + `currentLanguage()`), `text(WorkoutNameTexts)` (pure, tested with the real strings), `ResourceWorkoutNameTexts`, `programTitleRes`, `sourceLabelRes`. Strings: `workout_name_day` ("Day %1$d" / "Día %1$d"), `workout_name_day_with_title` ("%1$s · %2$s"), `home_day_available` ("%1$s available" / "%1$s disponible").

### 24.3 Fallback order
| Source | Resolved name | Fallback |
|---|---|---|
| GYM | the catalog routine of `gym:<id>`, `localizedName` (Spanish name if present) | stored title (routine removed, bad / no refId) |
| DAILY | "Day N" / "Día N" from `daily:<N>` (routineId = day number, pinned by `DailyDataValidationTest`), + " · title" in the viewer's language when the content has one (Spanish: only `titleEs`) | stored title (no / bad refId) |
| PROGRAM | the code of the workout the refId points to | stored title (program deleted) |
| ACTIVITY | OTHER → the custom name; otherwise the type label | stored title (unknown type code) |
| any | a blank stored title | the source label |

Feed posts: `titleKey` valid for the post's source and known to the reader (a gym routine in the reader's catalog, a known program code; a day number always) → that name; otherwise the post's `title`, and for ACTIVITY the `activityType` as before.

Daily `titleEs`: accepted from `routines.json` (`DailyRoutineDto.titleEs`, `Routine.titleEs`, both default null; validated: needs a `title`, not blank) but not stored in Room (`daily_routines` has no column), so routines read from the database have `titleEs = null`. No routine has a title today; showing a `titleEs` needs a database version + migration.

### 24.4 Feed `titleKey`
- Optional post field (rules: `isPostTitleKey`): `gym/<routineId>` (GYM), `daily/<dayNumber>` (DAILY) — 1–9 digits, no leading zero —, `program/<CODE>` (PROGRAM) — `[A-Z][A-Z0-9_]*` —; ≤ 80 characters; only on its own source; never on ACTIVITY; immutable (the thumbnail and reaction updates may change nothing else). docs/firestore-model.md "Title keys".
- `FeedPublisher(…, programTitles)` sets it with `titleKeyOf` when it builds the post (a key the rules would refuse is never sent: `WorkoutTitleKey.valueOrNull`); `FirestoreFeedRepository` writes it only when present and reads it back; `FakeFeedRepository` refuses an invalid key like the rules.
- Readers: `GroupFeedViewModel(…, names: WorkoutNameCatalogSource, clock)` keeps the catalog in `GroupFeedState.names` (observed in `init`; Room only, not a Firestore listener); `GroupFeedState.nameOf(post)` → the card's title.
- **The rules changed: deploy them after merging** (`firebase deploy --only firestore:rules --project tiominuter-e43f7`). Until then production refuses posts with a `titleKey` as an unknown field: a refused create is never retried, so **merge and deploy the rules before releasing this app version**.

### 24.5 Tests
JVM (32 new; **879 in total**): `WorkoutNameResolverTest` (18: every source × EN/ES × known / unknown / removed / old data; posts by key both ways and their fallbacks; activity posts; keys per source; no key when unknown or invalid; the strict format; every program code valid with both titles; codes pinned), `WorkoutNameStringsTest` (4), `ProgramTitleCodeTest` (3), `TrainingHistoryViewModelTest` (3 new, 1 rewritten; 28), `HomeViewModelTest` (+1), `FeedPublisherTest` (+2), `GroupFeedViewModelTest` (+2), `DailyDataValidationTest` (+2). Doubles: `testutil/WorkoutNameTestDoubles.kt` (`StringResources` reads `src/main/res/values{,-es}/strings.xml` and maps `R.string` ids by reflection; `XmlWorkoutNameTexts`; `FakeDailyCatalog`; `FakeProgramTitleCodes`; `dailyRoutine`; `workoutNameCatalogs`). Rules: 5 new tests, 16 new mutations (all 48 feed mutations killed), 152 green. Instrumented: `FeedEmulatorTest.title_keys_travel_and_each_reader_resolves_them_but_never_changes_them` (each kind written and resolved by the other user, a post without a key falls back, a foreign / bad / 81-character key refused, raw updates of the key refused, the thumbnail and reaction updates keep it).

## 25. Web app (Phase 5a: Flutter web, view + own reaction)

A second client in `web/`, served by Firebase Hosting at the site root. Read-mostly: a signed-in person views their week, history and groups and sets their own reaction in a feed. Rules: CLAUDE.md "Web"; run / check: `web/README.md`.

### 25.1 Versions
Flutter 3.47.7 stable, Dart 3.13.5 (pinned in `pubspec.yaml` and CI). Exact dependencies (lockfile committed): `firebase_core` 5.0.0, `firebase_auth` 7.0.0, `cloud_firestore` 7.0.0 (Firebase JS SDK 13.0.0 through `firebase_core_web` 4.0.0; `firebase_auth_web` 6.3.1, `cloud_firestore_web` 5.7.4), `flutter_bloc` 9.1.1 (`bloc` 9.2.1), `go_router` 18.1.1, `http` 1.6.0, `crypto` 3.0.7, `intl` 0.20.3, `cupertino_icons` 1.0.8; dev `flutter_lints` 6.0.0 (+ strict-casts / inference / raw-types and a few extra lints; `flutter analyze --fatal-infos`), `bloc_test` 10.0.0. Firebase web app "Tio Minuter Web" (`1:897898729805:web:4194859198281db6162704`), `lib/firebase_options.dart` from flutterfire 1.4.1 (the existing app, no new one).

### 25.2 Architecture
- **MVI with Bloc** (`core/mvi.dart`): `MviBloc<Intent, State, Effect>` = a Bloc whose events are the intents (sealed classes, one handler each), an immutable state, and a broadcast `effects` stream (`sendEffect`) collected by `EffectListener`; `VisibilityScope` sends visible / hidden intents on mount, unmount and the browser tab's visibility (`AppLifecycleListener` `onShow` / `onHide`). Pages: a `BlocProvider` + a stateless `*Content(state, callbacks)`.
- **Layers:** `domain/` (pure Dart, the Android rules: §25.4), `data/` (`repositories.dart` interfaces — `AuthRepository`, `UserDataRepository`, `GroupsRepository`; `firebase_repositories.dart`; `firestore_mappers.dart`; `content_repository.dart` + `jsonc.dart`), `ui/` (blocs and pages), `app/` (`TioMinuterWebApp`, `buildRouter`, `AppTheme`, `AppServices`, `AppConfig` from `--dart-define`).
- **Routing** (`go_router`, path URLs): `/` landing (signed out only; signed in → `/week`), and inside the shell `/week`, `/history`, `/groups`, `/groups/:groupId` (`?tab=feed` for the Feed tab, so switching keeps the page and its listeners), `/account`. The router redirects on every session change (`SessionBloc`, app-wide). Phase 6: `/groups/create`, `/groups/join` (`?code=` from a shared link) — before `/groups/:groupId` —, `/week/goal?then=/groups/create|join`; back from the deletion's re-authentication → `/account`.
- **Auth** (`FirebaseAuthRepository`): `signInWithPopup(GoogleAuthProvider())` on desktop; `signInWithRedirect` when `defaultTargetPlatform` is Android / iOS (mobile browsers) or the popup is blocked. Outcomes: signed in, cancelled (silent), popup blocked, no network, redirect incomplete, failed (messages); logs carry the error code only. Sign out returns to the landing. No profile write, no guest mode.
- **authDomain = the Hosting domain** (`app/firebase_web_options.dart`): `tiominuter-e43f7.web.app` (or `…firebaseapp.com` when the page is served there; `web.app` on localhost; `AUTH_DOMAIN` define to override), set in code over the generated `firebase_options.dart`. The redirect's handler (`/__/auth/handler`) is then same-site, so its storage isn't partitioned away (Safari ITP, Chrome): before, phones came back to the landing page signed out. OAuth Web client: origin `https://tiominuter-e43f7.web.app`, redirect URI `https://tiominuter-e43f7.web.app/__/auth/handler`.
- **Startup:** a redirect is marked in sessionStorage (`tm.signInRedirect`); `SessionBloc` awaits `completeRedirectSignIn()` (`getRedirectResult`; marked but no result and no user → `redirectIncomplete`) before subscribing to `authStateChanges`, so `isLoading` covers both and the router doesn't route meanwhile; the landing shows a spinner ("Signing in…" when returning) instead of its content, and `redirectError` inline (cleared by the next Sign in). Tests: `test/ui/sign_in_redirect_test.dart` (8: waits before listening, signed in → `/week`, incomplete / no network messages EN / ES, a normal visit, authDomain per host, the SPA rewrite vs `/__/`).
- **Firestore:** the persistent web cache since Phase 5b (§27.4); `USE_EMULATORS=true` → `useAuthEmulator` / `useFirestoreEmulator` on `EMULATOR_HOST` (default localhost).

### 25.3 Screens
- **Landing:** the mascot, the name, the tagline "No excuses — just 10 minutes a day." (Android's, the documented exception), what the web shows, "Train with the daily routines, your programs and the activities you log, here or in the Android app", Sign in with Google.
- **My week** (`WeekBloc`): the goal card ("3 of 4 workouts", a bar capped at 100 %, "N workouts left…" / "Goal met this week!" + "Streak: N weeks"), or "Set your weekly goal" with where to set it on Android; the Mon–Sun strip (a filled circle per day with workouts, the number when several, a check for one, today outlined, the icons of the day's sources; one sentence for screen readers); the counting rule. Listeners: this week's entries and the goals, while visible; the streak is read back from past weeks (`streakReadingBack`).
- **My history** (`HistoryBloc`): days newest first ("Today", "Yesterday", "Monday, 19 October" in the device language), rows with the source icon, the resolved name, "Gym routine · 32 min", "Doesn't count toward the goal" under 10 minutes, the time; pages of 20 with "Load more"; no photos.
- **Groups** (`GroupsBloc`, Phase 6): my groups (name, member count, "This week: #N", an owner star; the index reconciled first), empty state, Create group / Join with code (the weekly goal first: the goal-required dialog → `/week/goal?then=…`; disabled at 5 groups), the privacy note.
- **Create** (`GroupCreateBloc`, `/groups/create`): a name of 1–40 → "Group created!" with the code, Copy, Share (Web Share or a copy), Open group.
- **Join** (`GroupJoinBloc`, `/groups/join?code=`): the code normalized as typed, ambiguous characters flagged, a complete code previewed ("You're about to join …"), Join → the group; each error with Android's message.
- **Group** (`GroupDetailBloc`, `FeedBloc`): Ranking (position, avatar, name + "(You)", "done/goal" or "Not updated this week", streak, a compliance bar, the Mon–Sun source strip for a current summary) and Feed (this week's posts newest first: author, source · duration · date, the name by `titleKey`, the thumbnail decoded from the post's bytes, four reaction chips with counts, mine selected, TalkBack / screen-reader descriptions "On fire, 2 reactions" + "Your reaction"; "Load more"; the Monday empty text; moderation is on Android). A group I lost access to says it is gone. **Phase 6:** the invite code (Copy / Share) for every member; Leave (a member: posts first; an owner with members: pick the next owner, transfer and leave; alone: delete); the owner's menu (Rename, New invite code, Transfer ownership, Delete group in two steps) and a menu per member (Make owner, Remove from group), each confirmed; the feed's post menu (Delete mine or, as the owner, any; Hide for me). On My week: the "already trained today" banner (`TrainedTodayCard`).
- **Account:** photo, name, email, Sign out, **Delete account** (`DeleteAccountBloc`: the explanation → the confirmation → `AccountDeletion`; "Deleting your account…"; resumed after the phone's redirect; each failure's message).
- Responsive: a navigation bar under 840 dp, a rail above; pages at most 720 dp wide with a 16 px gutter. Light / dark from the system.

### 25.4 Android logic ported (and from where)
| Dart | Kotlin source |
|---|---|
| `domain/calendar_dates.dart` | `domain/traininglog/CalendarDates.kt`, `LocalDateProvider.kt` (today / Monday in the device zone) |
| `domain/weekly_progress.dart` | `domain/goal/WorkoutCounting.kt`, `WeeklyProgressCalculator.kt`, `WeeklyGoal.kt`; day sources from `domain/groups/GroupSummaryCalculator.kt` |
| `domain/workout_names.dart`, `ui/workout_name_text.dart` | `domain/traininglog/WorkoutNames.kt` (`WorkoutNameResolver`, `ProgramTitleCode`, `WorkoutTitleKey`), `domain/program/model/ProgramWorkoutTitleCode.kt`, `ui/components/WorkoutNameText.kt`, `ui/components/LocalizedName.kt` |
| `domain/groups.dart` | `domain/groups/GroupRanking.kt` (+ `String.CASE_INSENSITIVE_ORDER`), `domain/groups/GroupModels.kt`, `domain/groups/FeedModels.kt` (`Reaction`, `ReactionRules`, `FeedLimits`), the week filter of `ui/groups/feed/GroupFeedViewModel.kt` |
| `data/content_repository.dart` | `data/content/ContentSyncer.kt`, `ContentManifest.kt` (manifest rules, size + SHA-256) |

Web-only: `streakReadingBack` (the same streak without a local copy of the history: 8 weeks per query, stopping where Android's loop stops); program names from the Firestore refId (`program:<uuid>:…`) and the synced workout payload (Android reads its local program).

### 25.5 Hosting layout
| Path | Serves | Cache-Control |
|---|---|---|
| `/`, every app route (`/week`, `/groups/…`) | `index.html` (rewrite `!/@(content|__)/**`: every path except `/content/**` and the reserved `/__/**`) | no-cache |
| `/__/auth/handler`, `/__/auth/iframe`, `/__/firebase/init.json` | Firebase's reserved paths (the sign-in handler, the SDK config), never the app | Firebase's |
| `/flutter_bootstrap.js`, `/flutter_service_worker.js`, `/version.json`, `/manifest.json` (PWA) | the Flutter loader and metadata | no-cache |
| `/main.dart.<sha256 prefix>.js` | the compiled app (renamed by `stage_hosting.py`, which points `flutter_bootstrap.js` at it) | 1 year, immutable |
| `/assets/**`, `/canvaskit/**`, `/icons/**`, `/flutter.js`, `/favicon.png` | Flutter assets (not hashed by Flutter) | 1 hour |
| `/content/v1/manifest.json` | the content manifest | no-cache, CORS * |
| `/content/v1/*.json(c)` | content files | 5 minutes, CORS * |
| `/content/v1/animations/*.gif` | animations | 1 year, immutable, CORS * |
| `/content/<missing>` | 404 (never the app) | — |

Checked with the Hosting emulator (`firebase emulators:start --only hosting`): every row above.

### 25.6 CI
`.github/workflows/web.yml` (pull requests and non-main pushes on `web/**`, `shared/**`, `firebase.json`, the staging script; reusable): the shared fixtures `--check`, Flutter 3.47.7 (`subosito/flutter-action@v2`), `flutter pub get --enforce-lockfile`, `flutter analyze --fatal-infos --fatal-warnings`, `dart format --set-exit-if-changed`, `flutter test`, `flutter build web --release`. `content-deploy.yml` (on `main`) runs the content gate and the web gate, builds the web, the manifest and the Hosting folder, then deploys Hosting only. `firestore-rules.yml` also runs on `shared/**` and checks the fixtures.

### 25.7 Tests
324 VM tests (`flutter test`) since Phase 6 (groups and Delete account: 45 in `test/groups/`); 188 at Phase 5b: the parity goldens (8, §26) and training (55, §27.6), plus the Phase 5a ones: mappers against the shared fixtures (11), dates / counting / goal / streak incl. the read-back streak (31), names (17), ranking / reactions / feed filter (8), content (9), blocs (18), screens (26), l10n (5: incl. the tagline exception). Opt-in Chrome test against the emulators (7, `scripts/test_web_emulator.sh`): seeded through the real rules by two Auth-emulator users (entries this week, a 5-minute one, past weeks, a tombstone, a live and a deleted program, goals and a goal tombstone, a group joined with last week's summary, a group I was removed from, a post with `titleKey`), then: the signed-in user, history pages (20 + 4, no tombstone), program codes (deleted → none), My week (1 counted, goal 1, streak 3), my groups (the stale entry skipped and not written), the ranking (a stale summary counts 0; a removed group reads as gone), the post's name by `titleKey`, my reaction set / changed / removed and another member's key refused. Rules tests: 152 green with the fixtures read from the shared JSON.

---

## 26. Parity: Kotlin ↔ Dart (golden files)

Kotlin is the reference implementation; the web's ports must give the same results. Rules: CLAUDE.md "Parity".

### 26.1 How it works
- `app/src/test/java/com/javargasp/tiominutermvi/parity/`: `Golden.kt` (canonical JSON: kotlinx `JsonElement`, keys sorted at every level, one-space indentation, a final newline, no run timestamps; `Golden.verify(name, document)`), and one test per domain running the **real Kotlin code** over a fixed matrix. A normal unit-test run **checks** the file byte for byte (a difference fails with the first differing line and "run scripts/update_golden.sh"); `-Pgolden.update=true` (`scripts/update_golden.sh` = `./gradlew :app:testDebugUnitTest --tests "…parity.*" -Pgolden.update=true --rerun`) rewrites it. `app/build.gradle.kts` passes the property to the test JVM and declares `shared/golden/` as a test input.
- `web/test/parity/`: `golden.dart` (loads `../shared/golden/*.json`; `Mismatches` collects every differing case), one test per domain asserting equality with the Dart port.
- CI: `android-tests.yml` (new: `testDebugUnitTest` on app / content / golden / Gradle changes), `content-validate.yml` (+ `parity.*`), `web.yml` (+ `shared/golden/**`, `content/**`).

### 26.2 The matrix (shared/golden/)
| File | Kotlin code | Matrix | Size |
|---|---|---|---|
| `program_generator.json` | `ProgramGenerator.generate` over content/v1 (SHA-256 of both program files recorded) + `computeBmi`, `isLowImpact`, `isSenior` | every goal × location × level × 2–5 days × focus for an adult (192), plus 7 edge profiles (BMI 30.04 / 29.98, age 60, the upper limits 90 y / 250 kg / 120 cm with 12 weeks, the lower limits 16 y / 30 kg / 230 cm with 4 weeks, 4 and 12 weeks) × location × days × focus (224): **416 programs**; each workout as its stored payload string, the 3,370 distinct ones listed once | 3.1 MB |
| `weekly.json` | `countsTowardGoal`, `countedWorkoutsPerDay`, `WeeklyProgressCalculator` (`goalForWeek`, `progress`, `streak`, `currentWeek`), `GroupSummaryCalculator.summary` | 16 hand-written (0 / 1 / 599 / 600 / 601 s / null, Monday and Sunday, a new year, a leap day, no goal, a future goal, goal changes, a broken streak, the current week, short workouts, all four sources in one day, stale history, a future-dated entry, > 100 workouts, a streak > 1000 weeks) + 60 generated (`java.util.Random(seed)`, seed and inputs written in the case): **76** | 372 KB |
| `sessions.json` | `SessionRules`; `ActiveWorkoutSession` + `ActiveSessionTracker` + `ActiveTimeMeter` under virtual time (5-s checkpoints as in the app); `ProgramStrengthSessions` | the rules' edges (isInactive at 2 h / 2 h + 1 ms, a clock moved back; durationSeconds; latestRun) + 16 active-time sequences (timed, REPS shown / hidden, background with the timer running / stopped, a pause, before start, process death + reopen, the 2 h boundary both ways after a process death and on the same screen, the clock moved back / forward, double start / finish, another workout open, a long workout) + 7 set-to-set sequences + 9 gym sequences (`GymSessionTracker`: sets then completed, a completion shown again, 1 h 59 min and exactly 2 h still open, 2 h 1 min recorded + progress reset, a late set after 2 h + 1 ms, a manual reset discards, the clock moved back, two routines): **32**, each with the stored session after every event, what was recorded, the finished seconds and the number of saves | 52 KB |
| `workout_names.json` | `WorkoutNameResolver.nameOf` / `nameOfPost` / `titleKeyOf`, `WorkoutTitleKey.parse`, `WorkoutName.text` with the real `strings.xml` (EN, ES) | every source × refIds at the format's edges (leading zeros, `+` / `-`, hex, spaces, overflow) × titles (blank, `\uFEFF`, `\u00A0`) × activity types; posts: every source × 30 keys × titles: **465** (225 entries, 240 posts) | 156 KB |

### 26.3 What the goldens found in the Dart ports
- `int.tryParse` accepts hex (`0x40`) and surrounding spaces where Kotlin's `toIntOrNull` doesn't; Dart's `trim()` treats U+FEFF / U+0085 as blank and U+001C–U+001F as not, Kotlin the other way round: `domain/kotlin_text.dart` ports Kotlin's (`isKotlinBlank`, `kotlinTrim`, `kotlinToIntOrNull`), used by the names, the activity rules and the program form.
- In a browser `jsonEncode(5.0)` gives `5`, Kotlin `5.0`: payloads go through `kotlinJson` / `KotlinDouble`, so they are byte-identical in Chrome too. `roundToInt` is ported as Java's `Math.round`.
- The rest are exact on the first run (the generator, the weekly rules and the summary, the session sequences including the save counts; the gym cases through the reused `StrengthSessions`).

## 27. Training on the web (Phase 5b)

Rules: CLAUDE.md "Training on the web". No Firestore rule, index or model change.

### 27.1 Flows (blocs in `web/lib/ui/train/`, `ui/week/weekly_goal_page.dart`)
- **Train hub** (`TrainBloc` + `TrainContent`, route `/train`, the 2nd destination) = Android Home's navigation: the workout in progress (from `progress.daily` + its open session) with Continue; three `TrainEntryCard`s in Home's order and strings — Start Today's Routine (filled) + "Day X available" (`availableDay`: the highest daily routineId, Day 1 when the content failed, hidden without routines; `DailyWorkoutName` through the resolver), Gym routines + the browser-local note, Programs; then Log activity and Weekly goal (`/week/goal?from=train`). A column below 600 dp, a row of three above (`IntrinsicHeight`, equal widths). Shown → `closeInactiveSessions` (daily, cardio, strength, gym) and "Your unfinished workout was saved (…)".
- **Daily selection** (`DailySelectionBloc`, `ui/train/daily_selection_page.dart`, `/train/daily`): Android's `DailyRoutineSelectionScreen` — "Select Routine", the routines by id with "N exercises", the available day highlighted (`primaryContainer`, "Available today"), a Start per day (filled for the available one; its semantics "Start, Day 3"; the card is not a semantic container so the header and the button stay separate). Shown → inactive sessions closed, the routines read once; error with Retry; empty state.
- **Program list** (`ProgramListBloc`, `ui/train/program_list_page.dart`, `/train/programs`): my programs (one read each + a completions count), each card → its detail; Create program (disabled without the program content). Program detail and create go back here.
- **Daily workout** (`DailyWorkoutBloc`, `/train/daily/:id`, full screen; Back → the selection): Android's `WorkoutViewModel` — Get Ready (5 s) → TIME countdown → sound + vibration → next; REPS → Complete Reps; a rest starts on its own (its end also beeps, as Android); Skip; after the last one the entry (DAILY, `daily:<id>`, the content's `routineDay` as title, the active seconds) and the completion view with the share switch. The first action stores the session; every state change sets "timer running" / "REPS on screen"; the page's visibility follows the tab; Back stops the timers (recorded after 2 h). Reload: the same exercise and session.
- **Log activity** (`LogActivityBloc`, `/train/activity`, `/history/activity/:entryId`): Android's form — 14 types with their emoji, the custom name ≤ 40, quick durations 15–90 + a 3-digit field, "won't count" under 10 min, intensity segments described for screen readers, today / yesterday / the 5 days before (+ the entry's own older day), time picker (18:00 default for a past day unless chosen), note ≤ 200; errors after the first save, then live; discard / delete dialogs; the share switch (new activities, members of a group) and the explanation after the save.
- **Programs:** create (`ProgramCreateBloc`: `ProgramForm` = Android's fields, filters and ranges; BMI, low-impact note, underweight warning; week-1 preview and creation with the Dart generator), detail (weeks "Week 2 · 66.7 %", Deload, each workout's title, summary and state), strength workout (the slots with `programExerciseSummary`, "X of Y exercises completed", Reset), strength exercise (`StrengthExerciseBloc` = `ProgramStrengthExerciseViewModel`: sets, rest + Skip rest, Get Ready, per-side left → 5-s switch → right, exit confirmation; the last exercise → `markSlotCompleted`), treadmill (`TreadmillBloc`: segments, Start / Pause / Resume / Skip segment, total remaining) and circuit (`CircuitBloc`: rounds, work / rest / rest between rounds, Skip). Outdoor run: "Available in the Android app".
- **Gym routines** (`ui/train/gym_blocs.dart`, `gym_pages.dart`; Android's three gym ViewModels, kept apart from the program screens):
  - Selection (`GymSelectionBloc`, `/train/gym`): the catalog once, cards open independently (a 48-px toggle, a button with "Show / Hide exercises" and Expanded / Collapsed), each slot's name + `exerciseSummary` ("Sets: 4 · Reps: 10, 10, 10, 10" / "Series: 2 · Tiempo: 30 s, 45 s"), "This routine has no exercises yet.", Start routine, the browser-local note.
  - Routine (`GymRoutineBloc`, `/train/gym/:id`): each shown → close inactive sessions ("…was saved"), read the progress; slots with Completed / Not completed (semantics value), "X of Y exercises completed" + a bar, Reset (only with progress) → confirmation → progress cleared + session discarded, nothing recorded; every slot done → the completion view (the entry recorded once by `completeGymRoutine`; shown again without a session → no entry, no share switch), Finish → progress cleared. Not found / empty routine.
  - Exercise (`GymExerciseBloc`, `/train/gym/:id/:position`): `GymPerformingReps` (Set done), `GymReadyToStartTimed` (Start) → `GymGetReady` 5 s → `GymPerformingTimed`, `GymResting` between sets only when `rest` > 0 (Skip rest); every non-last set → `onSetCompleted`; the last → `onSetCompleted`, `markGymCompleted`, sound + vibration, back. Feedback at a TIME set's end (not the last), a rest's end and once on completion. Back after a set → "Leave exercise?" (leaving doesn't mark it). Wake lock while open; `ExerciseVisual` (type icon).
- **Weekly goal** (`WeeklyGoalBloc`, `/week/goal`, from My week's Set goal / Change goal; `/week/goal?from=train` from the hub, back and Save returning there): 1–14, starts from this week's goal (3 without one).
- **History:** a menu per row (Edit activity for an activity, Delete entry with Android's confirmation); the row leaves at once.
- **Account:** Sign out with pending writes asks first.

### 27.2 Writes per action (budget)
| Action | Writes | Reads |
|---|---|---|
| Finish a daily / cardio / program workout, log an activity | 1 entry (+ 1 completion for a program workout); my summary: ≤ 5 updates, only when it changed; posts: ≤ 5 (counted, current week, explanation answered) | my index (≤ 5), goals (all), this week's entries, the streak's past weeks (8 per query, only as far as it goes), the last counted date before this week only when this week has none (pages of 25, ≤ 4) |
| Edit an activity | 1 entry + summary (≤ 5) | as above |
| Delete an entry | 1 tombstone + 1 post delete per group of my index (no read first) + summary (≤ 5) | index + as above |
| Set the weekly goal | 1 + summary (≤ 5) | as above |
| Create a program | 1 batch: 1 program + weeks × days workouts (≤ 61) | — |
| Turn sharing off | 1 delete per group posted | — |
| Add / change / remove a photo | 0 for the photo (IndexedDB) + 1 `update` of `thumb` per group posted from this browser | — |
| History row | — | 0 (the photo from IndexedDB) |
| Strength and gym sets, sessions, workout / routine positions | 0 (localStorage) | — |
| Complete a gym routine, or a gym session closed after 2 h | 1 GYM entry (`gym:<id>`, the English routine name, the session's duration) + summary (≤ 5, when changed) + posts (≤ 5, `titleKey` `gym/<id>`, counted, explanation answered) | as for a workout |
| Gym selection / routine / exercise | — | — (content only, already loaded) |
| **Create group** | 1 batch: group, my member doc, invite code, my index entry, counter (5 writes) + my summary (≤ 5 updates, forced) | my index + my member doc + group per entry (reconciliation, ≤ 15), the invite code (taken? up to 3 tries) |
| **Join** | 1 batch: memberCount + 1, my member doc, index, counter (4) + my summary | the invite code (1), reconciliation (≤ 15) |
| **Leave** (member) | 1 delete per post of mine + 1 batch: memberCount − 1, my member doc, index, counter (4) | my posts (1 query) |
| **Leave** (owner with members) | my posts + 1 batch: the above + ownerUid (transfer and leave) | my posts |
| **Remove member** | 1 batch: memberCount − 1, their member doc (2) + 1 delete per post of theirs | their posts (1 query) |
| **Transfer / Rename / New code** | 1 batch: 1 / 2 (group + invite) / 3 (group, old invite deleted, new invite) | — |
| **Delete group** | 1 delete per post (pages of 50), 1 batch per other member (2 each), the final batch: my member doc, group, invite, index, counter (5) | posts (pages), members (1 query) |
| **Reconciliation** (≤ every 6 h, list, create / join) | 1 batch per stale entry: index entry + counter (+ my member doc) | my index + my member doc + the group per entry |
| **Banner** (My week) | — | my index + members per group (≤ 5 × 20), cached 10 min |
| **Delete a post / Hide for me** | 1 delete / 0 (this browser) | — |
| **Sign-in** | `users/{uid}`: 1 set (create or merge) | 1 get |
| **Delete account** | my posts (1 delete each), the group cleanup (as Leave / Delete group per group, then the counter), `users/{uid}`: programs + workouts + completions + log + goals (batches ≤ 500) + the profile doc, then the Auth user | my index, groups, members, my posts, each collection by pages |
| Train hub | — | my programs (1 per program) + 1 count aggregation per program |
| A program's detail / workout / exercise | — | the program, its workouts (≤ 60) and completions (≤ 60), once per page load (cached in `TrainingService`) |

### 27.3 Code
Screens: `ui/train/train_page.dart` (hub, `TrainEntryCard`), `daily_selection_page.dart`, `program_list_page.dart`, `gym_pages.dart`, the workout pages; routes in `app/app.dart` (`Routes.daily`, `Routes.programs`, `Routes.weeklyGoalFromTrain`; the hub's children inside the shell, workouts outside it and matched first). `app/training_service.dart` (`TrainingService`: recording, activities, delete, goal, programs + detail cache, strength slots and `markSlotCompleted`, `publishSummary` / `currentSummary`, `shareEntry` / `unshareEntry`, the localStorage session stores, gym progress and sessions (`gymSessions`, `gymCompletedPositions`, `markGymCompleted`, `clearGymProgress`, `resetGymRoutine`, `completeGymRoutine`, `rememberGymRoutine`; `_LocalGymStore`: `tm.<uid>.session.gym`, `tm.<uid>.progress.gym`), `clearLocalData`; `memberProfileOf`, `newUuid`), `app/training_texts.dart`, `app/clocks.dart` (`TimerScheduler`, monotonic and wall clocks), `data/write_mappers.dart`, `data/training_repositories.dart` (`TrainingRepository`, `SharingRepository`, `PendingWrites`, program summaries / details), `data/firebase_training_repositories.dart`, `domain/` (§26 ports + `daily.dart`, `activity_entry.dart`, `countdown.dart`, `gym.dart`: `GymContent`, `exerciseSummary`, `GymSessions`), `platform/browser*.dart` (localStorage, wake lock, Web Audio beep + vibration; in-memory / no-op on the VM). Content: `HttpContentRepository.training()` adds `exercises.json`, `program_exercises.json`, `program_templates.json`, each verified by the manifest, and parses `routines_for_gym.jsonc` into `TrainingContent.gymRoutines`.

### 27.4 Offline
`Settings(persistenceEnabled: true, webPersistentTabManager: WebPersistentMultipleTabManager())`; `PendingWrites` (this page's unacknowledged writes + `waitForPendingWrites()` at start) → the shell's "Not synced yet" banner; sign-out with pending writes asks first, then clears the account's `tm.<uid>.*` keys, `terminate()` + `clearPersistence()` and reloads.

### 27.5 Differences with Android (by design)
- A reload resumes the same daily workout and session (Android records the session after a process death and the restarted workout is a new one).
- Gym progress and sessions live in this browser (Android: on the phone; neither is synced), so a routine started on one device is finished there; only the entry travels.
- Photos stay in this browser (IndexedDB) and Safari may evict them (the iOS Home Screen hint); photos taken on the phone are not on the web and the reverse. No outdoor run; posts are made when the workout finishes (no background queue): an explanation not answered on that screen posts nothing later. The share switch is not offered when editing an activity.
- Exercise animations are **ON for the private test group** (`AppConfig.showExerciseAnimations = true`, the product owner's decision). Most GIFs are still unlicensed: **revisit before any public launch** (the release checklist of branch `chore/release-readiness`, `docs/release.md`). `ExerciseVisual` (`ui/train/exercise_animation.dart`): same-origin `/content/v1/animations/<file>`, the type icon as placeholder / fallback / reduced motion in the same box, only the current GIF (the next one preloaded), paused in a hidden tab, alt text = the localized name. Tests: `test/train/exercise_animation_test.dart` (7: flag on + same-origin URL, the animation and its alt text, a missing file → the icon in the same box, reduced motion (browser and OS), hidden tab / lists / flag off, rest animation, the daily workout's current + preloaded GIF in EN / ES).

### 27.6 Photos
- **Flow:** tap Add photo → a file input (`accept="image/*"`, opened inside the tap; iOS shows Take Photo / Photo Library) or a drop on desktop → `PhotoProcessor.process` (≤ 25 MB, decode upright, full 1080 px JPEG 70, thumbnail by `ThumbnailFitter` ≤ 60 KB / 65,536, metadata stripped) → `TrainingService.attachPhoto` (IndexedDB, then `thumb` on my posts of the entry) → `navigator.storage.persist()`. Completion views: `EntryPhotoSection`; Log activity: `FormPhoto` (None / Stored / Staged, an unsaved change; Save commits, removal on Save deletes, Back discards); history: `HistoryPhotoThumbnail` → `PhotoViewer` (full screen, close button, "Photos added on another device stay on that device.").
- **Storage:** IndexedDB `tiominuter-photos`, store `photos`, key `<uid>/<entryId>`, value {uid, entryId, full: Uint8Array, thumb: Uint8Array | null, savedAt}; `clear(uid)` deletes the key range `<uid>/…`; QuotaExceededError → `PhotoError.noSpace`; IndexedDB refused → memory for the page.
- **Limits:** input 25 MB; full 1080 px, JPEG 70; thumbnail 400 → 64 px, quality 60 / 50 / 40 / 30, target 60 KB, cap 65,536 bytes; output without any APPn (but JFIF) or COM block.
- **Errors (EN / ES):** too large, HEIC not decodable, unreadable, no space, failed; a short snackbar; the entry is never lost (Log activity saves it and reports the photo).
- **iOS:** `InstallHintRule` (iPhone / iPod / iPad UA or a touch "MacIntel", Safari's `Version/… Safari/`, not CriOS / FxiOS / EdgiOS / OPiOS / GSA / in-app views; not standalone; not dismissed); manifest `id` / `start_url` / `scope` `/`, standalone, `#6650A4`; `apple-touch-icon` 180 px opaque; maskable icons padded (`web/tool/make_icons.dart`).
- **Not verifiable without a real iPhone:** Safari's file input sheet (camera vs library), HEIC → JPEG conversion by iOS on upload, `createImageBitmap` orientation in Safari (the `<img>` fallback covers older versions), IndexedDB eviction timing, the Add to Home Screen icon and standalone launch.

### 27.7 Tests
VM (`test/train/`, 121; photos also in `test/domain/photos_test` (11) and Chrome `test/browser/photo_browser_test` (5)): `photo_service_test` (9: storage per account, delete, sign-out, eviction, full storage; the post's `thumb` as the fixture and its later updates, one per group), `photo_ui_test` (12: each screen's attach UI, errors, drops, Log activity create / edit / discard, the history thumbnail and viewer, the iOS hint), `train_menu_test` (16: the hub's entries in Home's order with its EN / ES strings, the day line incl. Day 1 / none, Continue, a column vs a row at 400 / 1000 px, Tab + Enter; the daily selection's highlight, Start and its semantics, its bloc; the program list and its bloc; the full app router: each entry on its route and back to the hub, a deep link with the hub below, Start → the full-screen workout and back to the selection, Log activity / Weekly goal round trips, every earlier path + the non-number redirects, a reload on `/train/daily/3` resuming the REPS exercise and Continue on the hub), `write_mappers_test` (7: every write vs the shared fixtures), `daily_workout_test` (7: the full flow with its active time, REPS only while visible, a running timer in the background, reload recovery, the 2-hour close dated on its start and announced, another routine's open session recorded, not found), `training_service_test` (17), `log_activity_test` (6), `program_flows_test` (6: creation = the golden case, refused form, strength sets / per side / completion with first → last set and no second record, exit confirmation, treadmill with a pause, circuit rounds), `train_screens_test` (11: the animation flag off, the pending banner, daily states EN / ES, Log activity errors, the goal stepper; the hub moved to `train_menu_test`), `gym_test` (22: the real catalog and Android's lenient parsing, the summary EN / ES, localized names, the selection; REPS with rest, Skip rest, TIME with get-ready and no rest, Back / exit confirmation on a fake clock; progress line, reset = discard, empty / not found, the completion recorded once incl. a second visit, a reload over the same storage; 1 h 59 min and 2 h open, 2 h 1 min recorded dated on its start, closed by the routine screen, a late set, a clock moved back, the hub close + sign-out; the entry vs `gymEntry`, a short one not posted, a counted one's summary and post vs `post` + `gym/64`), `gym_screens_test` (8: cards, toggle semantics, summaries, the routine's states, the completion, every exercise phase, Spanish). Opt-in `scripts/test_web_train_emulator.sh` (Chrome + emulator-5554 + the emulators): `web_train_cross_test.dart` phase 1 → `WebInteropEmulatorTest` (pull into a fresh Room: entries, goal, program payloads = `previewProgram`, completion, PROGRAM refId → the local id, my member doc = `GroupSummaryCalculator`, the posts' keys; the web's gym routine 65 = what `GymSessionTracker` records: `gymRefId(65)`, the bundled catalog's name, 1800 s, its post's `gym/65`; then push its own gym workout, goal 5, the activity deleted) → phase 3 (the web reads them).

---

## 28. Daily unlock (Android + web, synced)

The daily routines unlock one at a time (CLAUDE.md "Daily unlock" has the product rules). State: `DailyProgress(lastCompletedDay, cycle)`; every day ≤ `lastCompletedDay` was finished in this cycle.

### 28.1 Rules (`domain/daily/DailyProgress.kt` ↔ `web/lib/domain/daily_progress.dart`, golden `shared/golden/daily_unlock.json`)

| Situation | Result |
|---|---|
| New person (no DAILY entries) | `(0, 0)`: Day 1 available, every other day locked; "Day 1 available" |
| Existing person, first read of this version | `(highest daily:<N> of the DAILY entries, 0)`, once (a row / document exists afterwards) |
| Day N finished, N = last + 1 | `last = N`; Day N + 1 unlocks |
| A completed day repeated, or a day that isn't the next one | unchanged |
| A session closed for inactivity | unchanged (only the completion screen calls `onDayFinished` / `finishDailyDay`) |
| Restart days (confirmed) | `(0, cycle + 1)`; history, weekly goal and groups untouched |
| Last routine (30) finished | `last = 30`: "Day 30 available" stays; "You completed every day!" + "Start again from Day 1" (completion of day 30 and the selection) |
| Day line | `min(last + 1, lastRoutineId)`; Day 1 when the routines (web: or the progress) can't be read; none without routines |
| Two states meet | the higher `cycle` wins; same cycle → the higher `last` |

### 28.2 Android
- **Room v11:** `daily_progress` (`DailyProgressEntity`: `id` 0, `lastCompletedDay`, `cycle`, `updatedAt`), `AutoMigration(10, 11)` (new table only; `MigrationTest` 10 → 11 with a DAILY entry, the outbox and the feed queue kept, and 1 → 11). Schema `app/schemas/…TioMinuterDatabase/11.json`.
- **Repository** `data/daily/DailyProgressRepositoryImpl(dao, clock, changes)`: `observeProgress()` creates the row on its first read from `DailyProgressDao.dailyRefIds()` (and again after a local wipe removed it); `onDayFinished` / `restart` write only when the state changes; every write enqueues `dailyProgress/state` in the same transaction (`ChangeTracker`), under one `Mutex`, `NonCancellable`.
- **Where it's written:** `WorkoutViewModel.recordCompletion` (the single finish: after the DAILY entry; a failure is logged and never breaks the workout), the selection's and the completion's restart.
- **Sync:** `SyncCollection.DAILY_PROGRESS` (id `state`, `canTombstone` false). `AppSyncStore.pullPlan` pulls it last, `fullFetch` (one document: the store learns when the server has none). `applyDailyProgress` → `DailyProgressSync.reconcile(local, remote?, history)`: server ahead → the row takes the remote state and its server time, the pending change is dropped; device ahead → kept and enqueued (pushed in the same sync); equal → nothing (the pending change dropped); no server document → the device's state (cycle 0 raised to the history) is uploaded. `pending()` drops a daily-progress change whose row is gone (never a tombstone). `FirestoreRemoteUserData` writes it without `deleted`; `deleteAll` (Delete account) deletes the `dailyProgress` collection.
- **Wipe:** `DefaultLocalUserDataWiper` clears `daily_progress` (an account switch: the next account's document is pulled; without one, its history raises it).
- **UI:** the selection (`DailyDayStatus`; a completed day shows a check, the next one "Available today" and a highlighted card, a locked one a lock, "Complete Day N to unlock" and no click; the card's `stateDescription` "Completed" / "Available" / "Locked"; "Restart days" in the top bar once a day is done; the cycle-complete card); `RestartDaysDialog`; Home's day line (observed); `WorkoutCompletionViewModel` (route `workout_completion/{routineId}`; the offer only for the last routine once the cycle is complete). Previews: `DailyRoutineSelectionPreviews.kt`.

### 28.3 Web
- `TrainingService.dailyProgress` (read; created from `trainingLog where source == "DAILY"` only when missing), `finishDailyDay`, `restartDailyDays`: each change runs `updateDailyProgress` = a Firestore **transaction** over the server's current document (finish = `afterFinishing(server)`, restart = `restart(server)`), so a newer state from the phone is never overwritten; offline the change applies to the cached copy and is queued.
- Train hub: `TrainBloc.availableDayOf(training, progress, state)`, the progress read on load and on every return. Daily selection: the same three states (the state as each day header's semantics value), Start only on unlocked days, "Restart days" in the app bar, the cycle-complete card, `confirmRestartDays`; progress read on every visit, Retry when it can't be read. Daily workout: a locked day opened by its URL shows the lock line and ignores every action; the finish calls `finishDailyDay`; after the last day the completion offers "Start again from Day 1" (`DailyRestartDays` → back to the days).
- Reads and writes: `get dailyProgress/state` per hub / selection visit and workout open; the DAILY history once; one transaction per finish or restart.

### 28.4 Firestore
`users/{uid}/dailyProgress/state`: exactly `lastCompletedDay` (int 0–999), `cycle` (int 0–2147483647), `updatedAt` (= `request.time`), `schemaVersion` 1; owner only; id `state` only; no `deleted`, no tombstone; no order enforced (the clients merge). Rules tests `firebase/rules-tests/test/dailyProgress.test.js` (12) + the generic `documentTypes` cases (3): **167 rules tests**. 19 mutations of the new rule, 18 killed; the survivor (`hasAll` of the required keys dropped) is equivalent, since every required key is read by a later check. Shared fixture `dailyProgress` (`fixtureData.js` → `shared/fixtures/firestore_documents.json`), checked by the web's mapper tests.

### 28.5 Parity and tests
- `DailyUnlockGoldenTest` → `shared/golden/daily_unlock.json` (86 cases: 31 day states with every day from −1 to last + 2 — unlocked, completed, after finishing, restart, the day line, cycle complete —, 5 histories incl. Kotlin `toIntOrNull` edge refIds ("+5", "05", overflow), 50 merges incl. 40 seeded) ↔ `web/test/parity/daily_unlock_golden_test.dart`.
- JVM: 41 new (`DailyUnlockRulesTest` 9, `DailyProgressSyncTest` 5, `DailyProgressRepositoryImplTest` 5, `DailyRoutineSelectionViewModelTest` 9, `WorkoutCompletionViewModelTest` 3, `WorkoutViewModelTrainingLogTest` +3, `HomeViewModelTest` +4, `DocMappersTest` +2, `DailyUnlockGoldenTest` 1); **927 in total**.
- Instrumented: 123 (119 + `MigrationTest` 10 → 11 and 1 → 11 + 2 opt-in `WebInteropEmulatorTest` steps); 112 pass, 11 skipped (the opt-in emulator tests).
- Web: **338** (`flutter test`; 14 new: the golden, the selection states / restart dialog / cycle complete, the hub's rule and refresh, the workout's unlock / repeat / locked URL / inactivity / first read, the mappers against the shared fixture).
- Cross-client (`scripts/test_web_train_emulator.sh`, a 4th step): Android finishes Day 1 and pushes → the web reads `(1, 0)`, Day 2 available, restarts in a transaction → `(0, 1)` with exactly the four fields → Android, holding `(1, 0)` of the old cycle, pulls `(0, 1)`: only Day 1 unlocked, nothing to push back.

