package com.javargasp.tiominutermvi.di

import com.javargasp.tiominutermvi.BuildConfig
import com.javargasp.tiominutermvi.data.content.ActiveContent
import com.javargasp.tiominutermvi.data.content.AnimationLocator
import com.javargasp.tiominutermvi.data.content.AssetBundledContent
import com.javargasp.tiominutermvi.data.content.BundledContent
import com.javargasp.tiominutermvi.data.content.BundledContentHashes
import com.javargasp.tiominutermvi.data.content.CachedContentSource
import com.javargasp.tiominutermvi.data.content.ContentAnimationLocator
import com.javargasp.tiominutermvi.data.content.ContentHttpClient
import com.javargasp.tiominutermvi.data.content.ContentJsonFile
import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.content.ContentStore
import com.javargasp.tiominutermvi.data.content.ContentSyncer
import com.javargasp.tiominutermvi.data.content.HttpUrlConnectionContentHttpClient
import com.javargasp.tiominutermvi.data.remote.AssetDailyRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.AssetGymRemoteDataSource
import com.javargasp.tiominutermvi.data.remote.EXERCISES_CONTENT
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/**
 * Koin module of the content (core, also used by [programModule]): the single read path of the
 * content files ([ContentSource], [AnimationLocator]) and the sync with Firebase Hosting.
 *
 * Every content JSON file is a [ContentJsonFile] registered with its path as qualifier; the
 * program module registers its own two. The syncer and the bundle fingerprint use all of them.
 */
val contentModule = module {

    single(named(EXERCISES_CONTENT.path)) { EXERCISES_CONTENT }
    single(named(AssetDailyRemoteDataSource.DAILY_ROUTINES_FILE)) { AssetDailyRemoteDataSource.DAILY_ROUTINES_CONTENT }
    single(named(AssetGymRemoteDataSource.GYM_ROUTINES_FILE)) { AssetGymRemoteDataSource.GYM_ROUTINES_CONTENT }

    single<ContentHttpClient> { HttpUrlConnectionContentHttpClient() }
    single<BundledContent> { AssetBundledContent(androidContext().assets) }
    single { ContentStore(File(androidContext().filesDir, ContentStore.DIRECTORY)) }
    single { ActiveContent(get()) }
    single { BundledContentHashes(get(), getAll<ContentJsonFile<*>>().map { it.path }) }

    single<ContentSource> { CachedContentSource(get(), get(), get(), get()) }
    single<AnimationLocator> { ContentAnimationLocator(get(), get(), get()) }
    single {
        ContentSyncer(BuildConfig.CONTENT_BASE_URL, get(), get(), get(), get(), get(), getAll<ContentJsonFile<*>>())
    }
}
