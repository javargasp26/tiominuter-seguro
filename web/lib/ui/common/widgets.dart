import 'package:flutter/material.dart';

import '../../data/repositories.dart';
import '../../domain/training.dart';
import '../../l10n/app_localizations.dart';

/// Pages are centered and at most this wide on desktop.
const double maxContentWidth = 720;

/// A page body: scrollable, centered, at most [maxContentWidth] wide, with a 16 px gutter.
class PageBody extends StatelessWidget {
  const PageBody({required this.children, this.controller, super.key});

  final List<Widget> children;
  final ScrollController? controller;

  @override
  Widget build(BuildContext context) => Align(
    alignment: Alignment.topCenter,
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: maxContentWidth),
      child: ListView(controller: controller, padding: const EdgeInsets.all(16), children: children),
    ),
  );
}

class PageTitle extends StatelessWidget {
  const PageTitle(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: Semantics(header: true, child: Text(text, style: Theme.of(context).textTheme.headlineSmall)),
  );
}

class LoadingView extends StatelessWidget {
  const LoadingView({super.key});

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(32),
    child: Center(
      child: Semantics(
        label: AppLocalizations.of(context).loading,
        liveRegion: true,
        child: const CircularProgressIndicator(),
      ),
    ),
  );
}

/// A failed read: what happened and Retry.
class ErrorView extends StatelessWidget {
  const ErrorView({required this.error, required this.onRetry, super.key});

  final DataError error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        children: [
          Semantics(
            liveRegion: true,
            child: Text(error == DataError.network ? l10n.noNetwork : l10n.loadFailed, textAlign: TextAlign.center),
          ),
          const SizedBox(height: 12),
          FilledButton.tonal(onPressed: onRetry, child: Text(l10n.retry)),
        ],
      ),
    );
  }
}

class EmptyView extends StatelessWidget {
  const EmptyView({required this.text, this.title, super.key});

  final String? title;
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 8),
    child: Column(
      children: [
        if (title != null) ...[
          Text(title!, style: Theme.of(context).textTheme.titleMedium, textAlign: TextAlign.center),
          const SizedBox(height: 8),
        ],
        Text(text, textAlign: TextAlign.center),
      ],
    ),
  );
}

/// A short note ("Use the Android app…").
class InfoNote extends StatelessWidget {
  const InfoNote(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card.outlined(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Icon(Icons.phone_android, color: scheme.primary),
            const SizedBox(width: 12),
            Expanded(child: Text(text)),
          ],
        ),
      ),
    );
  }
}

/// The decorative icon of a source (Android's history icons).
IconData sourceIcon(TrainingSource source) => switch (source) {
  TrainingSource.daily => Icons.date_range,
  TrainingSource.gym => Icons.fitness_center,
  TrainingSource.program => Icons.list,
  TrainingSource.activity => Icons.directions_run,
};

/// A round photo (https only), else the initial or the account icon.
class Avatar extends StatelessWidget {
  const Avatar({required this.photoUrl, required this.name, this.size = 40, super.key});

  final String? photoUrl;
  final String name;
  final double size;

  @override
  Widget build(BuildContext context) {
    final url = photoUrl;
    final initial = name.trim().isEmpty ? null : name.trim().characters.first.toUpperCase();
    final fallback = CircleAvatar(
      radius: size / 2,
      child: initial == null ? Icon(Icons.person, size: size * 0.6) : Text(initial),
    );
    if (url == null || !url.startsWith('https://')) return ExcludeSemantics(child: fallback);
    return ExcludeSemantics(
      child: ClipOval(
        child: Image.network(
          url,
          width: size,
          height: size,
          fit: BoxFit.cover,
          // Profile photo hosts may not send CORS headers: then the browser's <img> shows it
          webHtmlElementStrategy: WebHtmlElementStrategy.fallback,
          errorBuilder: (_, _, _) => fallback,
        ),
      ),
    );
  }
}
