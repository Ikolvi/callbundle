/// Enum representing the type of a native call.
enum NativeCallType {
  /// Voice-only call.
  voice,

  /// Video call.
  video;

  /// Creates a [NativeCallType] from a string value.
  ///
  /// Defaults to [NativeCallType.voice] if the value is not recognized.
  factory NativeCallType.fromString(String? value) {
    switch (value?.toLowerCase()) {
      case 'video':
      case '1':
        return NativeCallType.video;
      case 'voice':
      case '0':
      default:
        return NativeCallType.voice;
    }
  }

  /// Returns the integer value for MethodChannel serialization.
  ///
  /// `0` for [voice], `1` for [video].
  int get intValue => this == NativeCallType.video ? 1 : 0;
}

/// Enum representing the state of a native call.
enum NativeCallState {
  /// Call is ringing (incoming, not yet answered).
  ringing,

  /// Call is dialing (outgoing, not yet connected).
  dialing,

  /// Call is active (connected and in progress).
  active,

  /// Call is on hold.
  held,

  /// Call has ended.
  ended;

  /// Creates a [NativeCallState] from a string value.
  factory NativeCallState.fromString(String? value) {
    switch (value?.toLowerCase()) {
      case 'ringing':
        return NativeCallState.ringing;
      case 'dialing':
        return NativeCallState.dialing;
      case 'active':
        return NativeCallState.active;
      case 'held':
        return NativeCallState.held;
      case 'ended':
        return NativeCallState.ended;
      default:
        return NativeCallState.ended;
    }
  }
}

/// Enum representing the handle type for iOS CallKit.
enum NativeHandleType {
  /// Generic handle (not a phone number or email).
  generic,

  /// Phone number handle.
  phone,

  /// Email address handle.
  email;

  /// Creates a [NativeHandleType] from a string value.
  factory NativeHandleType.fromString(String? value) {
    switch (value?.toLowerCase()) {
      case 'phone':
        return NativeHandleType.phone;
      case 'email':
        return NativeHandleType.email;
      case 'generic':
      default:
        return NativeHandleType.generic;
    }
  }
}

/// Enum representing the type of a native call event.
enum NativeCallEventType {
  /// User tapped Accept on native UI.
  accepted,

  /// User tapped Decline on native UI.
  declined,

  /// Call ended (user-initiated or system/programmatic).
  ended,

  /// Ring timer expired without interaction.
  timedOut,

  /// Call was not answered (moved to missed notification).
  missed,

  /// User tapped "Call Back" on missed call notification.
  callback,

  /// User toggled mute from native UI (iOS).
  muted,

  /// User toggled hold from native UI (iOS).
  held,

  /// Speaker/earpiece/Bluetooth route changed.
  audioRouteChanged;

  /// Creates a [NativeCallEventType] from a string value.
  factory NativeCallEventType.fromString(String? value) {
    switch (value?.toLowerCase()) {
      case 'accepted':
        return NativeCallEventType.accepted;
      case 'declined':
        return NativeCallEventType.declined;
      case 'ended':
        return NativeCallEventType.ended;
      case 'timedout':
      case 'timed_out':
        return NativeCallEventType.timedOut;
      case 'missed':
        return NativeCallEventType.missed;
      case 'callback':
        return NativeCallEventType.callback;
      case 'muted':
        return NativeCallEventType.muted;
      case 'held':
        return NativeCallEventType.held;
      case 'audioroutechanged':
      case 'audio_route_changed':
        return NativeCallEventType.audioRouteChanged;
      default:
        return NativeCallEventType.ended;
    }
  }
}

/// Enum representing the status of a permission.
enum PermissionStatus {
  /// Permission has been granted.
  granted,

  /// Permission has been denied.
  denied,

  /// Permission is restricted by the system.
  restricted,

  /// Permission has not been requested yet.
  notDetermined;

  /// Creates a [PermissionStatus] from a string value.
  factory PermissionStatus.fromString(String? value) {
    switch (value?.toLowerCase()) {
      case 'granted':
        return PermissionStatus.granted;
      case 'denied':
        return PermissionStatus.denied;
      case 'restricted':
        return PermissionStatus.restricted;
      case 'notdetermined':
      case 'not_determined':
        return PermissionStatus.notDetermined;
      default:
        return PermissionStatus.notDetermined;
    }
  }

  /// Whether the permission has been granted.
  bool get isGranted => this == PermissionStatus.granted;
}
