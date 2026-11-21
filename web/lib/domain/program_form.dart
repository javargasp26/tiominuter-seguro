/// The program create form's rules, ported from Android's `ui/program/create/ProgramCreateState.kt`
/// and `ProgramCreateViewModel.kt`: what each field keeps (`digits`, `decimal`), the parsed values
/// and their ranges, the errors, the BMI, the low-impact note and the underweight warning.
library;

import 'kotlin_text.dart';
import 'program.dart';
import 'program_generator.dart';

enum ProgramFieldError { required, outOfRange }

class ProgramForm {
  const ProgramForm({
    this.ageText = '',
    this.weightText = '',
    this.heightText = '',
    this.level = ProgramLevel.beginner,
    this.goal = ProgramGoal.gainMuscle,
    this.focus = ProgramFocus.defaultFocus,
    this.location = ProgramLocation.home,
    this.weeks = ProgramProfile.defaultWeeks,
    this.daysPerWeek = ProgramProfile.defaultDaysPerWeek,
    this.showAllErrors = false,
  });

  static const double underweightBmi = 18.5;

  final String ageText;
  final String weightText;
  final String heightText;
  final ProgramLevel level;
  final ProgramGoal goal;
  final ProgramFocus focus;
  final ProgramLocation location;
  final int weeks;
  final int daysPerWeek;
  final bool showAllErrors;

  ProgramForm copyWith({
    String? ageText,
    String? weightText,
    String? heightText,
    ProgramLevel? level,
    ProgramGoal? goal,
    ProgramFocus? focus,
    ProgramLocation? location,
    int? weeks,
    int? daysPerWeek,
    bool? showAllErrors,
  }) => ProgramForm(
    ageText: ageText ?? this.ageText,
    weightText: weightText ?? this.weightText,
    heightText: heightText ?? this.heightText,
    level: level ?? this.level,
    goal: goal ?? this.goal,
    focus: focus ?? this.focus,
    location: location ?? this.location,
    weeks: weeks == null ? this.weeks : _clamp(weeks, ProgramProfile.weeksRange),
    daysPerWeek: daysPerWeek == null ? this.daysPerWeek : _clamp(daysPerWeek, ProgramProfile.daysPerWeekRange),
    showAllErrors: showAllErrors ?? this.showAllErrors,
  );

  int? get age => _inRange(kotlinToIntOrNull(ageText), ProgramProfile.ageRange);

  double? get weightKg {
    final weight = parseDecimal(weightText);
    return weight != null && weight >= ProgramProfile.minWeightKg && weight <= ProgramProfile.maxWeightKg
        ? weight
        : null;
  }

  int? get heightCm => _inRange(kotlinToIntOrNull(heightText), ProgramProfile.heightRangeCm);

  ProgramFieldError? get ageError => _fieldError(ageText, age != null);
  ProgramFieldError? get weightError => _fieldError(weightText, weightKg != null);
  ProgramFieldError? get heightError => _fieldError(heightText, heightCm != null);

  /// The profile when every field is valid.
  ProgramProfile? get profile {
    final age = this.age;
    final weight = weightKg;
    final height = heightCm;
    if (age == null || weight == null || height == null) return null;
    return ProgramProfile(
      age: age,
      weightKg: weight,
      heightCm: height,
      level: level,
      goal: goal,
      location: location,
      weeks: weeks,
      daysPerWeek: daysPerWeek,
      focus: focus,
    );
  }

  double? get bmi {
    final weight = weightKg;
    final height = heightCm;
    return weight == null || height == null ? null : computeBmi(weight, height);
  }

  bool isLowImpactWith(ImpactRules? rules) {
    final profile = this.profile;
    return rules != null && profile != null && isLowImpact(profile, rules);
  }

  /// BMI under 18.5 with "Lose fat": suggest "Gain muscle".
  bool get showUnderweightWarning {
    final bmi = this.bmi;
    return bmi != null && goal == ProgramGoal.loseFat && bmi < underweightBmi;
  }

  ProgramFieldError? _fieldError(String text, bool valid) {
    if (valid) return null;
    if (text.isEmpty) return showAllErrors ? ProgramFieldError.required : null;
    return ProgramFieldError.outOfRange;
  }

  /// Accepts "72.5" and "72,5" (Kotlin's `toDoubleOrNull` on digits and one separator).
  static double? parseDecimal(String text) {
    final normalized = text.replaceAll(',', '.');
    if (!RegExp(r'^[0-9]*\.?[0-9]*$').hasMatch(normalized) || !RegExp('[0-9]').hasMatch(normalized)) return null;
    return double.tryParse(normalized.endsWith('.') ? '${normalized}0' : normalized);
  }

  /// The age and height fields keep digits only (2 and 3 at most).
  static String digits(String text, int maxLength) {
    final digits = text.replaceAll(RegExp(r'[^0-9]'), '');
    return digits.length > maxLength ? digits.substring(0, maxLength) : digits;
  }

  /// Digits plus one decimal separator, at most 6 characters ("120.5").
  static String decimal(String text) {
    var separatorSeen = false;
    final out = StringBuffer();
    for (final char in text.split('')) {
      final isDigit = RegExp('^[0-9]\$').hasMatch(char);
      if (isDigit) {
        out.write(char);
      } else if ((char == '.' || char == ',') && !separatorSeen) {
        separatorSeen = true;
        out.write(char);
      }
    }
    final value = out.toString();
    return value.length > 6 ? value.substring(0, 6) : value;
  }

  static int _clamp(int value, IntRange range) =>
      value < range.first ? range.first : (value > range.last ? range.last : value);

  static int? _inRange(int? value, IntRange range) => value != null && range.contains(value) ? value : null;
}
