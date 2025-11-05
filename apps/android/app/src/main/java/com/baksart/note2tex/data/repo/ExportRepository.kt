package com.baksart.note2tex.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.sink
import java.io.File
import java.util.concurrent.TimeUnit


data class ConvertDocxReq(val latex: String, val format: String = "docx")
data class ConvertDocxResp(val docx_url: String?)

class ExportRepository(
    private val context: Context,
    private val baseUrl: String = "https://note2tex.baksart.ru"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val convAdapter = moshi.adapter(ConvertDocxResp::class.java)

    fun downloadToCache(url: String, fileName: String): File {
        val req = Request.Builder().url(url).get().build()
        val outFile = File(context.cacheDir, fileName)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Download failed: ${resp.code} ${resp.message}")
            resp.body?.source()?.use { src ->
                outFile.sink().buffer().use { sink -> sink.writeAll(src) }
            }
        }
        return outFile
    }

    fun requestDocx(latex: String): File {
        val url = "$baseUrl/v1/convert"
        val json = """{"latex":${escapeJson(latex)},"format":"docx"}"""
        val req = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            Log.d("ExportRepo", "convert resp ${resp.code}: ${raw.take(500)}")
            if (!resp.isSuccessful) error("Convert failed: ${resp.code} ${resp.message}: $raw")
            val parsed = convAdapter.fromJson(raw) ?: error("Bad JSON: $raw")
            val docxUrl = parsed.docx_url ?: error("No docx_url in response")
            return downloadToCache(docxUrl, "note2tex_export.docx")
        }
    }

    private fun escapeJson(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
