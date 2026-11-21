import 'dart:async';
import 'dart:math';
import 'dart:typed_data';

import 'package:image/image.dart' as img;
import 'package:tiominuter_web/domain/photos.dart';
import 'package:tiominuter_web/platform/photos.dart';

/// A real JPEG codec on the VM (package:image): decodes, turns the image upright by its EXIF
/// orientation (what the browser's `imageOrientation: "from-image"` does) and encodes. By default
/// it behaves like the worst browser: the encoded JPEG keeps the source's EXIF (GPS included), so
/// the processor's own stripping is what the tests check.
class VmImageCodec implements ImageCodec {
  VmImageCodec({this.keepMetadata = true, this.failEncoding = false});

  final bool keepMetadata;
  bool failEncoding;
  int encodes = 0;

  @override
  Future<DecodedImage> decode(Uint8List bytes, String? mimeType) async {
    final image = img.decodeImage(bytes);
    if (image == null) throw const FormatException('not an image');
    return _VmImage(img.bakeOrientation(image), image.exif.clone());
  }

  @override
  Future<Uint8List?> encodeJpeg(DecodedImage image, int width, int height, int quality) async {
    encodes++;
    if (failEncoding) return null;
    final source = image as _VmImage;
    final resized = img.copyResize(
      source.image,
      width: width,
      height: height,
      interpolation: img.Interpolation.average,
    );
    resized.exif = keepMetadata ? source.exif : img.ExifData();
    return img.encodeJpg(resized, quality: quality);
  }
}

class _VmImage implements DecodedImage {
  _VmImage(this.image, this.exif);

  final img.Image image;
  final img.ExifData exif;

  @override
  int get width => image.width;

  @override
  int get height => image.height;

  @override
  void dispose() {}
}

/// A JPEG as a phone writes it: [orientation] in EXIF, the camera, a date and a GPS position.
/// [noise] makes it hard to compress (the worst case for the thumbnail's size).
Uint8List cameraJpeg({int width = 300, int height = 200, int orientation = 1, bool noise = false, int quality = 90}) {
  final image = img.Image(width: width, height: height);
  final random = Random(7);
  for (final pixel in image) {
    if (noise) {
      pixel
        ..r = random.nextInt(256)
        ..g = random.nextInt(256)
        ..b = random.nextInt(256);
    } else {
      // Left half red, right half blue: the orientation is visible in the pixels
      final left = pixel.x < width / 2;
      pixel
        ..r = left ? 220 : 20
        ..g = 30
        ..b = left ? 20 : 220;
    }
  }
  image.exif.imageIfd
    ..orientation = orientation
    ..make = 'Apple'
    ..model = 'iPhone 15';
  image.exif.gpsIfd.setGpsLocation(latitude: -33.4489, longitude: -70.6693);
  return img.encodeJpg(image, quality: quality);
}

/// The size of a JPEG, decoded again.
(int, int) jpegSize(Uint8List bytes) {
  final image = img.decodeJpg(bytes)!;
  return (image.width, image.height);
}

/// Hands out a prepared file; drops are pushed by the test.
class FakePhotoPicker implements PhotoPicker {
  PickedPhoto? next;
  int picks = 0;
  final StreamController<PickedPhoto> _drops = StreamController<PickedPhoto>.broadcast();

  @override
  Future<PickedPhoto?> pick() async {
    picks++;
    return next;
  }

  @override
  Stream<PickedPhoto> get drops => _drops.stream;

  bool get hasDropListener => _drops.hasListener;

  void drop(PickedPhoto photo) => _drops.add(photo);
}

/// A store whose reads throw (an unreadable IndexedDB).
class BrokenPhotoStore extends MemoryPhotoStore {
  @override
  Future<StoredPhoto?> read(String uid, String entryId) => throw StateError('IndexedDB is gone');
}
