/// Logged activities, ported from Android's `domain/traininglog/ActivityEntryRules.kt` and
/// `ActivityType.kt` (`Intensity`), with the form rules of `ui/logactivity/LogActivityViewModel.kt`
/// (the duration text, the day options, the default time).
library;

import 'calendar_dates.dart';
import 'kotlin_text.dart';
import 'training.dart';

enum Intensity {
  easy('EASY'),
  moderate('MODERATE'),
  hard('HARD');

  const Intensity(this.code);

  final String code;

  static Intensity? fromCode(Object? code) {
    for (final value in values) {
      if (value.code == code) return value;
    }
    return null;
  }
}

enum ActivityEntryError {
  typeMissing,
  nameMissing,
  nameTooLong,
  durationOutOfRange,
  intensityMissing,
  dateTooEarly,
  dateInFuture,
  noteTooLong,
}

/// What the person entered. [durationMinutes] and [intensity] are required; [customName] only for
/// OTHER; [note] is optional. [completedAt] is wall-clock millis.
class ActivityEntryInput {
  const ActivityEntryInput({
    required this.type,
    required this.customName,
    required this.durationMinutes,
    required this.intensity,
    required this.completedAt,
    required this.note,
  });

  final ActivityType? type;
  final String customName;
  final int? durationMinutes;
  final Intensity? intensity;
  final int completedAt;
  final String note;
}

abstract final class ActivityEntryRules {
  static const int minDurationMinutes = 1;
  static const int maxDurationMinutes = 600;
  static const int nameMaxLength = 40;
  static const int noteMaxLength = 200;

  /// The oldest day allowed: today and the 6 days before it.
  static const int daysBack = 6;

  /// 00:00 (browser zone) of the oldest allowed day.
  static int windowStart(String today) => startOfDay(CalendarDates.plusDays(today, -daysBack));

  /// Every rule [input] breaks; empty when it can be saved.
  static Set<ActivityEntryError> validate(ActivityEntryInput input, int nowMillis, int earliestMillis) {
    final errors = <ActivityEntryError>{};
    if (input.type == null) errors.add(ActivityEntryError.typeMissing);
    if (input.type == ActivityType.other) {
      // Kotlin's trim(); the length counts UTF-16 units on both sides
      final name = kotlinTrim(input.customName);
      if (name.isEmpty) errors.add(ActivityEntryError.nameMissing);
      if (name.length > nameMaxLength) errors.add(ActivityEntryError.nameTooLong);
    }
    final minutes = input.durationMinutes;
    if (minutes == null || minutes < minDurationMinutes || minutes > maxDurationMinutes) {
      errors.add(ActivityEntryError.durationOutOfRange);
    }
    if (input.intensity == null) errors.add(ActivityEntryError.intensityMissing);
    if (input.completedAt > nowMillis) errors.add(ActivityEntryError.dateInFuture);
    if (input.completedAt < earliestMillis) errors.add(ActivityEntryError.dateTooEarly);
    if (input.note.length > noteMaxLength) errors.add(ActivityEntryError.noteTooLong);
    return errors;
  }

  /// The stored title: the trimmed custom name for OTHER, empty for every other type.
  static String titleFor(ActivityEntryInput input) =>
      input.type == ActivityType.other ? kotlinTrim(input.customName) : '';

  /// The stored note: trimmed, null when blank.
  static String? noteFor(ActivityEntryInput input) {
    final note = kotlinTrim(input.note);
    return note.isEmpty ? null : note;
  }

  /// The duration field keeps digits only, at most 3 (Android's form).
  static String durationText(String text) {
    final digits = text.replaceAll(RegExp(r'[^0-9]'), '');
    return digits.length > 3 ? digits.substring(0, 3) : digits;
  }

  /// The days offered: today, yesterday and the 5 before; plus [entryDate] when editing an older one.
  static List<String> dayOptions(String today, {String? entryDate}) {
    final days = [for (var i = 0; i <= daysBack; i++) CalendarDates.plusDays(today, -i)];
    if (entryDate != null && !days.contains(entryDate)) days.add(entryDate);
    return days;
  }

  /// The default time of a day: now for today, 18:00 for a past day.
  static (int, int) defaultTime(String date, String today, DateTime now) =>
      date == today ? (now.hour, now.minute) : (18, 0);

  /// 00:00 of [date] in the browser zone, as epoch millis.
  static int startOfDay(String date) => epochMillisAt(date, 0, 0);

  /// [date] at [hour]:[minute] in the browser zone, as epoch millis.
  static int epochMillisAt(String date, int hour, int minute) {
    final utc = CalendarDates.toUtc(date);
    return DateTime(utc.year, utc.month, utc.day, hour, minute).millisecondsSinceEpoch;
  }
}
