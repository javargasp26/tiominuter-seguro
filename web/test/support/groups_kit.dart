import 'package:tiominuter_web/app/account_deletion.dart';
import 'package:tiominuter_web/app/groups_service.dart';
import 'package:tiominuter_web/data/repositories.dart';
import 'package:tiominuter_web/domain/groups.dart';
import 'package:tiominuter_web/domain/training.dart';

import 'fakes.dart';
import 'training_fakes.dart';

/// Groups on the web over fakes: the training kit (my summary, my profile, localStorage, the
/// clock), an in-memory Firestore of groups with the rules' semantics, and the service under test.
class GroupsKit {
  GroupsKit({DateTime? start}) : training = TrainingKit(start: start) {
    training.training.storedGoals[training.monday] = 3;
    service = GroupsService(
      repository: groups,
      training: training.service,
      store: training.store,
      dates: training.dates,
      wallClock: training.wall,
      isOnline: () => online,
      newCode: () => codes.isNotEmpty ? codes.removeAt(0) : 'K7MPX2Q${'ABCDEFGH'[_codes++ % 8]}',
    );
  }

  final TrainingKit training;
  final FakeGroupsRepository groups = FakeGroupsRepository();
  final FakeAccountRepository account = FakeAccountRepository();
  late final GroupsService service;
  bool online = true;

  /// The next invite codes the service draws (then generated ones).
  final List<String> codes = [];
  int _codes = 0;

  WebUser user(String uid, [String? name]) =>
      WebUser(uid: uid, displayName: name ?? uid[0].toUpperCase() + uid.substring(1));

  AccountDeletion deletion(FakeAuthRepository auth) => AccountDeletion(
    auth: auth,
    groups: service,
    account: account,
    training: training.service,
    isOnline: () => online,
  );

  /// [owner] creates a group and every one of [joiners] joins it with its code; returns the group.
  Future<Group> groupOf(WebUser owner, List<WebUser> joiners, {String name = 'Morning crew'}) async {
    final created = await service.create(owner, name);
    final invite = (await service.findInvite(created.code));
    for (final joiner in joiners) {
      await service.join(joiner, invite);
    }
    return groups.groups[created.id]!;
  }

  /// A post of [author] in the group.
  void post(String groupId, String postId, String author) =>
      (groups.posts[groupId] ??= []).add(feedPost(postId, author, week: training.monday, source: TrainingSource.daily));
}
