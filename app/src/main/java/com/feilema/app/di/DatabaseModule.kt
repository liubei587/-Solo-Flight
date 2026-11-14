package com.feilema.app.di

import android.content.Context
import androidx.room.Room
import com.feilema.app.data.local.FeiLeMaDatabase
import com.feilema.app.data.local.dao.BehaviorEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FeiLeMaDatabase =
        Room.databaseBuilder(context, FeiLeMaDatabase::class.java, "feilema.db").build()

    @Provides
    fun provideBehaviorEventDao(database: FeiLeMaDatabase): BehaviorEventDao =
        database.behaviorEventDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
