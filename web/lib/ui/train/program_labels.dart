/// The program feature's labels (Android's `ui/program/common/ProgramUi.kt`).
library;

import '../../domain/program.dart';
import '../../l10n/app_localizations.dart';
import '../workout_name_text.dart';
import 'train_widgets.dart';

String goalLabel(ProgramGoal goal, AppLocalizations l10n) => switch (goal) {
  ProgramGoal.gainMuscle => l10n.programGoalGainMuscle,
  ProgramGoal.loseFat => l10n.programGoalLoseFat,
};

String levelLabel(ProgramLevel level, AppLocalizations l10n) => switch (level) {
  ProgramLevel.beginner => l10n.programLevelBeginner,
  ProgramLevel.intermediate => l10n.programLevelIntermediate,
  ProgramLevel.advanced => l10n.programLevelAdvanced,
};

String locationLabel(ProgramLocation location, AppLocalizations l10n) => switch (location) {
  ProgramLocation.home => l10n.programLocationHome,
  ProgramLocation.gym => l10n.programLocationGym,
};

String focusLabel(ProgramFocus focus, AppLocalizations l10n) => switch (focus) {
  ProgramFocus.balanced => l10n.programFocusBalanced,
  ProgramFocus.lowerBody => l10n.programFocusLowerBody,
  ProgramFocus.upperBody => l10n.programFocusUpperBody,
  ProgramFocus.core => l10n.programFocusCore,
};

String focusDescription(ProgramFocus focus, AppLocalizations l10n) => switch (focus) {
  ProgramFocus.balanced => l10n.programFocusBalancedDescription,
  ProgramFocus.lowerBody => l10n.programFocusLowerBodyDescription,
  ProgramFocus.upperBody => l10n.programFocusUpperBodyDescription,
  ProgramFocus.core => l10n.programFocusCoreDescription,
};

String segmentLabel(TreadmillSegmentLabel label, AppLocalizations l10n) => switch (label) {
  TreadmillSegmentLabel.warmUp => l10n.programSegmentWarmUp,
  TreadmillSegmentLabel.jog => l10n.programSegmentJog,
  TreadmillSegmentLabel.walk => l10n.programSegmentWalk,
  TreadmillSegmentLabel.inclineWalk => l10n.programSegmentInclineWalk,
  TreadmillSegmentLabel.coolDown => l10n.programSegmentCoolDown,
};

/// The workout's title in the current language ("Full body A", "Treadmill"…), the one table of
/// program titles (`programTitle`).
String workoutTitle(ProgramWorkout workout, AppLocalizations l10n) => programTitle(workout.titleCode, l10n);

/// The program's name on its card: its goal (as Android's list).
String programName(ProgramProfile profile, AppLocalizations l10n) => goalLabel(profile.goal, l10n);

/// "6 weeks · 3 days per week".
String programMeta(ProgramProfile profile, AppLocalizations l10n) =>
    l10n.programCardMeta(l10n.programWeeks(profile.weeks), l10n.programDaysPerWeek(profile.daysPerWeek));

/// Android's `workoutSummary`: "6 exercises", "3.0 km", "35 min · 6 intervals", "3 rounds · 5 exercises".
String workoutSummary(ProgramWorkout workout, AppLocalizations l10n) => switch (workout) {
  StrengthWorkout(:final slots) => l10n.trainExercisesCount(slots.length),
  OutdoorRunWorkout(:final distanceKm) => l10n.programDistanceKm(formatOneDecimal(distanceKm, languageCode(l10n))),
  TreadmillWorkout(:final segments) => l10n.programSummaryPair(
    l10n.programMinutes((segments.fold<int>(0, (sum, s) => sum + s.durationSeconds) + 59) ~/ 60),
    l10n.programIntervals(
      segments
          .where((s) => s.label == TreadmillSegmentLabel.jog || s.label == TreadmillSegmentLabel.inclineWalk)
          .length,
    ),
  ),
  HomeCircuitWorkout(:final rounds, :final exerciseIds) => l10n.programSummaryPair(
    l10n.programRounds(rounds),
    l10n.trainExercisesCount(exerciseIds.length),
  ),
};

String languageCode(AppLocalizations l10n) => l10n.localeName.split('_').first;
