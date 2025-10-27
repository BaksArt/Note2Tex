package com.baksart.note2tex.data.repo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.baksart.note2tex.domain.model.OcrResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrRepository(
    private val context: Context,
    val baseUrl: String = "http://192.168.50.68:8003"
) {


    private val logger = HttpLoggingInterceptor { m -> Log.d("HTTP", m) }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    private val client = OkHttpClient.Builder()
        .addInterceptor(logger)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val respAdapter = moshi.adapter(OcrResponse::class.java)

    // OcrRepository.kt
    suspend fun infer(imageUri: Uri): OcrResponse = withContext(Dispatchers.IO) {
        val r = context.contentResolver
        val mime = r.getType(imageUri) ?: "image/jpeg"
        val name = "input." + if (mime.contains("png")) "png" else "jpg"

        val input = r.openInputStream(imageUri)
            ?: throw IllegalArgumentException("openInputStream вернул null для URI=$imageUri")

        val bytes = runCatching { input.readBytes() }
            .onFailure { input.close() }
            .getOrElse { t ->
                throw IllegalStateException("Не удалось прочитать байты изображения: ${t.javaClass.simpleName}: ${t.message}")
            }
        input.close()

        if (bytes.isEmpty()) {
            throw IllegalStateException("Файл пуст: 0 bytes для URI=$imageUri (mime=$mime)")
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", name, bytes.toRequestBody(mime.toMediaType()))
            .build()

        val url = "$baseUrl/v1/infer"
        Log.d("OcrRepo", "POST $url (mime=$mime, bytes=${bytes.size})")

        val req = Request.Builder().url(url).post(body).build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            Log.d("OcrRepo", "Resp ${resp.code} ${resp.message} from $url\n${raw.take(2000)}")
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} ${resp.message}: ${raw.take(2000)}")
            }
            respAdapter.fromJson(raw) ?: throw IllegalStateException("Bad JSON: ${raw.take(2000)}")
        }
    }



    /** Скачивает PDF по прямому URL в cacheDir и возвращает File */
    suspend fun downloadPdf(pdfUrl: String): File = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(pdfUrl).get().build()
        val outFile = File(context.cacheDir, "ocr_result.pdf")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Failed to download pdf: ${resp.code}")
            resp.body?.source()?.use { src ->
                outFile.sink().buffer().use { sink -> sink.writeAll(src) }
            }
        }
        outFile
    }

    fun pickPdfUrl(r: OcrResponse): String? {
        return when {
            !r.pdf_url.isNullOrBlank() -> r.pdf_url
            !r.pdf_path.isNullOrBlank() -> {
                val name = r.pdf_path.substringAfterLast('\\').substringAfterLast('/')
                if (name.isNotBlank()) "$baseUrl/files/$name" else null
            }
            else -> null
        }
    }

}
