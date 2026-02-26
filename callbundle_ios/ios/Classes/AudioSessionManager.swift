import Foundation
import AVFoundation

/// Manages the iOS audio session for CallKit calls.
///
/// ## Key Design Decision
///
/// Uses `.mixWithOthers` option in the category to prevent HMS
/// (Huawei Mobile Services) and other SDKs from killing the audio
/// session when they initialize their own audio categories.
///
/// ## Audio Session Lifecycle
///
/// 1. **Before call**: Audio session is configured but NOT activated.
/// 2. **CallKit activates**: `provider(_:didActivate:)` fires → we activate.
/// 3. **Call ends**: `provider(_:didDeactivate:)` fires → we deactivate.
///
/// This lifecycle is controlled by CallKit, NOT manually, to prevent
/// conflicts with other audio systems.
class AudioSessionManager {

    // MARK: - Properties

    private let audioSession = AVAudioSession.sharedInstance()
    private var isConfigured = false

    // MARK: - Configuration

    /// Configures the audio session for voice calls.
    ///
    /// Uses `.playAndRecord` with `.mixWithOthers` to prevent
    /// third-party SDK audio conflicts.
    private func configureIfNeeded() {
        guard !isConfigured else { return }

        do {
            try audioSession.setCategory(
                .playAndRecord,
                mode: .voiceChat,
                options: [.mixWithOthers, .allowBluetooth, .defaultToSpeaker]
            )
            isConfigured = true
            NSLog("[CallBundle] Audio session configured")
        } catch {
            NSLog("[CallBundle] Failed to configure audio session: \(error.localizedDescription)")
        }
    }

    // MARK: - Activation

    /// Activates the audio session for a call.
    ///
    /// Called from `CXProviderDelegate.provider(_:didActivate:)` —
    /// the only correct place to activate audio for CallKit calls.
    func activateForCall() {
        configureIfNeeded()

        do {
            try audioSession.setActive(true, options: [])
            NSLog("[CallBundle] Audio session activated")
        } catch {
            NSLog("[CallBundle] Failed to activate audio session: \(error.localizedDescription)")
        }
    }

    /// Deactivates the audio session after a call ends.
    ///
    /// Called from `CXProviderDelegate.provider(_:didDeactivate:)`.
    func deactivate() {
        do {
            try audioSession.setActive(false, options: [.notifyOthersOnDeactivation])
            isConfigured = false
            NSLog("[CallBundle] Audio session deactivated")
        } catch {
            NSLog("[CallBundle] Failed to deactivate audio session: \(error.localizedDescription)")
        }
    }

    /// Configures audio for speaker mode toggle.
    func setSpeakerEnabled(_ enabled: Bool) {
        do {
            if enabled {
                try audioSession.overrideOutputAudioPort(.speaker)
            } else {
                try audioSession.overrideOutputAudioPort(.none)
            }
            NSLog("[CallBundle] Speaker \(enabled ? "enabled" : "disabled")")
        } catch {
            NSLog("[CallBundle] Failed to set speaker: \(error.localizedDescription)")
        }
    }
}
