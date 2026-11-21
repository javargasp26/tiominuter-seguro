import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:tiominuter_web/domain/photos.dart';

import '../support/photo_fakes.dart';

/// The photo rules (Android's `PhotoSizing`, `ThumbnailFitter`, `TrainingPhotoProcessor`) over a
/// real JPEG codec on the VM; the browser's own codec is checked in Chrome (test/browser/).
void main() {
  group('sizes', () {
    test('the longest side at most the limit, the aspect kept, never upscaled', () {
      expect(PhotoSizing.fit(4000, 3000, 1080), (1080, 810));
      expect(PhotoSizing.fit(3000, 4000, 1080), (810, 1080));
      expect(PhotoSizing.fit(1080, 1080, 1080), (1080, 1080));
      expect(PhotoSizing.fit(800, 600, 1080), (800, 600));
      expect(PhotoSizing.fit(12000, 10, 400), (400, 1));
    });

    test('the thumbnail fitter: quality first, then the size by 0.75, down to 64 px (Android\'s order)', () async {
      final tried = <(int, int)>[];
      // Bytes proportional to the area and the quality: only a small, low-quality one fits
      final bytes = await ThumbnailFitter.fit(12000, (side, quality) async {
        tried.add((side, quality));
        return Uint8List(side * side * quality ~/ 50);
      });
      expect(tried.take(5), [(400, 60), (400, 50), (400, 40), (400, 30), (300, 60)]);
      expect(bytes!.length, lessThanOrEqualTo(PhotoLimits.thumbTargetBytes));
      expect(tried.last.$1, greaterThanOrEqualTo(PhotoLimits.thumbMinSide));
    });

    test('nothing fits, or the encoder fails: no thumbnail', () async {
      expect(await ThumbnailFitter.fit(400, (_, _) async => Uint8List(PhotoLimits.thumbTargetBytes + 1)), isNull);
      expect(await ThumbnailFitter.fit(400, (_, _) async => null), isNull);
    });
  });

  group('metadata', () {
    test('a camera JPEG has Exif (with GPS); stripped, it has none and still decodes the same', () {
      final camera = cameraJpeg();
      expect(JpegMetadata.metadataMarkers(camera), contains(0xE1));
      expect(img.decodeJpg(camera)!.exif.gpsIfd.isEmpty, isFalse);
      final clean = JpegMetadata.strip(camera)!;
      expect(JpegMetadata.hasMetadata(clean), isFalse);
      final decoded = img.decodeJpg(clean)!;
      expect((decoded.width, decoded.height), (300, 200));
      expect(decoded.exif.isEmpty, isTrue);
      expect(JpegMetadata.strip(Uint8List.fromList([1, 2, 3])), isNull);
    });

    test('HEIC is recognised by its brand or its type', () {
      final heic = Uint8List.fromList([0, 0, 0, 24, ...'ftypheic'.codeUnits, 0, 0, 0, 0]);
      expect(looksLikeHeic(heic), isTrue);
      expect(looksLikeHeic(Uint8List.fromList([0, 0, 0, 24, ...'ftypmif1'.codeUnits])), isTrue);
      expect(looksLikeHeic(Uint8List(16), 'image/heif'), isTrue);
      expect(looksLikeHeic(cameraJpeg(), 'image/jpeg'), isFalse);
    });
  });

  group('processing', () {
    test('turned upright by its EXIF orientation, re-encoded: no Exif, no GPS, nothing of the camera', () async {
      // Orientation 6: stored 300 × 200, shown 200 × 300 (rotated 90° clockwise)
      final photo = await PhotoProcessor(VmImageCodec())
          .process(PickedPhoto(bytes: cameraJpeg(orientation: 6), mimeType: 'image/jpeg'));
      expect(jpegSize(photo.full), (200, 300));
      expect((photo.width, photo.height), (200, 300));
      for (final jpeg in [photo.full, photo.thumb!]) {
        expect(JpegMetadata.hasMetadata(jpeg), isFalse, reason: 'the codec kept the Exif; the processor drops it');
        final decoded = img.decodeJpg(jpeg)!;
        expect(decoded.exif.isEmpty, isTrue);
      }
      // The red half (left in the stored image) is on top once upright
      final upright = img.decodeJpg(photo.full)!;
      expect(upright.getPixel(100, 20).r, greaterThan(150));
      expect(upright.getPixel(100, 280).b, greaterThan(150));
    });

    test('a large photo: the full one ≤ 1080 px; a small one is never upscaled', () async {
      final processor = PhotoProcessor(VmImageCodec());
      final large = await processor.process(PickedPhoto(bytes: cameraJpeg(width: 2400, height: 1800)));
      expect(jpegSize(large.full), (1080, 810));
      final (tw, th) = jpegSize(large.thumb!);
      expect(tw, lessThanOrEqualTo(PhotoLimits.thumbMaxSide));
      expect(th, lessThanOrEqualTo(PhotoLimits.thumbMaxSide));
      final small = await processor.process(PickedPhoto(bytes: cameraJpeg(width: 320, height: 240)));
      expect(jpegSize(small.full), (320, 240));
      expect(jpegSize(small.thumb!), (320, 240));
    });

    test('a noisy photo (the worst to compress; downscaling barely smooths it): the thumbnail stays ≤ 60 KB, '
        'never above 65,536 bytes', () async {
      final codec = VmImageCodec();
      final photo = await PhotoProcessor(codec)
          .process(PickedPhoto(bytes: cameraJpeg(width: 500, height: 375, noise: true, quality: 100)));
      expect(photo.thumb!.length, lessThanOrEqualTo(PhotoLimits.thumbTargetBytes));
      expect(photo.thumb!.length, lessThanOrEqualTo(PhotoLimits.thumbMaxBytes));
      expect(codec.encodes, greaterThan(2), reason: 'it had to step the quality / size down');
    });

    test('undecodable, HEIC the browser can\'t read, too large, an encoder that fails: a clear error', () async {
      final processor = PhotoProcessor(VmImageCodec());
      Future<PhotoError?> errorOf(PickedPhoto picked, [PhotoProcessor? other]) async {
        try {
          await (other ?? processor).process(picked);
          return null;
        } on PhotoException catch (e) {
          return e.error;
        }
      }

      expect(await errorOf(PickedPhoto(bytes: Uint8List.fromList('not a photo'.codeUnits))), PhotoError.unreadable);
      expect(await errorOf(PickedPhoto(bytes: Uint8List(0))), PhotoError.unreadable);
      final heic = Uint8List.fromList([0, 0, 0, 24, ...'ftypheic'.codeUnits, ...List.filled(64, 1)]);
      expect(await errorOf(PickedPhoto(bytes: heic, mimeType: 'image/heic')), PhotoError.unsupportedFormat);
      expect(await errorOf(PickedPhoto(bytes: Uint8List(PhotoLimits.maxInputBytes + 1))), PhotoError.tooLarge);
      final failing = PhotoProcessor(VmImageCodec(failEncoding: true));
      expect(await errorOf(PickedPhoto(bytes: cameraJpeg()), failing), PhotoError.failed);
    });
  });

  group('the iOS Home Screen hint', () {
    const iphoneSafari =
        'Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) '
        'Version/18.5 Mobile/15E148 Safari/604.1';
    const ipadDesktopSafari =
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) '
        'Version/18.5 Safari/605.1.15';
    const iphoneChrome =
        'Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) '
        'CriOS/138.0.7204.156 Mobile/15E148 Safari/604.1';
    const instagram =
        'Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) '
        'Mobile/15E148 Instagram 390.0.0';
    const androidChrome =
        'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Mobile Safari/537.36';

    bool shows(String ua, {String platform = '', int touch = 0, bool standalone = false, bool dismissed = false}) =>
        InstallHintRule.shouldShow(
          DeviceInfo(userAgent: ua, platform: platform, maxTouchPoints: touch, standalone: standalone),
          dismissed: dismissed,
        );

    test('Safari on iPhone and on iPad (which says "Mac"), in a tab, until dismissed', () {
      expect(shows(iphoneSafari, touch: 5), isTrue);
      expect(shows(ipadDesktopSafari, platform: 'MacIntel', touch: 5), isTrue);
      expect(shows(iphoneSafari, touch: 5, standalone: true), isFalse, reason: 'already on the Home Screen');
      expect(shows(iphoneSafari, touch: 5, dismissed: true), isFalse);
    });

    test('never in other browsers, in-app views, a Mac or Android', () {
      expect(shows(iphoneChrome, touch: 5), isFalse);
      expect(shows(instagram, touch: 5), isFalse);
      expect(shows(ipadDesktopSafari, platform: 'MacIntel'), isFalse, reason: 'a Mac: no touch screen');
      expect(shows(androidChrome, platform: 'Linux armv8l', touch: 5), isFalse);
    });
  });
}
