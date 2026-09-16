package com.app.community.di

import com.app.community.core.ui.locale.LanguagePreferenceManager
import com.app.community.core.ui.theme.ThemeManager
import com.app.community.core.data.repository.ActivityRepository
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.data.repository.BlockRepository
import com.app.community.core.data.repository.CommunityMessageRepository
import com.app.community.core.data.repository.CommunityRepository
import com.app.community.core.data.repository.PaymentRepository
import com.app.community.core.data.repository.GuestRepository
import com.app.community.core.data.repository.NotificationRepository
import com.app.community.core.data.repository.ProfileRepository
import com.app.community.core.data.repository.ReportRepository
import com.app.community.core.data.repository.SlotRepository
import com.app.community.core.data.repository.SlotTemplateRepository
import com.app.community.core.data.repository.TagRepository
import com.app.community.core.domain.auth.GetAuthStateUseCase
import com.app.community.core.domain.auth.SignInUseCase
import com.app.community.core.domain.auth.SignUpUseCase
import com.app.community.core.domain.community.CreateCommunityUseCase
import com.app.community.core.domain.community.GetMyCommunitiesUseCase
import com.app.community.core.domain.community.JoinCommunityUseCase
import com.app.community.feature.activity.presentation.ActivityDetailScreenModel
import com.app.community.feature.activity.presentation.CreateActivityScreenModel
import com.app.community.feature.activity.presentation.EditActivityScreenModel
import com.app.community.feature.auth.presentation.ForgotPasswordScreenModel
import com.app.community.feature.auth.presentation.LoginScreenModel
import com.app.community.feature.auth.presentation.ProfileScreenModel
import com.app.community.feature.auth.presentation.RegisterScreenModel
import com.app.community.feature.community.presentation.CommunityChatScreenModel
import com.app.community.feature.community.presentation.CommunityDetailScreenModel
import com.app.community.feature.community.presentation.CommunityPaymentsScreenModel
import com.app.community.feature.community.presentation.CommunityListScreenModel
import com.app.community.feature.community.presentation.CommunityPreviewScreenModel
import com.app.community.feature.community.presentation.CreateCommunityScreenModel
import com.app.community.feature.community.presentation.ExploreCommunitiesScreenModel
import com.app.community.feature.community.presentation.AutoJoinByInviteScreenModel
import com.app.community.feature.community.presentation.JoinCommunityScreenModel
import com.app.community.feature.community.presentation.JoinRequestsScreenModel
import com.app.community.feature.community.presentation.MemberManagementScreenModel
import com.app.community.feature.notification.presentation.NotificationListScreenModel
import com.app.community.dashboard.DashboardScreenModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

fun repositoryModule(settings: Settings) = module {
    single<Settings> { settings }
    // Scope de aplicación: sobrevive a la destrucción de cualquier ScreenModel.
    // Necesario para escrituras que no deben cancelarse cuando la recomposición
    // forzada por key(isDarkMode)/key(locale) desmonta la pantalla.
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { ThemeManager(get()) }
    single { LanguagePreferenceManager(get()) }
    single { AuthRepository() }
    single { ProfileRepository() }
    single { CommunityRepository() }
    single { PaymentRepository() }
    single { CommunityMessageRepository() }
    single { ActivityRepository() }
    single { SlotRepository() }
    single { NotificationRepository() }
    single { SlotTemplateRepository() }
    single { TagRepository() }
    single { ReportRepository() }
    single { BlockRepository() }
    single { GuestRepository() }
}

val useCaseModule = module {
    factory { GetAuthStateUseCase(get()) }
    factory { SignInUseCase(get()) }
    factory { SignUpUseCase(get()) }
    factory { GetMyCommunitiesUseCase(get(), get()) }
    factory { CreateCommunityUseCase(get(), get()) }
    factory { JoinCommunityUseCase(get(), get()) }
}

val screenModelModule = module {
    factory {
        DashboardScreenModel(
            activityRepository = get(),
            slotRepository = get(),
            authRepository = get(),
            profileRepository = get(),
            communityRepository = get(),
        )
    }
    factory { params ->
        CreateActivityScreenModel(
            communityId = params.get(),
            activityRepository = get(),
            slotRepository = get(),
            authRepository = get(),
            slotTemplateRepository = get(),
            communityRepository = get(),
        )
    }
    factory { params ->
        EditActivityScreenModel(
            activityId = params.get(),
            activityRepository = get(),
        )
    }
    factory { params ->
        ActivityDetailScreenModel(
            activityId = params.get(),
            activityRepository = get(),
            slotRepository = get(),
            authRepository = get(),
            communityRepository = get(),
            profileRepository = get(),
            guestRepository = get(),
            paymentRepository = get(),
        )
    }
    factory { params ->
        CommunityDetailScreenModel(
            communityId = params.get(),
            communityRepository = get(),
            activityRepository = get(),
            authRepository = get(),
            tagRepository = get(),
        )
    }
    factory {
        CommunityListScreenModel(
            getMyCommunitiesUseCase = get(),
        )
    }
    factory {
        CreateCommunityScreenModel(
            createCommunityUseCase = get(),
            tagRepository = get(),
        )
    }
    factory {
        JoinCommunityScreenModel(
            joinCommunityUseCase = get(),
        )
    }
    factory { params ->
        AutoJoinByInviteScreenModel(
            inviteCode = params.get(),
            communityRepository = get(),
        )
    }
    factory { params ->
        JoinRequestsScreenModel(
            communityId = params.get(),
            communityRepository = get(),
        )
    }
    factory {
        ExploreCommunitiesScreenModel(
            communityRepository = get(),
            tagRepository = get(),
        )
    }
    factory { params ->
        CommunityPreviewScreenModel(
            communityId = params.get(),
            communityRepository = get(),
            authRepository = get(),
        )
    }
    factory { params ->
        CommunityPaymentsScreenModel(
            communityId = params.get(),
            paymentRepository = get(),
        )
    }
    factory { params ->
        MemberManagementScreenModel(
            communityId = params.get(),
            communityRepository = get(),
            authRepository = get(),
            blockRepository = get(),
            reportRepository = get(),
        )
    }
    factory { params ->
        CommunityChatScreenModel(
            communityId = params.get(),
            messageRepo = get(),
            communityRepo = get(),
            authRepo = get(),
            blockRepository = get(),
            reportRepository = get(),
        )
    }
    factory { LoginScreenModel(signInUseCase = get()) }
    factory { RegisterScreenModel(signUpUseCase = get()) }
    factory { ForgotPasswordScreenModel(authRepository = get()) }
    factory {
        ProfileScreenModel(
            authRepository = get(),
            profileRepository = get(),
            themeManager = get(),
            languageManager = get(),
            appScope = get(),
        )
    }
    factory {
        NotificationListScreenModel(
            notificationRepository = get(),
            authRepository = get(),
        )
    }
}

fun appModules(settings: Settings) = listOf(repositoryModule(settings), useCaseModule, screenModelModule)
