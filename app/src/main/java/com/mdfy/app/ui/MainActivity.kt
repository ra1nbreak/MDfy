package com.mdfy.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mdfy.app.ui.navigation.MdfyNavigation
import com.mdfy.app.ui.theme.MdfyTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Единственная Activity MDfy.
 *
 * Архитектура single-Activity: вся навигация — через NavHost в [MdfyNavigation].
 * Edge-to-edge включён — контент рисуется под системными барами,
 * отступы управляются через WindowInsets в Scaffold каждого экрана.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MdfyTheme {
                MdfyNavigation()
            }
        }
    }
}
