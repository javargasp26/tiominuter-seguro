import 'dart:async';
import 'dart:js_interop';
import 'dart:js_interop_unsafe';

import 'package:flutter/foundation.dart';
import 'package:web/web.dart' as web;

import '../domain/photos.dart';
import 'photos.dart';

PhotoPicker picker() => _FileInputPicker();

ImageCodec codec() => const _CanvasCodec();

PhotoStore store() => _IndexedDbPhotoStore();

DeviceInfo device() {
  final navigator = web.window.navigator;
  bool standalone;
  try {
    standalone =
        web.window.matchMedia('(display-mode: standalone)').matches ||
        (navigator as JSObject).getProperty<JSAny?>('standalone'.toJS).dartify() == true;
  } on Object {
    standalone = false;
  }
  return DeviceInfo(
    userAgent: navigator.userAgent,
    platform: navigator.platform,
    maxTouchPoints: navigator.maxTouchPoints,
    standalone: standalone,
  );
}

Future<void> requestPersistence() async {
  try {
    await web.window.navigator.storage.persist().toDart;
  } on Object catch (e) {
    debugPrint('Photos: persistent storage not granted (${e.runtimeType})');
  }
}

Future<Uint8List> _bytesOf(web.Blob blob) async => (await blob.arrayBuffer().toDart).toDart.asUint8List();

Future<PickedPhoto> _pickedOf(web.File file) async =>
    PickedPhoto(bytes: await _bytesOf(file), mimeType: file.type.isEmpty ? null : file.type, name: file.name);

// ---------------------------------------------------------------------------
// The file input: accept="image/*" lets iOS offer "Take Photo" or the library itself (no
// getUserMedia, no permission prompt of ours); drag and drop on desktop
// ---------------------------------------------------------------------------

class _FileInputPicker implements PhotoPicker {
  Completer<PickedPhoto?>? _pending;
  StreamController<PickedPhoto>? _drops;
  JSFunction? _onDragOver;
  JSFunction? _onDrop;

  @override
  Future<PickedPhoto?> pick() {
    _pending?.complete(null);
    final completer = _pending = Completer<PickedPhoto?>();
    final input = web.document.createElement('input') as web.HTMLInputElement
      ..type = 'file'
      ..accept = 'image/*';
    input.style.display = 'none';
    // In the page while open: some iOS versions ignore a click on a detached input
    web.document.body?.append(input);
    void finish(PickedPhoto? photo) {
      input.remove();
      if (!completer.isCompleted) completer.complete(photo);
      if (identical(_pending, completer)) _pending = null;
    }

    input.addEventListener(
      'change',
      ((web.Event _) {
        final file = input.files?.item(0);
        if (file == null) {
          finish(null);
          return;
        }
        _pickedOf(file).then(finish, onError: (Object _) => finish(null));
      }).toJS,
    );
    input.addEventListener('cancel', ((web.Event _) => finish(null)).toJS);
    // Inside the tap's event handler: the browser counts it as the user's gesture
    input.click();
    return completer.future;
  }

  @override
  Stream<PickedPhoto> get drops {
    return (_drops ??= StreamController<PickedPhoto>.broadcast(onListen: _listen, onCancel: _unlisten)).stream;
  }

  void _listen() {
    _onDragOver = ((web.DragEvent event) => event.preventDefault()).toJS;
    _onDrop = ((web.DragEvent event) {
      event.preventDefault();
      final file = event.dataTransfer?.files.item(0);
      if (file == null) return;
      _pickedOf(file).then((photo) => _drops?.add(photo), onError: (Object _) {});
    }).toJS;
    web.window.addEventListener('dragover', _onDragOver);
    web.window.addEventListener('drop', _onDrop);
  }

  void _unlisten() {
    web.window.removeEventListener('dragover', _onDragOver);
    web.window.removeEventListener('drop', _onDrop);
    _onDragOver = null;
    _onDrop = null;
  }
}

// ---------------------------------------------------------------------------
// Decode upright, re-encode as JPEG: createImageBitmap(imageOrientation: "from-image") decodes off
// the main thread where the browser can, OffscreenCanvas.convertToBlob encodes asynchronously; an
// <img> + <canvas> where they are missing (an <img> is drawn upright by every current browser)
// ---------------------------------------------------------------------------

class _Decoded implements DecodedImage {
  _Decoded(this.source, this.width, this.height, {this.objectUrl});

  /// An ImageBitmap or an HTMLImageElement.
  final JSObject source;
  final String? objectUrl;

  @override
  final int width;

  @override
  final int height;

  @override
  void dispose() {
    if (source.isA<web.ImageBitmap>()) (source as web.ImageBitmap).close();
    final url = objectUrl;
    if (url != null) web.URL.revokeObjectURL(url);
  }
}

class _CanvasCodec implements ImageCodec {
  const _CanvasCodec();

  static bool get _hasOffscreenCanvas => web.window.has('OffscreenCanvas');

  @override
  Future<DecodedImage> decode(Uint8List bytes, String? mimeType) async {
    final blob = web.Blob([bytes.toJS].toJS, web.BlobPropertyBag(type: mimeType ?? ''));
    try {
      final bitmap = await web.window
          .createImageBitmap(blob, web.ImageBitmapOptions(imageOrientation: 'from-image'))
          .toDart;
      return _Decoded(bitmap, bitmap.width, bitmap.height);
    } on Object {
      // Older Safari: no createImageBitmap for a Blob, or no options
    }
    final url = web.URL.createObjectURL(blob);
    final image = web.HTMLImageElement()..src = url;
    try {
      await image.decode().toDart;
    } on Object {
      web.URL.revokeObjectURL(url);
      rethrow;
    }
    if (image.naturalWidth == 0 || image.naturalHeight == 0) {
      web.URL.revokeObjectURL(url);
      throw StateError('empty image');
    }
    return _Decoded(image, image.naturalWidth, image.naturalHeight, objectUrl: url);
  }

  @override
  Future<Uint8List?> encodeJpeg(DecodedImage image, int width, int height, int quality) async {
    final source = (image as _Decoded).source;
    if (_hasOffscreenCanvas) {
      try {
        final canvas = web.OffscreenCanvas(width, height);
        final context = canvas.getContext('2d') as web.OffscreenCanvasRenderingContext2D?;
        if (context != null) {
          context
            ..fillStyle = 'white'.toJS
            ..fillRect(0, 0, width, height)
            ..imageSmoothingEnabled = true
            ..imageSmoothingQuality = 'high'
            ..drawImage(source, 0, 0, width, height);
          final blob = await canvas
              .convertToBlob(web.ImageEncodeOptions(type: 'image/jpeg', quality: quality / 100))
              .toDart;
          if (blob.type == 'image/jpeg') return await _bytesOf(blob);
        }
      } on Object {
        // Fall back to a <canvas>
      }
    }
    final canvas = web.HTMLCanvasElement()
      ..width = width
      ..height = height;
    final context = canvas.getContext('2d') as web.CanvasRenderingContext2D?;
    if (context == null) return null;
    context
      ..fillStyle = 'white'.toJS
      ..fillRect(0, 0, width, height)
      ..imageSmoothingEnabled = true
      ..imageSmoothingQuality = 'high'
      ..drawImage(source, 0, 0, width, height);
    final completer = Completer<web.Blob?>();
    // ignore: unnecessary_lambdas (a JS callback needs a JS-typed parameter)
    canvas.toBlob(((web.Blob? blob) => completer.complete(blob)).toJS, 'image/jpeg', (quality / 100).toJS);
    final blob = await completer.future;
    if (blob == null || blob.type != 'image/jpeg') return null;
    return _bytesOf(blob);
  }
}

// ---------------------------------------------------------------------------
// IndexedDB: one store, key "<uid>/<entryId>", value {uid, entryId, full, thumb, savedAt}
// ---------------------------------------------------------------------------

class _IndexedDbPhotoStore implements PhotoStore {
  static const String _name = 'tiominuter-photos';
  static const String _store = 'photos';

  Future<web.IDBDatabase?>? _db;
  final MemoryPhotoStore _fallback = MemoryPhotoStore();

  Future<web.IDBDatabase?> _open() => _db ??= () async {
    try {
      final request = web.window.indexedDB.open(_name, 1);
      request.onupgradeneeded = ((web.Event _) {
        final db = request.result as web.IDBDatabase;
        if (!db.objectStoreNames.contains(_store)) db.createObjectStore(_store);
      }).toJS;
      return await _done(request) as web.IDBDatabase;
    } on Object catch (e) {
      // E.g. a private window that refuses IndexedDB: photos live in memory for this page
      debugPrint('Photos: IndexedDB unavailable (${e.runtimeType}), using memory');
      return null;
    }
  }();

  static String _key(String uid, String entryId) => '$uid/$entryId';

  static Future<JSAny?> _done(web.IDBRequest request) {
    final completer = Completer<JSAny?>();
    request.onsuccess = ((web.Event _) => completer.complete(request.result)).toJS;
    request.onerror = ((web.Event event) {
      event.preventDefault();
      completer.completeError(_IdbFailure(request.error?.name ?? 'Error'));
    }).toJS;
    return completer.future;
  }

  static Future<void> _committed(web.IDBTransaction transaction) {
    final completer = Completer<void>();
    transaction.oncomplete = ((web.Event _) => completer.complete()).toJS;
    void failed(web.Event _) {
      if (!completer.isCompleted) completer.completeError(_IdbFailure(transaction.error?.name ?? 'AbortError'));
    }

    transaction.onerror = failed.toJS;
    transaction.onabort = failed.toJS;
    return completer.future;
  }

  @override
  Future<StoredPhoto?> read(String uid, String entryId) async {
    try {
      final db = await _open();
      if (db == null) return await _fallback.read(uid, entryId);
      final value = await _done(
        db.transaction(_store.toJS, 'readonly').objectStore(_store).get(_key(uid, entryId).toJS),
      );
      if (value == null || !value.isA<JSObject>()) return null;
      final object = value as JSObject;
      final full = object.getProperty<JSAny?>('full'.toJS);
      if (full == null || !full.isA<JSUint8Array>()) return null;
      final thumb = object.getProperty<JSAny?>('thumb'.toJS);
      final savedAt = object.getProperty<JSAny?>('savedAt'.toJS);
      return StoredPhoto(
        full: (full as JSUint8Array).toDart,
        thumb: thumb != null && thumb.isA<JSUint8Array>() ? (thumb as JSUint8Array).toDart : null,
        savedAt: savedAt != null && savedAt.isA<JSNumber>() ? (savedAt as JSNumber).toDartInt : 0,
      );
    } on Object catch (e) {
      debugPrint('Photos: not read (${e.runtimeType})');
      return null;
    }
  }

  @override
  Future<void> save(String uid, String entryId, StoredPhoto photo) async {
    final db = await _open();
    if (db == null) return _fallback.save(uid, entryId, photo);
    try {
      final value = JSObject()
        ..setProperty('uid'.toJS, uid.toJS)
        ..setProperty('entryId'.toJS, entryId.toJS)
        ..setProperty('full'.toJS, photo.full.toJS)
        ..setProperty('thumb'.toJS, photo.thumb?.toJS)
        ..setProperty('savedAt'.toJS, photo.savedAt.toJS);
      final transaction = db.transaction(_store.toJS, 'readwrite');
      transaction.objectStore(_store).put(value, _key(uid, entryId).toJS);
      await _committed(transaction);
    } on _IdbFailure catch (e) {
      debugPrint('Photos: not saved (${e.name})');
      throw PhotoException(e.name == 'QuotaExceededError' ? PhotoError.noSpace : PhotoError.failed);
    } on Object catch (e) {
      debugPrint('Photos: not saved (${e.runtimeType})');
      throw const PhotoException(PhotoError.failed);
    }
  }

  @override
  Future<void> delete(String uid, String entryId) =>
      _deleteKey(uid, _key(uid, entryId).toJS, () => _fallback.delete(uid, entryId));

  @override
  Future<void> clear(String uid) =>
      _deleteKey(uid, web.IDBKeyRange.bound('$uid/'.toJS, '$uid/￿'.toJS), () => _fallback.clear(uid));

  Future<void> _deleteKey(String uid, JSAny key, Future<void> Function() inMemory) async {
    try {
      final db = await _open();
      if (db == null) return await inMemory();
      final transaction = db.transaction(_store.toJS, 'readwrite');
      transaction.objectStore(_store).delete(key);
      await _committed(transaction);
    } on Object catch (e) {
      debugPrint('Photos: not deleted (${e.runtimeType})');
    }
  }
}

class _IdbFailure implements Exception {
  const _IdbFailure(this.name);

  final String name;
}
