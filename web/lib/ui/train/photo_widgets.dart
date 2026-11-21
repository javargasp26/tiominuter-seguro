/// Training photos on the web (Android's `ui/components/photo`): the attach panel (Add / Change /
/// Remove, the privacy note, drops on desktop, the iOS Home Screen hint), the full-screen viewer,
/// the section of a completion view and the history's thumbnail. The full photo stays in this
/// browser; only its thumbnail goes on my posts (`TrainingService.attachPhoto`).
library;

import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';

import '../../app/services.dart';
import '../../domain/photos.dart';
import '../../domain/training.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/photos.dart';
import 'train_widgets.dart';

String photoErrorText(PhotoError error, AppLocalizations l10n) => switch (error) {
  PhotoError.tooLarge => l10n.photoErrorTooLarge(PhotoLimits.maxInputBytes ~/ (1024 * 1024)),
  PhotoError.unsupportedFormat => l10n.photoErrorHeic,
  PhotoError.unreadable => l10n.photoErrorUnreadable,
  PhotoError.noSpace => l10n.photoErrorNoSpace,
  PhotoError.failed => l10n.photoErrorFailed,
};

/// A short message (Android's `showTrainingPhotoError` Toast): it stays over dialogs and after
/// going back, as a snackbar of the app's messenger.
void showPhotoError(BuildContext context, PhotoError error) {
  final l10n = AppLocalizations.of(context);
  ScaffoldMessenger.maybeOf(context)?.showSnackBar(SnackBar(content: Text(photoErrorText(error, l10n))));
}

/// "Workout photo, 21 Oct 2026" for screen readers.
String photoDescription(AppLocalizations l10n, {int? takenAt, required String locale}) => takenAt == null
    ? l10n.photoContentDescriptionUndated
    : l10n.photoContentDescription(DateFormat.yMMMd(locale).format(DateTime.fromMillisecondsSinceEpoch(takenAt)));

Future<void> showPhotoViewer(BuildContext context, Uint8List photo, String description) => showDialog<void>(
  context: context,
  barrierColor: Colors.black,
  builder: (context) => PhotoViewer(photo: photo, description: description),
);

/// The photo full screen, with a labelled close button and the one line about other devices.
class PhotoViewer extends StatelessWidget {
  const PhotoViewer({required this.photo, required this.description, super.key});

  final Uint8List photo;
  final String description;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Dialog.fullscreen(
      backgroundColor: Colors.black,
      child: Stack(
        children: [
          Positioned.fill(
            child: InteractiveViewer(
              child: Center(child: Image.memory(photo, semanticLabel: description, gaplessPlayback: true)),
            ),
          ),
          Positioned(
            top: 8,
            right: 8,
            child: IconButton(
              tooltip: l10n.photoClose,
              color: Colors.white,
              iconSize: 28,
              style: IconButton.styleFrom(minimumSize: const Size(48, 48)),
              icon: const Icon(Icons.close),
              onPressed: () => Navigator.pop(context),
            ),
          ),
          Positioned(
            left: 16,
            right: 16,
            bottom: 16,
            child: Text(
              l10n.photoOtherDevices,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white70),
            ),
          ),
        ],
      ),
    );
  }
}

/// A square thumbnail that opens the photo (48 px at least).
class PhotoThumbnail extends StatelessWidget {
  const PhotoThumbnail({
    required this.bytes,
    required this.description,
    required this.onOpen,
    this.size = 72,
    super.key,
  });

  final Uint8List bytes;
  final String description;
  final VoidCallback onOpen;
  final double size;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    // Its own node: inside a list row it stays a separate button
    return Semantics(
      container: true,
      button: true,
      label: description,
      onTapHint: l10n.photoOpen,
      excludeSemantics: true,
      onTap: onOpen,
      child: InkWell(
        onTap: onOpen,
        borderRadius: BorderRadius.circular(8),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: Image.memory(bytes, width: size, height: size, fit: BoxFit.cover, gaplessPlayback: true),
        ),
      ),
    );
  }
}

/// The attach panel: the photo's thumbnail with Change / Remove, or Add photo; "Saving photo…"
/// while it is processed; the privacy note; on desktop "You can also drop a photo here"; the iOS
/// hint. Stateless: the screens own the photo.
class PhotoPanel extends StatelessWidget {
  const PhotoPanel({
    required this.thumb,
    required this.isProcessing,
    required this.onAdd,
    required this.onRemove,
    required this.onOpen,
    required this.description,
    this.showDropHint = false,
    super.key,
  });

  /// The photo's thumbnail (null without a photo).
  final Uint8List? thumb;
  final bool isProcessing;
  final VoidCallback onAdd;
  final VoidCallback onRemove;
  final VoidCallback onOpen;
  final String description;
  final bool showDropHint;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final image = thumb;
    final small = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (isProcessing)
          Semantics(
            liveRegion: true,
            child: Row(
              children: [
                const SizedBox.square(dimension: 24, child: CircularProgressIndicator(strokeWidth: 3)),
                const SizedBox(width: 12),
                Text(l10n.photoProcessing),
              ],
            ),
          )
        else if (image == null)
          Align(
            alignment: Alignment.centerLeft,
            child: OutlinedButton.icon(
              onPressed: onAdd,
              style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
              icon: const Icon(Icons.add_a_photo_outlined),
              label: Text(l10n.photoAdd),
            ),
          )
        else
          Row(
            children: [
              PhotoThumbnail(bytes: image, description: description, onOpen: onOpen),
              const SizedBox(width: 12),
              Expanded(
                child: Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  children: [
                    TextButton(
                      onPressed: onAdd,
                      style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                      child: Text(l10n.photoChange),
                    ),
                    TextButton(
                      onPressed: onRemove,
                      style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                      child: Text(l10n.photoRemove),
                    ),
                  ],
                ),
              ),
            ],
          ),
        const SizedBox(height: 4),
        Text(l10n.photoPrivacyNote, style: small),
        if (showDropHint && !isProcessing) Text(l10n.photoDropHint, style: small),
        const InstallHintCard(),
      ],
    );
  }
}

/// "Remove the photo?" (Android's confirmation); true to remove.
Future<bool> confirmPhotoRemoval(BuildContext context) async {
  final l10n = AppLocalizations.of(context);
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(l10n.photoRemoveTitle),
      content: Text(l10n.photoRemoveMessage),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
        TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.photoRemoveConfirm)),
      ],
    ),
  );
  return confirmed == true;
}

/// Desktop drag and drop: the hint only where a mouse is likely (no touch screen, not iOS).
bool showsDropHint(DeviceInfo device) => device.maxTouchPoints == 0 && !InstallHintRule.isIos(device);

/// The one-time hint in Safari on iPhone / iPad ([InstallHintRule]); dismissed once for this browser.
class InstallHintCard extends StatefulWidget {
  const InstallHintCard({super.key});

  /// localStorage, for every account of this browser.
  static const String dismissedKey = 'tm.installHintDismissed';

  @override
  State<InstallHintCard> createState() => _InstallHintCardState();
}

class _InstallHintCardState extends State<InstallHintCard> {
  late bool _visible = _shouldShow();

  bool _shouldShow() {
    final deps = context.read<AppServices>().trainingDeps;
    if (deps == null) return false;
    final dismissed = deps.service.store.read(InstallHintCard.dismissedKey) == 'true';
    return InstallHintRule.shouldShow(deps.device, dismissed: dismissed);
  }

  void _dismiss() {
    context.read<AppServices>().training.service.store.write(InstallHintCard.dismissedKey, 'true');
    setState(() => _visible = false);
  }

  @override
  Widget build(BuildContext context) {
    if (!_visible) return const SizedBox.shrink();
    final l10n = AppLocalizations.of(context);
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Card(
        margin: EdgeInsets.zero,
        color: scheme.secondaryContainer,
        child: Padding(
          padding: const EdgeInsets.only(left: 16),
          child: Row(
            children: [
              ExcludeSemantics(child: Icon(Icons.add_to_home_screen, color: scheme.onSecondaryContainer)),
              const SizedBox(width: 12),
              Expanded(
                child: Text(l10n.installHintText, style: TextStyle(color: scheme.onSecondaryContainer)),
              ),
              IconButton(
                tooltip: l10n.installHintDismiss,
                style: IconButton.styleFrom(minimumSize: const Size(48, 48)),
                icon: const Icon(Icons.close),
                onPressed: _dismiss,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Picks or receives a dropped file, processes it in the browser and reports the result.
mixin PhotoInput<T extends StatefulWidget> on State<T> {
  StreamSubscription<PickedPhoto>? _drops;

  TrainingDeps get photoDeps => context.read<AppServices>().training;

  /// A processed photo, ready to keep.
  void onPhotoProcessed(ProcessedPhoto photo);

  void onPhotoProcessing(bool processing);

  bool get acceptsPhotos => true;

  void listenToDrops() {
    _drops = photoDeps.photoPicker.drops.listen((picked) {
      if (mounted && acceptsPhotos) unawaited(processPicked(picked));
    });
  }

  /// From a tap: the file chooser opens in the same gesture.
  Future<void> pickPhoto() async {
    final picked = await photoDeps.photoPicker.pick();
    if (picked != null && mounted) await processPicked(picked);
  }

  Future<void> processPicked(PickedPhoto picked) async {
    onPhotoProcessing(true);
    try {
      final photo = await photoDeps.photoProcessor.process(picked);
      if (mounted) onPhotoProcessed(photo);
    } on PhotoException catch (e) {
      if (mounted) showPhotoError(context, e.error);
    } finally {
      if (mounted) onPhotoProcessing(false);
    }
  }

  @override
  void dispose() {
    unawaited(_drops?.cancel());
    super.dispose();
  }
}

/// The photo of a finished workout (the completion views): stored here and put on my posts.
class EntryPhotoSection extends StatefulWidget {
  const EntryPhotoSection({required this.entry, super.key});

  final TrainingEntry entry;

  @override
  State<EntryPhotoSection> createState() => _EntryPhotoSectionState();
}

class _EntryPhotoSectionState extends State<EntryPhotoSection> with PhotoInput<EntryPhotoSection> {
  StoredPhoto? _photo;
  bool _processing = false;

  @override
  void initState() {
    super.initState();
    final deps = photoDeps;
    final uid = context.read<WebUserHolder>().user.uid;
    unawaited(
      deps.service.photoOf(uid, widget.entry.id).then((photo) {
        if (mounted && photo != null) setState(() => _photo = photo);
      }),
    );
    listenToDrops();
  }

  @override
  bool get acceptsPhotos => !_processing;

  @override
  void onPhotoProcessing(bool processing) => setState(() => _processing = processing);

  @override
  Future<void> onPhotoProcessed(ProcessedPhoto photo) async {
    final deps = photoDeps;
    final user = context.read<WebUserHolder>().user;
    try {
      await deps.service.attachPhoto(user, widget.entry.id, photo);
      unawaited(deps.requestPersistence());
      if (mounted) {
        setState(() => _photo = StoredPhoto(full: photo.full, thumb: photo.thumb, savedAt: deps.wall()));
      }
    } on PhotoException catch (e) {
      if (mounted) showPhotoError(context, e.error);
    }
  }

  Future<void> _remove() async {
    if (!await confirmPhotoRemoval(context) || !mounted) return;
    await photoDeps.service.removePhoto(context.read<WebUserHolder>().user, widget.entry.id);
    if (mounted) setState(() => _photo = null);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final photo = _photo;
    final description = photoDescription(
      l10n,
      takenAt: widget.entry.completedAt,
      locale: Localizations.localeOf(context).toLanguageTag(),
    );
    return PhotoPanel(
      thumb: photo == null ? null : photo.thumb ?? photo.full,
      isProcessing: _processing,
      onAdd: pickPhoto,
      onRemove: _remove,
      onOpen: () => photo == null ? null : showPhotoViewer(context, photo.full, description),
      description: description,
      showDropHint: showsDropHint(photoDeps.device),
    );
  }
}

/// A history row's thumbnail when this browser has the entry's photo (nothing otherwise).
class HistoryPhotoThumbnail extends StatefulWidget {
  const HistoryPhotoThumbnail({required this.uid, required this.entry, super.key});

  final String uid;
  final TrainingEntry entry;

  @override
  State<HistoryPhotoThumbnail> createState() => _HistoryPhotoThumbnailState();
}

class _HistoryPhotoThumbnailState extends State<HistoryPhotoThumbnail> {
  late Future<StoredPhoto?> _photo = _read();

  Future<StoredPhoto?> _read() async =>
      context.read<AppServices>().trainingDeps?.service.photoOf(widget.uid, widget.entry.id);

  @override
  void didUpdateWidget(HistoryPhotoThumbnail old) {
    super.didUpdateWidget(old);
    if (old.entry.id != widget.entry.id) _photo = _read();
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<StoredPhoto?>(
    future: _photo,
    builder: (context, snapshot) {
      final photo = snapshot.data;
      if (photo == null) return const SizedBox.shrink();
      final l10n = AppLocalizations.of(context);
      final description = photoDescription(
        l10n,
        takenAt: widget.entry.completedAt,
        locale: Localizations.localeOf(context).toLanguageTag(),
      );
      return Padding(
        padding: const EdgeInsets.only(left: 8),
        child: PhotoThumbnail(
          bytes: photo.thumb ?? photo.full,
          description: description,
          size: 48,
          onOpen: () => showPhotoViewer(context, photo.full, description),
        ),
      );
    },
  );
}
