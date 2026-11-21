/// Pure arithmetic on local calendar dates ("yyyy-MM-dd"), as Android's `CalendarDates`
/// (app/src/main/java/com/javargasp/tiominutermvi/domain/traininglog/CalendarDates.kt).
/// A date string has no zone, so the math runs in UTC, where every day has 24 hours.
/// Weeks start on Monday. An invalid date throws [ArgumentError], as Android's
/// `IllegalArgumentException`.
abstract final class CalendarDates {
  static const int daysPerWeek = 7;

  static final RegExp _pattern = RegExp(r'^(\d{4,})-(\d{2})-(\d{2})$');

  /// [date] moved by [days] calendar days (negative to go back).
  static String plusDays(String date, int days) => format(_parse(date).add(Duration(days: days)));

  /// The Monday of the week that contains [date].
  static String mondayOf(String date) {
    final parsed = _parse(date);
    // DateTime.weekday: Monday = 1 ... Sunday = 7
    return format(parsed.subtract(Duration(days: parsed.weekday - DateTime.monday)));
  }

  /// The 7 dates (Monday to Sunday) of the week that contains [date].
  static List<String> weekDates(String date) {
    final monday = mondayOf(date);
    return [for (var i = 0; i < daysPerWeek; i++) plusDays(monday, i)];
  }

  /// The Monday [weeks] weeks after (or before) the week of [date].
  static String plusWeeks(String date, int weeks) => plusDays(mondayOf(date), weeks * daysPerWeek);

  /// Whether [date] is a valid "yyyy-MM-dd" date (no lenient rollover: 2026-02-30 is not).
  static bool isValid(String date) {
    try {
      _parse(date);
      return true;
    } on ArgumentError {
      return false;
    }
  }

  /// The local date of [moment] in the device zone, as Android's `LocalDateProvider.dateOf`.
  static String dateOf(DateTime moment) => format(moment.toLocal());

  /// "yyyy-MM-dd" of the calendar date of [value] (its year, month and day, whatever the zone).
  static String format(DateTime value) {
    final year = value.year.toString().padLeft(4, '0');
    final month = value.month.toString().padLeft(2, '0');
    final day = value.day.toString().padLeft(2, '0');
    return '$year-$month-$day';
  }

  /// The UTC midnight of [date] (to format it or read its weekday).
  static DateTime toUtc(String date) => _parse(date);

  static DateTime _parse(String date) {
    final match = _pattern.firstMatch(date);
    if (match == null) throw ArgumentError('Not a yyyy-MM-dd date: $date');
    final year = int.parse(match.group(1)!);
    final month = int.parse(match.group(2)!);
    final day = int.parse(match.group(3)!);
    final parsed = DateTime.utc(year, month, day);
    if (parsed.year != year || parsed.month != month || parsed.day != day) {
      throw ArgumentError('Not a yyyy-MM-dd date: $date');
    }
    return parsed;
  }
}

/// "Today" and the current week in the device zone, read on every call (as Android's
/// `LocalDateProvider`, which reads the zone each time). [now] is injectable for tests.
class LocalDates {
  LocalDates({DateTime Function()? now}) : _now = now ?? DateTime.now;

  final DateTime Function() _now;

  String today() => CalendarDates.dateOf(_now());

  String currentWeekStart() => CalendarDates.mondayOf(today());

  DateTime now() => _now();
}
