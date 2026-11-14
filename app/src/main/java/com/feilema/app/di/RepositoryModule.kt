package com.feilema.app.di

import com.feilema.app.data.repository.BehaviorRepositoryImpl
import com.feilema.app.data.repository.ReminderRepositoryImpl
import com.feilema.app.domain.repository.BehaviorRepository
import com.feilema.app.domain.repository.ReminderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBehaviorRepository(impl: BehaviorRepositoryImpl): BehaviorRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository
}
