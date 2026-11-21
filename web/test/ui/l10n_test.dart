import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// The web's strings follow the same rules as Android's (CLAUDE.md "Conventions", Android's
/// NeutralLanguageTest): both languages define the same keys and placeholders, and no gendered or
/// blaming words refer to the person.
void main() {
  Map<String, Object?> arb(String locale) =>
      jsonDecode(File('lib/l10n/app_$locale.arb').readAsStringSync()) as Map<String, Object?>;
  final en = arb('en');
  final es = arb('es');
  Iterable<String> keys(Map<String, Object?> strings) => strings.keys.where((key) => !key.startsWith('@'));

  test('English and Spanish define the same keys', () {
    expect(keys(es).toSet(), keys(en).toSet());
  });

  test('every string keeps its placeholders in both languages', () {
    final placeholder = RegExp(r'\{(\w+)[,}]');
    for (final key in keys(en)) {
      Set<String> of(Map<String, Object?> strings) =>
          placeholder.allMatches(strings[key]! as String).map((m) => m.group(1)!).toSet();
      expect(of(es), of(en), reason: key);
    }
  });

  // Android's NeutralLanguageTest denylists, plus the tone words the guidelines name
  const esDenylist = [
    'bienvenido', 'bienvenida', 'atrasado', 'atrasada', 'preparado', 'preparada', 'cansado', 'cansada', //
    'motivado', 'motivada', 'comprometido', 'comprometida', 'listo', 'lista',
  ];
  const enDenylist = ['guys', 'bro', 'man up', 'beast mode'];
  const toneDenylist = ['no cumpliste', 'fallaste', 'perezoso', 'lazy', 'sin excusas', 'no excuses'];

  // Key → the denylisted words it may contain, as Android's NeutralLanguageTest allowlist. The brand
  // tagline (Android home_screen_tagline) is the product owner's decision: don't change it without
  // their approval. Only its phrase is allowed, and only there
  const allowlist = {
    'tagline': {'no excuses', 'sin excusas'},
  };

  List<String> offenders(Map<String, Object?> strings, List<String> denylist) => [
    for (final key in keys(strings))
      for (final word in denylist)
        if (!(allowlist[key]?.contains(word) ?? false) &&
            RegExp(
              '\\b${RegExp.escape(word)}\\b',
              caseSensitive: false,
              unicode: true,
            ).hasMatch(strings[key]! as String))
          '$key: $word',
  ];

  test('Spanish strings use neutral language', () {
    expect(offenders(es, [...esDenylist, ...toneDenylist]), isEmpty);
    final inclusiveEnding = RegExp(
      r'\b(bienvenid|atrasad|preparad|cansad|motivad|comprometid|list)[oa@xe]\b',
      caseSensitive: false,
    );
    expect(keys(es).where((key) => inclusiveEnding.hasMatch(es[key]! as String)), isEmpty);
  });

  test('English strings use neutral language', () {
    expect(offenders(en, [...enDenylist, ...toneDenylist]), isEmpty);
  });

  test('the tagline is the same as Android and the only string allowed its phrase', () {
    expect(en['tagline'], 'No excuses — just 10 minutes a day.');
    expect(es['tagline'], 'Sin excusas — solo 10 minutos al día.');
    final strings = {
      'tagline': 'Sin excusas — solo 10 minutos al día.',
      'weekGoalMet': '¡Sin excusas! Meta cumplida.',
      'feedEmpty': 'No excuses this week.',
    };
    expect(offenders(strings, toneDenylist), ['weekGoalMet: sin excusas', 'feedEmpty: no excuses']);
    expect(offenders({'tagline': 'Sin excusas, perezoso.'}, toneDenylist), ['tagline: perezoso']);
  });
}
