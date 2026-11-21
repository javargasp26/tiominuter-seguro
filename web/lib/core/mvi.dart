import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

export 'package:flutter_bloc/flutter_bloc.dart' show Emitter;

/// The web's MVI base, the same shape as Android's `BaseViewModel<Intent, State, Effect>`
/// (app/.../ui/base/): the UI sends intents (`add`), a handler per intent reduces them into a new
/// immutable state (`emit`), and one-shot effects (a message, navigation) go through [effects].
abstract class MviBloc<I, S, E> extends Bloc<I, S> {
  MviBloc(super.initialState);

  final StreamController<E> _effects = StreamController<E>.broadcast();

  /// One-shot events for the UI (collected by [EffectListener]).
  Stream<E> get effects => _effects.stream;

  @protected
  void sendEffect(E effect) {
    if (!_effects.isClosed) _effects.add(effect);
  }

  @override
  Future<void> close() {
    // Not awaited: a broadcast controller's close waits for its listeners to see "done"
    unawaited(_effects.close());
    return super.close();
  }
}

/// Collects a bloc's effects while mounted (Android's `CollectEffects`).
class EffectListener<B extends MviBloc<Object?, Object?, E>, E> extends StatefulWidget {
  const EffectListener({required this.onEffect, required this.child, super.key});

  final void Function(BuildContext context, E effect) onEffect;
  final Widget child;

  @override
  State<EffectListener<B, E>> createState() => _EffectListenerState<B, E>();
}

class _EffectListenerState<B extends MviBloc<Object?, Object?, E>, E> extends State<EffectListener<B, E>> {
  StreamSubscription<E>? _subscription;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _subscription?.cancel();
    _subscription = context.read<B>().effects.listen((effect) {
      if (mounted) widget.onEffect(context, effect);
    });
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

/// Tells [onVisible] / [onHidden] when the page is shown or hidden: on mount and unmount, and when
/// the browser tab is hidden or shown again (Flutter's app lifecycle on the web follows the page's
/// visibility). Screens with Firestore listeners use it so listeners run only while visible, as
/// Android's `LifecycleStartEffect`.
class VisibilityScope extends StatefulWidget {
  const VisibilityScope({required this.onVisible, required this.onHidden, required this.child, super.key});

  final VoidCallback onVisible;
  final VoidCallback onHidden;
  final Widget child;

  @override
  State<VisibilityScope> createState() => _VisibilityScopeState();
}

class _VisibilityScopeState extends State<VisibilityScope> {
  late final AppLifecycleListener _lifecycle;

  @override
  void initState() {
    super.initState();
    _lifecycle = AppLifecycleListener(onShow: () => widget.onVisible(), onHide: () => widget.onHidden());
    widget.onVisible();
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    widget.onHidden();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

/// [VisibilityScope] for the bloc [B] above it, read at build time: `onHidden` also runs while the
/// page is being removed, when looking the bloc up from that context is no longer allowed. A
/// closed bloc gets nothing.
class BlocVisibilityScope<B extends BlocBase<Object?>> extends StatelessWidget {
  const BlocVisibilityScope({required this.onVisible, required this.onHidden, required this.child, super.key});

  final void Function(B bloc) onVisible;
  final void Function(B bloc) onHidden;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final bloc = context.read<B>();
    return VisibilityScope(
      onVisible: () => onVisible(bloc),
      onHidden: () {
        if (!bloc.isClosed) onHidden(bloc);
      },
      child: child,
    );
  }
}
