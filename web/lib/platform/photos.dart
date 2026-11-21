/// What training photos need from the browser, behind interfaces (in-memory / no-op on the VM):
/// [PhotoPicker] (a file input with `accept="image/*"`, so iOS offers the camera or the library
/// itself; drag and drop on desktop), [ImageCodec] (decode upright, encode JPEG), [PhotoStore]
/// (IndexedDB, per account, keyed by entry) and [DeviceInfo] (for the iOS Home Screen hint). Every
/// call tolerates failure: a photo never breaks a workout.
library;

import 'dart:async';
import 'dart:typed_data';

import '../domain/photos.dart';
import 'photos_stub.dart' if (dart.library.js_interop) 'photos_web.dart' as impl;

abstract interface class PhotoPicker {
  /// Opens the browser's file chooser; null when cancelled. Call it from a tap (a user gesture).
  Future<PickedPhoto?> pick();

  /// Files dropped on the page while listened to (desktop drag and drop).
  Stream<PickedPhoto> get drops;
}

/// A photo kept in this browser: the full one and its thumbnail (history and feed).
class StoredPhoto {
  const StoredPhoto({required this.full, required this.thumb, required this.savedAt});

  final Uint8List full;
  final Uint8List? thumb;
  final int savedAt;
}

/// The photos of each account in this browser, keyed by entry id. Reads give null when the photo
/// is missing (never added here, or evicted by the browser); writes throw [PhotoException]
/// ([PhotoError.noSpace] when the storage is full).
abstract interface class PhotoStore {
  Future<StoredPhoto?> read(String uid, String entryId);

  Future<void> save(String uid, String entryId, StoredPhoto photo);

  Future<void> delete(String uid, String entryId);

  /// Every photo of [uid] (sign-out).
  Future<void> clear(String uid);
}

abstract final class BrowserPhotos {
  static PhotoPicker picker() => impl.picker();

  static ImageCodec codec() => impl.codec();

  /// IndexedDB; in memory when it can't be opened (private modes of some browsers).
  static PhotoStore store() => impl.store();

  static DeviceInfo device() => impl.device();

  /// Asks the browser to keep this site's storage (photos) from eviction; a refusal is ignored.
  static Future<void> requestPersistence() => impl.requestPersistence();
}

/// In memory: the VM, tests, and the fallback without IndexedDB. [evict] simulates the browser
/// dropping the storage; [failWith] makes the next save fail.
class MemoryPhotoStore implements PhotoStore {
  final Map<String, StoredPhoto> photos = {};
  PhotoError? failWith;

  String _key(String uid, String entryId) => '$uid/$entryId';

  @override
  Future<StoredPhoto?> read(String uid, String entryId) async => photos[_key(uid, entryId)];

  @override
  Future<void> save(String uid, String entryId, StoredPhoto photo) async {
    final failure = failWith;
    if (failure != null) {
      failWith = null;
      throw PhotoException(failure);
    }
    photos[_key(uid, entryId)] = photo;
  }

  @override
  Future<void> delete(String uid, String entryId) async => photos.remove(_key(uid, entryId));

  @override
  Future<void> clear(String uid) async => photos.removeWhere((key, _) => key.startsWith('$uid/'));

  void evict() => photos.clear();
}

/// No file chooser (the VM).
class NoPhotoPicker implements PhotoPicker {
  const NoPhotoPicker();

  @override
  Future<PickedPhoto?> pick() async => null;

  @override
  Stream<PickedPhoto> get drops => const Stream.empty();
}
