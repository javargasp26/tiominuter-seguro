// Regenerates the Home Screen icons from web/icons/Icon-512.png (the mascot on transparency):
//   - icons/Icon-180.png: the apple-touch-icon, opaque (iOS draws transparency as black);
//   - icons/Icon-maskable-192.png / -512.png: the mascot inside the maskable safe zone (the
//     central 80 %), so Android's round / squircle masks don't cut it.
// Run from web/: dart run tool/make_icons.dart
import 'dart:io';

import 'package:image/image.dart' as img;

void main() {
  final source = img.decodePng(File('web/icons/Icon-512.png').readAsBytesSync())!;
  final white = img.ColorRgba8(255, 255, 255, 255);

  img.Image onWhite(int size, double scale) {
    final canvas = img.Image(width: size, height: size, numChannels: 4)..clear(white);
    final side = (size * scale).round();
    final mascot = img.copyResize(source, width: side, height: side, interpolation: img.Interpolation.cubic);
    final offset = (size - side) ~/ 2;
    return img.compositeImage(canvas, mascot, dstX: offset, dstY: offset);
  }

  File('web/icons/Icon-180.png').writeAsBytesSync(img.encodePng(onWhite(180, 0.9)));
  for (final size in [192, 512]) {
    File('web/icons/Icon-maskable-$size.png').writeAsBytesSync(img.encodePng(onWhite(size, 0.72)));
  }
}
