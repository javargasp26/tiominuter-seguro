import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/mvi.dart';
import '../../data/repositories.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/browser.dart';
import '../account/account_page.dart' show accountDeletedKey;
import '../session/session_bloc.dart';

/// What a failed sign-in says (a cancelled one says nothing).
String signInErrorText(SignInOutcome outcome, AppLocalizations l10n) => switch (outcome) {
  SignInOutcome.noNetwork => l10n.noNetwork,
  SignInOutcome.popupBlocked => l10n.popupBlocked,
  SignInOutcome.redirectIncomplete => l10n.signInRedirectIncomplete,
  _ => l10n.signInFailed,
};

/// Signed out: what the app is and "Sign in with Google" (no guest mode on the web).
class LandingPage extends StatelessWidget {
  const LandingPage({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return EffectListener<SessionBloc, SessionEffect>(
      onEffect: (context, effect) {
        if (effect is! SignInFailed) return;
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(signInErrorText(effect.outcome, l10n))));
      },
      child: Scaffold(
        body: _AccountDeletedNotice(
          child: BlocBuilder<SessionBloc, SessionState>(
            builder: (context, state) => Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 560),
                  child: state.isLoading
                      // Never the landing content before the session is known (no flash after a redirect)
                      ? Semantics(
                          liveRegion: true,
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const CircularProgressIndicator(),
                              if (state.isCompletingRedirect) ...[
                                const SizedBox(height: 16),
                                Text(l10n.signingIn, style: theme.textTheme.titleMedium),
                              ],
                            ],
                          ),
                        )
                      : Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Image(
                              image: AssetImage('assets/mascot.png'),
                              width: 120,
                              height: 120,
                              excludeFromSemantics: true,
                            ),
                            const SizedBox(height: 16),
                            Semantics(
                              header: true,
                              child: Text(
                                l10n.appName,
                                style: theme.textTheme.displaySmall,
                                textAlign: TextAlign.center,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Text(l10n.tagline, style: theme.textTheme.titleMedium, textAlign: TextAlign.center),
                            const SizedBox(height: 24),
                            Text(l10n.landingTitle, style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
                            const SizedBox(height: 12),
                            Text(l10n.landingText, textAlign: TextAlign.center),
                            const SizedBox(height: 8),
                            Text(l10n.landingTrainOnPhone, textAlign: TextAlign.center),
                            const SizedBox(height: 32),
                            if (state.redirectError case final error?) ...[
                              Semantics(
                                liveRegion: true,
                                child: Text(
                                  signInErrorText(error, l10n),
                                  textAlign: TextAlign.center,
                                  style: TextStyle(color: theme.colorScheme.error),
                                ),
                              ),
                              const SizedBox(height: 12),
                            ],
                            FilledButton.icon(
                              onPressed: state.isSigningIn
                                  ? null
                                  : () => context.read<SessionBloc>().add(const SignInClicked()),
                              icon: const Icon(Icons.login),
                              label: Text(state.isSigningIn ? l10n.signingIn : l10n.signInWithGoogle),
                              style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                            ),
                          ],
                        ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// After Delete account the page reloads (Firestore's cache cleared): the landing says it once.
class _AccountDeletedNotice extends StatefulWidget {
  const _AccountDeletedNotice({required this.child});

  final Widget child;

  @override
  State<_AccountDeletedNotice> createState() => _AccountDeletedNoticeState();
}

class _AccountDeletedNoticeState extends State<_AccountDeletedNotice> {
  @override
  void initState() {
    super.initState();
    final tab = Browser.sessionStorage();
    if (tab.read(accountDeletedKey) != '1') return;
    tab.remove(accountDeletedKey);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(AppLocalizations.of(context).accountDeletedWeb)));
    });
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
