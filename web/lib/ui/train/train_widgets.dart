/// Pieces shared by the training screens: the exercise visual (exercise_animation.dart, exported
/// here), the countdown text, the "unfinished
/// workout saved" message, and the completion view with the share switch (Android's
/// `ShareInGroupsSection` and its one-time explanation).
library;

export 'exercise_animation.dart';

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../app/services.dart';
import '../../data/repositories.dart';
import '../../domain/training.dart';
import '../../l10n/app_localizations.dart';
import 'photo_widgets.dart';

/// `mm:ss`, as Android's `formatSeconds`.
String formatSeconds(int seconds) {
  final safe = seconds < 0 ? 0 : seconds;
  return '${(safe ~/ 60).toString().padLeft(2, '0')}:${(safe % 60).toString().padLeft(2, '0')}';
}

/// "1 h 05 min" / "12 min": minutes rounded down, at least 1 (Android's `showSessionSaved`).
String sessionDurationText(int seconds, AppLocalizations l10n) {
  var minutes = seconds ~/ 60;
  if (minutes < 1) minutes = 1;
  if (minutes < 60) return l10n.sessionDurationMinutes(minutes);
  return l10n.sessionDurationHoursMinutes(minutes ~/ 60, (minutes % 60).toString().padLeft(2, '0'));
}

void showSessionSaved(BuildContext context, int durationSeconds) {
  final l10n = AppLocalizations.of(context);
  ScaffoldMessenger.of(context)
      .showSnackBar(SnackBar(content: Text(l10n.sessionSaved(sessionDurationText(durationSeconds, l10n)))));
}

/// A large countdown, read politely by screen readers.
class TimerText extends StatelessWidget {
  const TimerText(this.seconds, {this.style, super.key});

  final int seconds;
  final TextStyle? style;

  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    child: Text(
      formatSeconds(seconds),
      style: style ?? Theme.of(context).textTheme.displayMedium?.copyWith(fontFeatures: const []),
      textAlign: TextAlign.center,
    ),
  );
}

/// A full-width button at least 48 px tall.
class WideButton extends StatelessWidget {
  const WideButton({required this.label, required this.onPressed, this.filled = true, this.icon, super.key});

  final String label;
  final VoidCallback? onPressed;
  final bool filled;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final child = Text(label, textAlign: TextAlign.center);
    final style = ButtonStyle(minimumSize: WidgetStateProperty.all(const Size.fromHeight(48)));
    if (filled) {
      return icon == null
          ? FilledButton(onPressed: onPressed, style: style, child: child)
          : FilledButton.icon(onPressed: onPressed, style: style, icon: Icon(icon), label: child);
    }
    return OutlinedButton(onPressed: onPressed, style: style, child: child);
  }
}

/// A finished workout: the title, the text, the photo, the share switch, and Done.
class CompletionView extends StatelessWidget {
  const CompletionView({
    required this.title,
    required this.text,
    required this.entry,
    required this.onDone,
    this.titleKey,
    this.doneLabel,
    this.extra,
    super.key,
  });

  final String title;

  /// Shown above the button (the daily workout's "Start again from Day 1").
  final Widget? extra;
  final String text;

  /// The button's label; "Done" by default.
  final String? doneLabel;

  /// The recorded entry (null while it is being made).
  final TrainingEntry? entry;
  final String? titleKey;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    final recorded = entry;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 24),
        Icon(Icons.emoji_events_outlined, size: 72, color: theme.colorScheme.primary),
        const SizedBox(height: 16),
        Semantics(
          header: true,
          liveRegion: true,
          child: Text(title, style: theme.textTheme.headlineSmall, textAlign: TextAlign.center),
        ),
        const SizedBox(height: 8),
        Text(text, textAlign: TextAlign.center),
        const SizedBox(height: 24),
        if (recorded != null) ...[
          // Android's CompletionPhotoSection, then ShareInGroupsSection
          EntryPhotoSection(entry: recorded),
          const SizedBox(height: 16),
          ShareSection(entry: recorded, titleKey: titleKey),
        ],
        const SizedBox(height: 24),
        if (extra != null) ...[extra!, const SizedBox(height: 12)],
        WideButton(label: doneLabel ?? l10n.done, onPressed: onDone),
      ],
    );
  }
}

/// "Share in my groups" for one entry (Android's `ShareInGroupsSection`): shown only to members of at
/// least one group. ON by default; nothing is posted until the one-time explanation is answered ("OK"
/// posts, "Don't share this one" turns this workout off). Turning it off deletes the posts made.
class ShareSection extends StatefulWidget {
  const ShareSection({required this.entry, this.titleKey, super.key});

  final TrainingEntry entry;
  final String? titleKey;

  @override
  State<ShareSection> createState() => _ShareSectionState();
}

class _ShareSectionState extends State<ShareSection> {
  bool? _hasGroups;
  bool _introSeen = false;
  bool _on = true;

  @override
  void initState() {
    super.initState();
    final services = context.read<AppServices>();
    final uid = context.read<WebUserHolder>().user.uid;
    final service = services.training.service;
    _introSeen = service.isFeedIntroSeen(uid);
    unawaited(
      service.sharing
          .myGroupIds(uid)
          .then(
            (groups) {
              if (mounted) setState(() => _hasGroups = groups.isNotEmpty);
            },
            onError: (Object _) {
              if (mounted) setState(() => _hasGroups = false);
            },
          ),
    );
  }

  void _setShared(bool on) {
    final holder = context.read<WebUserHolder>();
    final service = context.read<AppServices>().training.service;
    setState(() => _on = on);
    if (on) {
      unawaited(service.shareEntry(holder.user, widget.entry, titleKey: widget.titleKey));
    } else {
      service.unshareEntry(holder.user.uid, widget.entry.id);
    }
  }

  void _answerIntro({required bool share}) {
    final holder = context.read<WebUserHolder>();
    context.read<AppServices>().training.service.setFeedIntroSeen(holder.user.uid);
    setState(() => _introSeen = true);
    _setShared(share);
  }

  @override
  Widget build(BuildContext context) {
    if (_hasGroups != true) return const SizedBox.shrink();
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    if (!_introSeen) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Semantics(header: true, child: Text(l10n.shareIntroTitle, style: theme.textTheme.titleMedium)),
              const SizedBox(height: 8),
              Text(l10n.shareIntroText),
              const SizedBox(height: 12),
              Wrap(
                alignment: WrapAlignment.end,
                spacing: 8,
                children: [
                  TextButton(
                    onPressed: () => _answerIntro(share: false),
                    style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                    child: Text(l10n.shareIntroDontShare),
                  ),
                  FilledButton(
                    onPressed: () => _answerIntro(share: true),
                    style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                    child: Text(l10n.shareIntroOk),
                  ),
                ],
              ),
            ],
          ),
        ),
      );
    }
    return SwitchListTile(
      value: _on,
      onChanged: _setShared,
      title: Text(l10n.shareInGroups),
      subtitle: Text(_on ? l10n.shareInGroupsOn : l10n.shareInGroupsOff),
      secondary: const Icon(Icons.groups_outlined),
    );
  }
}

/// The signed-in user, provided above the training pages.
class WebUserHolder {
  const WebUserHolder(this.user);

  final WebUser user;
}

/// One decimal with the language's separator ("66.7" / "66,7"), as Android's `formatOneDecimal`.
String formatOneDecimal(double value, String language) {
  final rounded = (value * 10).round() / 10;
  final text = rounded.toStringAsFixed(1);
  return language == 'es' ? text.replaceAll('.', ',') : text;
}

/// Android's `formatPercent`: completed / total as a percentage with one decimal.
String formatPercent(int completed, int total, String language) =>
    formatOneDecimal(total <= 0 ? 0 : completed * 100.0 / total, language);

/// "Sets: 3 · Reps: 10, 10, 10" / "Sets: 3 · Time: 40s, 40s, 40s per side" (Android's
/// `programExerciseSummary`).
String programExerciseSummary(
  AppLocalizations l10n, {
  required bool isTime,
  required List<int> values,
  bool perSide = false,
}) {
  final detail = isTime
      ? '${l10n.programSummaryTime}: ${values.map(l10n.programSecondsShort).join(', ')}'
      : '${l10n.programSummaryReps}: ${values.join(', ')}';
  final side = perSide ? ' ${l10n.programPerSide}' : '';
  return '${l10n.programSummarySets}: ${values.length} · $detail$side';
}

/// The language code of [l10n] ("en", "es").
String languageOf(AppLocalizations l10n) => l10n.localeName.split('_').first;

/// A text field driven by the state: it shows [value] (also when the state changes it, e.g. a quick
/// duration chip) without losing the cursor while the person types.
class SyncedTextField extends StatefulWidget {
  const SyncedTextField({
    required this.value,
    required this.onChanged,
    this.decoration = const InputDecoration(),
    this.keyboardType,
    this.inputFormatters,
    this.maxLines = 1,
    this.maxLength,
    super.key,
  });

  final String value;
  final ValueChanged<String> onChanged;
  final InputDecoration decoration;
  final TextInputType? keyboardType;
  final List<TextInputFormatter>? inputFormatters;
  final int maxLines;
  final int? maxLength;

  @override
  State<SyncedTextField> createState() => _SyncedTextFieldState();
}

class _SyncedTextFieldState extends State<SyncedTextField> {
  late final TextEditingController _controller = TextEditingController(text: widget.value);

  @override
  void didUpdateWidget(covariant SyncedTextField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.value != _controller.text) {
      _controller.value = TextEditingValue(
        text: widget.value,
        selection: TextSelection.collapsed(offset: widget.value.length),
      );
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => TextField(
    controller: _controller,
    decoration: widget.decoration,
    keyboardType: widget.keyboardType,
    inputFormatters: widget.inputFormatters,
    maxLines: widget.maxLines,
    maxLength: widget.maxLength,
    onChanged: widget.onChanged,
  );
}

/// Back to the hub: a pop when the hub is below (the browser's back does the same), else a go.
void backToTrain(BuildContext context) {
  if (context.canPop()) {
    context.pop();
  } else {
    context.go(Routes.train);
  }
}
