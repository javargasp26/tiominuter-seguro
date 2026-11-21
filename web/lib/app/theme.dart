import 'package:flutter/material.dart';

/// Material 3, light and dark following the system, from the Android theme's colors
/// (app/.../ui/theme/Color.kt: Purple40 / Purple80 and the success greens).
abstract final class AppTheme {
  static const Color _seed = Color(0xFF6650A4);

  static final ThemeData light = _theme(Brightness.light);
  static final ThemeData dark = _theme(Brightness.dark);

  static ThemeData _theme(Brightness brightness) => ThemeData(
    colorScheme: ColorScheme.fromSeed(seedColor: _seed, brightness: brightness),
    useMaterial3: true,
    extensions: [brightness == Brightness.light ? SuccessColors.light : SuccessColors.dark],
  );
}

/// Android's `ExtendedColors` success palette (goal met).
@immutable
class SuccessColors extends ThemeExtension<SuccessColors> {
  const SuccessColors({required this.success, required this.onSuccessContainer, required this.successContainer});

  static const light = SuccessColors(
    success: Color(0xFF2E7D32),
    successContainer: Color(0xFFB7E4B9),
    onSuccessContainer: Color(0xFF0B3D0F),
  );

  static const dark = SuccessColors(
    success: Color(0xFF81C784),
    successContainer: Color(0xFF1B5E20),
    onSuccessContainer: Color(0xFFD7EFD9),
  );

  final Color success;
  final Color successContainer;
  final Color onSuccessContainer;

  static SuccessColors of(BuildContext context) => Theme.of(context).extension<SuccessColors>() ?? light;

  @override
  SuccessColors copyWith({Color? success, Color? successContainer, Color? onSuccessContainer}) => SuccessColors(
    success: success ?? this.success,
    successContainer: successContainer ?? this.successContainer,
    onSuccessContainer: onSuccessContainer ?? this.onSuccessContainer,
  );

  @override
  SuccessColors lerp(SuccessColors? other, double t) => other == null
      ? this
      : SuccessColors(
          success: Color.lerp(success, other.success, t)!,
          successContainer: Color.lerp(successContainer, other.successContainer, t)!,
          onSuccessContainer: Color.lerp(onSuccessContainer, other.onSuccessContainer, t)!,
        );
}
