package io.github.openpause.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.openpause.data.database.AppRuleDao
import io.github.openpause.data.database.OpenPauseDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenPauseDatabase {
        return Room.databaseBuilder(
            context,
            OpenPauseDatabase::class.java,
            "open_pause.db",
        ).build()
    }

    @Provides
    fun provideAppRuleDao(database: OpenPauseDatabase): AppRuleDao = database.appRuleDao()
}
