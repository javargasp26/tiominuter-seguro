import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:tiominuter_web/data/content_repository.dart';
import 'package:tiominuter_web/data/jsonc.dart';

/// The content read path: the real content/v1 files served by a fake Hosting, the manifest checked
/// as Android checks it, every file verified by size and SHA-256 before its names are used.
void main() {
  final contentDir = Directory('../content/v1');
  final gym = File('${contentDir.path}/routines_for_gym.jsonc').readAsBytesSync();
  final daily = File('${contentDir.path}/routines.json').readAsBytesSync();

  Map<String, Object?> entry(String path, List<int> bytes) => {
    'path': path,
    'size': bytes.length,
    'sha256': sha256.convert(bytes).toString(),
  };

  String manifest({List<Map<String, Object?>>? files, int schemaVersion = 1}) => jsonEncode({
    'schemaVersion': schemaVersion,
    'contentVersion': '20261021000000',
    'files': files ?? [entry('routines.json', daily), entry('routines_for_gym.jsonc', gym)],
  });

  ({HttpContentRepository repository, List<String> requested}) hosting(
    String manifestText, {
    Map<String, List<int>>? files,
  }) {
    final requested = <String>[];
    final served = files ?? {'routines_for_gym.jsonc': gym, 'routines.json': daily};
    final client = MockClient((request) async {
      requested.add(request.url.path.split('/').last + (request.url.hasQuery ? '?${request.url.query}' : ''));
      final name = request.url.pathSegments.last;
      if (name == 'manifest.json') return http.Response(manifestText, 200);
      final body = served[name];
      return body == null ? http.Response('', 404) : http.Response.bytes(body, 200);
    });
    return (
      repository: HttpContentRepository(
        const ContentLocation('https://tiominuter-e43f7.web.app/content/v1/'),
        client: client,
      ),
      requested: requested,
    );
  }

  test('the verified catalogs give the gym names (EN + ES) and the daily days', () async {
    final (:repository, :requested) = hosting(manifest());
    final catalog = await repository.catalog();
    expect(catalog.gymRoutines[64]?.name, 'Pull Day');
    expect(catalog.gymRoutines[64]?.nameEs, 'Día de tirón');
    expect(catalog.gymRoutines, hasLength(11));
    expect(catalog.dailyRoutines.keys, containsAll([1, 30]));
    expect(catalog.dailyRoutines[1]?.title, isNull);
    // The manifest first, then only the two files the names need, each named by its hash
    expect(requested.first, 'manifest.json');
    expect(requested.skip(1).map((r) => r.split('?').first), ['routines_for_gym.jsonc', 'routines.json']);
    expect(requested[1], 'routines_for_gym.jsonc?v=${sha256.convert(gym)}');
  });

  test('read once per page load', () async {
    final (:repository, :requested) = hosting(manifest());
    await repository.catalog();
    await repository.catalog();
    expect(requested, hasLength(3));
  });

  test('a file whose hash or size differs from the manifest is never used', () async {
    final changed = utf8.encode(utf8.decode(gym).replaceFirst('Pull Day', 'Pull Dax'));
    expect(
      (await hosting(
        manifest(),
        files: {'routines_for_gym.jsonc': changed, 'routines.json': daily},
      ).repository.catalog()).gymRoutines,
      isEmpty,
    );
    final longer = [...daily, 32];
    expect(
      (await hosting(
        manifest(),
        files: {'routines_for_gym.jsonc': gym, 'routines.json': longer},
      ).repository.catalog()).dailyRoutines,
      isEmpty,
    );
  });

  test('a manifest Android would reject leaves the catalogs empty', () async {
    for (final bad in [
      manifest(schemaVersion: 2),
      manifest(files: [entry('../routines.json', daily), entry('routines_for_gym.jsonc', gym)]),
      manifest(
        files: [entry('routines.json', daily), entry('routines.json', daily), entry('routines_for_gym.jsonc', gym)],
      ),
      manifest(
        files: [entry('routines.json', daily), entry('new_file.json', daily), entry('routines_for_gym.jsonc', gym)],
      ),
      manifest(files: [entry('routines.json', daily)]),
      'not json',
    ]) {
      final catalog = await hosting(bad).repository.catalog();
      expect(catalog.gymRoutines, isEmpty, reason: bad);
    }
  });

  test('offline (no manifest) the catalogs are empty and the next call tries again', () async {
    var online = false;
    final client = MockClient((request) async {
      if (!online) throw http.ClientException('offline');
      final name = request.url.pathSegments.last;
      return switch (name) {
        'manifest.json' => http.Response(manifest(), 200),
        'routines_for_gym.jsonc' => http.Response.bytes(gym, 200),
        _ => http.Response.bytes(daily, 200),
      };
    });
    final repository = HttpContentRepository(const ContentLocation('https://example.test/content/v1/'), client: client);
    expect((await repository.catalog()).gymRoutines, isEmpty);
    online = true;
    expect((await repository.catalog()).gymRoutines, isNotEmpty);
  });

  test('manifest entries follow the same rules as Android', () {
    expect(
      HttpContentRepository.parseManifest(
        manifest(
          files: [
            entry('animations/2_squat.gif', [1]),
          ],
        ),
      ).keys,
      ['animations/2_squat.gif'],
    );
    for (final path in [
      '/routines.json',
      r'animations\x.gif',
      'animations/.x.gif',
      'animations/x.png',
      'other/x.gif',
    ]) {
      expect(
        () => HttpContentRepository.parseManifest(
          manifest(
            files: [
              entry(path, [1]),
            ],
          ),
        ),
        throwsA(isA<ContentException>()),
        reason: path,
      );
    }
  });

  test('daily titles and their optional titleEs are read', () {
    final names = HttpContentRepository.parseDailyRoutineNames([
      {'routineId': 31, 'routineDay': 'Day 31', 'title': 'Core', 'titleEs': 'Abdomen', 'exercises': <Object>[]},
      {'routineId': 32, 'routineDay': 'Day 32', 'exercises': <Object>[]},
    ]);
    expect(names[31]?.title, 'Core');
    expect(names[31]?.titleEs, 'Abdomen');
    expect(names[32]?.title, isNull);
  });

  group('JSONC', () {
    test('comments and trailing commas go, strings are untouched', () {
      const text = '''
      // a comment
      [
        { "a": "x // not a comment", "b": "y /* nor this */", /* inline */ "c": [1, 2,], },
        { "d": "quote \\" and comma ,]" },
      ]
      ''';
      expect(parseJsonc(text), [
        {
          'a': 'x // not a comment',
          'b': 'y /* nor this */',
          'c': [1, 2],
        },
        {'d': 'quote " and comma ,]'},
      ]);
    });

    test('the real routines_for_gym.jsonc parses', () {
      expect(parseJsonc(utf8.decode(gym)), isA<List<Object?>>());
    });
  });
}
