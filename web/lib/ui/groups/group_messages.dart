/// What group operations say (Android's `GroupError.messageRes`) and how an invite is shared.
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../domain/groups.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/browser.dart';

String groupErrorText(GroupError error, AppLocalizations l10n) => switch (error) {
  GroupError.network => l10n.groupsMessageNoNetwork,
  GroupError.goalRequired => l10n.groupsMessageGoalRequired,
  GroupError.invalidCode => l10n.groupsMessageInvalidCode,
  GroupError.groupFull => l10n.groupsMessageGroupFull,
  GroupError.alreadyMember => l10n.groupsMessageAlreadyMember,
  GroupError.groupLimit => l10n.groupsMessageGroupLimit,
  GroupError.invalidName => l10n.groupsMessageInvalidName,
  GroupError.notFound => l10n.groupsMessageNotFound,
  GroupError.permissionDenied => l10n.groupsMessageNotAllowed,
  GroupError.failed => l10n.groupsMessageFailed,
};

void showGroupMessage(BuildContext context, String text) =>
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(SnackBar(content: Text(text)));

/// The link friends open to join (`/groups/join?code=…` on this site).
Uri joinLink(String code) => Uri.base.resolve('/groups/join?code=$code');

/// Shares the invite (Android's share sheet): the Web Share API where the browser has it (iPhone,
/// Android), else the text and link are copied. [share] and [copy] are the browser's by default.
Future<void> shareInvite(
  BuildContext context, {
  required String groupName,
  required String code,
  Future<ShareResult> Function({required String text, String? url})? share,
  Future<void> Function(String text)? copy,
}) async {
  final l10n = AppLocalizations.of(context);
  final messenger = ScaffoldMessenger.maybeOf(context);
  final text = l10n.groupsShareText(groupName, code, l10n.appName);
  final url = joinLink(code).toString();
  final result = await (share ?? Browser.share)(text: text, url: url);
  if (result != ShareResult.unsupported) return;
  await (copy ?? _copy)('$text $url');
  messenger?.showSnackBar(SnackBar(content: Text(l10n.groupsShareCopied)));
}

Future<void> copyCode(BuildContext context, String code, {Future<void> Function(String text)? copy}) async {
  final l10n = AppLocalizations.of(context);
  final messenger = ScaffoldMessenger.maybeOf(context);
  await (copy ?? _copy)(code);
  messenger?.showSnackBar(SnackBar(content: Text(l10n.groupsCodeCopied)));
}

Future<void> _copy(String text) => Clipboard.setData(ClipboardData(text: text));

/// The invite code, large, with Copy and Share (Android's code card).
class InviteCodeCard extends StatelessWidget {
  const InviteCodeCard({required this.groupName, required this.code, super.key});

  final String groupName;
  final String code;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.groupsCodeLabel, style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            SelectableText(
              code,
              textAlign: TextAlign.center,
              style: theme.textTheme.headlineMedium?.copyWith(letterSpacing: 4, fontFeatures: const []),
            ),
            const SizedBox(height: 12),
            Wrap(
              alignment: WrapAlignment.center,
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton.icon(
                  onPressed: () => copyCode(context, code),
                  style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
                  icon: const Icon(Icons.copy),
                  label: Text(l10n.groupsCopyCode),
                ),
                FilledButton.icon(
                  onPressed: () => shareInvite(context, groupName: groupName, code: code),
                  style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                  icon: const Icon(Icons.share),
                  label: Text(l10n.groupsShareCode),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
