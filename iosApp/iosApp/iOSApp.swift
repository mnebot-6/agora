import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.keyboard)
                .onOpenURL { url in
                    handleDeepLink(url)
                }
        }
    }

    /// Mirrors Android's MainActivity.handleDeepLink (androidMain/MainActivity.kt:51-57):
    ///   agora://invite/{code} -> DeepLinkHandler.setInviteCode(code)
    private func handleDeepLink(_ url: URL) {
        guard url.scheme == "agora", url.host == "invite" else { return }
        let segments = url.pathComponents.filter { $0 != "/" }
        guard let code = segments.first, !code.isEmpty else { return }
        DeepLinkHandler.shared.setInviteCode(code: code)
    }
}
