# QuantumAC - Advanced Anti-Cheat Plugin

QuantumAC is a comprehensive anti-cheat solution for Minecraft servers, designed to detect and prevent various types of cheating.

## Component-Based Architecture

QuantumAC uses a component-based architecture for its checks, which provides several benefits:

1. **Modularity**: Each component focuses on a specific aspect of detection
2. **Reusability**: Components can be shared across different checks
3. **Maintainability**: Easier to update and fix individual components
4. **Testability**: Components can be tested in isolation

### Movement Checks

#### Fly Checks

The `FlyA` check has been refactored to use the following components:

- **GravityCheck**: Detects when players aren't falling as expected
- **VerticalAccelerationCheck**: Detects excessive upward velocity
- **TerminalVelocityCheck**: Detects when players fall too slowly
- **MotionInconsistencyCheck**: Detects erratic vertical movement patterns

### Combat Checks

#### KillAura Checks

The KillAura checks have been refactored to use the following components:

- **LateAttackComponent**: Detects when players attack too late after swinging their arm
- **EarlyAttackComponent**: Detects when players attack before swinging their arm
- **AttackRateComponent**: Detects suspiciously high attack rates
- **AttackPatternComponent**: Detects unnaturally consistent attack patterns

## Adding New Components

To add a new component:

1. Create a new class in the appropriate component directory
2. Implement the detection logic with clear thresholds and state tracking
3. Add a method to check for violations that returns a violation message
4. Add a reset method to clear the component's state
5. Integrate the component into the relevant check class

## Configuration

Each check can be configured in the `checks.yml` file, including:

- Enabling/disabling checks
- Setting violation thresholds
- Configuring punishment commands

## API

QuantumAC provides an API for other plugins to interact with it, allowing:

- Access to player data
- Exempting players from checks
- Retrieving check information

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Use the component-based architecture for new checks
2. Write clear documentation for your components
3. Test thoroughly to minimize false positives
4. Follow the existing code style 