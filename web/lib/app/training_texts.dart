import 'dart:ui';

import '../domain/program.dart';
import '../l10n/app_localizations.dart';
import '../ui/workout_name_text.dart';
import 'training_service.dart';

/// The stored texts in the browser's language (a stored title is in the recording device's
/// language, as on Android; readers name workouts in their own language anyway).
class LocaleTrainingTexts implements TrainingTexts {
  LocaleTrainingTexts([Locale? locale])
    : _l10n = lookupAppLocalizations(_supported(locale ?? PlatformDispatcher.instance.locale));

  final AppLocalizations _l10n;

  static Locale _supported(Locale locale) =>
      AppLocalizations.supportedLocales.any((l) => l.languageCode == locale.languageCode)
      ? Locale(locale.languageCode)
      : const Locale('en');

  @override
  String get memberFallbackName => _l10n.memberFallbackName;

  @override
  String programTitle(ProgramWorkout workout) => programTitleOf(workout.titleCode, _l10n);
}
