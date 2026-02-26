import Foundation
import UserNotifications

/// Manages missed call local notifications on iOS.
///
/// When a call is missed (not answered within the timeout),
/// this manager posts a local notification so the user sees
/// the missed call even if the app was killed.
class MissedCallNotificationManager {

    // MARK: - Properties

    private let notificationCenter = UNUserNotificationCenter.current()
    private static let categoryIdentifier = "CALLBUNDLE_MISSED_CALL"

    // MARK: - Permission

    /// Requests notification permission for missed call alerts.
    func requestNotificationPermission(completion: @escaping (Bool) -> Void) {
        notificationCenter.requestAuthorization(
            options: [.alert, .sound, .badge]
        ) { granted, error in
            if let error = error {
                NSLog("[CallBundle] Notification permission error: \(error.localizedDescription)")
            }
            DispatchQueue.main.async {
                completion(granted)
            }
        }
    }

    // MARK: - Missed Call Notification

    /// Shows a missed call notification.
    ///
    /// Called when a call ends without being answered (timeout or caller hangup).
    func showMissedCallNotification(callerName: String, handle: String, callId: String) {
        let content = UNMutableNotificationContent()
        content.title = NSLocalizedString("Missed Call", comment: "Missed call notification title")
        content.body = callerName.isEmpty ? handle : callerName
        content.sound = .default
        content.categoryIdentifier = MissedCallNotificationManager.categoryIdentifier
        content.userInfo = [
            "callId": callId,
            "callerName": callerName,
            "handle": handle,
            "type": "missed_call",
        ]

        // Use callId as identifier so duplicate notifications are replaced
        let request = UNNotificationRequest(
            identifier: "callbundle_missed_\(callId)",
            content: content,
            trigger: nil // Deliver immediately
        )

        notificationCenter.add(request) { error in
            if let error = error {
                NSLog("[CallBundle] Failed to show missed call notification: \(error.localizedDescription)")
            } else {
                NSLog("[CallBundle] Missed call notification shown for: \(callerName)")
            }
        }
    }

    /// Removes a missed call notification by callId.
    func removeMissedCallNotification(callId: String) {
        notificationCenter.removeDeliveredNotifications(
            withIdentifiers: ["callbundle_missed_\(callId)"]
        )
    }

    /// Removes all missed call notifications from this plugin.
    func removeAllMissedCallNotifications() {
        notificationCenter.getDeliveredNotifications { notifications in
            let ids = notifications
                .filter { $0.request.identifier.hasPrefix("callbundle_missed_") }
                .map { $0.request.identifier }

            if !ids.isEmpty {
                self.notificationCenter.removeDeliveredNotifications(withIdentifiers: ids)
            }
        }
    }
}
