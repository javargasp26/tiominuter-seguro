@TestOn('browser')
library;

import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:tiominuter_web/domain/photos.dart';
import 'package:tiominuter_web/platform/photos.dart';

import '../support/photo_fakes.dart';

/// The browser's side of photos, in Chrome (`flutter test --platform chrome test/browser`, run by
/// scripts/test_web_emulator.sh): the real decoder / encoder (createImageBitmap with
/// imageOrientation "from-image", OffscreenCanvas) and IndexedDB. Safari's own behaviour needs a real
/// iPhone (see TECHNICAL_SUMMARY §27).
void main() {
  final processor = PhotoProcessor(BrowserPhotos.codec());

  group('the browser codec', () {
    test('upright by its EXIF orientation, ≤ 1080 px, re-encoded without Exif or GPS', () async {
      final photo = await processor.process(PickedPhoto(bytes: cameraJpeg(orientation: 6), mimeType: 'image/jpeg'));
      expect(jpegSize(photo.full), (200, 300));
      for (final jpeg in [photo.full, photo.thumb!]) {
        expect(JpegMetadata.hasMetadata(jpeg), isFalse);
        expect(img.decodeJpg(jpeg)!.exif.isEmpty, isTrue);
      }
      final upright = img.decodeJpg(photo.full)!;
      expect(upright.getPixel(100, 20).r, greaterThan(150), reason: 'the red half on top');
      final large = await processor.process(PickedPhoto(bytes: cameraJpeg(width: 4000, height: 3000)));
      expect(jpegSize(large.full), (1080, 810));
    });

    test('a large noisy photo: the thumbnail ≤ 60 KB and ≤ 400 px', () async {
      final photo = await processor.process(
        PickedPhoto(bytes: cameraJpeg(width: 1600, height: 1200, noise: true, quality: 95)),
      );
      expect(photo.thumb!.length, lessThanOrEqualTo(PhotoLimits.thumbTargetBytes));
      final (w, h) = jpegSize(photo.thumb!);
      expect(w <= 400 && h <= 400, isTrue);
    });

    test('a transparent PNG becomes a JPEG on white', () async {
      final png = img.encodePng(img.Image(width: 40, height: 40, numChannels: 4));
      final photo = await processor.process(PickedPhoto(bytes: png, mimeType: 'image/png'));
      final pixel = img.decodeJpg(photo.full)!.getPixel(20, 20);
      expect(pixel.r, greaterThan(240));
    });

    test('not an image, or HEIC this browser can\'t decode: a clear error', () async {
      Future<PhotoError?> errorOf(PickedPhoto picked) async {
        try {
          await processor.process(picked);
          return null;
        } on PhotoException catch (e) {
          return e.error;
        }
      }

      expect(await errorOf(PickedPhoto(bytes: Uint8List.fromList('not a photo'.codeUnits))), PhotoError.unreadable);
      final heic = Uint8List.fromList([0, 0, 0, 24, ...'ftypheic'.codeUnits, ...List.filled(64, 1)]);
      expect(await errorOf(PickedPhoto(bytes: heic, mimeType: 'image/heic')), PhotoError.unsupportedFormat);
    });
  });

  group('IndexedDB', () {
    final store = BrowserPhotos.store();

    test('saved per account and entry, read back byte for byte, deleted, cleared at sign-out', () async {
      final full = Uint8List.fromList(List.generate(5000, (i) => i % 256));
      final thumb = Uint8List.fromList([1, 2, 3]);
      await store.save('me', 'e1', StoredPhoto(full: full, thumb: thumb, savedAt: 42));
      await store.save('me', 'e2', StoredPhoto(full: thumb, thumb: null, savedAt: 1));
      await store.save('other', 'e1', StoredPhoto(full: thumb, thumb: thumb, savedAt: 2));
      final read = (await store.read('me', 'e1'))!;
      expect(read.full, full);
      expect(read.thumb, thumb);
      expect(read.savedAt, 42);
      expect((await store.read('me', 'e2'))!.thumb, isNull);
      expect(await store.read('me', 'missing'), isNull);
      await store.delete('me', 'e2');
      expect(await store.read('me', 'e2'), isNull);
      await store.clear('me');
      expect(await store.read('me', 'e1'), isNull);
      expect(await store.read('other', 'e1'), isNotNull, reason: 'another account keeps its photos');
      await store.clear('other');
    });
  });
}
