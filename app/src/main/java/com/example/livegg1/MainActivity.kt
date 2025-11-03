package com.example.livegg1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.livegg1.ext.setImmersiveFullscreen
import com.example.livegg1.ui.theme.LiveGG1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 设置全屏沉浸式模式
        setImmersiveFullscreen()

        setContent {
            LiveGG1Theme {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
