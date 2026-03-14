package com.mdfy.app.di

import com.mdfy.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt-модуль для сетевых зависимостей.
 *
 * Предоставляет:
 *   - [OkHttpClient] — один клиент на всё приложение
 *   - Retrofit для Spotify API
 *   - Retrofit для YouTube Data API
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Общий OkHttpClient.
     * Логирование запросов — только в debug-сборке.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .build()
    }

    /**
     * Настройки JSON-сериализации.
     * ignoreUnknownKeys = true — не падаем при добавлении новых полей в API.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * Retrofit для Spotify Web API.
     * Базовый URL: https://api.spotify.com/v1/
     */
    @Provides
    @Singleton
    @Named("spotify")
    fun provideSpotifyRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/v1/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /**
     * Retrofit для YouTube Data API v3.
     * Базовый URL: https://www.googleapis.com/youtube/v3/
     */
    @Provides
    @Singleton
    @Named("youtube")
    fun provideYoutubeRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/youtube/v3/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
