import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:tiominuter_web/app/app.dart';
import 'package:tiominuter_web/app/firebase_web_options.dart';
import 'package:tiominuter_web/app/services.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/domain/calendar_dates.dart';
import 'package:tiominuter_web/firebase_options.dart';
import 'package:tiominuter_web/ui/session/session_bloc.dart';

import '../support/fakes.dart';

/// Mobile sign-in (a redirect): the app waits for the redirect's result before routing, says
/// "Signing in…" meanwhile, goes to the app when it signed in and explains a failure; the
/// authDomain is the Hosting domain; Hosting's SPA rewrite leaves the reserved /__/ paths alone.
void main() {
  const me = WebUser(uid: 'me', displayName: 'Alex Rivera', email: 'alex@example.test');

  FakeAuthRepository returning() => FakeAuthRepository()
    ..returningFromRedirect = true
    ..redirectResult = Completer<SignInOutcome?>()
    ..redirectUser = me;

  group('the session', () {
    test('waits for the redirect result before listening to the user (no "signed out" moment)', () async {
      final auth = returning();
      final bloc = SessionBloc(auth)..add(const SessionStarted());
      await Future<void>.delayed(Duration.zero);
      expect(bloc.state.isLoading, isTrue);
      expect(bloc.state.isCompletingRedirect, isTrue);
      expect(auth.userListensBeforeRedirect, 0);
      auth.redirectResult!.complete(SignInOutcome.signedIn);
      await Future<void>.delayed(const Duration(milliseconds: 10));
      expect(bloc.state.isLoading, isFalse);
      expect(bloc.state.isCompletingRedirect, isFalse);
      expect(bloc.state.user?.uid, 'me');
      expect(bloc.state.redirectError, isNull);
      await bloc.close();
    });

    test('a redirect that came back without a session: the error, signed out; trying again clears it', () async {
      final auth = returning();
      final bloc = SessionBloc(auth)..add(const SessionStarted());
      auth.redirectResult!.complete(SignInOutcome.redirectIncomplete);
      await Future<void>.delayed(const Duration(milliseconds: 10));
      expect(bloc.state.isLoading, isFalse);
      expect(bloc.state.user, isNull);
      expect(bloc.state.redirectError, SignInOutcome.redirectIncomplete);
      auth.nextSignIn = SignInOutcome.cancelled;
      bloc.add(const SignInClicked());
      await Future<void>.delayed(const Duration(milliseconds: 10));
      expect(bloc.state.redirectError, isNull);
      await bloc.close();
    });

    test('no redirect (a normal visit, a restored session): straight to the user', () async {
      final auth = FakeAuthRepository();
      final bloc = SessionBloc(auth)..add(const SessionStarted());
      await Future<void>.delayed(const Duration(milliseconds: 10));
      expect(bloc.state.isCompletingRedirect, isFalse);
      expect(bloc.state.isLoading, isFalse);
      expect(bloc.state.user, isNull);
      await bloc.close();
    });
  });

  group('the app', () {
    Future<void> open(WidgetTester tester, FakeAuthRepository auth, {Locale? locale}) async {
      tester.view.physicalSize = const Size(400, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      if (locale != null) tester.platformDispatcher.localesTestValue = [locale];
      addTearDown(tester.platformDispatcher.clearLocalesTestValue);
      await tester.pumpWidget(
        TioMinuterWebApp(
          services: AppServices(
            auth: auth,
            userData: FakeUserDataRepository(),
            groups: FakeGroupsRepository(),
            content: FakeContentRepository(),
            dates: LocalDates(now: () => DateTime(2026, 10, 22, 10)),
          ),
        ),
      );
      await tester.pump();
    }

    String location(WidgetTester tester) => GoRouterLocation.of(tester);

    testWidgets('"Signing in…" while the redirect resolves, never the landing page; then the app', (tester) async {
      final auth = returning();
      await open(tester, auth);
      expect(find.text('Signing in…'), findsOneWidget);
      expect(find.text('Sign in with Google'), findsNothing, reason: 'no flash of the landing page');
      auth.redirectResult!.complete(SignInOutcome.signedIn);
      await tester.pumpAndSettle();
      expect(location(tester), Routes.week);
      expect(find.text('Signing in…'), findsNothing);
    });

    testWidgets('a failed redirect: the landing page explains it (English and Spanish)', (tester) async {
      for (final (locale, text) in [
        (const Locale('en'), 'Sign-in didn\'t finish in this browser'),
        (const Locale('es'), 'El inicio de sesión no terminó en este navegador'),
      ]) {
        final auth = returning();
        await open(tester, auth, locale: locale);
        auth.redirectResult!.complete(SignInOutcome.redirectIncomplete);
        await tester.pumpAndSettle();
        expect(location(tester), Routes.landing);
        expect(find.textContaining(text), findsOneWidget);
        await tester.pumpWidget(const SizedBox());
      }
    });

    testWidgets('other failures say so too (no network, a generic failure)', (tester) async {
      final auth = returning();
      await open(tester, auth);
      auth.redirectResult!.complete(SignInOutcome.noNetwork);
      await tester.pumpAndSettle();
      expect(find.textContaining('No internet connection'), findsOneWidget);
    });
  });

  group('authDomain', () {
    test('the page\'s own Hosting domain, never firebaseapp.com from web.app; web.app elsewhere', () {
      expect(webFirebaseOptions('tiominuter-e43f7.web.app').authDomain, 'tiominuter-e43f7.web.app');
      expect(webFirebaseOptions('tiominuter-e43f7.firebaseapp.com').authDomain, 'tiominuter-e43f7.firebaseapp.com');
      expect(webFirebaseOptions('localhost').authDomain, 'tiominuter-e43f7.web.app');
      // The rest is the generated options, untouched
      final options = webFirebaseOptions('tiominuter-e43f7.web.app');
      expect(options.apiKey, DefaultFirebaseOptions.web.apiKey);
      expect(options.appId, DefaultFirebaseOptions.web.appId);
      expect(options.projectId, 'tiominuter-e43f7');
    });
  });

  group('Hosting', () {
    final hosting = (jsonDecode(File('../firebase.json').readAsStringSync()) as Map)['hosting'] as Map;
    final rewrites = (hosting['rewrites'] as List).cast<Map<Object?, Object?>>();

    /// Firebase Hosting's glob for the patterns used here: an optional "!", then `/@(a|b)/**`.
    bool matches(String pattern, String path) {
      final negated = pattern.startsWith('!');
      final body = negated ? pattern.substring(1) : pattern;
      final match = RegExp(r'^/@\(([^)]*)\)/\*\*$').firstMatch(body) ?? RegExp(r'^/([^/*]+)/\*\*$').firstMatch(body);
      if (match == null) throw ArgumentError('unsupported pattern $pattern');
      final dirs = match.group(1)!.split('|');
      final inside = dirs.any((dir) => path.startsWith('/$dir/'));
      return negated ? !inside : inside;
    }

    test('the SPA rewrite never catches the reserved /__/ paths nor /content', () {
      final spa = rewrites.where((r) => r['destination'] == '/index.html').single['source']! as String;
      for (final reserved in [
        '/__/auth/handler',
        '/__/auth/iframe',
        '/__/firebase/init.json',
        '/__/firebase/init.js',
      ]) {
        expect(matches(spa, reserved), isFalse, reason: reserved);
      }
      expect(matches(spa, '/content/v1/missing.json'), isFalse);
      for (final route in ['/', '/week', '/train/daily/3', '/groups/g1', '/__x']) {
        expect(matches(spa, route), isTrue, reason: route);
      }
    });
  });
}

/// The router's current location, read from the app.
abstract final class GoRouterLocation {
  static String of(WidgetTester tester) {
    final context = tester.element(find.byType(Scaffold).first);
    return GoRouter.of(context).routerDelegate.currentConfiguration.uri.toString();
  }
}
