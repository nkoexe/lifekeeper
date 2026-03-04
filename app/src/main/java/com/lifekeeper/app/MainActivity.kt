package com.lifekeeper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifekeeper.app.ui.navigation.NavGraph
import com.lifekeeper.app.ui.theme.LifekeeperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifekeeperTheme {
                NavGraph()
            }
        }
    }
}
