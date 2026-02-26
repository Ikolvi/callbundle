# callbundle_android

The Android implementation of [`callbundle`](https://pub.dev/packages/callbundle).

## Usage

This package is [endorsed](https://dart.dev/tools/pub/dependencies#endorsed-packages), which means you can simply use `callbundle` normally. This package will be automatically included in your app when you target Android.

```yaml
dependencies:
  callbundle: ^1.0.0
```

## Features

- **TelecomManager** integration with `ConnectionService`
- **CallStyle notifications** (API 31+) with standard fallback
- **OEM-adaptive mode** — detects 18+ budget manufacturers and adapts notification strategy
- **Consumer ProGuard rules** — no app-level configuration needed
- **PendingCallStore** — SharedPreferences-based cold-start event persistence
- **Thread-safe** call state via ConcurrentHashMap

## Links

- [CallBundle on pub.dev](https://pub.dev/packages/callbundle)
- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [Ikolvi](https://ikolvi.com)

