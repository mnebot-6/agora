import UIKit
import FirebaseCore
import FirebaseMessaging
import UserNotifications
import ComposeApp

/// Mirrors the Android setup:
/// - FcmService (androidMain/kotlin/com/app/community/fcm/FcmService.kt) — delivers FCM token to Kotlin
/// - MainActivity.requestNotificationPermission (androidMain/MainActivity.kt:45-49) — asks user for permission
///
/// FirebaseAppDelegateProxyEnabled is set to false in Info.plist so this delegate
/// controls the APNs-token → FCM → Kotlin hand-off explicitly.
class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self

        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .badge, .sound]
        ) { granted, _ in
            if granted {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
        return true
    }

    // APNs device token → FCM (FCM will in turn call didReceiveRegistrationToken below).
    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("APNs registration failed: \(error.localizedDescription)")
    }

    // FCM token → Kotlin. PushTokenProviderKt.setPushToken unblocks the suspend
    // call in App.kt:52 which persists the token to profiles.fcm_token in Supabase.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        PushTokenProviderKt.setPushToken(token: token)
    }

    // Show banner while app is in foreground (Android's FCM auto-displays — this matches).
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .badge])
    }

    // Hook for notification-tap deep links (not used yet, matches current Android behavior).
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        completionHandler()
    }
}
