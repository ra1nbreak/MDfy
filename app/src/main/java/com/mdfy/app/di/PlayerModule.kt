package com.mdfy.app.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-модуль для плеера.
 *
 * ExoPlayer не создаётся здесь напрямую (он привязан к Service lifecycle),
 * но здесь можно предоставить вспомогательные зависимости плеера.
 */
@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    /**
     * AudioAttributes для ExoPlayer.
     * Сообщает системе, что это музыкальный плеер —
     * правильный routing, управление аудиофокусом, поведение при звонке.
     */
    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
}
