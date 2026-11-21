/// Groups and their feed as the web reads them (docs/firestore-model.md "Groups", "Feed").
/// Ported from Android: `domain/groups/GroupModels.kt` (`MemberSummary`, `GroupMember`),
/// `domain/groups/GroupRanking.kt` (the ranking), `domain/groups/FeedModels.kt` (`Reaction`,
/// `FeedPost`, `ReactionRules`, `FeedLimits`) and the week filter of
/// `ui/groups/feed/GroupFeedViewModel.kt`.
library;

import 'dart:math';
import 'dart:typed_data';

import 'calendar_dates.dart';
import 'kotlin_text.dart';
import 'training.dart';

/// A group the reader is a member of (`groups/{groupId}`).
class Group {
  const Group({
    required this.id,
    required this.name,
    required this.ownerUid,
    required this.memberCount,
    this.inviteCode = '',
  });

  final String id;
  final String name;
  final String ownerUid;
  final int memberCount;

  /// The code friends join with (`inviteCodes/{code}`).
  final String inviteCode;
}

/// A member's public weekly summary. [daysThisWeek]: day of week (1 = Monday … 7 = Sunday) → the
/// distinct sources of that day.
class MemberSummary {
  const MemberSummary({
    required this.weekStartLocal,
    required this.workoutsThisWeek,
    required this.weeklyGoal,
    required this.streakWeeks,
    required this.daysThisWeek,
    required this.lastTrainedLocal,
  });

  final String weekStartLocal;
  final int workoutsThisWeek;
  final int weeklyGoal;
  final int streakWeeks;
  final Map<int, List<TrainingSource>> daysThisWeek;
  final String? lastTrainedLocal;
}

/// One member doc of a group (`groups/{groupId}/members/{uid}`).
class GroupMember {
  const GroupMember({
    required this.uid,
    required this.displayName,
    required this.photoUrl,
    required this.summary,
    this.joinedAtMillis,
  });

  final String uid;
  final String displayName;
  final String? photoUrl;
  final MemberSummary summary;

  /// When they joined (server time); null while pending. The heir of an owner leaving is the oldest.
  final int? joinedAtMillis;
}

/// One line of the ranking.
class RankedMember {
  const RankedMember({
    required this.position,
    required this.member,
    required this.workouts,
    required this.compliance,
    required this.isCurrentWeek,
    required this.isMe,
  });

  final int position;
  final GroupMember member;

  /// This week's counted workouts (0 for a summary of another week).
  final int workouts;

  /// min(workouts / goal, 1).
  final double compliance;

  /// False: the summary is from another week ("Not updated this week").
  final bool isCurrentWeek;
  final bool isMe;

  RankedMember withPosition(int value) => RankedMember(
    position: value,
    member: member,
    workouts: workouts,
    compliance: compliance,
    isCurrentWeek: isCurrentWeek,
    isMe: isMe,
  );
}

/// Kotlin's `String.CASE_INSENSITIVE_ORDER`, which Android's ranking uses: char by char, comparing
/// the upper-case forms and then the lower-case forms of each UTF-16 unit.
int caseInsensitiveCompare(String a, String b) {
  final length = a.length < b.length ? a.length : b.length;
  for (var i = 0; i < length; i++) {
    var c1 = a.codeUnitAt(i);
    var c2 = b.codeUnitAt(i);
    if (c1 == c2) continue;
    c1 = _upper(c1);
    c2 = _upper(c2);
    if (c1 == c2) continue;
    c1 = _lower(c1);
    c2 = _lower(c2);
    if (c1 != c2) return c1 - c2;
  }
  return a.length - b.length;
}

// Character-level case mapping, as Java's Character.toUpperCase(char): a mapping that is not one
// unit (such as "ß" → "SS") leaves the unit as it is
int _upper(int unit) {
  final mapped = String.fromCharCode(unit).toUpperCase();
  return mapped.length == 1 ? mapped.codeUnitAt(0) : unit;
}

int _lower(int unit) {
  final mapped = String.fromCharCode(unit).toLowerCase();
  return mapped.length == 1 ? mapped.codeUnitAt(0) : unit;
}

/// The ranking every member computes (never stored; Android's `GroupRanking`).
abstract final class GroupRanking {
  /// Order: compliance (desc), then streak (desc), then name (case-insensitive), then uid, so the
  /// order is stable. [currentWeekStart] is the reader's Monday: a summary of another week counts 0.
  static List<RankedMember> rank(List<GroupMember> members, String currentWeekStart, String? myUid) {
    final lines = [
      for (final member in members)
        () {
          final current = member.summary.weekStartLocal == currentWeekStart;
          final workouts = current ? member.summary.workoutsThisWeek : 0;
          final goal = member.summary.weeklyGoal < 1 ? 1 : member.summary.weeklyGoal;
          final ratio = workouts / goal;
          return RankedMember(
            position: 0,
            member: member,
            workouts: workouts,
            compliance: ratio > 1 ? 1.0 : ratio,
            isCurrentWeek: current,
            isMe: member.uid == myUid,
          );
        }(),
    ];
    lines.sort((a, b) {
      final byCompliance = b.compliance.compareTo(a.compliance);
      if (byCompliance != 0) return byCompliance;
      final byStreak = b.member.summary.streakWeeks.compareTo(a.member.summary.streakWeeks);
      if (byStreak != 0) return byStreak;
      final byName = caseInsensitiveCompare(a.member.displayName, b.member.displayName);
      if (byName != 0) return byName;
      return a.member.uid.compareTo(b.member.uid);
    });
    return [for (var i = 0; i < lines.length; i++) lines[i].withPosition(i + 1)];
  }
}

/// The four reactions; [code] is what the post stores.
enum Reaction {
  strong('strong', '💪'),
  fire('fire', '🔥'),
  clap('clap', '👏'),
  raised('raised', '🙌');

  const Reaction(this.code, this.emoji);

  final String code;
  final String emoji;

  static Reaction? fromCode(Object? code) {
    for (final reaction in values) {
      if (reaction.code == code) return reaction;
    }
    return null;
  }
}

/// Android's `ReactionRules`.
abstract final class ReactionRules {
  /// Tapping [tapped]: the same reaction again removes it, another one replaces it.
  static Reaction? toggle(Reaction? current, Reaction tapped) => current == tapped ? null : tapped;

  /// The post's reactions with mine set to [mine] (null: removed).
  static Map<String, Reaction> withMine(Map<String, Reaction> reactions, String myUid, Reaction? mine) {
    final result = Map<String, Reaction>.of(reactions);
    if (mine == null) {
      result.remove(myUid);
    } else {
      result[myUid] = mine;
    }
    return result;
  }

  /// How many members reacted with each reaction (absent: none).
  static Map<Reaction, int> counts(Map<String, Reaction> reactions) {
    final counts = <Reaction, int>{};
    for (final reaction in reactions.values) {
      counts[reaction] = (counts[reaction] ?? 0) + 1;
    }
    return counts;
  }
}

abstract final class FeedLimits {
  /// Posts per page of the week's feed (Android's `FeedLimits.PAGE_SIZE`).
  static const int pageSize = 20;

  /// A post lives 8 days (the rules' expiration; "Hide for me" forgets a post after it).
  static const int postLifetimeMillis = 8 * 24 * 60 * 60 * 1000;
}

/// A post as members read it (`groups/{groupId}/posts/{postId}`).
class FeedPost {
  const FeedPost({
    required this.id,
    required this.authorUid,
    required this.authorName,
    required this.authorPhotoUrl,
    required this.source,
    required this.title,
    required this.titleKey,
    required this.activityType,
    required this.durationSeconds,
    required this.dateLocal,
    required this.weekStartLocal,
    required this.createdAtMillis,
    required this.reactions,
    required this.thumb,
  });

  final String id;
  final String authorUid;
  final String authorName;
  final String? authorPhotoUrl;
  final TrainingSource source;
  final String title;
  final String? titleKey;
  final ActivityType? activityType;
  final int? durationSeconds;
  final String dateLocal;
  final String weekStartLocal;

  /// The server time; null while a write is pending.
  final int? createdAtMillis;
  final Map<String, Reaction> reactions;
  final Uint8List? thumb;

  FeedPost withReactions(Map<String, Reaction> value) => FeedPost(
    id: id,
    authorUid: authorUid,
    authorName: authorName,
    authorPhotoUrl: authorPhotoUrl,
    source: source,
    title: title,
    titleKey: titleKey,
    activityType: activityType,
    durationSeconds: durationSeconds,
    dateLocal: dateLocal,
    weekStartLocal: weekStartLocal,
    createdAtMillis: createdAtMillis,
    reactions: value,
    thumb: thumb,
  );
}

/// The posts the feed shows: the reader's current week only (defensive: the query already
/// filters), newest first, with my pending reactions applied.
List<FeedPost> currentWeekPosts(
  List<FeedPost> posts, {
  required String weekStart,
  required String? myUid,
  Map<String, Reaction?> pendingReactions = const {},
}) {
  final shown = [
    for (final post in posts)
      if (post.weekStartLocal == weekStart)
        myUid != null && pendingReactions.containsKey(post.id)
            ? post.withReactions(ReactionRules.withMine(post.reactions, myUid, pendingReactions[post.id]))
            : post,
  ];
  // A pending post of mine (no server time yet) is the newest
  shown.sort((a, b) => (b.createdAtMillis ?? 1 << 52).compareTo(a.createdAtMillis ?? 1 << 52));
  return shown;
}

// ---------------------------------------------------------------------------
// Managing groups (Android's InviteCodes, GroupLimits, GroupError, TrainedTodayRule,
// GroupAccountCleaner.oldestOtherMember)
// ---------------------------------------------------------------------------

/// Invite codes: 8 characters, uppercase, without the ambiguous 0 O 1 I L (the rules check the same).
abstract final class InviteCodes {
  static const int length = 8;
  static const String alphabet = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

  static String generate(Random random) =>
      String.fromCharCodes(List.generate(length, (_) => alphabet.codeUnitAt(random.nextInt(alphabet.length))));

  /// What the person typed, uppercased, without spaces or dashes (still possibly invalid).
  static String normalize(String input) => input.toUpperCase().replaceAll(RegExp(r'[\s-]'), '');

  /// Characters a code never uses (0, O, 1, I, L, anything else).
  static bool hasInvalidCharacters(String normalized) => normalized.split('').any((c) => !alphabet.contains(c));

  static bool isValid(String normalized) => normalized.length == length && !hasInvalidCharacters(normalized);
}

/// Product limits of groups (the rules enforce the same ones).
abstract final class GroupRules {
  static const int maxMembers = 20;
  static const int maxGroups = 5;
  static const int nameMaxLength = 40;

  /// 1–40 characters after trimming, one line (Android's `GroupLimits.normalizeName`).
  static String? normalizeName(String input) {
    final name = kotlinTrim(input);
    if (name.isEmpty || name.length > nameMaxLength || name.contains('\n') || name.contains('\r')) return null;
    return name;
  }
}

/// Why a group operation failed (Android's `GroupError`); the screens map each to a message.
enum GroupError {
  network,
  goalRequired,
  invalidCode,
  groupFull,
  alreadyMember,
  groupLimit,
  invalidName,

  /// The group or my membership is gone (deleted, or I was removed).
  notFound,

  /// The rules refused it (logged without personal data, never retried in a loop).
  permissionDenied,
  failed,
}

class GroupException implements Exception {
  const GroupException(this.error);

  final GroupError error;

  @override
  String toString() => 'GroupException(${error.name})';
}

/// What `inviteCodes/{code}` tells someone about to join.
class InvitePreview {
  const InvitePreview({required this.code, required this.groupId, required this.groupName});

  final String code;
  final String groupId;
  final String groupName;
}

/// One of my groups on the list, with my place in this week's ranking.
class MyGroup {
  const MyGroup({required this.group, required this.myPosition, required this.isOwner});

  final Group group;
  final int? myPosition;
  final bool isOwner;
}

/// A group just created, with its code to share.
class CreatedGroup {
  const CreatedGroup({required this.id, required this.name, required this.code});

  final String id;
  final String name;
  final String code;
}

/// The members of one of my groups, as My week fetched them for the banner.
class GroupMembers {
  const GroupMembers({required this.groupId, required this.members});

  final String groupId;
  final List<GroupMember> members;
}

/// "Already trained today": who trained, and in which of my groups.
class TrainedTodayBanner {
  const TrainedTodayBanner({required this.names, required this.groupIds});

  final List<String> names;
  final List<String> groupIds;
}

/// Android's `TrainedTodayRule`: shown to a signed-in person without a counted workout today, not
/// dismissed today, when another member of their groups trained today by their own summary (this
/// week's). Names: distinct people (by uid) across all groups, me excluded, sorted by name.
abstract final class TrainedTodayRule {
  static TrainedTodayBanner? banner({
    required String? myUid,
    required String today,
    required bool iTrainedToday,
    required String? dismissedOn,
    required List<GroupMembers> groups,
  }) {
    if (myUid == null || iTrainedToday || dismissedOn == today) return null;
    final weekStart = CalendarDates.mondayOf(today);
    final byUid = <String, GroupMember>{};
    final groupIds = <String>[];
    for (final group in groups) {
      final trainers = group.members.where(
        (m) => m.uid != myUid && m.summary.lastTrainedLocal == today && m.summary.weekStartLocal == weekStart,
      );
      if (trainers.isNotEmpty) groupIds.add(group.groupId);
      for (final trainer in trainers) {
        byUid.putIfAbsent(trainer.uid, () => trainer);
      }
    }
    if (byUid.isEmpty) return null;
    final names = [for (final m in byUid.values) m.displayName]..sort(caseInsensitiveCompare);
    return TrainedTodayBanner(names: names, groupIds: groupIds);
  }
}

/// The member an owner's group goes to when they delete their account: the oldest other member by
/// joinedAt (a pending time last), then by uid (Android's `GroupAccountCleaner.oldestOtherMember`).
GroupMember? oldestOtherMember(String uid, List<GroupMember> members) {
  final others = members.where((m) => m.uid != uid).toList()
    ..sort((a, b) {
      // A pending time (null) sorts last, as Android's Long.MAX_VALUE (no 64-bit literal on the web)
      final at = a.joinedAtMillis;
      final bt = b.joinedAtMillis;
      final byTime = at == null ? (bt == null ? 0 : 1) : (bt == null ? -1 : at.compareTo(bt));
      return byTime != 0 ? byTime : a.uid.compareTo(b.uid);
    });
  return others.firstOrNull;
}
