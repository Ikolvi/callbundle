# callbundle_platform_interface

A common platform interface for the [`callbundle`](https://pub.dev/packages/callbundle) plugin.

This interface allows platform-specific implementations of the `callbundle` plugin, as well as the plugin itself, to ensure they are supporting the same interface.

## Usage

To implement a new platform-specific implementation of `callbundle`, extend [`CallBundlePlatform`](lib/src/callbundle_platform.dart) with an implementation that performs the platform-specific behavior.

## Note on Breaking Changes

Strongly prefer non-breaking changes (such as adding a method to the interface) over breaking changes for this package. See [flutter/flutter#127396](https://github.com/flutter/flutter/issues/127396) for discussion.

## Links

- [CallBundle on pub.dev](https://pub.dev/packages/callbundle)
- [GitHub Repository](https://github.com/Ikolvi/callbundle)
- [Ikolvi](https://ikolvi.com)
