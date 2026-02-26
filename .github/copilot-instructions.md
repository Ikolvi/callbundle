# CallBundle — Copilot Task Instructions

> **Project:** CallBundle Federated Flutter Plugin  
> **Created:** 2025-02-26  
> **Purpose:** Complete instruction set for every task and command in this project.  
> **Classification:** Internal — Engineering

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Environment Setup Commands](#2-environment-setup-commands)
3. [Package Creation Commands](#3-package-creation-commands)
4. [Architecture Rules](#4-architecture-rules)
5. [Coding Standards](#5-coding-standards)
6. [Task Workflow](#6-task-workflow)
7. [File Naming Conventions](#7-file-naming-conventions)
8. [Testing Standards](#8-testing-standards)
9. [Build & Release Commands](#9-build--release-commands)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Project Overview

**CallBundle** is a custom federated Flutter plugin replacing `flutter_callkit_incoming`.  
It provides native incoming & outgoing call UI on iOS (CallKit) and Android (TelecomManager + Notification).

### Monorepo Structure

```
callbundle/                                  # Root workspace
├── callbundle/                              # App-facing package (what apps import)
├── callbundle_platform_interface/           # Abstract API contract + models
├── callbundle_android/                      # Android native implementation
├── callbundle_ios/                          # iOS native implementation
├── example/                                 # Example app for dev/testing
├── melos.yaml                               # Monorepo management
├── COPILOT_INSTRUCTIONS.md                  # THIS FILE
└── README.md
```

### Key Pain Points Being Solved

| ID  | Problem | Solution |
|-----|---------|----------|
| P1  | EventChannel accept events silently dropped | MethodChannel for ALL native→Dart communication |
| P2  | 3 parallel accept-detection paths | Single reliable MethodChannel path |
| P3  | Budget OEM notifications silently fail | Built-in OEM-adaptive notification strategy |
| P4  | Cold-start 3-second hardcoded delay | Deterministic handshake protocol via PendingCallStore |
| P5  | Gradle injection on every build | Standard federated plugin registration |
| P6  | iOS audio session conflict | Controlled AudioSessionManager with .mixWithOthers |
| P7  | 16 ProGuard keep rules in app | Consumer ProGuard rules shipped in plugin |
| P8  | 14 resource keep rules | Plugin manages own resources |
| P9  | Notification channel recreation | Plugin creates/manages channels proactively |
| P10 | `_isEndingCallKitProgrammatically` flag | `isUserInitiated` field on NativeCallEvent |
| P11 | Background isolate crashes | BackgroundIsolateBinaryMessenger support |
| P12 | 437-line fallback plugin in app code | Built-in AdaptiveCallNotification |

---

## 2. Environment Setup Commands

### 2.1 FVM Setup

```bash
# Install FVM (if not installed)
dart pub global activate fvm

# Use stable Flutter
fvm use stable

# Verify
fvm flutter --version
```

### 2.2 Melos Setup

```bash
# Install melos globally
dart pub global activate melos

# Bootstrap all packages
melos bootstrap

# Verify all packages
melos run analyze
```

### 2.3 Project Init

```bash
cd /Users/admin/Documents/CallBundle

# Set FVM version for the project
fvm use stable

# All flutter/dart commands use: fvm flutter / fvm dart
```

---

## 3. Package Creation Commands

### 3.1 Platform Interface Package

```bash
fvm flutter create --template=package callbundle_platform_interface
```

### 3.2 Android Implementation Package

```bash
fvm flutter create --template=plugin \
  --platforms=android \
  --org=com.callbundle \
  callbundle_android
```

### 3.3 iOS Implementation Package

```bash
fvm flutter create --template=plugin \
  --platforms=ios \
  --org=com.callbundle \
  callbundle_ios
```

### 3.4 App-Facing Package

```bash
fvm flutter create --template=package callbundle
```

### 3.5 Example App

```bash
fvm flutter create --org=com.callbundle example
```

---

## 4. Architecture Rules

### 4.1 Federated Plugin Architecture

- **NEVER** put platform-specific code in the app-facing package.
- **ALWAYS** define the API contract in `callbundle_platform_interface`.
- **ALWAYS** extend `PlatformInterface` from `plugin_platform_interface` package.
- **ALWAYS** use `MethodChannel` for native↔Dart communication (NOT EventChannel).
- **ALWAYS** use consumer ProGuard rules in the Android plugin (NOT app-level rules).
- **ALWAYS** declare required permissions in the plugin's AndroidManifest.xml.
- **ALWAYS** handle PushKit inside the iOS plugin (NOT in AppDelegate).
- **ALWAYS** use `PendingCallStore` for cold-start event persistence.
- **NEVER** use hardcoded delays for initialization timing.
- **NEVER** use `WeakReference` for event sinks or callbacks.

### 4.2 Package Dependencies

```
callbundle (app-facing)
  ├── depends on: callbundle_platform_interface
  ├── depends on: callbundle_android (endorsed)
  └── depends on: callbundle_ios (endorsed)

callbundle_android
  └── depends on: callbundle_platform_interface

callbundle_ios
  └── depends on: callbundle_platform_interface

callbundle_platform_interface
  └── depends on: plugin_platform_interface
```

### 4.3 MethodChannel Contract

```
Channel Name: "com.callbundle/main"

Dart → Native:
  configure(Map config)
  showIncomingCall(Map params)
  showOutgoingCall(Map params)
  endCall(String callId)
  endAllCalls()
  setCallConnected(String callId)
  getActiveCalls() → List<Map>
  requestPermissions() → Map
  getVoipToken() → String?
  dispose()

Native → Dart (via MethodChannel):
  onCallEvent(Map event)
  onVoipTokenUpdated(String token)
  onReady()
```

---

## 5. Coding Standards

### 5.1 Dart Standards

- **Null safety:** Fully sound null-safe.
- **Linting:** Use `very_good_analysis` or `flutter_lints` (strict mode).
- **Formatting:** `dart format` with 80-char line width.
- **Documentation:** All public APIs must have `///` doc comments.
- **Exports:** Use barrel files (`callbundle.dart`) for clean exports.
- **Error handling:** Use typed exceptions, never catch generic `Exception`.
- **Immutability:** All data models are immutable (`@immutable` + `final` fields).
- **Equality:** Use `equatable` or override `==` and `hashCode` for models.
- **Enums:** Use enhanced enums with `fromString()` factory constructors.
- **Streams:** Use `StreamController.broadcast()` for event streams.

### 5.2 Kotlin Standards (Android)

- **Min SDK:** 21 (Android 5.0)
- **Target SDK:** 34 (Android 14)
- **Kotlin version:** Latest stable (1.9+)
- **Coroutines:** Use for async operations.
- **Thread safety:** `@Synchronized` or `ReentrantLock` for shared state.
- **Null safety:** Leverage Kotlin's type system fully, minimize `!!`.
- **Logging:** Use `android.util.Log` with TAG constants.
- **Service lifecycle:** Proper `onBind`/`onUnbind`/`onDestroy` cleanup.

### 5.3 Swift Standards (iOS)

- **Min iOS:** 13.0
- **Swift version:** 5.9+
- **Concurrency:** Use GCD (`DispatchQueue`), avoid raw threads.
- **Thread safety:** Serial `DispatchQueue` for call store operations.
- **Memory:** Avoid retain cycles — use `[weak self]` in closures.
- **Error handling:** Use Swift `Result` type and `throws` properly.
- **PushKit:** MUST report `CXProvider.reportNewIncomingCall` synchronously.
- **Audio session:** Always configure before activating.

### 5.4 Version Constraints

- All packages start at `1.0.0`.
- Platform interface uses strict semver.
- SDK constraint: `">=3.0.0 <4.0.0"` for Dart.
- Flutter constraint: `">=3.10.0"`.

---

## 6. Task Workflow

### 6.1 For Every Task

1. **Read** — Understand the requirement fully before writing code.
2. **Plan** — Break into subtasks and track via todo list.
3. **Implement** — Write code following coding standards.
4. **Test** — Run `fvm flutter analyze` and `fvm flutter test` after changes.
5. **Verify** — Check for errors using the IDE error checker.
6. **Commit** — Stage and describe changes clearly.

### 6.2 After Every Task

Present a multichoice question to the user:

```
What would you like to do next?

[1] Continue to the next planned task
[2] Review what was just implemented
[3] Run tests on the current changes
[4] Modify the implementation approach
[5] Skip to a different task
[   ] Custom input: describe what you'd like
```

---

## 7. File Naming Conventions

### 7.1 Dart

| Type | Convention | Example |
|------|-----------|---------|
| Models | `snake_case.dart` | `native_call_params.dart` |
| Abstract classes | `snake_case.dart` | `callbundle_platform.dart` |
| Implementations | `method_channel_callbundle.dart` | — |
| Barrel exports | `package_name.dart` | `callbundle.dart` |
| Tests | `*_test.dart` | `callbundle_platform_test.dart` |
| Enums | `snake_case.dart` | `native_call_type.dart` |

### 7.2 Kotlin

| Type | Convention | Example |
|------|-----------|---------|
| Plugin entry | `PascalCase.kt` | `CallBundlePlugin.kt` |
| Services | `PascalCase.kt` | `CallConnectionService.kt` |
| Data classes | `PascalCase.kt` | `CallState.kt` |
| Utilities | `PascalCase.kt` | `OemDetector.kt` |

### 7.3 Swift

| Type | Convention | Example |
|------|-----------|---------|
| Plugin entry | `PascalCase.swift` | `CallBundlePlugin.swift` |
| Delegates | `PascalCase.swift` | `CallKitProvider.swift` |
| Managers | `PascalCase.swift` | `AudioSessionManager.swift` |
| Handlers | `PascalCase.swift` | `PushKitHandler.swift` |

---

## 8. Testing Standards

### 8.1 Unit Tests

```bash
# Run all tests
melos run test

# Run tests for specific package
cd callbundle_platform_interface && fvm flutter test

# Run with coverage
fvm flutter test --coverage
```

### 8.2 Test Coverage Requirements

| Package | Min Coverage |
|---------|-------------|
| `callbundle_platform_interface` | 100% (models + serialization) |
| `callbundle` (app-facing) | 90% (delegation + stream wiring) |
| `callbundle_android` | 80% (Dart side, native tested manually) |
| `callbundle_ios` | 80% (Dart side, native tested manually) |

### 8.3 Integration Testing

```bash
cd example
fvm flutter test integration_test/
```

---

## 9. Build & Release Commands

### 9.1 Analysis

```bash
melos run analyze          # Lint all packages
melos run format           # Format all packages
melos run format:check     # Check formatting without modifying
```

### 9.2 Build

```bash
# Build example app for Android
cd example && fvm flutter build apk --debug

# Build example app for iOS
cd example && fvm flutter build ios --no-codesign
```

### 9.3 Publish (when ready)

```bash
# Dry run
fvm dart pub publish --dry-run

# Publish (order matters!)
# 1. callbundle_platform_interface
# 2. callbundle_android
# 3. callbundle_ios
# 4. callbundle (app-facing)
```

---

## 10. Troubleshooting

### 10.1 Common Issues

| Issue | Solution |
|-------|----------|
| `fvm flutter` not found | Run `fvm use stable` in project root |
| Package not found during bootstrap | Run `melos clean && melos bootstrap` |
| Android build fails | Check `minSdk` is 21+ and `compileSdk` is 34+ |
| iOS build fails | Run `cd example/ios && pod install --repo-update` |
| Tests fail with platform error | Ensure mock platform is registered in test setup |
| MethodChannel not found | Ensure `TestDefaultBinaryMessengerBinding` in tests |

### 10.2 Useful Debug Commands

```bash
# Check package dependencies
fvm dart pub deps

# Check outdated packages
fvm dart pub outdated

# Clean rebuild
fvm flutter clean && fvm flutter pub get

# Regenerate platform registrations
fvm flutter pub get
```

---

## Appendix: Task Execution Log

> This section is auto-updated as tasks are completed.

| # | Task | Status | Date |
|---|------|--------|------|
| 1 | Create COPILOT_INSTRUCTIONS.md | ✅ Completed | 2025-02-26 |
| 2 | Setup FVM + monorepo structure | ⬜ Pending | — |
| 3 | Create callbundle_platform_interface | ⬜ Pending | — |
| 4 | Create callbundle_android | ⬜ Pending | — |
| 5 | Create callbundle_ios | ⬜ Pending | — |
| 6 | Create callbundle app-facing | ⬜ Pending | — |
| 7 | Implement platform interface models | ⬜ Pending | — |
| 8 | Implement abstract platform class | ⬜ Pending | — |
| 9 | Implement MethodChannel default | ⬜ Pending | — |
| 10 | Wire app-facing API | ⬜ Pending | — |
| 11 | Implement Android native code | ⬜ Pending | — |
| 12 | Implement iOS native code | ⬜ Pending | — |
| 13 | Setup melos.yaml | ⬜ Pending | — |
| 14 | Create example app | ⬜ Pending | — |
| 15 | Add consumer ProGuard rules | ⬜ Pending | — |
| 16 | Add Android permissions manifest | ⬜ Pending | — |
| 17 | Configure analysis_options.yaml | ⬜ Pending | — |
| 18 | Write unit tests | ⬜ Pending | — |

---

*This file is the single source of truth for all CallBundle project commands and standards.*
