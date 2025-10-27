package com.baksart.note2tex.presentation.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baksart.note2tex.data.repo.ExportRepository
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
    val pdfFile: File? = null,
    val mode: OcrMode = OcrMode.PreviewPdf,
    val pdfPageIndex: Int = 0,
    val pdfPageCount: Int = 0,
    val docxConverting: Boolean = false,
    val docxFile: File? = null
)

class OcrViewModel(
    app: Application,
    private val repo: OcrRepository
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, OcrRepository(app))

    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state

    private val exportRepo by lazy { ExportRepository(getApplication()) }

    fun start(imageUri: Uri) {
        _state.value = OcrUiState(loading = true, imageUri = imageUri)
        viewModelScope.launch {
            try {
                val r = repo.infer(imageUri)
                val pdfUrl = pickPdfUrl(r)
                val pdfFile = pdfUrl?.let { withContext(Dispatchers.IO) { repo.downloadPdf(it) } }

                _state.value = _state.value.copy(
                    loading = false,
                    message = null,
                    latex = r.latex.orEmpty(),
                    pdfFile = pdfFile,
                    mode = OcrMode.PreviewPdf
                )

                startDocxConversionIfNeeded()
            } catch (t: Throwable) {
                val msg = "${t.javaClass.simpleName}: ${t.message ?: "без сообщения"}"
                Log.e("OcrVM", "Infer failed", t)
                _state.value = _state.value.copy(loading = false, message = msg)
            }
        }
    }

    private fun pickPdfUrl(resp: Any): String? = try {
        val k = resp::class
        val url = k.members.firstOrNull { it.name == "pdf_url" }?.call(resp) as? String
        val path = k.members.firstOrNull { it.name == "pdf_path" }?.call(resp) as? String
        when {
            !url.isNullOrBlank() -> url
            !path.isNullOrBlank() -> {
                val base = repo.baseUrl.trimEnd('/') + "/files/"
                val name = path.substringAfterLast('\\').substringAfterLast('/')
                if (name.isNotBlank()) base + name else null
            }
            else -> null
        }
    } catch (_: Throwable) { null }

    fun setMode(mode: OcrMode) { _state.value = _state.value.copy(mode = mode) }
    fun updateLatex(newLatex: String) { _state.value = _state.value.copy(latex = newLatex) }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    fun reconvertDocx() {
        _state.value = _state.value.copy(docxFile = null)
        startDocxConversionIfNeeded()
    }

    private fun startDocxConversionIfNeeded() {
        val s = _state.value
        if (s.latex.isBlank() || s.docxConverting) return

        _state.value = s.copy(docxConverting = true)
        viewModelScope.launch {
            try {
                val f = exportRepo.requestDocx(_state.value.latex)
                _state.value = _state.value.copy(docxConverting = false, docxFile = f)
            } catch (t: Throwable) {
                Log.e("OcrVM", "DOCX convert failed", t)
                _state.value = _state.value.copy(
                    docxConverting = false,
                    message = "DOCX: ${t.message ?: "ошибка конвертации"}"
                )
            }
        }
    }
}
