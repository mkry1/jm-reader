package com.par9uet.jm.di

import com.par9uet.jm.repository.ComicRepository
import com.par9uet.jm.repository.ComicTagFilter
import com.par9uet.jm.repository.impl.ComicRepositoryImpl
import com.par9uet.jm.repository.impl.EmbeddedClientManager
import com.par9uet.jm.storage.ComicTagCacheStorage
import com.par9uet.jm.ui.viewModel.ComicDetailViewModel
import com.par9uet.jm.ui.viewModel.ComicReadViewModel
import com.par9uet.jm.ui.viewModel.ComicViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val comicModule = module {
    single { EmbeddedClientManager(get(), get()) }
    single { ComicRepositoryImpl(get(), get(), get(), get(), get(), get(), get()) } bind ComicRepository::class
    single { ComicTagCacheStorage(get()) }
    single { ComicTagFilter(get<ComicRepository>()::getComicDetail, get<ComicTagCacheStorage>()) }

    single { com.par9uet.jm.search.PublicationDateCache(
        fetchDetail = get<ComicRepository>()::getComicDetail,
        load = { get<com.par9uet.jm.storage.SecureStorage>().get<Map<String, String>>(
            "publicationDates", object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type,
        ) ?: emptyMap() },
        persist = { get<com.par9uet.jm.storage.SecureStorage>().set("publicationDates", it) },
    ) }
    viewModel { ComicViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { com.par9uet.jm.ui.viewModel.PersonalRecommendationViewModel(get()) }
    viewModel { ComicDetailViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ComicReadViewModel(get(), get(), get(), get(), get(), get()) }
}
