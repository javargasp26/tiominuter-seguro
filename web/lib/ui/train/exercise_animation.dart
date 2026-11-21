/// Exercise animations on the web (Android's `ExerciseAnimation` + `AnimationLocator`): the
/// catalog's GIF from the same-origin `/content/v1/animations/<file>` (Hosting caches GIFs for a
/// year, immutable; the URL never changes, so the browser's cache serves them). Behind
/// [AppConfig.showExerciseAnimations]; the icon of the exercise type is the placeholder while
/// loading, the fallback when the file is missing or fails, and what reduced motion shows, always in
/// the same box (no layout jump). Only the current exercise's GIF is shown; [PreloadExerciseAnimation]
/// fetches the next one. A hidden tab stops the animation ([TickerMode]: `Image` stops decoding frames).
library;

import 'package:flutter/material.dart';

import '../../app/services.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/browser.dart';

/// Android's `REST_ANIMATION`: what a rest phase shows.
const String restAnimation = '13_rest.gif';

/// How animations are shown below it: [ExerciseAnimationScope] in the app, plain values in tests.
class ExerciseAnimationSettings extends InheritedWidget {
  const ExerciseAnimationSettings({
    required this.enabled,
    required this.imageFor,
    required super.child,
    this.reducedMotion = false,
    this.tabVisible = true,
    super.key,
  });

  final bool enabled;

  /// The image of an animation file (the network in the app, memory in tests).
  final ImageProvider Function(String file) imageFor;

  /// `prefers-reduced-motion: reduce`: the icon instead of an animation.
  final bool reducedMotion;

  /// The browser tab is visible: animations run only then.
  final bool tabVisible;

  /// Without a scope (e.g. a widget test of one screen) nothing is loaded: the icons.
  static const ExerciseAnimationSettings _none = ExerciseAnimationSettings(
    enabled: false,
    imageFor: _noImage,
    child: SizedBox.shrink(),
  );

  static ImageProvider _noImage(String file) => throw UnsupportedError('animations are off');

  static ExerciseAnimationSettings of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<ExerciseAnimationSettings>() ?? _none;

  /// Whether a GIF is shown here at all (the flag on, motion allowed by the browser and the OS).
  static bool animates(BuildContext context) {
    final settings = of(context);
    return settings.enabled && !settings.reducedMotion && !(MediaQuery.maybeDisableAnimationsOf(context) ?? false);
  }

  /// `/content/v1/animations/<file>` on this origin (or `CONTENT_BASE_URL`).
  static Uri urlOf(String file) =>
      Uri.base.resolve(AppConfig.contentBaseUrl).resolve('animations/${Uri.encodeComponent(file)}');

  @override
  bool updateShouldNotify(ExerciseAnimationSettings old) =>
      enabled != old.enabled ||
      reducedMotion != old.reducedMotion ||
      tabVisible != old.tabVisible ||
      imageFor != old.imageFor;
}

/// The app's settings: [AppConfig.showExerciseAnimations], GIFs from the network, the browser's
/// reduced-motion preference (read again when the tab comes back) and the tab's visibility.
class ExerciseAnimationScope extends StatefulWidget {
  const ExerciseAnimationScope({required this.child, super.key});

  final Widget child;

  @override
  State<ExerciseAnimationScope> createState() => _ExerciseAnimationScopeState();
}

class _ExerciseAnimationScopeState extends State<ExerciseAnimationScope> {
  late final AppLifecycleListener _lifecycle;
  bool _visible = true;
  bool _reducedMotion = Browser.prefersReducedMotion();

  static ImageProvider _network(String file) => NetworkImage(ExerciseAnimationSettings.urlOf(file).toString());

  @override
  void initState() {
    super.initState();
    _lifecycle = AppLifecycleListener(
      onHide: () => setState(() => _visible = false),
      onShow: () => setState(() {
        _visible = true;
        _reducedMotion = Browser.prefersReducedMotion();
      }),
    );
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ExerciseAnimationSettings(
    enabled: AppConfig.showExerciseAnimations,
    imageFor: _network,
    reducedMotion: _reducedMotion,
    tabVisible: _visible,
    child: widget.child,
  );
}

enum ExerciseVisualKind { time, reps, rest }

/// The exercise's animation (Android's `ExerciseAnimation`), or the icon of its type: a
/// [size]-square box either way. [name] (the exercise's localized name) is the image's alt text.
/// A rest phase shows the rest animation.
class ExerciseVisual extends StatelessWidget {
  const ExerciseVisual({
    required this.kind,
    this.animation,
    this.name,
    this.size = 120,
    this.animate = true,
    super.key,
  });

  final ExerciseVisualKind kind;

  /// The catalog's animation file name.
  final String? animation;
  final String? name;
  final double size;

  /// False in lists (the icon only: one GIF per screen, the current exercise's).
  final bool animate;

  String? get _file => kind == ExerciseVisualKind.rest ? restAnimation : animation;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final settings = ExerciseAnimationSettings.of(context);
    final label = name ?? _typeLabel(l10n);
    final icon = _TypeIcon(kind: kind, size: size, label: label);
    final file = _file;
    if (!animate || file == null || file.isEmpty || !ExerciseAnimationSettings.animates(context)) return icon;
    return SizedBox.square(
      dimension: size,
      // A hidden tab: Image stops listening to its frames (no decoding, no painting)
      child: TickerMode(
        enabled: settings.tabVisible,
        child: Image(
          image: settings.imageFor(file),
          width: size,
          height: size,
          fit: BoxFit.contain,
          semanticLabel: label,
          gaplessPlayback: true,
          // The icon until the first frame arrives, and if the file is missing or broken
          frameBuilder: (context, child, frame, synchronous) => frame == null && !synchronous ? icon : child,
          errorBuilder: (context, error, stack) => icon,
        ),
      ),
    );
  }

  String _typeLabel(AppLocalizations l10n) => switch (kind) {
    ExerciseVisualKind.time => l10n.exerciseTypeTime,
    ExerciseVisualKind.reps => l10n.exerciseTypeReps,
    ExerciseVisualKind.rest => l10n.exerciseTypeRest,
  };
}

class _TypeIcon extends StatelessWidget {
  const _TypeIcon({required this.kind, required this.size, required this.label});

  final ExerciseVisualKind kind;
  final double size;
  final String label;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final icon = switch (kind) {
      ExerciseVisualKind.time => Icons.timer_outlined,
      ExerciseVisualKind.reps => Icons.repeat,
      ExerciseVisualKind.rest => Icons.self_improvement,
    };
    return Semantics(
      label: label,
      image: true,
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(color: scheme.secondaryContainer, shape: BoxShape.circle),
        child: Icon(icon, size: size * 0.5, color: scheme.onSecondaryContainer),
      ),
    );
  }
}

/// Fetches the next exercise's GIF into the image cache while the current one plays (nothing when
/// animations are off or reduced). A failure is ignored: that exercise shows its icon.
class PreloadExerciseAnimation extends StatefulWidget {
  const PreloadExerciseAnimation({required this.animation, required this.child, super.key});

  final String? animation;
  final Widget child;

  @override
  State<PreloadExerciseAnimation> createState() => _PreloadExerciseAnimationState();
}

class _PreloadExerciseAnimationState extends State<PreloadExerciseAnimation> {
  String? _preloaded;

  void _preload() {
    final file = widget.animation;
    if (file == null || file.isEmpty || file == _preloaded || !ExerciseAnimationSettings.animates(context)) return;
    _preloaded = file;
    precacheImage(ExerciseAnimationSettings.of(context).imageFor(file), context, onError: (_, _) {});
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _preload();
  }

  @override
  void didUpdateWidget(PreloadExerciseAnimation old) {
    super.didUpdateWidget(old);
    _preload();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
