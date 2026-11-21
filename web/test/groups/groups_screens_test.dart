import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:tiominuter_web/app/app.dart';
import 'package:tiominuter_web/app/services.dart';
import 'package:tiominuter_web/app/theme.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/l10n/app_localizations.dart';
import 'package:tiominuter_web/platform/browser.dart';
import 'package:tiominuter_web/ui/account/account_page.dart';
import 'package:tiominuter_web/app/account_deletion.dart';
import 'package:tiominuter_web/ui/groups/group_detail_page.dart';
import 'package:tiominuter_web/ui/groups/group_messages.dart';
import 'package:tiominuter_web/ui/groups/groups_page.dart';
import 'package:tiominuter_web/ui/train/train_widgets.dart';
import 'package:tiominuter_web/ui/week/week_page.dart';

import '../support/fakes.dart';
import '../support/groups_kit.dart';

/// The groups screens on the web, with Android's texts: the list and the goal check, create with
/// Copy / Share (Web Share, or a copy), join and its messages, the detail's Leave and the owner's
/// actions behind their confirmations, feed moderation, the banner, Delete account's texts.
void main() {
  late GroupsKit kit;
  late WebUser ana;

  setUp(() {
    kit = GroupsKit();
    ana = kit.user('ana', 'Ana');
  });

  AppServices services() => AppServices(
    auth: FakeAuthRepository(user: ana),
    userData: FakeUserDataRepository(),
    groups: kit.groups,
    content: FakeContentRepository(),
    dates: kit.training.dates,
    trainingDeps: kit.training.deps,
    groupsService: kit.service,
    account: kit.account,
  );

  Widget wrap(Widget child, {Locale locale = const Locale('en')}) => RepositoryProvider<AppServices>.value(
    value: services(),
    child: RepositoryProvider.value(
      value: WebUserHolder(ana),
      child: MaterialApp(
        locale: locale,
        theme: AppTheme.light,
        localizationsDelegates: const [
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: child),
      ),
    ),
  );

  /// A router with the group screens, so navigation and back work as in the app.
  Future<GoRouter> routed(WidgetTester tester, String location) async {
    tester.view.physicalSize = const Size(600, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final router = GoRouter(
      initialLocation: location,
      routes: [
        GoRoute(
          path: Routes.groups,
          // In the app the shell gives Groups its Scaffold
          builder: (_, _) => const Scaffold(body: GroupsPage(uid: 'ana')),
        ),
        GoRoute(path: Routes.groupCreate, builder: (_, _) => const GroupCreatePage()),
        GoRoute(
          path: Routes.groupJoin,
          builder: (_, s) => GroupJoinPage(code: s.uri.queryParameters['code'] ?? ''),
        ),
        GoRoute(path: '/week/goal', builder: (_, s) => Text('goal then ${s.uri.queryParameters['then']}')),
        GoRoute(
          path: '/groups/:id',
          builder: (_, s) => GroupDetailPage(uid: 'ana', groupId: s.pathParameters['id']!, tab: GroupTab.ranking),
        ),
      ],
    );
    await tester.pumpWidget(
      RepositoryProvider<AppServices>.value(
        value: services(),
        child: RepositoryProvider.value(
          value: WebUserHolder(ana),
          child: MaterialApp.router(
            theme: AppTheme.light,
            localizationsDelegates: const [
              AppLocalizations.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: AppLocalizations.supportedLocales,
            routerConfig: router,
          ),
        ),
      ),
    );
    await settleAll(tester);
    return router;
  }

  String at(GoRouter router) => router.routerDelegate.currentConfiguration.uri.toString();

  group('the list', () {
    testWidgets('Create group and Join with code; without a goal, the dialog then the goal screen', (tester) async {
      final router = await routed(tester, Routes.groups);
      expect(find.text('No groups yet'), findsOneWidget);
      expect(find.textContaining('Create a group and share its code'), findsOneWidget);
      await tester.tap(find.text('Create group'));
      await settleAll(tester);
      expect(at(router), Routes.groupCreate);

      kit.training.training.storedGoals.clear();
      router.go(Routes.groups);
      await settleAll(tester);
      await tester.tap(find.text('Join with code'));
      await settleAll(tester);
      expect(find.text('Set your weekly goal first'), findsOneWidget);
      await tester.tap(find.text('Set goal'));
      await settleAll(tester);
      expect(find.text('goal then ${Routes.groupJoin}'), findsOneWidget);
    });
  });

  group('create', () {
    testWidgets('a name → the code, Copy and Share; an empty name is refused', (tester) async {
      kit.codes.add('K7MPX2QA');
      final router = await routed(tester, Routes.groupCreate);
      await tester.tap(find.text('Create'));
      await tester.pump();
      expect(find.text('Use 1 to 40 characters.'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'Morning crew');
      await tester.tap(find.text('Create'));
      await settleAll(tester);
      expect(find.text('Group created!'), findsOneWidget);
      expect(find.text('K7MPX2QA'), findsOneWidget);
      expect(find.text('Copy code'), findsOneWidget);
      expect(find.text('Share code'), findsOneWidget);
      await tester.tap(find.text('Open group'));
      await settleAll(tester);
      expect(at(router), startsWith('/groups/group-'));
    });

    testWidgets('Share: the Web Share sheet; without one, the invite and its link are copied', (tester) async {
      String? sharedText;
      String? sharedUrl;
      String? copied;
      await tester.pumpWidget(
        wrap(
          Builder(
            builder: (context) => Column(
              children: [
                TextButton(
                  onPressed: () => shareInvite(
                    context,
                    groupName: 'Crew',
                    code: 'K7MPX2QA',
                    share: ({required text, url}) async {
                      sharedText = text;
                      sharedUrl = url;
                      return ShareResult.shared;
                    },
                    copy: (text) async => copied = text,
                  ),
                  child: const Text('web share'),
                ),
                TextButton(
                  onPressed: () => shareInvite(
                    context,
                    groupName: 'Crew',
                    code: 'K7MPX2QA',
                    share: ({required text, url}) async => ShareResult.unsupported,
                    copy: (text) async => copied = text,
                  ),
                  child: const Text('no share'),
                ),
              ],
            ),
          ),
        ),
      );
      await tester.tap(find.text('web share'));
      await tester.pumpAndSettle();
      expect(sharedText, 'Join my group Crew on Tio Minuter with code K7MPX2QA');
      expect(sharedUrl, endsWith('/groups/join?code=K7MPX2QA'));
      expect(copied, isNull);
      await tester.tap(find.text('no share'));
      await tester.pumpAndSettle();
      expect(copied, 'Join my group Crew on Tio Minuter with code K7MPX2QA ${joinLink('K7MPX2QA')}');
      expect(find.textContaining('Invite copied'), findsOneWidget);
    });
  });

  group('join', () {
    testWidgets('a shared link arrives filled: the group\'s name, Join, then the group', (tester) async {
      kit.codes.add('K7MPX2QA');
      await kit.service.create(kit.user('bob', 'Bob'), 'Bob\'s crew');
      final router = await routed(tester, Routes.groupJoinCode('k7mp-x2qa'));
      expect(find.text('You\'re about to join Bob\'s crew.'), findsOneWidget);
      await tester.tap(find.text('Join'));
      await settleAll(tester);
      expect(at(router), startsWith('/groups/group-'));
      expect(kit.groups.index['ana'], hasLength(1));
    });

    testWidgets('ambiguous characters, an unknown code, already a member: Android\'s messages (Spanish)', (
      tester,
    ) async {
      final router = await routed(tester, Routes.groupJoin);
      await tester.enterText(find.byType(TextField), 'K7MPX2Q0');
      await settleAll(tester);
      expect(find.text('Codes never use 0, O, 1, I or L.'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'ZZZZZZZZ');
      await settleAll(tester);
      expect(find.textContaining('doesn\'t match any group'), findsOneWidget);
      final mine = await kit.groupOf(ana, []);
      await tester.enterText(find.byType(TextField), mine.inviteCode);
      await settleAll(tester);
      await tester.tap(find.text('Join'));
      await settleAll(tester);
      expect(find.text('You\'re already in that group.'), findsOneWidget);
      expect(at(router), Routes.groupJoin);
    });
  });

  group('the detail', () {
    testWidgets('a member leaves after the confirmation; back to Groups with the message', (tester) async {
      final group = await kit.groupOf(kit.user('bob', 'Bob'), [ana]);
      final router = await routed(tester, Routes.group(group.id));
      expect(find.text(group.inviteCode), findsOneWidget, reason: 'members share the code too');
      expect(find.byTooltip('Group options'), findsNothing, reason: 'not the owner');
      await tester.ensureVisible(find.text('Leave group'));
      await tester.tap(find.text('Leave group'));
      await settleAll(tester);
      expect(find.text('Leave ${group.name}?'), findsOneWidget);
      await tester.tap(find.text('Leave'));
      await settleAll(tester);
      expect(at(router), Routes.groups);
      expect(find.text('You left the group.'), findsOneWidget);
      expect(kit.groups.memberDocs[group.id]!.any((m) => m.uid == 'ana'), isFalse);
    });

    testWidgets('the owner: transfer before leaving; rename; a new code; remove a member; delete in two steps', (
      tester,
    ) async {
      final group = await kit.groupOf(ana, [kit.user('bob', 'Bob')]);
      final router = await routed(tester, Routes.group(group.id));

      await tester.tap(find.byTooltip('Group options'));
      await settleAll(tester);
      await tester.tap(find.text('Rename'));
      await settleAll(tester);
      await tester.enterText(find.byType(TextField).last, 'Night crew');
      await tester.tap(find.text('Save'));
      await settleAll(tester);
      expect(kit.groups.groups[group.id]!.name, 'Night crew');
      expect(find.text('Group renamed.'), findsOneWidget);
      clearMessages(tester);

      await tester.tap(find.byTooltip('Group options'));
      await settleAll(tester);
      await tester.tap(find.text('New invite code'));
      await settleAll(tester);
      expect(find.text('Create a new invite code?'), findsOneWidget);
      await tester.tap(find.text('New code'));
      await settleAll(tester);
      expect(kit.groups.groups[group.id]!.inviteCode, isNot(group.inviteCode));
      expect(find.textContaining('New code: '), findsOneWidget);
      clearMessages(tester);

      await tester.tap(find.byTooltip('Options for Bob'));
      await settleAll(tester);
      await tester.tap(find.text('Remove from group'));
      await settleAll(tester);
      expect(find.text('Remove Bob?'), findsOneWidget);
      await tester.tap(find.text('Remove'));
      await settleAll(tester);
      expect(find.text('Bob was removed.'), findsOneWidget);
      expect(kit.groups.memberDocs[group.id]!.map((m) => m.uid), ['ana']);

      await tester.tap(find.byTooltip('Group options'));
      await settleAll(tester);
      await tester.tap(find.text('Delete group'));
      await settleAll(tester);
      expect(find.text('Delete Night crew?'), findsOneWidget);
      await tester.tap(find.text('Continue'));
      await settleAll(tester);
      expect(find.text('This can\'t be undone'), findsOneWidget);
      await tester.tap(find.text('Delete group').last);
      await settleAll(tester);
      expect(at(router), Routes.groups);
      expect(kit.groups.groups, isEmpty);
    });

    testWidgets('an owner leaving with members picks the next owner (transfer and leave)', (tester) async {
      final group = await kit.groupOf(ana, [kit.user('bob', 'Bob')]);
      await routed(tester, Routes.group(group.id));
      await tester.ensureVisible(find.text('Leave group'));
      await tester.tap(find.text('Leave group'));
      await settleAll(tester);
      expect(find.text('Choose the new owner'), findsOneWidget);
      expect(find.textContaining('Choose who will own it before you leave'), findsOneWidget);
      await tester.tap(find.text('Bob').last);
      await settleAll(tester);
      expect(find.text('Make Bob the owner?'), findsOneWidget);
      await tester.tap(find.text('Transfer and leave'));
      await settleAll(tester);
      expect(kit.groups.groups[group.id]!.ownerUid, 'bob');
    });
  });

  group('the feed', () {
    testWidgets('my post: Delete (asked first); another\'s: Hide for me, and Delete for the owner', (tester) async {
      final group = await kit.groupOf(ana, [kit.user('bob', 'Bob')]);
      kit
        ..post(group.id, 'mine', 'ana')
        ..post(group.id, 'theirs', 'bob');
      tester.view.physicalSize = const Size(600, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        wrap(
          SizedBox(
            height: 1500,
            child: FeedTab(uid: 'ana', groupId: group.id, isOwner: true),
          ),
        ),
      );
      await settleAll(tester);
      await tester.tap(find.byTooltip('Options for the workout of Bob'));
      await settleAll(tester);
      expect(find.text('Hide for me'), findsOneWidget);
      expect(find.text('Delete'), findsOneWidget, reason: 'the owner');
      await tester.tap(find.text('Hide for me'));
      await settleAll(tester);
      expect(find.text('Post hidden on this device'), findsOneWidget);
      expect(kit.service.hiddenPosts('ana'), {'${group.id}/theirs'});
      await tester.tap(find.byTooltip('Options for the workout of Ana'));
      await settleAll(tester);
      expect(find.text('Hide for me'), findsNothing);
      await tester.tap(find.text('Delete'));
      await settleAll(tester);
      expect(find.text('Delete your post?'), findsOneWidget);
      await tester.tap(find.text('Delete').last);
      await settleAll(tester);
      expect(kit.groups.posts[group.id]!.map((p) => p.id), ['theirs']);
    });
  });

  group('the banner on My week', () {
    testWidgets('"Bob already trained today. Your turn 💪", tap opens the group, X hides it for today', (tester) async {
      final group = await kit.groupOf(ana, [kit.user('bob', 'Bob')]);
      final bob = kit.groups.memberDocs[group.id]!.firstWhere((m) => m.uid == 'bob');
      kit.groups.memberDocs[group.id] = [
        kit.groups.memberDocs[group.id]!.firstWhere((m) => m.uid == 'ana'),
        GroupMember(
          uid: 'bob',
          displayName: 'Bob',
          photoUrl: null,
          summary: MemberSummary(
            weekStartLocal: kit.training.monday,
            workoutsThisWeek: 1,
            weeklyGoal: 3,
            streakWeeks: 0,
            daysThisWeek: const {},
            lastTrainedLocal: kit.training.today,
          ),
          joinedAtMillis: bob.joinedAtMillis,
        ),
      ];
      await tester.pumpWidget(wrap(TrainedTodayCard(service: kit.service, user: ana, iTrainedToday: false)));
      await settleAll(tester);
      expect(find.text('Bob already trained today. Your turn 💪'), findsOneWidget);
      await tester.tap(find.byTooltip('Hide for today'));
      await settleAll(tester);
      expect(find.textContaining('already trained today'), findsNothing);
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(wrap(TrainedTodayCard(service: kit.service, user: ana, iTrainedToday: false)));
      await settleAll(tester);
      expect(find.textContaining('already trained today'), findsNothing, reason: 'dismissed for today');
    });

    testWidgets('names: two and more, in Spanish', (tester) async {
      final l10n = lookupAppLocalizations(const Locale('es'));
      expect(l10n.trainedTodayTwo('Ana', 'Luis'), 'Ana y Luis ya cumplieron hoy, te toca 💪');
      expect(l10n.trainedTodayMore('Ana', 'Luis', 3), 'Ana, Luis y 3 más ya cumplieron hoy, te toca 💪');
    });
  });

  group('Delete account', () {
    testWidgets('while deleting: the progress, nothing else to tap', (tester) async {
      await tester.pumpWidget(wrap(AccountContent(user: ana, onSignOut: () {}, onDelete: () {}, isDeleting: true)));
      expect(find.text('Deleting your account…'), findsOneWidget);
      expect(find.text('Delete account'), findsNothing);
    });

    test('every outcome has Android\'s message (English and Spanish)', () {
      final en = lookupAppLocalizations(const Locale('en'));
      final es = lookupAppLocalizations(const Locale('es'));
      expect(deleteResultText(DeleteAccountResult.groupsFailed, en), startsWith('Couldn\'t leave your groups'));
      expect(deleteResultText(DeleteAccountResult.serverDataFailed, en), startsWith('Couldn\'t delete your backed-up'));
      expect(
        deleteResultText(DeleteAccountResult.accountNotDeleted, en),
        startsWith('Your backed-up data was deleted'),
      );
      expect(deleteResultText(DeleteAccountResult.deleted, es), 'Cuenta eliminada.');
      expect(deleteResultText(DeleteAccountResult.wrongAccount, es), startsWith('Elige la cuenta de Google'));
      expect(es.deleteDialogMessageWeb, contains('Primero confirmarás con Google'));
    });
  });

  test('the profile doc: Android\'s limits (name ≤ 500, an https photo or none)', () {
    final long = WebUser(uid: 'u', displayName: 'x' * 600, photoUrl: 'http://insecure/p.png');
    final profile = profileOf(long);
    expect(profile.displayName!.length, 500);
    expect(profile.photoUrl, isNull);
    expect(profileOf(const WebUser(uid: 'u', photoUrl: 'https://lh3.googleusercontent.com/a')).photoUrl, isNotNull);
  });
}

/// Lets the services' real async work and the frames finish.
Future<void> settleAll(WidgetTester tester) async {
  for (var i = 0; i < 4; i++) {
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
    await tester.pumpAndSettle();
  }
}

/// SnackBars queue: the next message shows only once the current one is gone.
void clearMessages(WidgetTester tester) =>
    ScaffoldMessenger.of(tester.element(find.byType(Scaffold).first)).clearSnackBars();
