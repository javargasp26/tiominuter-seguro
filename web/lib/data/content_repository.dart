/// The published content (content/README.md), read the way Android reads it
/// (data/content/ContentSyncer.kt, ContentManifest.kt): `manifest.json` first (`schemaVersion` 1, every
/// entry checked), then only the files the web needs (`routines_for_gym.jsonc` and `routines.json`
/// for workout names; for training also `exercises.json`, `program_exercises.json`,
/// `program_templates.json`, and the gym catalog parsed), each verified by size and SHA-256 before it is used. Anything wrong leaves
/// the catalog empty: names then fall back as WorkoutNameResolver says. The web has no bundled
/// copy, so it never shows content that wasn't verified.
library;

import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;

import '../domain/daily.dart';
import '../domain/gym.dart';
import '../domain/program.dart';
import '../domain/program_content.dart';
import '../domain/workout_names.dart';
import 'jsonc.dart';

/// Where the content is served: the same origin's `/content/v1/` in production (relative URL), or
/// the published site when the app runs elsewhere (`flutter run`, see web/README.md).
class ContentLocation {
  const ContentLocation(this.baseUrl);

  /// Ends with "/".
  final String baseUrl;

  Uri resolve(String path) => Uri.base.resolve(baseUrl).resolve(path);
}

/// One file of the manifest.
class ManifestEntry {
  const ManifestEntry({required this.path, required this.size, required this.sha256});

  final String path;
  final int size;
  final String sha256;
}

class ContentException implements Exception {
  const ContentException(this.reason);

  final String reason;

  @override
  String toString() => 'ContentException: $reason';
}

/// What training needs from the content: the daily routines with their exercises, and the program
/// catalog and templates the generator uses.
class TrainingContent {
  const TrainingContent({
    required this.dailyRoutines,
    required this.exercises,
    required this.programCatalog,
    required this.programTemplates,
    this.gymRoutines = const [],
  });

  final List<DailyRoutine> dailyRoutines;

  /// The gym catalog (`routines_for_gym.jsonc`), by routineId.
  final List<GymRoutine> gymRoutines;
  final Map<int, CatalogExercise> exercises;
  final List<ProgramExercise> programCatalog;
  final ProgramTemplates programTemplates;

  DailyRoutine? dailyRoutine(int routineId) {
    for (final routine in dailyRoutines) {
      if (routine.routineId == routineId) return routine;
    }
    return null;
  }

  GymRoutine? gymRoutine(int routineId) {
    for (final routine in gymRoutines) {
      if (routine.routineId == routineId) return routine;
    }
    return null;
  }

  Map<int, ProgramExercise> get programExercises => {for (final e in programCatalog) e.exerciseId: e};
}

/// The catalog names the web needs from the content.
abstract interface class ContentRepository {
  /// The gym and daily names; empty maps when the content can't be read or verified.
  Future<WorkoutNameCatalog> catalog();

  /// The training content; null when it can't be read or verified (training then says so).
  Future<TrainingContent?> training();
}

class HttpContentRepository implements ContentRepository {
  HttpContentRepository(this._location, {http.Client? client}) : _client = client ?? http.Client();

  static const String manifestFile = 'manifest.json';
  static const String gymRoutinesFile = 'routines_for_gym.jsonc';
  static const String dailyRoutinesFile = 'routines.json';
  static const String exercisesFile = 'exercises.json';
  static const String programExercisesFile = 'program_exercises.json';
  static const String programTemplatesFile = 'program_templates.json';

  /// Android's `ContentRules.MAX_FILE_BYTES`: a file over 5 MB is rejected.
  static const int maxFileBytes = 5 * 1024 * 1024;
  static const int maxManifestBytes = 1024 * 1024;

  final ContentLocation _location;
  final http.Client _client;
  Future<WorkoutNameCatalog>? _catalog;
  Future<TrainingContent?>? _training;
  Future<Map<String, ManifestEntry>>? _manifest;

  /// Read once per page load (as Android reads its content once per process).
  @override
  Future<WorkoutNameCatalog> catalog() => _catalog ??= _load();

  @override
  Future<TrainingContent?> training() => _training ??= _loadTraining();

  Future<Map<String, ManifestEntry>> _manifestOnce() =>
      _manifest ??= _get(manifestFile, maxManifestBytes).then(parseManifest);

  Future<TrainingContent?> _loadTraining() async {
    try {
      final manifest = await _manifestOnce();
      return TrainingContent(
        dailyRoutines: DailyContent.routines(jsonDecode(await _verified(manifest, dailyRoutinesFile))),
        exercises: DailyContent.exercises(jsonDecode(await _verified(manifest, exercisesFile))),
        programCatalog: ProgramContent.catalog(jsonDecode(await _verified(manifest, programExercisesFile))),
        programTemplates: ProgramContent.templates(jsonDecode(await _verified(manifest, programTemplatesFile))),
        // Comments and trailing commas, as Android's lenient remoteJson
        gymRoutines: GymContent.routines(parseJsonc(await _verified(manifest, gymRoutinesFile))),
      );
    } on Object catch (e) {
      // Also a file of an unexpected shape (a failed cast is an Error): the same as an unreadable one
      // ignore: avoid_print
      print('Content: training content not available (${e.runtimeType})');
      _training = null;
      _manifest = null;
      return null;
    }
  }

  Future<WorkoutNameCatalog> _load() async {
    try {
      final manifest = await _manifestOnce();
      final gym = await _verified(manifest, gymRoutinesFile);
      final daily = await _verified(manifest, dailyRoutinesFile);
      return WorkoutNameCatalog(
        gymRoutines: parseGymRoutineNames(parseJsonc(gym)),
        dailyRoutines: parseDailyRoutineNames(jsonDecode(daily)),
      );
    } on Exception catch (e) {
      // No content: names fall back to the stored titles. No personal data in the message
      // ignore: avoid_print
      print('Content: not available (${e.runtimeType})');
      _catalog = null;
      _manifest = null;
      return WorkoutNameCatalog.empty;
    }
  }

  Future<String> _verified(Map<String, ManifestEntry> manifest, String path) async {
    final entry = manifest[path];
    if (entry == null) throw ContentException('$path not in the manifest');
    // The query names the version, so a copy cached under the old manifest is never reused
    final body = await _getBytes('$path?v=${entry.sha256}', entry.size);
    if (body.length != entry.size) throw ContentException('$path has another size');
    if (sha256.convert(body).toString() != entry.sha256) throw ContentException('$path has another hash');
    return utf8.decode(body);
  }

  Future<String> _get(String path, int maxBytes) async => utf8.decode(await _getBytes(path, maxBytes));

  Future<List<int>> _getBytes(String path, int maxBytes) async {
    final response = await _client.get(_location.resolve(path));
    if (response.statusCode != 200) throw ContentException('$path: HTTP ${response.statusCode}');
    if (response.bodyBytes.length > maxBytes) throw ContentException('$path is too big');
    return response.bodyBytes;
  }

  /// The manifest's entries by path; throws [ContentException] for a manifest Android would reject
  /// (`schemaVersion` other than 1, an invalid path, size or hash, a duplicate).
  static Map<String, ManifestEntry> parseManifest(String text) {
    final decoded = jsonDecode(text);
    if (decoded is! Map || decoded['schemaVersion'] != 1 || decoded['files'] is! List) {
      throw const ContentException('manifest: unknown format');
    }
    final entries = <String, ManifestEntry>{};
    for (final file in decoded['files'] as List) {
      if (file is! Map) throw const ContentException('manifest: bad entry');
      final path = file['path'];
      final size = file['size'];
      final hash = file['sha256'];
      if (path is! String || !_isAllowedPath(path) || size is! int || size < 0 || size > maxFileBytes) {
        throw const ContentException('manifest: bad entry');
      }
      if (hash is! String || !RegExp(r'^[0-9a-f]{64}$').hasMatch(hash)) {
        throw const ContentException('manifest: bad hash');
      }
      if (entries.containsKey(path)) throw const ContentException('manifest: duplicate path');
      entries[path] = ManifestEntry(path: path, size: size, sha256: hash);
    }
    return entries;
  }

  static const Set<String> _knownJsonFiles = {
    'exercises.json',
    'routines.json',
    'routines_for_gym.jsonc',
    'program_exercises.json',
    'program_templates.json',
  };

  // Android's ContentRules (ContentManifest.kt): the known top-level JSON files, or animations/<name>.gif
  static bool _isAllowedPath(String path) {
    if (path.startsWith('/') || path.contains('..') || path.contains(r'\')) return false;
    return _knownJsonFiles.contains(path) || RegExp(r'^animations/[A-Za-z0-9_-][A-Za-z0-9_.-]*\.gif$').hasMatch(path);
  }

  /// `routines_for_gym.jsonc`: routineId → routineName / routineNameEs.
  static Map<int, GymRoutineNames> parseGymRoutineNames(Object? decoded) {
    final names = <int, GymRoutineNames>{};
    if (decoded is! List) throw const ContentException('gym routines: not a list');
    for (final routine in decoded) {
      if (routine is! Map) continue;
      final id = routine['routineId'];
      final name = routine['routineName'];
      if (id is! int || name is! String) continue;
      final nameEs = routine['routineNameEs'];
      names[id] = GymRoutineNames(name, nameEs is String ? nameEs : null);
    }
    return names;
  }

  /// `routines.json`: routineId (the day number) → the optional title / titleEs.
  static Map<int, DailyRoutineNames> parseDailyRoutineNames(Object? decoded) {
    final names = <int, DailyRoutineNames>{};
    if (decoded is! List) throw const ContentException('daily routines: not a list');
    for (final routine in decoded) {
      if (routine is! Map) continue;
      final id = routine['routineId'];
      if (id is! int) continue;
      final title = routine['title'];
      final titleEs = routine['titleEs'];
      names[id] = DailyRoutineNames(title: title is String ? title : null, titleEs: titleEs is String ? titleEs : null);
    }
    return names;
  }
}
