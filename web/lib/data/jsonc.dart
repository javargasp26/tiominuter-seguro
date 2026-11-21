import 'dart:convert';

/// Decodes JSON with comments (`//`, `/* */`) and trailing commas, as Android's lenient
/// `remoteJson` reads `routines_for_gym.jsonc`. Comments and commas inside strings are kept.
Object? parseJsonc(String text) => jsonDecode(stripJsonc(text));

/// [text] without comments and trailing commas (string contents untouched).
String stripJsonc(String text) {
  final out = StringBuffer();
  var i = 0;
  while (i < text.length) {
    final char = text[i];
    if (char == '"') {
      // A string, with its escapes
      final start = i;
      i++;
      while (i < text.length && text[i] != '"') {
        i += text[i] == r'\' ? 2 : 1;
      }
      i++;
      out.write(text.substring(start, i > text.length ? text.length : i));
    } else if (text.startsWith('//', i)) {
      while (i < text.length && text[i] != '\n') {
        i++;
      }
    } else if (text.startsWith('/*', i)) {
      final end = text.indexOf('*/', i + 2);
      i = end < 0 ? text.length : end + 2;
    } else if (char == ',' && _closesNext(text, i + 1)) {
      i++;
    } else {
      out.write(char);
      i++;
    }
  }
  return out.toString();
}

// Whether the next meaningful character after [from] closes an array or object (skipping
// whitespace and comments), so the comma before it is a trailing one
bool _closesNext(String text, int from) {
  var i = from;
  while (i < text.length) {
    final char = text[i];
    if (char == ' ' || char == '\n' || char == '\r' || char == '\t') {
      i++;
    } else if (text.startsWith('//', i)) {
      while (i < text.length && text[i] != '\n') {
        i++;
      }
    } else if (text.startsWith('/*', i)) {
      final end = text.indexOf('*/', i + 2);
      i = end < 0 ? text.length : end + 2;
    } else {
      return char == ']' || char == '}';
    }
  }
  return false;
}
