package com.baksart.note2tex.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import com.baksart.note2tex.data.storage.TokenStore
import com.baksart.note2tex.domain.model.OcrResponse
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class OcrRepository(
    private val context: Context,
    val baseUrl: String = "https://note2tex.baksart.ru"
) {
    @JsonClass(generateAdapter = true)
    data class ProjectResp(
        val id: String,
        val title: String? = null,
        val description: String? = null,
        val status: String,
        @Json(name = "imageUrl") val imageUrl: String? = null,
        @Json(name = "texUrl") val texUrl: String? = null,
        @Json(name = "pdfUrl") val pdfUrl: String? = null,
        @Json(name = "docxUrl") val docxUrl: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class ErrorDetail(val detail: String?)

    @JsonClass(generateAdapter = true)
    data class PatchTexReq(val tex: String)

    @JsonClass(generateAdapter = true)
    data class RatingReq(val value: Int, val comment: String? = null)

    private val logger = HttpLoggingInterceptor { m -> Log.d("HTTP", m) }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val tokenStore = TokenStore(context)
    private val authInterceptor = Interceptor { chain ->
        val token = tokenStore.getTokenBlocking()
        val req = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else chain.request()
        chain.proceed(req)

    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logger)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val projectAdapter = moshi.adapter(ProjectResp::class.java)
    private val errorAdapter = moshi.adapter(ErrorDetail::class.java)
    private val patchAdapter = moshi.adapter(PatchTexReq::class.java)

    private val ratingAdapter = moshi.adapter(RatingReq::class.java)

    suspend fun createProjectPublic(imageUri: Uri): String = withContext(Dispatchers.IO) {
        createProject(imageUri)
    }

    suspend fun pollProjectUntilReadyPublic(id: String): ProjectResp = withContext(Dispatchers.IO) {
        pollProjectUntilReady(id)
    }

    suspend fun fetchTextPublic(url: String): String = withContext(Dispatchers.IO) {
        fetchText(url)
    }

    suspend fun downloadPdf(pdfUrl: String): File = downloadFile(pdfUrl, "ocr_result.pdf")

    suspend fun downloadFile(url: String, outName: String): File = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        val outFile = File(context.cacheDir, outName)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Download failed: ${resp.code}")
            resp.body?.source()?.use { src ->
                outFile.sink().buffer().use { sink -> sink.writeAll(src) }
            }
        }
        outFile
    }

    suspend fun infer(imageUri: Uri): OcrResponse = withContext(Dispatchers.IO) {
        val projectId = createProject(imageUri)
        val ready = pollProjectUntilReady(projectId)
        val latex = ready.texUrl?.let { fetchText(it) }.orEmpty()
        OcrResponse(
            latex = latex,
            blocks = null,
            tex_url = ready.texUrl,
            csv_url = null,
            pdf_url = ready.pdfUrl,
            tex_path = null,
            csv_path = null,
            pdf_path = null,
            time_ms = null,
            model_version = null,
            detector_weights = null
        ).copy(docx_url = ready.docxUrl)
    }

    suspend fun updateProjectTex(id: String, tex: String) = withContext(Dispatchers.IO) {
        val url = buildUrl("/projects/$id")
        val json = patchAdapter.toJson(PatchTexReq(tex))
        val req = Request.Builder()
            .url(url)
            .patch(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            Log.d("OcrRepo", "PATCH /projects/$id -> ${resp.code}: ${raw.take(500)}")
            if (!resp.isSuccessful) {
                val ed = runCatching { errorAdapter.fromJson(raw) }.getOrNull()
                val msg = ed?.detail ?: "HTTP ${resp.code} ${resp.message}"
                throw IllegalStateException("Проект $id: $msg")
            }
        }
    }


    private fun buildUrl(path: String) = baseUrl.trimEnd('/') + path

    private fun createProject(imageUri: Uri): String {
        val resolver = context.contentResolver
        val mime = resolver.getType(imageUri) ?: "image/jpeg"
        val name = "input." + if (mime.contains("png")) "png" else "jpg"

        val temp = copyToTemp(imageUri, name)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", temp.name, temp.asRequestBody(mime.toMediaType()))
            .build()

        val url = buildUrl("/projects")
        val req = Request.Builder().url(url).post(body).build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            Log.d("OcrRepo", "POST /projects -> ${resp.code}: ${raw.take(500)}")
            if (!resp.isSuccessful) {
                val ed = runCatching { errorAdapter.fromJson(raw) }.getOrNull()
                val msg = ed?.detail ?: "HTTP ${resp.code} ${resp.message}"
                throw IllegalStateException(msg)
            }
            val prj = projectAdapter.fromJson(raw)
                ?: throw IllegalStateException("Bad JSON: ${raw.take(500)}")
            return prj.id
        }
    }

    internal suspend fun pollProjectUntilReady(id: String): ProjectResp {
        val url = buildUrl("/projects/$id")
        val maxAttempts = 60
        repeat(maxAttempts) { attempt ->
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                Log.d("OcrRepo", "GET /projects/$id -> ${resp.code}: ${raw.take(500)}")

                if (!resp.isSuccessful) {
                    val ed = runCatching { errorAdapter.fromJson(raw) }.getOrNull()
                    val msg = ed?.detail ?: "HTTP ${resp.code} ${resp.message}"
                    throw IllegalStateException("Проект $id: $msg")
                }

                val prj = projectAdapter.fromJson(raw)
                    ?: throw IllegalStateException("Bad JSON: ${raw.take(500)}")

                when (prj.status.lowercase()) {
                    "ready" -> return prj
                    "failed", "error" -> throw IllegalStateException("Проект $id завершился с ошибкой")
                    else -> {
                        if (attempt < maxAttempts - 1) delay(5_000) else
                            throw IOException("Таймаут ожидания готовности проекта $id")
                    }
                }
            }
        }
        throw IOException("Таймаут ожидания готовности проекта $id")
    }

    internal fun fetchText(url: String): String {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Fetch text failed: ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun copyToTemp(uri: Uri, fileName: String): File {
        val tmp = File(context.cacheDir, "upload_${UUID.randomUUID().toString().take(8)}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out -> input.copyTo(out) }
        } ?: throw IllegalArgumentException("openInputStream вернул null для $uri")
        return tmp
    }

    suspend fun getProjectOnce(id: String): ProjectResp = withContext(Dispatchers.IO) {
        val url = buildUrl("/projects/$id")
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val ed = runCatching { errorAdapter.fromJson(raw) }.getOrNull()
                val msg = ed?.detail ?: "HTTP ${resp.code} ${resp.message}"
                throw IllegalStateException("Проект $id: $msg")
            }
            projectAdapter.fromJson(raw)
                ?: throw IllegalStateException("Bad JSON: ${raw.take(500)}")
        }
    }

    suspend fun sendRating(id: String, value: Int, comment: String?) = withContext(Dispatchers.IO) {
        val url = buildUrl("/projects/$id/rating")
        val body = ratingAdapter.toJson(RatingReq(value = value, comment = comment))
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            Log.d("OcrRepo", "POST /projects/$id/rating -> ${resp.code}: ${raw.take(500)}")
            if (!resp.isSuccessful) {
                val ed = runCatching { errorAdapter.fromJson(raw) }.getOrNull()
                val msg = ed?.detail ?: "HTTP ${resp.code} ${resp.message}"
                throw IllegalStateException("Рейтинг: $msg")
            }
        }
    }

}
