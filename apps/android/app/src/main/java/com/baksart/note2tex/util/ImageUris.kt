package com.baksart.note2tex.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object ImageUris {
    private fun newFile(context: Context, prefix: String, suffix: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        return File(dir, "${prefix}_${timeStamp}${suffix}")
    }

    fun newCameraUri(context: Context): Uri {
        val file = newFile(context, "camera", ".jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun newCroppedUri(context: Context): Uri {
        val file = newFile(context, "crop", ".jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
