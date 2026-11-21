/// The text of a workout name in the viewer's language, as Android's `WorkoutName.text()`
/// (app/.../ui/components/WorkoutNameText.kt) and `localizedName` (ui/components/LocalizedName.kt).
library;

import '../domain/kotlin_text.dart';
import '../domain/training.dart';
import '../domain/workout_names.dart';
import '../l10n/app_localizations.dart';

const String spanishLanguage = 'es';

/// The single rule for catalog names: in Spanish [nameEs] when present and not blank, else [name].
String localizedName(String name, String? nameEs, String language) =>
    language == spanishLanguage && nameEs != null && !isKotlinBlank(nameEs) ? nameEs : name;

extension WorkoutNameText on WorkoutName {
  /// Gym: [localizedName]. Daily: "Day N", plus the routine's title in that language when there is
  /// one (Spanish: only its Spanish title, never an English one). Program: the workout's title.
  /// Activity: its type. Stored: as it is. Nothing known: the source label.
  String text(AppLocalizations l10n) {
    final language = l10n.localeName.split('_').first;
    return switch (this) {
      GymWorkoutName(:final name, :final nameEs) => localizedName(name, nameEs, language),
      DailyWorkoutName(:final dayNumber, :final title, :final titleEs) => () {
        final day = l10n.workoutNameDay(dayNumber);
        final shown = language == spanishLanguage ? titleEs : title;
        return shown == null || isKotlinBlank(shown) ? day : l10n.workoutNameDayWithTitle(day, shown);
      }(),
      ProgramWorkoutName(:final code) => programTitle(code, l10n),
      ActivityWorkoutName(:final type) => activityLabel(type, l10n),
      StoredWorkoutName(:final text) => text,
      SourceOnlyWorkoutName(:final source) => sourceLabel(source, l10n),
    };
  }
}

String sourceLabel(TrainingSource source, AppLocalizations l10n) => switch (source) {
  TrainingSource.daily => l10n.sourceDaily,
  TrainingSource.gym => l10n.sourceGym,
  TrainingSource.program => l10n.sourceProgram,
  TrainingSource.activity => l10n.sourceActivity,
};

/// The one table of program titles (Android's `programTitleRes`).
String programTitleOf(ProgramTitleCode code, AppLocalizations l10n) => programTitle(code, l10n);

String programTitle(ProgramTitleCode code, AppLocalizations l10n) => switch (code) {
  ProgramTitleCode.fullBodyA => l10n.programTitleFullBodyA,
  ProgramTitleCode.fullBodyB => l10n.programTitleFullBodyB,
  ProgramTitleCode.fullBodyC => l10n.programTitleFullBodyC,
  ProgramTitleCode.upperA => l10n.programTitleUpperA,
  ProgramTitleCode.upperB => l10n.programTitleUpperB,
  ProgramTitleCode.lowerA => l10n.programTitleLowerA,
  ProgramTitleCode.lowerB => l10n.programTitleLowerB,
  ProgramTitleCode.push => l10n.programTitlePush,
  ProgramTitleCode.pull => l10n.programTitlePull,
  ProgramTitleCode.legs => l10n.programTitleLegs,
  ProgramTitleCode.fullBody => l10n.programTitleFullBody,
  ProgramTitleCode.upper => l10n.programTitleUpper,
  ProgramTitleCode.lower => l10n.programTitleLower,
  ProgramTitleCode.upperC => l10n.programTitleUpperC,
  ProgramTitleCode.lowerC => l10n.programTitleLowerC,
  ProgramTitleCode.outdoorRun => l10n.programTitleOutdoorRun,
  ProgramTitleCode.briskWalk => l10n.programTitleBriskWalk,
  ProgramTitleCode.treadmill => l10n.programTitleTreadmill,
  ProgramTitleCode.homeCircuit => l10n.programTitleHomeCircuit,
};

String activityLabel(ActivityType type, AppLocalizations l10n) => switch (type) {
  ActivityType.ownGymRoutine => l10n.activityOwnGymRoutine,
  ActivityType.running => l10n.activityRunning,
  ActivityType.walking => l10n.activityWalking,
  ActivityType.cycling => l10n.activityCycling,
  ActivityType.swimming => l10n.activitySwimming,
  ActivityType.yoga => l10n.activityYoga,
  ActivityType.pilates => l10n.activityPilates,
  ActivityType.dance => l10n.activityDance,
  ActivityType.teamSport => l10n.activityTeamSport,
  ActivityType.racketSport => l10n.activityRacketSport,
  ActivityType.martialArts => l10n.activityMartialArts,
  ActivityType.hiit => l10n.activityHiit,
  ActivityType.stretching => l10n.activityStretching,
  ActivityType.other => l10n.activityOther,
};

/// "32 min": rounded down, at least 1, as the Android history.
String durationText(int seconds, AppLocalizations l10n) {
  final minutes = seconds ~/ 60;
  return l10n.durationMinutes(minutes < 1 ? 1 : minutes);
}
