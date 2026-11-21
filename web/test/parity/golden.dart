import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';

/// The Kotlin ↔ Dart parity files (shared/golden/, written by the Kotlin golden tests in
/// app/src/test/java/com/javargasp/tiominutermvi/parity/; update with scripts/update_golden.sh).
/// Tests run from web/, so the paths are relative to it.
abstract final class Golden {
  static Map<String, Object?> load(String domain) =>
      jsonDecode(File('../shared/golden/$domain.json').readAsStringSync()) as Map<String, Object?>;

  static List<Map<String, Object?>> cases(Map<String, Object?> golden) => [
    for (final item in golden['cases']! as List) item as Map<String, Object?>,
  ];

  static String contentText(String file) => File('../content/v1/$file').readAsStringSync();

  static String sha256Of(String file) => sha256.convert(File('../content/v1/$file').readAsBytesSync()).toString();
}

/// Collects the cases that differ, so one run lists every mismatch (up to a limit) instead of the first.
class Mismatches {
  final List<String> _items = [];

  int get count => _items.length;

  void check(String id, String what, Object? actual, Object? expected) {
    if (!_deepEquals(actual, expected)) _items.add('$id: $what\n  dart:   $actual\n  kotlin: $expected');
  }

  String report([int limit = 15]) =>
      '${_items.length} mismatches with the Kotlin golden:\n${_items.take(limit).join('\n')}'
      '${_items.length > limit ? '\n…' : ''}';

  static bool _deepEquals(Object? a, Object? b) {
    if (a is List && b is List) {
      if (a.length != b.length) return false;
      for (var i = 0; i < a.length; i++) {
        if (!_deepEquals(a[i], b[i])) return false;
      }
      return true;
    }
    if (a is Map && b is Map) {
      if (a.length != b.length) return false;
      for (final key in a.keys) {
        if (!b.containsKey(key) || !_deepEquals(a[key], b[key])) return false;
      }
      return true;
    }
    // JSON numbers: 5 and 5.0 are the same number
    if (a is num && b is num) return a == b;
    return a == b;
  }
}
