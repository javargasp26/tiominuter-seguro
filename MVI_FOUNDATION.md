# MVI Architecture Foundation

This document describes the MVI (Model-View-Intent) architecture foundation set up for the TioMinuterMVI project.

## Architecture Overview

The project follows a clean MVI architecture with three main layers:

```
data/     - Data layer (repositories, data sources)
domain/   - Domain layer (use cases, business logic)
ui/       - UI layer (screens, ViewModels, navigation)
```

## MVI Pattern

The MVI pattern follows this flow:

```
User Action → Intent → ViewModel → State / Effect → Compose UI
```

### Key Components

1. **MviIntent**: Represents user actions or UI events
2. **MviState**: Represents the current UI state (immutable data classes)
3. **MviEffect**: Represents one-time events (navigation, messages, etc.)
4. **BaseViewModel**: Base class implementing the MVI pattern
5. **CollectEffects**: Composable helper for lifecycle-aware effect collection

### State vs Effects

- **State**: Persistent UI state managed via `StateFlow`
- **Effects**: One-time events emitted via `Channel`/`SharedFlow`

## Example Implementation

An example implementation is provided in `ui/example/`:
- `ExampleIntent.kt` - Sealed class of intents
- `ExampleState.kt` - Immutable data class for state
- `ExampleEffect.kt` - Sealed class of effects
- `ExampleViewModel.kt` - ViewModel extending BaseViewModel

## Dependencies Added

- `androidx.navigation.compose` - Navigation
- `androidx.lifecycle.viewmodel.compose` - ViewModel for Compose
- `kotlinx.serialization.json` - JSON serialization

## Next Steps

The foundation is ready for implementing:
- Home screen
- Routine selection
- Workout execution
- Exercise timer
- Repetition tracking
- Completion screens

All screens should follow the established MVI pattern using the base classes provided.