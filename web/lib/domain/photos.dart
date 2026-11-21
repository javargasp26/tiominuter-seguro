/// Training photos on the web, as Android's (`TrainingPhotoProcessor`, `PhotoSizing`,
/// `FeedModels.kt` `ThumbnailFitter` / `FeedLimits`): the full photo stays in this browser, only a
/// small thumbnail goes to the group feed. Pure: the browser's decoder / encoder is an [ImageCodec]
/// (`platform/photo_codec_web.dart`), so the rules run on the VM in tests.
library;

import 'dart:typed_data';

abstract final class PhotoLimits {
  /// A larger file is refused before it is read into an image.
  static const int maxInputBytes = 25 * 1024 * 1024;

  /// The kept photo: longest side, JPEG quality (Android's 1080 px, 70).
  static const int fullLongSide = 1080;
  static const int fullQuality = 70;

  /// The thumbnail (history and feed): Android's `ThumbnailFitter` and `FeedLimits`.
  static const int thumbMaxSide = 400;
  static const int thumbMinSide = 64;
  static const List<int> thumbQualities = [60, 50, 40, 30];
  static const double thumbScaleStep = 0.75;
  static const int thumbTargetBytes = 60 * 1024;

  /// The rules' cap on `posts.thumb` (firebase/firestore.rules).
  static const int thumbMaxBytes = 65536;
}

enum PhotoError {
  /// Over [PhotoLimits.maxInputBytes].
  tooLarge,

  /// HEIC / HEIF the browser can't decode.
  unsupportedFormat,

  /// Not an image, or one the browser can't decode.
  unreadable,

  /// The browser's storage is full (QuotaExceededError).
  noSpace,

  failed,
}

class PhotoException implements Exception {
  const PhotoException(this.error);

  final PhotoError error;

  @override
  String toString() => 'PhotoException(${error.name})';
}

/// A file chosen with the file input or dropped on the page.
class PickedPhoto {
  const PickedPhoto({required this.bytes, this.mimeType, this.name});

  final Uint8List bytes;
  final String? mimeType;
  final String? name;
}

/// The re-encoded photo: [full] ≤ 1080 px, [thumb] ≤ 400 px and ≤ 60 KB (null in the rare case no
/// size fits); both JPEG without any metadata block.
class ProcessedPhoto {
  const ProcessedPhoto({required this.full, required this.thumb, required this.width, required this.height});

  final Uint8List full;
  final Uint8List? thumb;
  final int width;
  final int height;
}

/// A decoded image, already turned upright by its EXIF orientation.
abstract interface class DecodedImage {
  int get width;
  int get height;
  void dispose();
}

abstract interface class ImageCodec {
  /// Throws when the bytes can't be decoded.
  Future<DecodedImage> decode(Uint8List bytes, String? mimeType);

  /// A JPEG of [image] scaled to [width] × [height] at [quality] (0–100); null when it can't.
  Future<Uint8List?> encodeJpeg(DecodedImage image, int width, int height, int quality);
}

/// Android's `PhotoSizing`: the size with the longest side at most [maxSide], never upscaled.
abstract final class PhotoSizing {
  static (int, int) fit(int width, int height, int maxSide) {
    final longest = width > height ? width : height;
    if (longest <= maxSide || longest <= 0) return (width, height);
    final scale = maxSide / longest;
    int side(int value) {
      final scaled = (value * scale).round();
      return scaled < 1 ? 1 : scaled;
    }

    return width >= height ? (maxSide, side(height)) : (side(width), maxSide);
  }
}

/// Android's `ThumbnailFitter.fit`: the quality steps down first, then the size by 0.75, down to
/// 64 px; the first result ≤ 60 KB wins.
abstract final class ThumbnailFitter {
  static Future<Uint8List?> fit(int longestSide, Future<Uint8List?> Function(int side, int quality) encode) async {
    var side = longestSide < PhotoLimits.thumbMaxSide ? longestSide : PhotoLimits.thumbMaxSide;
    while (side >= PhotoLimits.thumbMinSide) {
      for (final quality in PhotoLimits.thumbQualities) {
        final bytes = await encode(side, quality);
        if (bytes == null) return null;
        if (bytes.length <= PhotoLimits.thumbTargetBytes) return bytes;
      }
      side = (side * PhotoLimits.thumbScaleStep).toInt();
    }
    return null;
  }
}

/// The metadata blocks of a JPEG: every APPn but APP0 (JFIF) — Exif / GPS (APP1), XMP, ICC,
/// Photoshop… — and comments.
abstract final class JpegMetadata {
  static bool isJpeg(Uint8List bytes) => bytes.length > 3 && bytes[0] == 0xFF && bytes[1] == 0xD8;

  /// The markers of the metadata blocks found before the image data (e.g. 0xE1 for Exif).
  static List<int> metadataMarkers(Uint8List bytes) => [
    for (final s in _segments(bytes))
      if (_isMetadata(s.$1)) s.$1,
  ];

  static bool hasMetadata(Uint8List bytes) => metadataMarkers(bytes).isNotEmpty;

  /// [bytes] without its metadata blocks (the rest byte for byte); null when it isn't a JPEG.
  static Uint8List? strip(Uint8List bytes) {
    if (!isJpeg(bytes)) return null;
    final out = BytesBuilder(copy: false)..add(bytes.sublist(0, 2));
    var position = 2;
    for (final (marker, start, end) in _segments(bytes)) {
      if (start > position) out.add(bytes.sublist(position, start));
      if (!_isMetadata(marker)) out.add(bytes.sublist(start, end));
      position = end;
    }
    out.add(bytes.sublist(position));
    return out.takeBytes();
  }

  static bool _isMetadata(int marker) => (marker >= 0xE1 && marker <= 0xEF) || marker == 0xFE;

  /// (marker, start, end) of each segment with a length, up to the start of the scan (SOS).
  static Iterable<(int, int, int)> _segments(Uint8List bytes) sync* {
    if (!isJpeg(bytes)) return;
    var i = 2;
    while (i + 3 < bytes.length) {
      if (bytes[i] != 0xFF) return;
      final marker = bytes[i + 1];
      if (marker == 0xFF) {
        i++;
        continue;
      }
      // Markers without a length
      if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
        i += 2;
        continue;
      }
      if (marker == 0xDA || marker == 0xD9) return;
      final length = (bytes[i + 2] << 8) | bytes[i + 3];
      final end = i + 2 + length;
      if (length < 2 || end > bytes.length) return;
      yield (marker, i, end);
      i = end;
    }
  }
}

/// HEIC / HEIF by its `ftyp` box (`....ftypheic`, `mif1`…), for a clear message when the browser
/// can't decode it.
bool looksLikeHeic(Uint8List bytes, [String? mimeType]) {
  final type = mimeType?.toLowerCase() ?? '';
  if (type.contains('heic') || type.contains('heif')) return true;
  if (bytes.length < 12) return false;
  if (String.fromCharCodes(bytes.sublist(4, 8)) != 'ftyp') return false;
  const brands = {'heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'mif1', 'msf1'};
  return brands.contains(String.fromCharCodes(bytes.sublist(8, 12)));
}

/// Android's `TrainingPhotoProcessor` in the browser: refuse a file over the limit; decode it
/// upright (EXIF orientation applied); re-encode a full photo (≤ 1080 px, JPEG 70) and a
/// thumbnail ([ThumbnailFitter]); drop every metadata block, so nothing of the original (location
/// included) survives. Never upscales.
class PhotoProcessor {
  const PhotoProcessor(this._codec);

  final ImageCodec _codec;

  Future<ProcessedPhoto> process(PickedPhoto photo) async {
    if (photo.bytes.length > PhotoLimits.maxInputBytes) throw const PhotoException(PhotoError.tooLarge);
    if (photo.bytes.isEmpty) throw const PhotoException(PhotoError.unreadable);
    final DecodedImage image;
    try {
      image = await _codec.decode(photo.bytes, photo.mimeType);
    } on Object {
      throw PhotoException(
        looksLikeHeic(photo.bytes, photo.mimeType) ? PhotoError.unsupportedFormat : PhotoError.unreadable,
      );
    }
    try {
      if (image.width <= 0 || image.height <= 0) throw const PhotoException(PhotoError.unreadable);
      final (width, height) = PhotoSizing.fit(image.width, image.height, PhotoLimits.fullLongSide);
      final full = _clean(await _codec.encodeJpeg(image, width, height, PhotoLimits.fullQuality));
      if (full == null) throw const PhotoException(PhotoError.failed);
      final longest = image.width > image.height ? image.width : image.height;
      final thumb = await ThumbnailFitter.fit(longest, (side, quality) async {
        final (w, h) = PhotoSizing.fit(image.width, image.height, side);
        return _clean(await _codec.encodeJpeg(image, w, h, quality));
      });
      return ProcessedPhoto(
        full: full,
        // Never above the rules' cap, whatever the encoder did
        thumb: thumb != null && thumb.length <= PhotoLimits.thumbMaxBytes ? thumb : null,
        width: width,
        height: height,
      );
    } on PhotoException {
      rethrow;
    } on Object {
      throw const PhotoException(PhotoError.failed);
    } finally {
      image.dispose();
    }
  }

  static Uint8List? _clean(Uint8List? jpeg) => jpeg == null ? null : JpegMetadata.strip(jpeg);
}

/// What the iOS hint needs to know about the browser.
class DeviceInfo {
  const DeviceInfo({required this.userAgent, this.platform = '', this.maxTouchPoints = 0, this.standalone = false});

  final String userAgent;
  final String platform;
  final int maxTouchPoints;

  /// Opened from the Home Screen (`display-mode: standalone` or `navigator.standalone`).
  final bool standalone;
}

/// The one-time hint on iPhone / iPad Safari: photos kept by a site in Safari can be evicted after
/// some weeks without a visit; an app added to the Home Screen keeps its storage. Only Safari (other
/// iOS browsers and in-app views say "Add to Home Screen" differently, or can't), only in a tab
/// (not already on the Home Screen), until dismissed.
abstract final class InstallHintRule {
  static final RegExp _iosDevice = RegExp('iPhone|iPad|iPod');
  static final RegExp _otherBrowsers = RegExp(
    'CriOS|FxiOS|EdgiOS|OPiOS|OPT/|GSA/|DuckDuckGo|YaBrowser|FBAN|FBAV|Instagram|Line/',
  );

  static bool isIos(DeviceInfo device) =>
      _iosDevice.hasMatch(device.userAgent) ||
      // iPadOS asks for desktop pages: a "Mac" with a touch screen
      (device.platform == 'MacIntel' && device.maxTouchPoints > 1);

  static bool isIosSafari(DeviceInfo device) =>
      isIos(device) &&
      device.userAgent.contains('Safari/') &&
      device.userAgent.contains('Version/') &&
      !_otherBrowsers.hasMatch(device.userAgent);

  static bool shouldShow(DeviceInfo device, {required bool dismissed}) =>
      !dismissed && !device.standalone && isIosSafari(device);
}
