import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/domain/training.dart';

/// Ports of Android's ranking cases (GroupRulesTest) and reaction rules, plus the feed's
/// current-week filter.
void main() {
  const week = '2026-10-19';

  GroupMember member(String uid, String name, int done, int goal, {int streak = 0, String memberWeek = week}) =>
      GroupMember(
        uid: uid,
        displayName: name,
        photoUrl: null,
        summary: MemberSummary(
          weekStartLocal: memberWeek,
          workoutsThisWeek: done,
          weeklyGoal: goal,
          streakWeeks: streak,
          daysThisWeek: const {},
          lastTrainedLocal: null,
        ),
      );

  group('ranking', () {
    test('orders by compliance, capped at 1, then streak, then name, then uid', () {
      final ranking = GroupRanking.rank(
        [
          member('a', 'Zoe', 1, 4), // 0.25
          member('b', 'ana', 6, 3, streak: 1), // 1 (capped)
          member('c', 'Bea', 3, 3, streak: 2), // 1, longer streak
          member('d', 'Ana', 3, 3, streak: 1), // 1, ties with b: name, case-insensitive, then uid
          member('e', 'Luis', 2, 4), // 0.5
        ],
        week,
        'e',
      );
      expect(ranking.map((line) => line.member.uid), ['c', 'b', 'd', 'e', 'a']);
      expect(ranking.map((line) => line.position), [1, 2, 3, 4, 5]);
      expect(ranking[1].compliance, 1.0);
      expect(ranking[3].isMe, isTrue);
    });

    test('a summary from a previous week counts as 0', () {
      final ranking = GroupRanking.rank(
        [member('old', 'Old', 5, 3, streak: 9, memberWeek: '2026-10-12'), member('new', 'New', 1, 3)],
        week,
        null,
      );
      expect(ranking.map((line) => line.member.uid), ['new', 'old']);
      expect(ranking[1].workouts, 0);
      expect(ranking[1].compliance, 0.0);
      expect(ranking[1].isCurrentWeek, isFalse);
    });

    test('a goal below 1 counts as 1 (never a division by zero)', () {
      expect(GroupRanking.rank([member('a', 'A', 1, 0)], week, null).single.compliance, 1.0);
    });

    test("names compare as Kotlin's CASE_INSENSITIVE_ORDER", () {
      expect(caseInsensitiveCompare('ana', 'Ana'), 0);
      expect(caseInsensitiveCompare('Ana', 'bea') < 0, isTrue);
      expect(caseInsensitiveCompare('Zoe', 'ana') > 0, isTrue);
      expect(caseInsensitiveCompare('Ana', 'Anabel') < 0, isTrue);
      // Character by character: "Á" sorts after every ASCII letter, as on Android
      expect(caseInsensitiveCompare('Álvaro', 'Zoe') > 0, isTrue);
      expect(caseInsensitiveCompare('ÁLVARO', 'álvaro'), 0);
      // A one-to-many mapping ("ß" → "SS") is not applied, as Java's Character.toUpperCase
      expect(caseInsensitiveCompare('ß', 'ss') > 0, isTrue);
    });
  });

  group('reactions', () {
    test('tapping sets, the same again removes, another changes', () {
      expect(ReactionRules.toggle(null, Reaction.fire), Reaction.fire);
      expect(ReactionRules.toggle(Reaction.fire, Reaction.fire), isNull);
      expect(ReactionRules.toggle(Reaction.fire, Reaction.clap), Reaction.clap);
    });

    test('mine changes only my key; counts per reaction', () {
      final reactions = {'ana': Reaction.fire, 'bob': Reaction.fire, 'me': Reaction.clap};
      expect(ReactionRules.withMine(reactions, 'me', Reaction.strong), {
        'ana': Reaction.fire,
        'bob': Reaction.fire,
        'me': Reaction.strong,
      });
      expect(ReactionRules.withMine(reactions, 'me', null), {'ana': Reaction.fire, 'bob': Reaction.fire});
      expect(ReactionRules.counts(reactions), {Reaction.fire: 2, Reaction.clap: 1});
      expect(Reaction.values.map((r) => r.code), ['strong', 'fire', 'clap', 'raised']);
      expect(Reaction.fromCode('wow'), isNull);
    });
  });

  group('the feed shows the current week', () {
    FeedPost post(String id, String weekStart, int? createdAt, {Map<String, Reaction> reactions = const {}}) =>
        FeedPost(
          id: id,
          authorUid: 'ana',
          authorName: 'Ana',
          authorPhotoUrl: null,
          source: TrainingSource.gym,
          title: 'Pull Day',
          titleKey: null,
          activityType: null,
          durationSeconds: 1800,
          dateLocal: weekStart,
          weekStartLocal: weekStart,
          createdAtMillis: createdAt,
          reactions: reactions,
          thumb: null,
        );

    test("only the reader's week, newest first, a pending one on top", () {
      final shown = currentWeekPosts(
        [post('old', week, 1), post('lastWeek', '2026-10-12', 5), post('new', week, 3), post('pending', week, null)],
        weekStart: week,
        myUid: 'me',
      );
      expect(shown.map((p) => p.id), ['pending', 'new', 'old']);
    });

    test('my pending reactions are shown over the server ones', () {
      final shown = currentWeekPosts(
        [
          post('p', week, 1, reactions: {'ana': Reaction.fire, 'me': Reaction.clap}),
        ],
        weekStart: week,
        myUid: 'me',
        pendingReactions: {'p': null},
      );
      expect(shown.single.reactions, {'ana': Reaction.fire});
    });
  });
}
