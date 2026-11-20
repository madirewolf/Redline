package com.redline.app.di

import com.redline.app.community.CommunityRepository
import com.redline.app.community.local.LocalCommunityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Swap the binding here for a server-backed repository when there is one. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityModule {

    @Binds
    @Singleton
    abstract fun bindCommunityRepository(impl: LocalCommunityRepository): CommunityRepository
}
