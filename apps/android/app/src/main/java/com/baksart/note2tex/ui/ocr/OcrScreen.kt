package com.baksart.note2tex.ui.ocr

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.baksart.note2tex.data.repo.ExportFormat
import com.baksart.note2tex.data.repo.ExportRepository
import com.baksart.note2tex.presentation.viewmodel.ExportItem
import com.baksart.note2tex.presentation.viewmodel.OcrMode
import com.baksart.note2tex.presentation.viewmodel.OcrViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    imageUri: Uri,
    onExport: () -> Unit = {},
    vm: OcrViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(imageUri) { vm.start(imageUri) }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            Log.e("OcrScreen", "OCR error: $msg")
            //Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            //snack.showSnackbar(msg)
            vm.consumeMessage()
        }
    }

    val exportRepo = remember { ExportRepository(ctx) }
    var showExportSheet by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var pendingToSave by remember { mutableStateOf<ExportItem?>(null) }
    var lastSaved by remember { mutableStateOf<ExportItem?>(null) }

    var docxWorking by remember { mutableStateOf(false) }

    var askRating by remember { mutableStateOf(false) }
    var selectedStars by remember { mutableStateOf(0) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri: Uri? ->
        destUri?.let { uri ->
            pendingToSave?.let { item ->
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    item.file.inputStream().use { it.copyTo(os) }
                }
                lastSaved = item
                pendingToSave = null

                askRating = true
            }
        }
    }

    fun shareFile(item: ExportItem) {
        val shareDir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val src = item.file
        val safe = if (src.canonicalPath.startsWith(ctx.cacheDir.canonicalPath)) {
            src
        } else {
            File(shareDir, src.name).apply { src.copyTo(this, overwrite = true) }
        }

        val shareUri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", safe
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, shareUri)
            type = item.mime
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "Note2Tex Export")
            putExtra(Intent.EXTRA_TEXT, "Распознанный документ из Note2Tex")
        }
        ctx.startActivity(Intent.createChooser(intent, "Поделиться документом"))
    }


    LaunchedEffect(exportFormat) {
        val fmt = exportFormat ?: return@LaunchedEffect
        try {
            val item = withContext(Dispatchers.IO) {
                when (fmt) {
                    ExportFormat.PDF -> {
                        val f: File = state.pdfFile ?: error("PDF отсутствует")
                        ExportItem(f, "application/pdf", "note2tex.pdf")
                    }
                    ExportFormat.LATEX -> {
                        val tex = File(ctx.cacheDir, "note2tex.tex").apply {
                            writeText(state.latex.ifBlank { "% empty\n" })
                        }
                        ExportItem(tex, "application/x-tex", "note2tex.tex")
                    }
                    ExportFormat.DOCX -> {
                        val ready = state.docxFile
                        val f: File = if (ready != null) {
                            ready
                        } else {
                            docxWorking = true
                            try {
                                exportRepo.requestDocx(state.latex) // suspend
                            } finally {
                                docxWorking = false
                            }
                        }
                        ExportItem(
                            f,
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "note2tex.docx"
                        )
                    }
                }
            }
            pendingToSave = item
            createDocument.launch(item.suggestedName)
        } catch (t: Throwable) {
            Toast.makeText(ctx, "Не удалось подготовить файл: ${t.message}", Toast.LENGTH_SHORT).show()
        } finally {
            exportFormat = null
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Распознавание") },
                actions = {
                    TextButton(
                        onClick = { showExportSheet = true },
                        enabled = !state.loading && (state.pdfFile != null || state.latex.isNotBlank())
                    ) { Text("Экспорт") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { p ->
        Column(
            modifier = Modifier
                .padding(p)
                .fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Divider()

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (state.loading) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(0.6f))
                        Spacer(Modifier.height(12.dp))
                        Text("Обработка…")
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.mode == OcrMode.PreviewPdf,
                                onClick = { vm.setMode(OcrMode.PreviewPdf) },
                                label = { Text("Просмотр") }
                            )
                            FilterChip(
                                selected = state.mode == OcrMode.EditLatex,
                                onClick = { vm.setMode(OcrMode.EditLatex) },
                                label = { Text("Редактировать") }
                            )
                        }

                        if (state.docxConverting || docxWorking) {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                Text("Конвертация DOCX…", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        when (state.mode) {
                            OcrMode.PreviewPdf -> {
                                state.pdfFile?.let {
                                    PdfViewer(
                                        file = it,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } ?: Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) { Text("PDF не получен") }
                            }
                            OcrMode.EditLatex -> {
                                var text by remember(state.latex) { mutableStateOf(state.latex) }
                                LaunchedEffect(text) { vm.updateLatex(text) }
                                TextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                        .verticalScroll(rememberScrollState()),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    placeholder = { Text("LaTeX…") }
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { vm.reconvertDocx() },
                                        enabled = !state.docxConverting && state.latex.isNotBlank()
                                    ) { Text("Перегенерировать DOCX") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportSheet) {
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Экспортировать как", style = MaterialTheme.typography.titleMedium)

                // PDF
                androidx.compose.material3.Button(
                    enabled = state.pdfFile != null,
                    onClick = {
                        showExportSheet = false
                        exportFormat = ExportFormat.PDF
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("PDF") }

                // LaTeX
                androidx.compose.material3.Button(
                    enabled = state.latex.isNotBlank(),
                    onClick = {
                        showExportSheet = false
                        exportFormat = ExportFormat.LATEX
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("LaTeX (.tex)") }

                // DOCX
                androidx.compose.material3.OutlinedButton(
                    enabled = state.latex.isNotBlank() && !docxWorking,
                    onClick = {
                        showExportSheet = false
                        exportFormat = ExportFormat.DOCX
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val suffix = when {
                        state.docxConverting || docxWorking -> " (генерируется…)"
                        state.docxFile == null -> " (будет собран)"
                        else -> ""
                    }
                    Text("DOCX$suffix")
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (askRating) {
        AlertDialog(
            onDismissRequest = { askRating = false },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        askRating = false
                        lastSaved?.let { shareFile(it) }
                    }) { Text("Поделиться") }
                    TextButton(onClick = { askRating = false }) { Text("Готово") }
                }
            },
            dismissButton = null,
            title = { Text("Оцените результат") },
            text = {
                Column {
                    Text("Помогла ли генерация? Оцените качество результата.")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { i ->
                            Text(
                                text = if (i <= selectedStars) "⭐" else "☆",
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickable { selectedStars = i },
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
            }
        )
    }
}
