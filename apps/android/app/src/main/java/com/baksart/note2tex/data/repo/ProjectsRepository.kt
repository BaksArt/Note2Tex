package com.baksart.note2tex.data.repo

import android.content.Context
import android.util.Log
import com.baksart.note2tex.data.storage.TokenStore
import com.baksart.note2tex.domain.model.ProjectItem
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class ProjectsRepository(
    context: Context,
    private val baseUrl: String = "https://note2tex.baksart.ru"
) {
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

    private val logger = HttpLoggingInterceptor { m -> Log.d("HTTP", m) }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logger)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, ProjectItem::class.java)
    private val listAdapter = moshi.adapter<List<ProjectItem>>(listType)

    @JsonClass(generateAdapter = true)
    data class UpdateProjectReq(val title: String? = null, val description: String? = null)
    private val updateAdapter = moshi.adapter(UpdateProjectReq::class.java)

    suspend fun listProjects(): List<ProjectItem> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/projects"
        val req: Request = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("GET /projects -> ${resp.code} ${resp.message}: ${raw.take(300)}")
            }
            listAdapter.fromJson(raw) ?: emptyList()
        }
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/projects/$id"
        val req = Request.Builder().url(url).delete().build()
        client.newCall(req).execute().use { resp ->
            if (resp.code != 204 && !resp.isSuccessful) {
                val raw = resp.body?.string().orEmpty()
                throw IllegalStateException("DELETE /projects/$id -> ${resp.code} ${resp.message}: ${raw.take(300)}")
            }
        }
    }

    suspend fun updateProject(id: String, title: String?, description: String?) = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/projects/$id"
        val bodyJson = updateAdapter.toJson(UpdateProjectReq(title = title, description = description))
        val req = Request.Builder()
            .url(url)
            .patch(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val raw = resp.body?.string().orEmpty()
                throw IllegalStateException("PATCH /projects/$id -> ${resp.code} ${resp.message}: ${raw.take(300)}")
            }
        }
    }
}
