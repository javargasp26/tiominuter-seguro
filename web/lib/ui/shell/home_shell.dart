import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app/app.dart';
import '../../data/training_repositories.dart';
import '../../l10n/app_localizations.dart';

/// The signed-in frame: a navigation bar at phone width, a rail from 840 dp (desktop), and the
/// current page. Only the selected page is built, so a hidden page's listeners stop.
class HomeShell extends StatelessWidget {
  const HomeShell({required this.location, required this.child, this.pending, super.key});

  static const double railBreakpoint = 840;

  final String location;
  final Widget child;

  /// Writes not sent yet (the "Not synced yet" banner); null without training.
  final PendingWrites? pending;

  static const List<String> _roots = [Routes.week, Routes.train, Routes.history, Routes.groups, Routes.account];

  int get _selected {
    final index = _roots.indexWhere((root) => location == root || location.startsWith('$root/'));
    return index < 0 ? 0 : index;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final labels = [l10n.navWeek, l10n.navTrain, l10n.navHistory, l10n.navGroups, l10n.navAccount];
    const icons = [
      Icons.calendar_today_outlined,
      Icons.fitness_center_outlined,
      Icons.history,
      Icons.group_outlined,
      Icons.account_circle_outlined,
    ];
    const selectedIcons = [
      Icons.calendar_today,
      Icons.fitness_center,
      Icons.history,
      Icons.group,
      Icons.account_circle,
    ];
    final pending = this.pending;
    final body = pending == null
        ? child
        : Column(
            children: [
              PendingBanner(pending: pending),
              Expanded(child: child),
            ],
          );
    void go(int index) => context.go(_roots[index]);

    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth >= railBreakpoint) {
          return Scaffold(
            body: Row(
              children: [
                NavigationRail(
                  selectedIndex: _selected,
                  onDestinationSelected: go,
                  labelType: NavigationRailLabelType.all,
                  destinations: [
                    for (var i = 0; i < labels.length; i++)
                      NavigationRailDestination(
                        icon: Icon(icons[i]),
                        selectedIcon: Icon(selectedIcons[i]),
                        label: Text(labels[i]),
                      ),
                  ],
                ),
                const VerticalDivider(width: 1),
                Expanded(child: body),
              ],
            ),
          );
        }
        return Scaffold(
          body: body,
          bottomNavigationBar: NavigationBar(
            selectedIndex: _selected,
            onDestinationSelected: go,
            destinations: [
              for (var i = 0; i < labels.length; i++)
                NavigationDestination(icon: Icon(icons[i]), selectedIcon: Icon(selectedIcons[i]), label: labels[i]),
            ],
          ),
        );
      },
    );
  }
}

/// "Not synced yet" while writes wait for the server (offline, or still in flight from an earlier
/// page load): the browser's offline cache keeps them and sends them later.
class PendingBanner extends StatelessWidget {
  const PendingBanner({required this.pending, super.key});

  final PendingWrites pending;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: pending,
    builder: (context, _) {
      if (!pending.hasPending) return const SizedBox.shrink();
      final l10n = AppLocalizations.of(context);
      final theme = Theme.of(context);
      return Semantics(
        liveRegion: true,
        child: Tooltip(
          message: l10n.pendingWritesDescription,
          child: Container(
            width: double.infinity,
            color: theme.colorScheme.secondaryContainer,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                Icon(Icons.cloud_upload_outlined, size: 18, color: theme.colorScheme.onSecondaryContainer),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    l10n.pendingWrites,
                    style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSecondaryContainer),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    },
  );
}
