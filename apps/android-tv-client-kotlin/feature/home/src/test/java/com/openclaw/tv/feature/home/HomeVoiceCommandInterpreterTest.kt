package com.openclaw.tv.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVoiceCommandInterpreterTest {
    private val interpreter = HomeVoiceCommandInterpreter()

    @Test
    fun opensDefaultFeaturedChineseApps() {
        assertOpenApp(
            text = "打开芒果TV",
            title = "芒果TV",
            packageName = "com.starcor.mango",
        )
        assertOpenApp(
            text = "启动酷喵",
            title = "CIBN酷喵",
            packageName = "com.youku.iot",
        )
        assertOpenApp(
            text = "我要看云视听极光",
            title = "云视听极光",
            packageName = "com.ktcp.tvvideo",
        )
    }

    @Test
    fun routesHomeUtilityCommands() {
        assertTrue(interpreter.interpret("我要投屏") is HomeVoiceCommand.OpenCast)
        assertTrue(interpreter.interpret("打开应用列表") is HomeVoiceCommand.OpenLocalApps)
        assertTrue(interpreter.interpret("服务中心续费") is HomeVoiceCommand.OpenServiceCenter)
        assertTrue(interpreter.interpret("打开 Wi-Fi 设置") is HomeVoiceCommand.OpenSettings)
    }

    @Test
    fun unknownCommandStaysSafe() {
        assertTrue(interpreter.interpret("帮我随便操作一下") is HomeVoiceCommand.Unknown)
    }

    private fun assertOpenApp(
        text: String,
        title: String,
        packageName: String,
    ) {
        val command = interpreter.interpret(text)
        assertTrue(command is HomeVoiceCommand.OpenApp)
        val openApp = command as HomeVoiceCommand.OpenApp
        assertEquals(title, openApp.title)
        assertEquals(packageName, openApp.packageName)
    }
}
