import '../domain/photos.dart';
import 'photos.dart';

PhotoPicker picker() => const NoPhotoPicker();

ImageCodec codec() => const _NoCodec();

PhotoStore store() => MemoryPhotoStore();

DeviceInfo device() => const DeviceInfo(userAgent: '');

Future<void> requestPersistence() async {}

/// The VM has no image decoder: every photo is unreadable (tests inject their own codec).
class _NoCodec implements ImageCodec {
  const _NoCodec();

  @override
  Future<DecodedImage> decode(_, _) => throw UnsupportedError('no image decoder outside a browser');

  @override
  Future<Never> encodeJpeg(_, _, _, _) => throw UnsupportedError('no image encoder outside a browser');
}
