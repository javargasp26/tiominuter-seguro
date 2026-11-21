# Screen Previews

This document explains how to view the Compose previews for the app screens.

## Available Previews

### HomeScreen
- **File**: `app/src/main/java/com/javargasp/tiominutermvi/ui/home/HomeScreen.kt`
- **Previews**:
  - `HomeScreenPreview` - Standard preview
  - `HomeScreenPreviewMobile` - Mobile-sized preview (320x640dp)

### WorkoutScreen
- **File**: `app/src/main/java/com/javargasp/tiominutermvi/ui/workout/WorkoutScreen.kt`
- **Previews**:
  - `WorkoutScreenPreview` - Standard preview with timer running
  - `WorkoutScreenPreviewMobile` - Mobile-sized preview (320x640dp)
  - `WorkoutScreenRepsPreview` - Preview showing a reps-based exercise
  - `WorkoutScreenCountdownPreview` - Preview showing the "Get Ready" countdown

## How to View Previews

### In Android Studio
1. Open any of the screen files mentioned above
2. Click the **Split** or **Preview** tab in the top-right corner of the editor
3. The previews will render automatically
4. You can interact with the previews:
   - Click to simulate interactions
   - Change device configurations
   - Toggle between light/dark themes
   - Change device sizes

### Using Command Line
```bash
# Build the project to ensure previews are up to date
./gradlew assembleDebug

# To generate preview images (requires additional setup)
./gradlew :app:generateDebugPreviewScreenshot
```

## Current Features in Previews

### HomeScreen
- ✅ Uncle.png image displayed at the top
- ✅ "Tio Minuter" title
- ✅ Start button with proper styling
- ✅ Responsive layout

### WorkoutScreen
- ✅ Shows complete workout screen with mock data
- ✅ Timer exercise preview with countdown timer
- ✅ Reps exercise preview with completion tracking
- ✅ Ready countdown overlay preview
- ✅ Bottom-aligned buttons with proper spacing

## Adding More Previews

To add more detailed previews:

1. Create mock data objects
2. Add `@Preview` annotations with different parameters:
   ```kotlin
   @Preview(
       showBackground = true,
       name = "With Timer Running",
       widthDp = 320,
       heightDp = 640
   )
   @Composable
   private fun WorkoutScreenTimerRunningPreview() {
       MaterialTheme {
           WorkoutScreen(
               onNavigateToHome = {},
               viewModel = mockViewModelWithTimerRunning
           )
       }
   }
   ```

## Preview Parameters

Common preview parameters:
- `showBackground` - Shows/hides background
- `name` - Custom name for the preview
- `widthDp` / `heightDp` - Custom dimensions
- `locale` - Test different locales
- `uiMode` - Test light/dark themes
- `device` - Predefined device specifications

## Tips
- Previews update automatically when you save changes
- Use `@Preview` for quick visual feedback
- Combine with `@PreviewParameter` for dynamic data
- Previews don't run actual code - they're static representations