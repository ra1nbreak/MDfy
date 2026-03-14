package com.mdfy.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Точка входа приложения MDfy.
 * Аннотация @HiltAndroidApp запускает кодогенерацию Hilt —
 * без неё Dependency Injection работать не будет.
 */
@HiltAndroidApp
class MdfyApplication : Application()
