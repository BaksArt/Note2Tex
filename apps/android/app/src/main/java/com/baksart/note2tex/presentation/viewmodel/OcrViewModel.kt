package com.baksart.note2tex.presentation.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baksart.note2tex.R
import com.baksart.note2tex.data.repo.OcrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class OcrMode { PreviewPdf, EditLatex }

data class OcrUiState(
    val loading: Boolean = false,
    val message: String? = null,
    val imageUri: Uri? = null,
    val latex: String = "",
    val serverLatex: String = "",
    val pdfFile: File? = null,
    val mode: OcrMode = OcrMode.PreviewPdf,
    val pdfPageIndex: Int = 0,
    val pdfPageCount: Int = 0,
    val docxUrl: String? = null,
    val projectId: String? = null,
    val edited: Boolean = false
)

class OcrViewModel(
    app: Application,
    private val repo: OcrRepository
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, OcrRepository(app))

    private val ctx = app.applicationContext

    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state

    private fun msg(@StringRes id: Int, vararg args: Any): String =
        ctx.getString(id, *args.map { it as Any }.toTypedArray())

    fun start(imageUri: Uri) {
        _state.value = OcrUiState(loading = true, imageUri = imageUri)
        viewModelScope.launch {
            try {
                val projectId = repo.createProjectPublic(imageUri)
                val ready = repo.pollProjectUntilReadyPublic(projectId)
                val latex = ready.texUrl?.let { withContext(Dispatchers.IO) { repo.fetchTextPublic(it) } }.orEmpty()
                val pdfUrl = ready.pdfUrl
                val pdfFile = pdfUrl?.let { withContext(Dispatchers.IO) { repo.downloadPdf(it) } }
                _state.value = _state.value.copy(
                    loading = false,
                    message = null,
                    imageUri = imageUri,
                    latex = latex,
                    serverLatex = latex,
                    pdfFile = pdfFile,
                    docxUrl = ready.docxUrl,
                    mode = OcrMode.PreviewPdf,
                    projectId = projectId,
                    edited = false
                )
            } catch (t: Throwable) {
                Log.e("OcrVM", "Infer failed", t)
                _state.value = _state.value.copy(
                    loading = false,
                    message = t.message ?: msg(R.string.error_unknown)
                )
            }
        }
    }

    fun openExisting(projectId: String) {
        _state.value = OcrUiState(loading = true, projectId = projectId)
        viewModelScope.launch {
            try {
                val pr = repo.getProjectOnce(projectId)
                val imgUri = pr.imageUrl?.let { Uri.parse(it) }
                when (pr.status.lowercase()) {
                    "ready" -> {
                        val latex = pr.texUrl?.let { withContext(Dispatchers.IO) { repo.fetchTextPublic(it) } }.orEmpty()
                        val pdfFile = pr.pdfUrl?.let { withContext(Dispatchers.IO) { repo.downloadPdf(it) } }
                        _state.value = _state.value.copy(
                            loading = false,
                            message = null,
                            imageUri = imgUri,
                            latex = latex,
                            serverLatex = latex,
                            pdfFile = pdfFile,
                            docxUrl = pr.docxUrl,
                            mode = OcrMode.PreviewPdf,
                            projectId = projectId,
                            edited = false
                        )
                    }
                    "processing" -> {
                        _state.value = _state.value.copy(
                            loading = true,
                            message = null,
                            imageUri = imgUri,
                            latex = "",
                            serverLatex = "",
                            pdfFile = null,
                            docxUrl = pr.docxUrl,
                            mode = OcrMode.PreviewPdf,
                            projectId = projectId,
                            edited = false
                        )
                        launch {
                            try {
                                val ready = repo.pollProjectUntilReadyPublic(projectId)
                                val latex = ready.texUrl?.let {
                                    withContext(Dispatchers.IO) { repo.fetchTextPublic(it) }
                                }.orEmpty()
                                val pdfFile = ready.pdfUrl?.let {
                                    withContext(Dispatchers.IO) { repo.downloadPdf(it) }
                                }
                                _state.value = _state.value.copy(
                                    loading = false,
                                    latex = latex,
                                    serverLatex = latex,
                                    pdfFile = pdfFile,
                                    docxUrl = ready.docxUrl,
                                    mode = OcrMode.PreviewPdf,
                                    edited = false
                                )
                            } catch (t: Throwable) {
                                _state.value = _state.value.copy(
                                    loading = false,
                                    message = t.message ?: msg(R.string.error_unknown)
                                )
                            }
                        }
                    }
                    else -> {
                        _state.value = _state.value.copy(
                            loading = false,
                            imageUri = imgUri,
                            message = msg(R.string.project_status, pr.status)
                        )
                    }
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = t.message ?: msg(R.string.error_unknown)
                )
            }
        }
    }

    fun setMode(mode: OcrMode) { _state.value = _state.value.copy(mode = mode) }

    fun updateLatex(newLatex: String) {
        val was = _state.value
        _state.value = was.copy(latex = newLatex, edited = (newLatex != was.serverLatex))
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    fun rebuild() {
        val st = _state.value
        val pid = st.projectId ?: run {
            _state.value = st.copy(message = msg(R.string.project_not_created))
            return
        }
        val tex = st.latex
        _state.value = st.copy(loading = true)
        viewModelScope.launch {
            try {
                repo.updateProjectTex(pid, tex)
                val ready = repo.pollProjectUntilReadyPublic(pid)
                val newLatex = ready.texUrl?.let { withContext(Dispatchers.IO) { repo.fetchTextPublic(it) } }.orEmpty()
                val pdfUrl = ready.pdfUrl
                val pdfFile = pdfUrl?.let { withContext(Dispatchers.IO) { repo.downloadPdf(it) } }
                _state.value = _state.value.copy(
                    loading = false,
                    message = null,
                    latex = newLatex,
                    serverLatex = newLatex,
                    edited = false,
                    pdfFile = pdfFile,
                    docxUrl = ready.docxUrl,
                    mode = OcrMode.PreviewPdf
                )
            } catch (t: Throwable) {
                Log.e("OcrVM", "Rebuild failed", t)
                _state.value = _state.value.copy(
                    loading = false,
                    message = t.message ?: msg(R.string.error_unknown)
                )
            }
        }
    }

    suspend fun downloadToCache(url: String, outName: String): File =
        withContext(Dispatchers.IO) { repo.downloadFile(url, outName) }

    fun sendRating(value: Int, comment: String?) {
        val pid = state.value.projectId ?: return
        viewModelScope.launch {
            try {
                repo.sendRating(pid, value, comment ?: "")
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    message = msg(R.string.rating_send_error, t.message ?: msg(R.string.error_unknown))
                )
            }
        }
    }
}
