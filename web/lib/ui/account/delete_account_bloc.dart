import '../../app/account_deletion.dart';
import '../../core/mvi.dart';
import '../../data/repositories.dart';

sealed class DeleteAccountIntent {
  const DeleteAccountIntent();
}

final class DeleteAccountClicked extends DeleteAccountIntent {
  const DeleteAccountClicked();
}

/// "Continue" on the explanation.
final class DeleteAccountContinue extends DeleteAccountIntent {
  const DeleteAccountContinue();
}

/// "Delete account" on the final confirmation: the re-authentication, then the steps.
final class DeleteAccountConfirmed extends DeleteAccountIntent {
  const DeleteAccountConfirmed();
}

final class DeleteAccountDismissed extends DeleteAccountIntent {
  const DeleteAccountDismissed();
}

/// Back from the re-authentication redirect (a phone): the steps after it.
final class DeleteAccountResumed extends DeleteAccountIntent {
  const DeleteAccountResumed();
}

/// Back from that redirect, but it failed: say so (nothing was deleted).
final class DeleteAccountReauthFailed extends DeleteAccountIntent {
  const DeleteAccountReauthFailed(this.outcome);

  final ReauthOutcome outcome;
}

enum DeleteAccountDialog { none, explain, confirm }

class DeleteAccountState {
  const DeleteAccountState({this.dialog = DeleteAccountDialog.none, this.isDeleting = false});

  final DeleteAccountDialog dialog;

  /// Re-authenticated, deleting: "Deleting your account…", nothing else can be tapped.
  final bool isDeleting;
}

sealed class DeleteAccountEffect {
  const DeleteAccountEffect();
}

/// The deletion ended: [result] says how (deleted, or why not).
final class DeleteAccountFinished extends DeleteAccountEffect {
  const DeleteAccountFinished(this.result);

  final DeleteAccountResult result;
}

/// Android's two-step Delete account (DeleteDialog.Explain → DeleteDialog.Confirm), then
/// [AccountDeletion]. No "also delete from this device" choice: this browser's data always goes.
class DeleteAccountBloc extends MviBloc<DeleteAccountIntent, DeleteAccountState, DeleteAccountEffect> {
  DeleteAccountBloc({required AccountDeletion deletion, required WebUser user}) : super(const DeleteAccountState()) {
    Future<void> run(Emitter<DeleteAccountState> emit, Future<DeleteAccountResult> Function() steps) async {
      if (state.isDeleting) return;
      emit(const DeleteAccountState(isDeleting: true));
      final result = await steps();
      if (!isClosed) emit(DeleteAccountState(isDeleting: result == DeleteAccountResult.redirecting));
      // Cancelled: nothing happens and nothing is shown; redirecting: the page leaves
      if (result != DeleteAccountResult.cancelled && result != DeleteAccountResult.redirecting) {
        sendEffect(DeleteAccountFinished(result));
      }
    }

    on<DeleteAccountClicked>((_, emit) {
      if (!state.isDeleting) emit(const DeleteAccountState(dialog: DeleteAccountDialog.explain));
    });
    on<DeleteAccountContinue>((_, emit) => emit(const DeleteAccountState(dialog: DeleteAccountDialog.confirm)));
    on<DeleteAccountDismissed>((_, emit) => emit(const DeleteAccountState()));
    on<DeleteAccountConfirmed>((_, emit) => run(emit, () => deletion.start(user)));
    on<DeleteAccountResumed>((_, emit) => run(emit, () => deletion.continueAfterReauth(user)));
    on<DeleteAccountReauthFailed>((intent, emit) {
      final result = switch (intent.outcome) {
        ReauthOutcome.cancelled => DeleteAccountResult.cancelled,
        ReauthOutcome.noNetwork => DeleteAccountResult.noNetwork,
        ReauthOutcome.wrongAccount => DeleteAccountResult.wrongAccount,
        _ => DeleteAccountResult.reauthFailed,
      };
      if (result != DeleteAccountResult.cancelled) sendEffect(DeleteAccountFinished(result));
    });
  }
}
