import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../app/account_deletion.dart';
import '../../app/services.dart';
import '../../core/mvi.dart';
import '../../data/repositories.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/browser.dart';
import '../common/widgets.dart';
import '../session/session_bloc.dart';
import 'delete_account_bloc.dart';

/// The landing page's "Account deleted." after the page reloads (sessionStorage of this tab).
const String accountDeletedKey = 'tm.accountDeleted';

/// What a finished deletion says.
String deleteResultText(DeleteAccountResult result, AppLocalizations l10n) => switch (result) {
  DeleteAccountResult.deleted => l10n.accountDeletedWeb,
  DeleteAccountResult.noNetwork => l10n.noNetwork,
  DeleteAccountResult.reauthFailed => l10n.signInFailed,
  DeleteAccountResult.wrongAccount => l10n.deleteWrongAccount,
  DeleteAccountResult.groupsFailed => l10n.authErrorGroupsFailed,
  DeleteAccountResult.serverDataFailed => l10n.authErrorServerDataFailed,
  DeleteAccountResult.accountNotDeleted => l10n.authErrorAccountNotDeleted,
  DeleteAccountResult.cancelled || DeleteAccountResult.redirecting => '',
};

/// Name, photo, email, Sign out and Delete account (Android's two-step flow and order).
class AccountPage extends StatelessWidget {
  const AccountPage({super.key});

  /// With writes not sent yet, asks first (they would be lost); then this account's training data
  /// leaves the browser.
  Future<void> _signOut(BuildContext context) async {
    final services = context.read<AppServices>();
    final session = context.read<SessionBloc>();
    final training = services.trainingDeps;
    if (training != null && training.pending.hasPending) {
      final l10n = AppLocalizations.of(context);
      final anyway = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(l10n.signOutPendingTitle),
          content: Text(l10n.signOutPendingText),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
            TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.signOutAnyway)),
          ],
        ),
      );
      if (anyway != true) return;
    }
    final uid = session.state.user?.uid;
    // Before signing out: the account's photos and keys leave this browser
    if (training != null && uid != null) await training.service.clearLocalData(uid);
    if (uid != null) services.groupsService?.clearLocalData(uid);
    session.add(const SignOutClicked());
  }

  @override
  Widget build(BuildContext context) {
    final services = context.read<AppServices>();
    final session = context.read<SessionBloc>();
    final user = session.state.user;
    final groups = services.groupsService;
    final account = services.account;
    final training = services.trainingDeps;
    final canDelete = user != null && groups != null && account != null && training != null;
    final Widget page = EffectListener<SessionBloc, SessionEffect>(
      onEffect: (context, effect) {
        if (effect is SignedOut) {
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text(AppLocalizations.of(context).accountSignedOut)));
          context.read<AppServices>().trainingDeps?.clearOfflineCache?.call();
        }
      },
      child: BlocBuilder<SessionBloc, SessionState>(
        builder: (context, state) => canDelete
            ? BlocBuilder<DeleteAccountBloc, DeleteAccountState>(
                builder: (context, deletion) => AccountContent(
                  user: state.user,
                  onSignOut: () => _signOut(context),
                  onDelete: () => context.read<DeleteAccountBloc>().add(const DeleteAccountClicked()),
                  isDeleting: deletion.isDeleting,
                ),
              )
            : AccountContent(user: state.user, onSignOut: () => _signOut(context)),
      ),
    );
    if (!canDelete) return page;
    return BlocProvider(
      create: (_) {
        final bloc = DeleteAccountBloc(
          deletion: AccountDeletion(
            auth: services.auth,
            groups: groups,
            account: account,
            training: training.service,
            isOnline: Browser.isOnline,
          ),
          user: user,
        );
        // Back from the re-authentication redirect (a phone): continue, or say why not
        final failure = session.state.accountDeletionFailure;
        if (session.state.resumeAccountDeletion) {
          bloc.add(const DeleteAccountResumed());
        } else if (failure != null) {
          bloc.add(DeleteAccountReauthFailed(failure));
        }
        if (session.state.resumeAccountDeletion || failure != null) session.add(const AccountDeletionResumeTaken());
        return bloc;
      },
      child: EffectListener<DeleteAccountBloc, DeleteAccountEffect>(
        onEffect: (context, effect) {
          final l10n = AppLocalizations.of(context);
          switch (effect) {
            case DeleteAccountFinished(result: DeleteAccountResult.deleted):
              // The landing says it after the reload that clears Firestore's offline cache
              Browser.sessionStorage().write(accountDeletedKey, '1');
              final clear = context.read<AppServices>().trainingDeps?.clearOfflineCache;
              if (clear != null) {
                clear();
              } else {
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.accountDeletedWeb)));
              }
            case DeleteAccountFinished(:final result):
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(deleteResultText(result, l10n))));
          }
        },
        child: BlocListener<DeleteAccountBloc, DeleteAccountState>(
          listenWhen: (a, b) => a.dialog != b.dialog && b.dialog != DeleteAccountDialog.none,
          listener: (context, state) async {
            final l10n = AppLocalizations.of(context);
            final bloc = context.read<DeleteAccountBloc>();
            final explain = state.dialog == DeleteAccountDialog.explain;
            final next = await showDialog<bool>(
              context: context,
              builder: (context) => AlertDialog(
                title: Text(explain ? l10n.deleteDialogTitle : l10n.deleteConfirmTitle),
                content: Text(explain ? l10n.deleteDialogMessageWeb : l10n.deleteConfirmMessageWeb),
                actions: [
                  TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
                  TextButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: Text(explain ? l10n.deleteContinue : l10n.accountDelete),
                  ),
                ],
              ),
            );
            if (bloc.isClosed) return;
            if (next != true) {
              bloc.add(const DeleteAccountDismissed());
            } else {
              bloc.add(explain ? const DeleteAccountContinue() : const DeleteAccountConfirmed());
            }
          },
          child: page,
        ),
      ),
    );
  }
}

class AccountContent extends StatelessWidget {
  const AccountContent({
    required this.user,
    required this.onSignOut,
    this.onDelete,
    this.isDeleting = false,
    super.key,
  });

  final WebUser? user;
  final VoidCallback onSignOut;

  /// Delete account (null: not offered, e.g. a read-only test).
  final VoidCallback? onDelete;
  final bool isDeleting;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final current = user;
    return PageBody(
      children: [
        PageTitle(l10n.accountTitle),
        if (current != null) ...[
          Center(
            child: Semantics(
              label: l10n.accountPhotoDescription,
              image: true,
              child: Avatar(photoUrl: current.photoUrl, name: current.displayName ?? '', size: 88),
            ),
          ),
          const SizedBox(height: 12),
          if (current.displayName != null)
            Text(current.displayName!, style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
          if (current.email != null) Text(current.email!, textAlign: TextAlign.center),
          const SizedBox(height: 24),
          Center(
            child: OutlinedButton.icon(
              onPressed: isDeleting ? null : onSignOut,
              icon: const Icon(Icons.logout),
              label: Text(l10n.accountSignOut),
              style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
            ),
          ),
          if (onDelete != null) ...[
            const SizedBox(height: 32),
            if (isDeleting)
              Semantics(
                liveRegion: true,
                child: Column(
                  children: [
                    const CircularProgressIndicator(),
                    const SizedBox(height: 12),
                    Text(l10n.deleteInProgress, textAlign: TextAlign.center),
                  ],
                ),
              )
            else
              Center(
                child: TextButton.icon(
                  onPressed: onDelete,
                  icon: Icon(Icons.delete_forever_outlined, color: theme.colorScheme.error),
                  label: Text(l10n.accountDelete, style: TextStyle(color: theme.colorScheme.error)),
                  style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                ),
              ),
          ],
        ],
      ],
    );
  }
}
