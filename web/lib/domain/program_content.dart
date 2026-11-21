/// `program_exercises.json` and `program_templates.json` (content/v1) → the program model, ported
/// from Android's `data/program/mapper/ProgramMappers.kt` and the DTO defaults of
/// `data/program/remote/dto/` (`SplitDto.focus` = BALANCED, `SlotPatternDto.type` = REPS,
/// `required` = false, `ProgramExerciseDto.perSide` = false, `duplicateOf` = null).
/// - Catalog: an entry with an unknown enum value is skipped (as Android, which logs it).
/// - Templates: one consistent document, so an unknown value throws [FormatException].
/// Inputs are the decoded JSON (`jsonDecode` / the content repository's JSONC reader).
library;

import 'program.dart';

abstract final class ProgramContent {
  static List<ProgramExercise> catalog(Object? decoded) {
    final list = decoded is List ? decoded : throw const FormatException('program_exercises.json is not a list');
    return [for (final item in list) ?_exercise(item as Map)];
  }

  static ProgramExercise? _exercise(Map<Object?, Object?> json) {
    final muscles = <MuscleGroup>[];
    for (final code in json['primaryMuscles']! as List) {
      final muscle = MuscleGroup.fromCode(code);
      if (muscle == null) return null;
      muscles.add(muscle);
    }
    final places = <ProgramLocation>{};
    for (final code in json['locations']! as List) {
      final place = ProgramLocation.fromCode(code);
      if (place == null) return null;
      places.add(place);
    }
    final equipment = Equipment.fromCode(json['equipment']);
    final mechanic = Mechanic.fromCode(json['mechanic']);
    final impact = Impact.fromCode(json['impact']);
    if (equipment == null || mechanic == null || impact == null) return null;
    return ProgramExercise(
      exerciseId: json['exerciseId']! as int,
      name: json['name']! as String,
      nameEs: json['nameEs']! as String,
      animation: json['animation']! as String,
      primaryMuscles: muscles,
      equipment: equipment,
      mechanic: mechanic,
      impact: impact,
      locations: places,
      perSide: json['perSide'] as bool? ?? false,
      duplicateOf: json['duplicateOf'] as int?,
    );
  }

  static ProgramTemplates templates(Object? decoded) {
    final json = decoded is Map ? decoded : throw const FormatException('program_templates.json is not an object');
    final progression = json['progression']! as Map;
    final cardio = json['cardio']! as Map;
    final impact = json['impact']! as Map;
    final outdoorRun = cardio['outdoorRun']! as Map;
    final lowImpact = cardio['treadmillLowImpact']! as Map;
    final circuit = cardio['homeCircuit']! as Map;
    return ProgramTemplates(
      splits: [
        for (final split in _list(json['splits']))
          ProgramSplit(
            goal: _enum(ProgramGoal.fromCode, split['goal'], 'ProgramGoal'),
            daysPerWeek: split['daysPerWeek']! as int,
            focus: _enum(ProgramFocus.fromCode, split['focus'] ?? 'BALANCED', 'ProgramFocus'),
            days: [
              for (final day in _list(split['days']))
                switch (day['kind']) {
                  'STRENGTH' => StrengthSplitDay(
                    day['strengthDayId'] as String? ??
                        (throw const FormatException('STRENGTH day without strengthDayId')),
                  ),
                  'CARDIO' => const CardioSplitDay(),
                  final other => throw FormatException("Unknown split day kind '$other'"),
                },
            ],
          ),
      ],
      strengthDays: [
        for (final day in _list(json['strengthDays']))
          StrengthDayTemplate(
            id: day['id']! as String,
            title: _enum(StrengthDayTitle.fromCode, day['title'], 'StrengthDayTitle'),
            slots: [
              for (final slot in _list(day['slots']))
                SlotPattern(
                  muscle: _enum(MuscleGroup.fromCode, slot['muscle'], 'MuscleGroup'),
                  mechanic: _enum(Mechanic.fromCode, slot['mechanic'], 'Mechanic'),
                  type: _enum(ProgramSlotType.fromCode, slot['type'] ?? 'REPS', 'ProgramSlotType'),
                  candidates: _ints(slot['candidates']),
                  required: slot['required'] as bool? ?? false,
                ),
            ],
          ),
      ],
      prescriptions: [
        for (final p in _list(json['prescriptions']))
          Prescription(
            goal: _enum(ProgramGoal.fromCode, p['goal'], 'ProgramGoal'),
            level: _enum(ProgramLevel.fromCode, p['level'], 'ProgramLevel'),
            exercisesPerSession: _range(p['exercisesPerSession']),
            setsCompound: p['setsCompound']! as int,
            setsIsolation: p['setsIsolation']! as int,
            repsCompound: _range(p['repsCompound']),
            repsIsolation: _range(p['repsIsolation']),
            restCompoundSeconds: p['restCompoundSeconds']! as int,
            restIsolationSeconds: p['restIsolationSeconds']! as int,
            holdSeconds: p['holdSeconds']! as int,
          ),
      ],
      progression: ProgressionRules(
        repStepPerWeek: progression['repStepPerWeek']! as int,
        maxSets: progression['maxSets']! as int,
        holdStepSeconds: progression['holdStepSeconds']! as int,
        deloadEveryWeeks: progression['deloadEveryWeeks']! as int,
        deloadMinProgramWeeks: progression['deloadMinProgramWeeks']! as int,
        deloadSetsDrop: progression['deloadSetsDrop']! as int,
        deloadMinSets: progression['deloadMinSets']! as int,
        deloadCardioFactor: _double(progression['deloadCardioFactor']),
      ),
      cardio: CardioTemplates(
        // Kotlin's associate: a later entry for the same key wins
        rotation: {
          for (final r in _list(cardio['rotation']))
            _enum(ProgramLocation.fromCode, r['location'], 'ProgramLocation'): [
              for (final type in r['types']! as List) _enum(CardioType.fromCode, type, 'CardioType'),
            ],
        },
        outdoorRun: OutdoorRunTemplate(
          weeklyIncreasePercent: outdoorRun['weeklyIncreasePercent']! as int,
          runStartKm: {
            for (final l in _list(outdoorRun['levels']))
              _enum(ProgramLevel.fromCode, l['level'], 'ProgramLevel'): _double(l['runStartKm']),
          },
          walkStartKm: {
            for (final l in _list(outdoorRun['levels']))
              _enum(ProgramLevel.fromCode, l['level'], 'ProgramLevel'): _double(l['walkStartKm']),
          },
        ),
        treadmill: {
          for (final t in _list(cardio['treadmill']))
            _enum(ProgramLevel.fromCode, t['level'], 'ProgramLevel'): TreadmillTemplate(
              warmUpSeconds: t['warmUpSeconds']! as int,
              coolDownSeconds: t['coolDownSeconds']! as int,
              jogSpeedKmh: _double(t['jogSpeedKmh']),
              walkSpeedKmh: _double(t['walkSpeedKmh']),
              jogSeconds: t['jogSeconds']! as int,
              walkSeconds: t['walkSeconds']! as int,
              inclines: [for (final incline in t['inclines']! as List) _double(incline)],
              baseIntervals: t['baseIntervals']! as int,
              intervalsPerWeek: t['intervalsPerWeek']! as int,
              maxIntervals: t['maxIntervals']! as int,
            ),
        },
        treadmillLowImpact: TreadmillLowImpactTemplate(
          warmUpSeconds: lowImpact['warmUpSeconds']! as int,
          coolDownSeconds: lowImpact['coolDownSeconds']! as int,
          speedKmh: _double(lowImpact['speedKmh']),
          lowIncline: _double(lowImpact['lowIncline']),
          highIncline: _double(lowImpact['highIncline']),
          lowSeconds: lowImpact['lowSeconds']! as int,
          highSeconds: lowImpact['highSeconds']! as int,
          baseIntervals: {
            for (final b in _list(lowImpact['baseIntervals']))
              _enum(ProgramLevel.fromCode, b['level'], 'ProgramLevel'): b['value']! as int,
          },
          intervalsPerWeek: lowImpact['intervalsPerWeek']! as int,
          maxIntervals: lowImpact['maxIntervals']! as int,
        ),
        homeCircuit: HomeCircuitTemplate(
          levels: {
            for (final l in _list(circuit['levels']))
              _enum(ProgramLevel.fromCode, l['level'], 'ProgramLevel'): HomeCircuitLevel(
                rounds: l['rounds']! as int,
                workSeconds: l['workSeconds']! as int,
                restSeconds: l['restSeconds']! as int,
                exercises: l['exercises']! as int,
              ),
          },
          restBetweenRoundsSeconds: circuit['restBetweenRoundsSeconds']! as int,
          candidates: _ints(circuit['candidates']),
          lowImpactCandidates: _ints(circuit['lowImpactCandidates']),
        ),
      ),
      impact: ImpactRules(
        lowImpactMinBmi: _double(impact['lowImpactMinBmi']),
        lowImpactMinAge: impact['lowImpactMinAge']! as int,
        seniorMinAge: impact['seniorMinAge']! as int,
        seniorExtraRestSeconds: impact['seniorExtraRestSeconds']! as int,
        excludedForBeginnerOrLowImpact: _ints(impact['excludedForBeginnerOrLowImpact']).toSet(),
      ),
    );
  }

  static List<Map<Object?, Object?>> _list(Object? value) => [for (final item in value! as List) item as Map];

  static List<int> _ints(Object? value) => [for (final item in value! as List) item as int];

  // JSON numbers for Kotlin Double fields may be written without a decimal point ("5")
  static double _double(Object? value) => (value! as num).toDouble();

  static IntRange _range(Object? value) {
    final json = value! as Map;
    return IntRange(json['min']! as int, json['max']! as int);
  }

  static T _enum<T>(T? Function(Object?) fromCode, Object? code, String type) =>
      fromCode(code) ?? (throw FormatException("Unknown $type '$code'"));
}
