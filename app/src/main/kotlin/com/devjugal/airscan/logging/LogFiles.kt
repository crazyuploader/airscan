package com.devjugal.airscan.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object LogFiles {
    fun dir(context: Context): File = File(context.filesDir, "logs").apply { mkdirs() }

    /** Newest first. A recording makes one CSV and one GPX file with the same name. */
    fun list(context: Context): List<File> = dir(context).listFiles().orEmpty().sortedByDescending { it.name }

    fun share(
        context: Context,
        file: File,
    ) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension == "gpx") "application/gpx+xml" else "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(send, null))
    }
}

private val LOG_NAME = Regex("""airscan-(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})\.(csv|gpx)""")

/** "2026-11-22 13:23:24 · GPX" for a log file named by the recorder. Other names stay as they are. */
fun logLabel(fileName: String): String {
    val match = LOG_NAME.matchEntire(fileName) ?: return fileName
    val (year, month, day, hour, minute, second, extension) = match.destructured
    return "$year-$month-$day $hour:$minute:$second · ${extension.uppercase()}"
}
