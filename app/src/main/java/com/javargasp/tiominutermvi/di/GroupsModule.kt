package com.javargasp.tiominutermvi.di

import com.javargasp.tiominutermvi.data.groups.AndroidFeedThumbnailSource
import com.javargasp.tiominutermvi.data.groups.FirestoreFeedRepository
import com.javargasp.tiominutermvi.data.groups.FirestoreGroupRepository
import com.javargasp.tiominutermvi.domain.groups.FeedCleaner
import com.javargasp.tiominutermvi.domain.groups.FeedModeration
import com.javargasp.tiominutermvi.domain.groups.FeedPreferences
import com.javargasp.tiominutermvi.domain.groups.FeedPublisher
import com.javargasp.tiominutermvi.domain.groups.FeedRepository
import com.javargasp.tiominutermvi.domain.groups.FeedShareController
import com.javargasp.tiominutermvi.domain.groups.FeedThumbnailSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutSharing
import com.javargasp.tiominutermvi.ui.groups.feed.GroupFeedViewModel
import com.javargasp.tiominutermvi.data.groups.LogcatGroupLog
import com.javargasp.tiominutermvi.data.groups.ResourceGroupTexts
import com.javargasp.tiominutermvi.data.groups.SharedPreferencesGroupPreferences
import com.javargasp.tiominutermvi.domain.auth.AccountGroupsCleaner
import com.javargasp.tiominutermvi.domain.groups.DefaultGroupsHome
import com.javargasp.tiominutermvi.domain.groups.GroupAccountCleaner
import com.javargasp.tiominutermvi.domain.groups.GroupLog
import com.javargasp.tiominutermvi.domain.groups.GroupMembersCache
import com.javargasp.tiominutermvi.domain.groups.GroupOperations
import com.javargasp.tiominutermvi.domain.groups.GroupPreferences
import com.javargasp.tiominutermvi.domain.groups.GroupReconciler
import com.javargasp.tiominutermvi.domain.groups.GroupRepository
import com.javargasp.tiominutermvi.domain.groups.GroupSummaryPublisher
import com.javargasp.tiominutermvi.domain.groups.GroupsCoordinator
import com.javargasp.tiominutermvi.domain.groups.GroupsHome
import com.javargasp.tiominutermvi.domain.groups.MemberProfiles
import com.javargasp.tiominutermvi.domain.groups.MySummarySource
import com.javargasp.tiominutermvi.ui.groups.create.GroupCreateViewModel
import com.javargasp.tiominutermvi.ui.groups.detail.GroupDetailViewModel
import com.javargasp.tiominutermvi.ui.groups.join.GroupJoinViewModel
import com.javargasp.tiominutermvi.ui.groups.list.GroupListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Groups feature (Phase 3b). Core dependencies, read-only: the auth session
 * ([com.javargasp.tiominutermvi.domain.auth.AuthRepository]), the training log and weekly goal read
 * APIs, LocalDateProvider / WallClock, and the sync hooks (the Firestore instance, NetworkMonitor,
 * SyncRepository's status) from [syncModule].
 */
val groupsModule = module {

    single(named(GROUPS_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<GroupRepository> { FirestoreGroupRepository(get()) }
    single { SharedPreferencesGroupPreferences(androidContext()) }
    single<GroupPreferences> { get<SharedPreferencesGroupPreferences>() }
    single<FeedPreferences> { get<SharedPreferencesGroupPreferences>() }
    single<GroupLog> { LogcatGroupLog() }
    single { MemberProfiles(ResourceGroupTexts(androidContext())) }
    single { MySummarySource(get(), get(), get()) }
    single { GroupMembersCache(get()) }
    single { GroupReconciler(get(), get(), get(), get()) }
    single {
        GroupSummaryPublisher(
            auth = get(),
            summaries = get(),
            profiles = get(),
            repository = get(),
            preferences = get(),
            network = get(),
            sync = get(),
            log = get(),
            scope = get(named(GROUPS_SCOPE))
        )
    }
    // Feed (Phase 4b)
    single<FeedRepository> { FirestoreFeedRepository(get()) }
    single<FeedThumbnailSource> { AndroidFeedThumbnailSource(get()) }
    single { FeedModeration(get()) }
    single { FeedCleaner(get(), get(), get(), get(), get()) }
    single {
        FeedPublisher(
            auth = get(),
            store = get(),
            trainingLog = get(),
            groups = get(),
            feed = get(),
            profiles = get(),
            thumbnails = get(),
            preferences = get(),
            network = get(),
            sync = get(),
            dates = get(),
            clock = get(),
            log = get(),
            scope = get(named(GROUPS_SCOPE)),
            programTitles = get()
        )
    }
    single<WorkoutSharing> { FeedShareController(get(), get(), get(), get(), get(), get(), get()) }

    single {
        GroupOperations(
            repository = get(),
            auth = get(),
            summaries = get(),
            profiles = get(),
            network = get(),
            reconciler = get(),
            publisher = get(),
            cache = get(),
            dates = get(),
            log = get(),
            feed = get()
        )
    }
    single<GroupsHome> { DefaultGroupsHome(get(), get(), get(), get(), get(), get(), get(), get(named(GROUPS_SCOPE))) }
    single<AccountGroupsCleaner> { GroupAccountCleaner(get(), get(), get(), get()) }
    single {
        GroupsCoordinator(
            get(), get(), get(), get(), get(), get(named(GROUPS_SCOPE)),
            feedPublisher = get(),
            feedCleaner = get()
        )
    }

    viewModel { GroupListViewModel(get()) }
    viewModel { GroupCreateViewModel(get()) }
    viewModel { GroupJoinViewModel(get()) }
    viewModel { (groupId: String) -> GroupDetailViewModel(groupId, get(), get(), get(), get()) }
    viewModel { (groupId: String) -> GroupFeedViewModel(groupId, get(), get(), get(), get(), get(), get(), names = get(), clock = get()) }
}

private const val GROUPS_SCOPE = "groupsScope"
