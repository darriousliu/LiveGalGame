package com.example.livegg1.ext

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun Activity.setImmersiveFullscreen() {
    // ✅ Step 1: 使用新 API 隐藏导航栏和状态栏
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // ✅ Step 2: 创建 WindowInsetsController 来控制系统 UI 的显示/隐藏
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.apply {
        // 隐藏导航栏 + 状态栏
        hide(WindowInsetsCompat.Type.systemBars())
        // 设置沉浸式行为：滑动边缘时自动显示系统栏（可选）
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun Activity.exitImmersiveFullscreen() {
    WindowCompat.setDecorFitsSystemWindows(window, true)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        show(WindowInsetsCompat.Type.systemBars())
    }
}