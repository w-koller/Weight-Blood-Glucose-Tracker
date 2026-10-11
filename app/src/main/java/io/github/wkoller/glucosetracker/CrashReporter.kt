package io.github.wkoller.glucosetracker

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Keeps a short report of the last crash so it can be shared from Settings › About this app.
 * The report holds the error and the phone and app versions only, never any health records.
 */
object CrashReporter {
    private const val DIRECTORY = "reports"
    private const val FILE_NAME = "problem-report.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(appContext, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Saves [error] as the latest problem report. Also used for errors the app recovers from. */
    fun record(context: Context, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("Weight & Glucose Tracker problem report")
            appendLine("Time: ${ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            append(trace)
        }
        file(context).writeText(report)
    }

    fun file(context: Context): File = File(File(context.filesDir, DIRECTORY).apply { mkdirs() }, FILE_NAME)

    fun latest(context: Context): File? = file(context).takeIf { it.exists() && it.length() > 0 }

    fun delete(context: Context) {
        file(context).delete()
    }
}
