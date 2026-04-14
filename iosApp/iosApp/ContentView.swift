import SwiftUI
import UIKit
import ComposeApp

/// Hosts the Compose Multiplatform UIViewController produced by the Kotlin framework.
/// `MainViewControllerKt.MainViewController()` is defined in
/// composeApp/src/iosMain/kotlin/com/app/community/MainViewController.kt and starts Koin.
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No-op: Compose drives its own state.
    }
}
