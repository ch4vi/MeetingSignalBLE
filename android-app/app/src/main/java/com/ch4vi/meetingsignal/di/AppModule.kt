package com.ch4vi.meetingsignal.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

@Module
@InstallIn(ActivityRetainedComponent::class)
class AppModule

// @Module
// @InstallIn(ActivityRetainedComponent::class)
// object GithubRepoModule {
//
//    @Provides
//    @ActivityRetainedScoped
//    fun provideGithubReposDataSource(
//        service: GithubReposService,
//        pageMapper: PageMapper,
//        repoMapper: RepoMapper
//    ): GithubReposDataSource {
//        return GithubReposDataSource(service, pageMapper, repoMapper)
//    }
//
//    @Provides
//    @ActivityRetainedScoped
//    fun provideRepository(
//        dataSource: GithubReposDataSource,
//        database: AppDatabase,
//        cacheMapper: PageCacheMapper
//    ): GithubReposRepository {
//        return GithubReposRepositoryImp(dataSource, database, cacheMapper)
//    }
//
//    @Provides
//    @ActivityRetainedScoped
//    fun provideGetGithubRepos(repository: GithubReposRepository): GetGithubRepos {
//        return GetGithubRepos(repository)
//    }
//
//    @Provides
//    @ActivityRetainedScoped
//    fun provideGetGithubRepo(repository: GithubReposRepository): GetGithubRepo {
//        return GetGithubRepo(repository)
//    }
// }
