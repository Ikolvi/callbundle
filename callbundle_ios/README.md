# callbundle_ios

The iOS implementation of [`callbundle`](https://pub.dev/packages/callbundle).

## Usage

This package is [endorsed](https://dart.dev/tools/pub/dependencies#endorsed-packages), which means you can simply use `callbundle` normally. This package will be automatically included in your app when you target iOS.

```yaml
dependencies:
  callbundle: ^1.0.0
```

## Features

- **CallKit** integration with full CXProvider delegate
- **PushKit** handled in-plugin — no AppDelegate configuration needed
- **AudioSessionManager** — `.mixWithOthers` mode prevents HMS audio kill
- **PendingCallStore** — UserDefaults-based cold-start event persistence with 60s TTL
- **Missed call notifications** via UNUserNotificationCenter
- **Thread-safe** call state via serial DispatchQueue

## Requirements

- iOS 13.0+
- Swift 5.0+

## Links

- [CallBundle on pub.dev](https://pub.dev/packages/callbundle)
- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [Ikolvi](https://ikolvi.com)

