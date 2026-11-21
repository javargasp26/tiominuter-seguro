import 'dart:async';

import '../../core/mvi.dart';
import '../../data/repositories.dart';

// ---------- Intents ----------

sealed class SessionIntent {
  const SessionIntent();
}

final class SessionStarted extends SessionIntent {
  const SessionStarted();
}

final class SignInClicked extends SessionIntent {
  const SignInClicked();
}

final class SignOutClicked extends SessionIntent {
  const SignOutClicked();
}

/// The Account page took over the deletion resumed after the redirect.
final class AccountDeletionResumeTaken extends SessionIntent {
  const AccountDeletionResumeTaken();
}

final class _UserChanged extends SessionIntent {
  const _UserChanged(this.user);

  final WebUser? user;
}

// ---------- State ----------

class SessionState {
  const SessionState({
    this.isLoading = true,
    this.user,
    this.isSigningIn = false,
    this.isCompletingRedirect = false,
    this.redirectError,
    this.resumeAccountDeletion = false,
    this.accountDeletionFailure,
  });

  /// Back from the re-authentication redirect of Delete account: the Account page continues it.
  final bool resumeAccountDeletion;

  /// That re-authentication failed (nothing deleted): the Account page says so.
  final ReauthOutcome? accountDeletionFailure;

  /// Until the sign-in redirect's result is known and Firebase Auth reports the first user
  /// (restored session or none): the app doesn't decide where to go before.
  final bool isLoading;
  final WebUser? user;
  final bool isSigningIn;

  /// Back from Google's sign-in page: "Signing in…" while its result resolves.
  final bool isCompletingRedirect;

  /// How the sign-in redirect failed, shown on the landing page until the next attempt.
  final SignInOutcome? redirectError;

  SessionState copyWith({
    bool? isLoading,
    WebUser? user,
    bool clearUser = false,
    bool? isSigningIn,
    bool? isCompletingRedirect,
    SignInOutcome? redirectError,
    bool clearRedirectError = false,
    bool? resumeAccountDeletion,
    ReauthOutcome? accountDeletionFailure,
    bool clearAccountDeletion = false,
  }) => SessionState(
    isLoading: isLoading ?? this.isLoading,
    user: clearUser ? null : (user ?? this.user),
    isSigningIn: isSigningIn ?? this.isSigningIn,
    isCompletingRedirect: isCompletingRedirect ?? this.isCompletingRedirect,
    redirectError: clearRedirectError ? null : (redirectError ?? this.redirectError),
    resumeAccountDeletion: clearAccountDeletion ? false : (resumeAccountDeletion ?? this.resumeAccountDeletion),
    accountDeletionFailure: clearAccountDeletion ? null : (accountDeletionFailure ?? this.accountDeletionFailure),
  );
}

// ---------- Effects ----------

sealed class SessionEffect {
  const SessionEffect();
}

/// A sign-in that didn't work (a cancelled popup shows nothing, as Android's cancelled picker).
final class SignInFailed extends SessionEffect {
  const SignInFailed(this.outcome);

  final SignInOutcome outcome;
}

final class SignedOut extends SessionEffect {
  const SignedOut();
}

/// Who is signed in, app-wide. No guest mode on the web: without a user the landing page shows.
class SessionBloc extends MviBloc<SessionIntent, SessionState, SessionEffect> {
  SessionBloc(this._auth) : super(const SessionState()) {
    on<SessionStarted>((_, emit) async {
      if (_started) return;
      _started = true;
      // First the redirect's result (getRedirectResult), only then the user: listening earlier
      // would report "signed out" for a moment and route to the landing page
      if (_auth.isReturningFromRedirect) emit(state.copyWith(isCompletingRedirect: true));
      final outcome = await _auth.completeRedirectSignIn();
      final failed = outcome != null && outcome != SignInOutcome.signedIn;
      emit(
        state.copyWith(
          isCompletingRedirect: false,
          redirectError: failed ? outcome : null,
          resumeAccountDeletion: _auth.takeAccountDeletionResume(),
          accountDeletionFailure: _auth.takeAccountDeletionFailure(),
        ),
      );
      _subscription = _auth.user.listen((user) => add(_UserChanged(user)));
    });
    on<_UserChanged>((intent, emit) {
      emit(state.copyWith(isLoading: false, user: intent.user, clearUser: intent.user == null, isSigningIn: false));
    });
    on<SignInClicked>((_, emit) async {
      if (state.isSigningIn) return;
      emit(state.copyWith(isSigningIn: true, clearRedirectError: true));
      final outcome = await _auth.signInWithGoogle();
      emit(state.copyWith(isSigningIn: false));
      if (outcome != SignInOutcome.signedIn && outcome != SignInOutcome.cancelled) sendEffect(SignInFailed(outcome));
    });
    on<AccountDeletionResumeTaken>((_, emit) => emit(state.copyWith(clearAccountDeletion: true)));
    on<SignOutClicked>((_, _) async {
      await _auth.signOut();
      sendEffect(const SignedOut());
    });
  }

  final AuthRepository _auth;
  StreamSubscription<WebUser?>? _subscription;
  bool _started = false;

  @override
  Future<void> close() async {
    await _subscription?.cancel();
    return super.close();
  }
}
