package com.app.community.fcm

// TODO: Uncomment when Firebase dependencies are enabled (after adding google-services.json)
//
// import com.google.firebase.messaging.FirebaseMessagingService
// import com.google.firebase.messaging.RemoteMessage
// import com.app.community.core.data.repository.AuthRepository
// import com.app.community.core.data.repository.ProfileRepository
// import kotlinx.coroutines.CoroutineScope
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.SupervisorJob
// import kotlinx.coroutines.launch
// import org.koin.android.ext.android.inject
//
// class FcmService : FirebaseMessagingService() {
//
//     private val profileRepository: ProfileRepository by inject()
//     private val authRepository: AuthRepository by inject()
//     private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
//
//     override fun onNewToken(token: String) {
//         super.onNewToken(token)
//         scope.launch {
//             val userId = authRepository.currentUserId() ?: return@launch
//             profileRepository.updateFcmToken(userId, token)
//         }
//     }
//
//     override fun onMessageReceived(message: RemoteMessage) {
//         super.onMessageReceived(message)
//         // Notifications with a "notification" payload are auto-displayed by FCM
//         // Data-only messages can be handled here if needed
//     }
// }

// Placeholder until Firebase is configured
object FcmPlaceholder {
    const val NOTE = "Enable Firebase dependencies and uncomment FcmService above"
}
