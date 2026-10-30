package com.vxplayer.diagnostics

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import com.vxplayer.BuildConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

fun sanitizeCrashText(text: String): String = text
    .replace(Regex("(?i)(?:https?://|content://|file://)[^\\s]+"), "[redacted]")
    .replace(Regex("(?i)(?:[A-Z]:[\\\\/]|/(?:storage|sdcard|data|home|Users)/)[^\\r\\n]+"), "[path redacted]")
    .replace(Regex("(?i)(?:token|password|authorization|cookie|api[_-]?key)\\s*[:=]\\s*[^\\s]+"), "[secret redacted]")
    .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email redacted]").take(96_000)

class VXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalCrashReports.install(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        }
    }
}

object LocalCrashReports {
    @Volatile var lastRoute: String = "startup"
        private set
    fun route(template: String) { lastRoute = template.substringBefore('?').takeIf { it in setOf("library","settings","player","private","web","music") } ?: "other" }
    private val handling = AtomicBoolean(false)
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (handling.compareAndSet(false,true)) runCatching {
                val directory = File(app.filesDir,"crashes").apply { mkdirs() }
                val report = "VXPlayer ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nAndroid ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\nDevice ${Build.MANUFACTURER} ${Build.MODEL}\nThread ${sanitizeCrashText(thread.name)} (${thread.id})\nScreen $lastRoute\n\n${sanitizeCrashText(error.stackTraceToString())}"
                File(directory,"crash-${System.currentTimeMillis()}.txt").writeText(report)
                directory.listFiles()?.filter { it.name.matches(Regex("crash-[0-9]+\\.txt")) }?.sortedByDescending { it.name }?.drop(3)?.forEach { it.delete() }
            }
            if (previous != null) previous.uncaughtException(thread,error) else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }
    fun latest(context: Context): Pair<String,String>? = runCatching {
        File(context.filesDir,"crashes").listFiles()?.filter { it.name.matches(Regex("crash-[0-9]+\\.txt")) }?.maxByOrNull { it.name }?.let { it.name to it.readText().take(100_000) }
    }.getOrNull()
}
