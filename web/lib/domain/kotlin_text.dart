/// Kotlin's text helpers where Dart's differ, so the ports decide exactly as Android:
/// `String.isBlank()` (Dart's `trim()` removes U+0085 and U+FEFF, which Kotlin keeps, and keeps
/// U+001C–U+001F, which Kotlin removes) and `String.toIntOrNull()` (Dart's `int.tryParse` also reads
/// hex and ignores surrounding whitespace). Checked by shared/golden/workout_names.json.
library;

/// Kotlin's `Char.isWhitespace()`: Java's `isWhitespace` or `isSpaceChar` (Zs, Zl, Zp).
bool isKotlinWhitespace(int unit) =>
    (unit >= 0x09 && unit <= 0x0D) ||
    (unit >= 0x1C && unit <= 0x20) ||
    unit == 0xA0 ||
    unit == 0x1680 ||
    (unit >= 0x2000 && unit <= 0x200A) ||
    unit == 0x2028 ||
    unit == 0x2029 ||
    unit == 0x202F ||
    unit == 0x205F ||
    unit == 0x3000;

/// Kotlin's `CharSequence.isBlank()`: empty or only [isKotlinWhitespace] characters.
bool isKotlinBlank(String text) => text.codeUnits.every(isKotlinWhitespace);

/// Kotlin's `String.toIntOrNull()` (radix 10): an optional `+` / `-`, then ASCII digits only, within
/// the 32-bit Int range; anything else is null.
int? kotlinToIntOrNull(String text) {
  if (text.isEmpty) return null;
  var start = 0;
  var negative = false;
  final first = text.codeUnitAt(0);
  if (first == 0x2B || first == 0x2D) {
    if (text.length == 1) return null;
    negative = first == 0x2D;
    start = 1;
  }
  var value = 0;
  for (var i = start; i < text.length; i++) {
    final digit = text.codeUnitAt(i) - 0x30;
    if (digit < 0 || digit > 9) return null;
    value = value * 10 + digit;
    if (value > 2147483648) return null;
  }
  if (negative) value = -value;
  if (value > 2147483647 || value < -2147483648) return null;
  return value;
}

/// Kotlin's `String.trim()`: removes [isKotlinWhitespace] characters at both ends.
String kotlinTrim(String text) {
  var start = 0;
  var end = text.length;
  while (start < end && isKotlinWhitespace(text.codeUnitAt(start))) {
    start++;
  }
  while (end > start && isKotlinWhitespace(text.codeUnitAt(end - 1))) {
    end--;
  }
  return text.substring(start, end);
}
