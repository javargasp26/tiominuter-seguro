import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/domain/activity_entry.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/data/write_mappers.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/ui/train/log_activity_bloc.dart';

import '../support/training_fakes.dart';

/// Log activity (Android's LogActivityViewModel): defaults, validation shown after the first save,
/// the short-duration hint, the day options, the share explanation, edit and delete.
void main() {
  late TrainingKit kit;

  setUp(() => kit = TrainingKit());

  Future<LogActivityBloc> open({String? entryId}) async {
    final bloc = LogActivityBloc(user: alex, deps: kit.deps, dates: kit.dates, entryId: entryId)
      ..add(const LogActivityLoad());
    await settle();
    await settle();
    return bloc;
  }

  test('defaults: today, now, nothing chosen; the 7 days offered', () async {
    final bloc = await open();
    final form = bloc.state.form!;
    expect(form.date, kit.today);
    expect((form.hour, form.minute), (18, 0));
    expect(form.type, isNull);
    expect(bloc.state.dayOptions, hasLength(7));
    expect(bloc.state.dayOptions.last, CalendarDates.plusDays(kit.today, -6));
    await bloc.close();
  });

  test('errors show after the first save, then follow every edit; nothing is written', () async {
    final bloc = await open();
    bloc.add(const LogActivitySave());
    await settle();
    expect(bloc.state.errors, {
      ActivityEntryError.typeMissing,
      ActivityEntryError.durationOutOfRange,
      ActivityEntryError.intensityMissing,
    });
    bloc.add(const LogActivityTypeSelected(ActivityType.other));
    await settle();
    expect(bloc.state.errors, contains(ActivityEntryError.nameMissing));
    bloc
      ..add(LogActivityNameChanged('x' * 50))
      ..add(const LogActivityDurationChanged('9a99'));
    await settle();
    expect(bloc.state.form!.customName.length, 40, reason: 'cut at 40 as Android');
    expect(bloc.state.form!.durationText, '999');
    expect(bloc.state.errors, contains(ActivityEntryError.durationOutOfRange));
    expect(kit.training.writes, isEmpty);
    await bloc.close();
  });

  test('a short duration shows the hint; a past day gets 18:00 unless a time was chosen', () async {
    final bloc = await open();
    bloc.add(const LogActivityQuickDuration(15));
    await settle();
    expect(bloc.state.showsShortDurationHint, isFalse);
    bloc.add(const LogActivityDurationChanged('9'));
    await settle();
    expect(bloc.state.showsShortDurationHint, isTrue);
    final yesterday = CalendarDates.plusDays(kit.today, -1);
    bloc.add(LogActivityDateSelected(yesterday));
    await settle();
    expect((bloc.state.form!.hour, bloc.state.form!.minute), (18, 0));
    bloc
      ..add(const LogActivityTimeSelected(7, 30))
      ..add(LogActivityDateSelected(CalendarDates.plusDays(kit.today, -2)));
    await settle();
    expect((bloc.state.form!.hour, bloc.state.form!.minute), (7, 30));
    await bloc.close();
  });

  test('saved, then the share explanation (first time, member of a group); OK posts it', () async {
    kit.sharing.groupIds = ['g1'];
    final bloc = await open();
    final back = <LogActivityEffect>[];
    final sub = bloc.effects.listen(back.add);
    expect(bloc.state.canShare, isTrue);
    bloc
      ..add(const LogActivityTypeSelected(ActivityType.yoga))
      ..add(const LogActivityQuickDuration(45))
      ..add(const LogActivityIntensitySelected(Intensity.easy))
      ..add(const LogActivityTimeSelected(17, 0))
      ..add(const LogActivitySave());
    await settle();
    await settle();
    expect(kit.training.live.single.activityTypeCode, 'YOGA');
    expect(bloc.state.dialog, LogActivityDialog.shareIntro);
    expect(kit.sharing.posts, isEmpty);
    bloc.add(const LogActivityShareIntroAnswered(share: true));
    await settle();
    await settle();
    expect(kit.sharing.posts['g1'], hasLength(1));
    expect(kit.service.isFeedIntroSeen(alex.uid), isTrue);
    expect(back.whereType<LogActivityNavigateBack>(), hasLength(1));
    await sub.cancel();
    await bloc.close();
  });

  test('edit: loads the entry, keeps its id, asks before discarding changes; delete writes the tombstone', () async {
    await kit.training.saveEntry(
      alex.uid,
      TrainingEntryDoc(
        entryId: 'act-1',
        dateLocal: kit.today,
        completedAt: DateTime.parse('${kit.today} 07:15').millisecondsSinceEpoch,
        source: TrainingSource.activity,
        refId: null,
        title: 'Climbing',
        durationSeconds: 3600,
        createdAt: 1,
        activityType: 'OTHER',
        intensity: 'HARD',
        note: 'Gym wall',
      ),
    );
    final bloc = await open(entryId: 'act-1');
    final form = bloc.state.form!;
    expect(bloc.state.isEditMode, isTrue);
    expect(
      (form.type, form.customName, form.durationText, form.intensity, form.hour, form.minute, form.note),
      (ActivityType.other, 'Climbing', '60', Intensity.hard, 7, 15, 'Gym wall'),
    );
    bloc.add(const LogActivityNoteChanged('Bouldering'));
    await settle();
    bloc.add(const LogActivityBack());
    await settle();
    expect(bloc.state.dialog, LogActivityDialog.discard);
    bloc
      ..add(const LogActivityDismissDialog())
      ..add(const LogActivitySave());
    await settle();
    await settle();
    expect(kit.training.entries['act-1']!.note, 'Bouldering');
    expect(kit.training.entries['act-1']!.createdAt, 1);
    bloc
      ..add(const LogActivityDeleteClicked())
      ..add(const LogActivityConfirmDelete());
    await settle();
    await settle();
    expect(kit.training.entries['act-1'], isNull);
    await bloc.close();
  });

  test('another source can not be edited here', () async {
    await kit.training.saveEntry(
      alex.uid,
      const TrainingEntryDoc(
        entryId: 'gym-1',
        dateLocal: '2026-10-21',
        completedAt: 0,
        source: TrainingSource.gym,
        refId: 'gym:64',
        title: 'Pull Day',
        durationSeconds: 1800,
        createdAt: 0,
      ),
    );
    final bloc = await open(entryId: 'gym-1');
    expect(bloc.state.notFound, isTrue);
    await bloc.close();
  });
}
