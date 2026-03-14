package com.mdfy.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// DataStore extension — создаётся один раз на уровне Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mdfy_settings")

/**
 * Основной Hilt-модуль.
 * Предоставляет синглтоны, которые живут всё время работы приложения.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * DataStore для хранения настроек (тема, язык, качество и т.д.)
     * Используется в SettingsViewModel.
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.dataStore
}
