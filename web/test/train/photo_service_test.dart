import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:tiominuter_web/app/training_service.dart';
import 'package:tiominuter_web/domain/photos.dart';
import 'package:tiominuter_web/domain/training.dart';
import 'package:tiominuter_web/platform/photos.dart';

import '../support/fixtures.dart';
import '../support/photo_fakes.dart';
import '../support/training_fakes.dart';

/// Photos in this browser and their thumbnails in the feed: storage per account (save, read,
/// delete with the entry, sign-out, eviction, a full storage), the post's `thumb` when shared and
/// its update or removal when the photo changes later (one write per group posted).
void main() {
  late TrainingKit kit;

  setUp(() {
    kit = TrainingKit();
    kit.training.storedGoals[kit.monday] = 3;
    kit.sharing.groupIds = ['g1', 'g2'];
    kit.service.setFeedIntroSeen(alex.uid);
  });

  Future<ProcessedPhoto> processed({int width = 600, int height = 400}) => kit.deps.photoProcessor.process(
    PickedPhoto(
      bytes: cameraJpeg(width: width, height: height),
    ),
  );

  /// A counted workout of this week, recorded (and posted to both groups).
  Future<TrainingEntry> workout({bool share = true}) async {
    final entry = await kit.service.recordWorkout(
      user: alex,
      source: TrainingSource.daily,
      refId: 'daily:3',
      title: 'Day 3',
      durationSeconds: 1200,
      share: share,
    );
    await settle();
    await settle();
    return entry;
  }

  group('storage', () {
    test('saved per account and entry; read back; another account sees nothing', () async {
      final entry = await workout();
      final photo = await processed();
      await kit.service.attachPhoto(alex, entry.id, photo);
      final stored = (await kit.service.photoOf(alex.uid, entry.id))!;
      expect(stored.full, photo.full);
      expect(stored.thumb, photo.thumb);
      expect(stored.savedAt, kit.wall());
      expect(await kit.service.photoOf('someone-else', entry.id), isNull);
    });

    test('deleting the entry deletes its photo', () async {
      final entry = await workout();
      await kit.service.attachPhoto(alex, entry.id, await processed());
      await kit.service.deleteEntry(alex, entry);
      await settle();
      expect(await kit.service.photoOf(alex.uid, entry.id), isNull);
    });

    test('sign-out clears this account\'s photos only', () async {
      final entry = await workout();
      await kit.service.attachPhoto(alex, entry.id, await processed());
      await kit.photos.save('other', 'x', StoredPhoto(full: Uint8List(3), thumb: null, savedAt: 0));
      await kit.service.clearLocalData(alex.uid);
      expect(await kit.service.photoOf(alex.uid, entry.id), isNull);
      expect(await kit.service.photoOf('other', 'x'), isNotNull);
    });

    test('evicted by the browser or unreadable: simply no photo', () async {
      final entry = await workout();
      await kit.service.attachPhoto(alex, entry.id, await processed());
      kit.photos.evict();
      expect(await kit.service.photoOf(alex.uid, entry.id), isNull);
      final broken = TrainingService(
        training: kit.training,
        sharing: kit.sharing,
        store: kit.store,
        dates: kit.dates,
        texts: EnglishTrainingTexts(),
        photos: BrokenPhotoStore(),
      );
      expect(await broken.photoOf(alex.uid, entry.id), isNull);
    });

    test('a full storage: a clear error, nothing kept, the posts untouched', () async {
      final entry = await workout();
      kit.photos.failWith = PhotoError.noSpace;
      await expectLater(
        kit.service.attachPhoto(alex, entry.id, await processed()),
        throwsA(isA<PhotoException>().having((e) => e.error, 'error', PhotoError.noSpace)),
      );
      expect(await kit.service.photoOf(alex.uid, entry.id), isNull);
      expect(kit.sharing.writes.where((w) => w.startsWith('thumb')), isEmpty);
    });
  });

  group('the feed', () {
    test('a photo added before posting: the post carries its thumbnail (≤ 65,536 bytes), as the fixture', () async {
      final entry = await kit.service.recordWorkout(
        user: alex,
        source: TrainingSource.daily,
        refId: 'daily:3',
        title: 'Day 3',
        durationSeconds: 1200,
        share: false,
      );
      final photo = await processed(width: 2000, height: 1500);
      await kit.service.attachPhoto(alex, entry.id, photo);
      await kit.service.shareEntry(alex, entry);
      await settle();
      for (final group in ['g1', 'g2']) {
        final post = kit.sharing.posts[group]![entry.id]!;
        expect(post['thumb'], photo.thumb);
        expect((post['thumb']! as Uint8List).length, lessThanOrEqualTo(PhotoLimits.thumbMaxBytes));
        expect(JpegMetadata.hasMetadata(post['thumb']! as Uint8List), isFalse);
        // The fixture's fields (Android's post), the key and the thumbnail
        expect(post.keys.toSet(), {...SharedFixtures.raw('post').keys, 'titleKey', 'thumb'});
      }
    });

    test('added, changed and removed after posting: one update of thumb per group posted', () async {
      final entry = await workout();
      expect(kit.sharing.posts['g1']![entry.id]!.containsKey('thumb'), isFalse);
      final first = await processed();
      await kit.service.attachPhoto(alex, entry.id, first);
      await settle();
      expect(kit.sharing.writes.where((w) => w.startsWith('thumb')), [
        'thumb g1 ${entry.id} set',
        'thumb g2 ${entry.id} set',
      ]);
      expect(kit.sharing.posts['g2']![entry.id]!['thumb'], first.thumb);

      final second = await processed(width: 400, height: 600);
      await kit.service.attachPhoto(alex, entry.id, second);
      await settle();
      expect(kit.sharing.posts['g1']![entry.id]!['thumb'], second.thumb);

      await kit.service.removePhoto(alex, entry.id);
      await settle();
      expect(kit.sharing.posts['g1']![entry.id]!.containsKey('thumb'), isFalse);
      expect(kit.sharing.writes.where((w) => w.startsWith('thumb')), hasLength(6));
      expect(await kit.service.photoOf(alex.uid, entry.id), isNull);
    });

    test('a workout not posted (sharing off, or no groups): a photo changes no post', () async {
      final entry = await workout(share: false);
      await kit.service.attachPhoto(alex, entry.id, await processed());
      await kit.service.removePhoto(alex, entry.id);
      await settle();
      expect(kit.sharing.writes.where((w) => w.startsWith('thumb')), isEmpty);
    });

    test('a post deleted meanwhile (the group gone): its refused update is ignored', () async {
      final entry = await workout();
      kit.sharing.posts['g2']!.remove(entry.id);
      await kit.service.attachPhoto(alex, entry.id, await processed());
      await settle();
      expect(kit.sharing.posts['g1']![entry.id]!['thumb'], isNotNull);
    });
  });
}
