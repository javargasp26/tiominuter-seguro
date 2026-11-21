import 'dart:typed_data';

import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../domain/photos.dart';
import '../../platform/photos.dart';
import '../../data/repositories.dart';
import '../../data/write_mappers.dart';
import '../../domain/activity_entry.dart';
import '../../domain/calendar_dates.dart';
import '../../domain/training.dart';
import '../../domain/weekly_progress.dart';

/// The form's photo (Android's `FormPhoto`): none, the one stored in this browser, or a new one
/// processed and waiting for Save. Compared by identity: a picked photo is a change.
sealed class FormPhoto {
  const FormPhoto();

  /// What the panel shows.
  Uint8List? get thumb;
}

final class NoFormPhoto extends FormPhoto {
  const NoFormPhoto();

  @override
  Uint8List? get thumb => null;

  @override
  bool operator ==(Object other) => other is NoFormPhoto;

  @override
  int get hashCode => 0;
}

final class StoredFormPhoto extends FormPhoto {
  const StoredFormPhoto(this.photo);

  final StoredPhoto photo;

  @override
  Uint8List get thumb => photo.thumb ?? photo.full;
}

final class StagedFormPhoto extends FormPhoto {
  const StagedFormPhoto(this.photo);

  final ProcessedPhoto photo;

  @override
  Uint8List get thumb => photo.thumb ?? photo.full;
}

/// The form (Android's `LogActivityForm`): what the person typed and chose.
class LogActivityForm {
  const LogActivityForm({
    this.type,
    this.customName = '',
    this.durationText = '',
    this.intensity,
    required this.date,
    required this.hour,
    required this.minute,
    this.note = '',
    this.photo = const NoFormPhoto(),
  });

  /// Android's quick chips.
  static const List<int> quickDurations = [15, 30, 45, 60, 90];

  final ActivityType? type;
  final String customName;
  final String durationText;
  final Intensity? intensity;
  final String date;
  final int hour;
  final int minute;
  final String note;
  final FormPhoto photo;

  int? get durationMinutes => int.tryParse(durationText);

  ActivityEntryInput toInput() => ActivityEntryInput(
    type: type,
    customName: customName,
    durationMinutes: durationMinutes,
    intensity: intensity,
    completedAt: ActivityEntryRules.epochMillisAt(date, hour, minute),
    note: note,
  );

  LogActivityForm copyWith({
    ActivityType? type,
    String? customName,
    String? durationText,
    Intensity? intensity,
    String? date,
    int? hour,
    int? minute,
    String? note,
    FormPhoto? photo,
  }) => LogActivityForm(
    type: type ?? this.type,
    customName: customName ?? this.customName,
    durationText: durationText ?? this.durationText,
    intensity: intensity ?? this.intensity,
    date: date ?? this.date,
    hour: hour ?? this.hour,
    minute: minute ?? this.minute,
    note: note ?? this.note,
    photo: photo ?? this.photo,
  );

  @override
  bool operator ==(Object other) =>
      other is LogActivityForm &&
      other.type == type &&
      other.customName == customName &&
      other.durationText == durationText &&
      other.intensity == intensity &&
      other.date == date &&
      other.hour == hour &&
      other.minute == minute &&
      other.note == note &&
      other.photo == photo;

  @override
  int get hashCode => Object.hash(type, customName, durationText, intensity, date, hour, minute, note, photo);
}

// ---------- Intents ----------

sealed class LogActivityIntent {
  const LogActivityIntent();
}

final class LogActivityLoad extends LogActivityIntent {
  const LogActivityLoad();
}

final class LogActivityTypeSelected extends LogActivityIntent {
  const LogActivityTypeSelected(this.type);
  final ActivityType type;
}

final class LogActivityNameChanged extends LogActivityIntent {
  const LogActivityNameChanged(this.name);
  final String name;
}

final class LogActivityDurationChanged extends LogActivityIntent {
  const LogActivityDurationChanged(this.text);
  final String text;
}

final class LogActivityQuickDuration extends LogActivityIntent {
  const LogActivityQuickDuration(this.minutes);
  final int minutes;
}

final class LogActivityIntensitySelected extends LogActivityIntent {
  const LogActivityIntensitySelected(this.intensity);
  final Intensity intensity;
}

final class LogActivityDateSelected extends LogActivityIntent {
  const LogActivityDateSelected(this.date);
  final String date;
}

final class LogActivityTimeSelected extends LogActivityIntent {
  const LogActivityTimeSelected(this.hour, this.minute);
  final int hour;
  final int minute;
}

final class LogActivityNoteChanged extends LogActivityIntent {
  const LogActivityNoteChanged(this.note);
  final String note;
}

final class LogActivityShareChanged extends LogActivityIntent {
  const LogActivityShareChanged(this.share);
  final bool share;
}

/// A file was picked or dropped: it is processed at once (Android's `stage`).
final class LogActivityPhotoPicked extends LogActivityIntent {
  const LogActivityPhotoPicked(this.photo);
  final PickedPhoto photo;
}

final class LogActivityPhotoRemoved extends LogActivityIntent {
  const LogActivityPhotoRemoved();
}

final class LogActivitySave extends LogActivityIntent {
  const LogActivitySave();
}

final class LogActivityDeleteClicked extends LogActivityIntent {
  const LogActivityDeleteClicked();
}

final class LogActivityConfirmDelete extends LogActivityIntent {
  const LogActivityConfirmDelete();
}

final class LogActivityBack extends LogActivityIntent {
  const LogActivityBack();
}

final class LogActivityConfirmDiscard extends LogActivityIntent {
  const LogActivityConfirmDiscard();
}

final class LogActivityDismissDialog extends LogActivityIntent {
  const LogActivityDismissDialog();
}

final class LogActivityShareIntroAnswered extends LogActivityIntent {
  const LogActivityShareIntroAnswered({required this.share});
  final bool share;
}

// ---------- State ----------

enum LogActivityDialog { none, discard, delete, shareIntro }

class LogActivityState {
  const LogActivityState({
    this.isLoading = true,
    this.notFound = false,
    this.form,
    this.initialForm,
    this.existing,
    this.dayOptions = const [],
    this.today = '',
    this.showErrors = false,
    this.errors = const {},
    this.isSaving = false,
    this.saveFailed = false,
    this.timeEdited = false,
    this.share = true,
    this.canShare = false,
    this.dialog = LogActivityDialog.none,
    this.isProcessingPhoto = false,
  });

  final bool isLoading;

  /// A picked photo is being processed: Save waits.
  final bool isProcessingPhoto;
  final bool notFound;
  final LogActivityForm? form;
  final LogActivityForm? initialForm;

  /// The entry being edited (null when logging a new one).
  final TrainingEntryDoc? existing;
  final List<String> dayOptions;
  final String today;
  final bool showErrors;
  final Set<ActivityEntryError> errors;
  final bool isSaving;
  final bool saveFailed;
  final bool timeEdited;

  /// The share switch (new activities only, for members of a group).
  final bool share;
  final bool canShare;
  final LogActivityDialog dialog;

  bool get isEditMode => existing != null;

  bool get hasUnsavedChanges => form != null && form != initialForm;

  /// "It won't count toward your weekly goal" under 10 minutes.
  bool get showsShortDurationHint {
    final minutes = form?.durationMinutes;
    return minutes != null && minutes >= 1 && minutes * 60 < minCountedDurationSeconds;
  }

  LogActivityState copyWith({
    bool? isLoading,
    bool? notFound,
    LogActivityForm? form,
    LogActivityForm? initialForm,
    TrainingEntryDoc? existing,
    List<String>? dayOptions,
    String? today,
    bool? showErrors,
    Set<ActivityEntryError>? errors,
    bool? isSaving,
    bool? saveFailed,
    bool? timeEdited,
    bool? share,
    bool? canShare,
    LogActivityDialog? dialog,
    bool? isProcessingPhoto,
  }) => LogActivityState(
    isLoading: isLoading ?? this.isLoading,
    notFound: notFound ?? this.notFound,
    form: form ?? this.form,
    initialForm: initialForm ?? this.initialForm,
    existing: existing ?? this.existing,
    dayOptions: dayOptions ?? this.dayOptions,
    today: today ?? this.today,
    showErrors: showErrors ?? this.showErrors,
    errors: errors ?? this.errors,
    isSaving: isSaving ?? this.isSaving,
    saveFailed: saveFailed ?? this.saveFailed,
    timeEdited: timeEdited ?? this.timeEdited,
    share: share ?? this.share,
    canShare: canShare ?? this.canShare,
    dialog: dialog ?? this.dialog,
    isProcessingPhoto: isProcessingPhoto ?? this.isProcessingPhoto,
  );
}

sealed class LogActivityEffect {
  const LogActivityEffect();
}

final class LogActivityNavigateBack extends LogActivityEffect {
  const LogActivityNavigateBack();
}

/// A photo couldn't be read or kept (the entry itself is always saved).
final class LogActivityPhotoFailed extends LogActivityEffect {
  const LogActivityPhotoFailed(this.error);
  final PhotoError error;
}

/// Log or edit an activity (Android's `LogActivityViewModel`): the same form, validation, defaults
/// and dialogs, and the photo (staged in the form, kept on Save; Back discards it). Saving writes the
/// entry; a new shared one is posted to my groups (with the photo's thumbnail) once the one-time
/// explanation was answered.
class LogActivityBloc extends MviBloc<LogActivityIntent, LogActivityState, LogActivityEffect> {
  LogActivityBloc({required this.user, required this.deps, required this.dates, this.entryId})
    : super(const LogActivityState()) {
    on<LogActivityLoad>((_, emit) => _load(emit));
    on<LogActivityTypeSelected>((i, emit) => _update(emit, (f) => f.copyWith(type: i.type)));
    on<LogActivityNameChanged>(
      (i, emit) => _update(emit, (f) => f.copyWith(customName: _take(i.name, ActivityEntryRules.nameMaxLength))),
    );
    on<LogActivityDurationChanged>(
      (i, emit) => _update(emit, (f) => f.copyWith(durationText: ActivityEntryRules.durationText(i.text))),
    );
    on<LogActivityQuickDuration>((i, emit) => _update(emit, (f) => f.copyWith(durationText: '${i.minutes}')));
    on<LogActivityIntensitySelected>((i, emit) => _update(emit, (f) => f.copyWith(intensity: i.intensity)));
    on<LogActivityDateSelected>((i, emit) {
      if (!state.dayOptions.contains(i.date)) return;
      _update(emit, (f) {
        final withDate = f.copyWith(date: i.date);
        if (state.timeEdited) return withDate;
        final (hour, minute) = ActivityEntryRules.defaultTime(i.date, dates.today(), dates.now());
        return withDate.copyWith(hour: hour, minute: minute);
      });
    });
    on<LogActivityTimeSelected>((i, emit) {
      emit(state.copyWith(timeEdited: true));
      _update(emit, (f) => f.copyWith(hour: i.hour, minute: i.minute));
    });
    on<LogActivityNoteChanged>(
      (i, emit) => _update(emit, (f) => f.copyWith(note: _take(i.note, ActivityEntryRules.noteMaxLength))),
    );
    on<LogActivityShareChanged>((i, emit) => emit(state.copyWith(share: i.share)));
    on<LogActivityPhotoPicked>((i, emit) async {
      if (state.form == null || state.isProcessingPhoto) return;
      emit(state.copyWith(isProcessingPhoto: true));
      try {
        final photo = await deps.photoProcessor.process(i.photo);
        _update(emit, (f) => f.copyWith(photo: StagedFormPhoto(photo)));
      } on PhotoException catch (e) {
        sendEffect(LogActivityPhotoFailed(e.error));
      } finally {
        emit(state.copyWith(isProcessingPhoto: false));
      }
    });
    on<LogActivityPhotoRemoved>((_, emit) => _update(emit, (f) => f.copyWith(photo: const NoFormPhoto())));
    on<LogActivitySave>((_, emit) => _save(emit));
    on<LogActivityDeleteClicked>((_, emit) {
      if (state.isEditMode) emit(state.copyWith(dialog: LogActivityDialog.delete));
    });
    on<LogActivityConfirmDelete>((_, emit) async {
      final existing = state.existing;
      emit(state.copyWith(dialog: LogActivityDialog.none));
      if (existing == null) return;
      await deps.service.deleteEntry(user, existing.toEntry());
      sendEffect(const LogActivityNavigateBack());
    });
    on<LogActivityBack>((_, emit) {
      if (state.hasUnsavedChanges) {
        emit(state.copyWith(dialog: LogActivityDialog.discard));
      } else {
        sendEffect(const LogActivityNavigateBack());
      }
    });
    on<LogActivityConfirmDiscard>((_, emit) {
      emit(state.copyWith(dialog: LogActivityDialog.none));
      sendEffect(const LogActivityNavigateBack());
    });
    on<LogActivityDismissDialog>((_, emit) {
      final wasIntro = state.dialog == LogActivityDialog.shareIntro;
      emit(state.copyWith(dialog: LogActivityDialog.none));
      // The explanation not answered: nothing is posted
      if (wasIntro) sendEffect(const LogActivityNavigateBack());
    });
    on<LogActivityShareIntroAnswered>((i, emit) async {
      deps.service.setFeedIntroSeen(user.uid);
      emit(state.copyWith(dialog: LogActivityDialog.none, share: i.share));
      final saved = _saved;
      if (i.share && saved != null) await deps.service.shareEntry(user, saved);
      sendEffect(const LogActivityNavigateBack());
    });
  }

  final WebUser user;
  final TrainingDeps deps;
  final LocalDates dates;

  /// The entry to edit; null to log a new one.
  final String? entryId;

  TrainingEntry? _saved;

  String _take(String text, int max) => text.length > max ? text.substring(0, max) : text;

  Set<ActivityEntryError> _validate(LogActivityForm form) {
    var earliest = ActivityEntryRules.windowStart(dates.today());
    final existing = state.existing;
    if (existing != null) {
      final ownDay = ActivityEntryRules.startOfDay(existing.dateLocal);
      if (ownDay < earliest) earliest = ownDay;
    }
    return ActivityEntryRules.validate(form.toInput(), deps.wall(), earliest);
  }

  /// Applies [change]; once errors are shown they follow every edit.
  void _update(Emitter<LogActivityState> emit, LogActivityForm Function(LogActivityForm) change) {
    final form = state.form;
    if (form == null) return;
    final next = change(form);
    emit(state.copyWith(form: next, saveFailed: false, errors: state.showErrors ? _validate(next) : const {}));
  }

  Future<void> _load(Emitter<LogActivityState> emit) async {
    final today = dates.today();
    final id = entryId;
    if (id == null) {
      final now = dates.now();
      final form = LogActivityForm(date: today, hour: now.hour, minute: now.minute);
      emit(
        state.copyWith(
          isLoading: false,
          form: form,
          initialForm: form,
          today: today,
          dayOptions: ActivityEntryRules.dayOptions(today),
        ),
      );
      try {
        final groups = await deps.service.sharing.myGroupIds(user.uid);
        emit(state.copyWith(canShare: groups.isNotEmpty));
      } on DataException {
        // Without the index, the switch isn't shown
      }
      return;
    }
    try {
      final doc = await deps.service.training.entry(user.uid, id);
      if (doc == null || doc.source != TrainingSource.activity) {
        emit(state.copyWith(isLoading: false, notFound: true));
        return;
      }
      final at = DateTime.fromMillisecondsSinceEpoch(doc.completedAt);
      final form = LogActivityForm(
        type: ActivityType.fromCode(doc.activityType),
        customName: doc.activityType == ActivityType.other.code ? doc.title : '',
        durationText: doc.durationSeconds == null ? '' : '${doc.durationSeconds! ~/ 60}',
        intensity: Intensity.fromCode(doc.intensity),
        date: doc.dateLocal,
        hour: at.hour,
        minute: at.minute,
        note: doc.note ?? '',
        photo: switch (await deps.service.photoOf(user.uid, doc.entryId)) {
          final StoredPhoto stored => StoredFormPhoto(stored),
          null => const NoFormPhoto(),
        },
      );
      emit(
        state.copyWith(
          isLoading: false,
          existing: doc,
          form: form,
          initialForm: form,
          today: today,
          timeEdited: true,
          dayOptions: ActivityEntryRules.dayOptions(today, entryDate: doc.dateLocal),
        ),
      );
    } on DataException {
      emit(state.copyWith(isLoading: false, notFound: true));
    }
  }

  Future<void> _save(Emitter<LogActivityState> emit) async {
    final form = state.form;
    if (form == null || state.isSaving || state.isProcessingPhoto) return;
    final errors = _validate(form);
    if (errors.isNotEmpty) {
      emit(state.copyWith(showErrors: true, errors: errors));
      return;
    }
    emit(state.copyWith(isSaving: true, saveFailed: false));
    final share = state.canShare && state.share && !state.isEditMode;
    final result = await deps.service.saveActivity(
      user: user,
      input: form.toInput(),
      existing: state.existing,
      share: share,
      photo: switch (form.photo) {
        StagedFormPhoto(:final photo) => photo,
        _ => null,
      },
      removePhoto: form.photo is NoFormPhoto && state.initialForm?.photo is StoredFormPhoto,
    );
    if (result.errors.isNotEmpty) {
      // E.g. midnight passed and the oldest day left the window
      emit(state.copyWith(isSaving: false, showErrors: true, errors: result.errors));
      return;
    }
    _saved = result.entry;
    if (result.photoError case final error?) sendEffect(LogActivityPhotoFailed(error));
    final needsIntro = share && !deps.service.isFeedIntroSeen(user.uid) && countsTowardGoal(result.entry!);
    emit(state.copyWith(isSaving: false, initialForm: form, dialog: needsIntro ? LogActivityDialog.shareIntro : null));
    if (!needsIntro) sendEffect(const LogActivityNavigateBack());
  }
}

/// Day labels: "Today", "Yesterday", or the date ("Mon, 13 Oct").
enum DayLabel { today, yesterday, date }

DayLabel dayLabelOf(String date, String today) {
  if (date == today) return DayLabel.today;
  if (date == CalendarDates.plusDays(today, -1)) return DayLabel.yesterday;
  return DayLabel.date;
}
