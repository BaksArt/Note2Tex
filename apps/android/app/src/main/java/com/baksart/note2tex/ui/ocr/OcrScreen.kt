package com.baksart.note2tex.ui.ocr

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.baksart.note2tex.data.repo.ExportFormat
import com.baksart.note2tex.presentation.viewmodel.ExportItem
import com.baksart.note2tex.presentation.viewmodel.OcrMode
import com.baksart.note2tex.presentation.viewmodel.OcrViewModel
import com.baksart.note2tex.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.io.File
import com.baksart.note2tex.R
private enum class ExportAction { Save, Share }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    imageUri: Uri? = null,
    projectId: String? = null,
    onExport: () -> Unit = {},
    onBack: () -> Unit = {},
    vm: OcrViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current

    val settingsVm: SettingsViewModel = viewModel()
    val defaultExport by settingsVm.defaultExport.collectAsState()


    val screenScope = rememberCoroutineScope()

    LaunchedEffect(projectId, imageUri) {
        when {
            projectId != null -> vm.openExisting(projectId)
            imageUri != null   -> vm.start(imageUri)
            else -> { /* нет входных данных */ }
        }
    }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            Log.e("OcrScreen", "OCR error: $msg")
            snack.showSnackbar(msg)
            vm.consumeMessage()
        }
    }

    var showActionSheet by remember { mutableStateOf(false) }
    var showFormatSheet by remember { mutableStateOf(false) }
    var exportAction by remember { mutableStateOf<ExportAction?>(null) }

    var pendingToSave by remember { mutableStateOf<ExportItem?>(null) }
    var lastExported by remember { mutableStateOf<ExportItem?>(null) }

    var askRating by remember { mutableStateOf(false) }
    var selectedStars by remember { mutableStateOf(0) }
    var ratingComment by remember { mutableStateOf("") }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri: Uri? ->
        destUri?.let { uri ->
            pendingToSave?.let { item ->
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    item.file.inputStream().use { it.copyTo(os) }
                }
                lastExported = item
                pendingToSave = null
                askRating = true
            }
        }
    }

    fun shareFile(item: ExportItem) {
        val shareDir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val src = item.file
        val safe = if (src.canonicalPath.startsWith(ctx.cacheDir.canonicalPath)) src
        else File(shareDir, src.name).apply { src.copyTo(this, overwrite = true) }

        val shareUri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", safe
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, shareUri)
            type = item.mime
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.export_subject))
            putExtra(Intent.EXTRA_TEXT, ctx.getString(R.string.export_title))
        }
        ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.export_share_title)))

        lastExported = item
        askRating = true
    }

    suspend fun buildExportItem(fmt: ExportFormat): ExportItem =
        when (fmt) {
            ExportFormat.PDF -> {
                val f: File = state.pdfFile ?: error(ctx.getString(R.string.pdf_missing))
                ExportItem(f, "application/pdf", "note2tex.pdf")
            }
            ExportFormat.LATEX -> {
                val tex = File(ctx.cacheDir, "note2tex.tex").apply {
                    writeText(state.latex.ifBlank { "% empty\n" })
                }
                ExportItem(tex, "application/x-tex", "note2tex.tex")
            }
            ExportFormat.DOCX -> {
                val url = state.docxUrl ?: error(ctx.getString(R.string.docx_missing))
                val f = vm.downloadToCache(url, "note2tex.docx")
                ExportItem(
                    f,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "note2tex.docx"
                )
            }
        }

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface,
        labelColor = primary,
        selectedContainerColor = primary,
        selectedLabelColor = onPrimary
    )

    var editExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = editExpanded) {
        editExpanded = false
        focus.clearFocus()
    }
    BackHandler(enabled = !editExpanded) {
        onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(ctx.getString(R.string.ocr_title)) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { onBack() }) {
                        androidx.compose.material3.Icon(Icons.Filled.ArrowBack, contentDescription = ctx.getString(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showActionSheet = true },
                        enabled = !state.loading && (state.pdfFile != null || state.latex.isNotBlank())
                    ) { Text(ctx.getString(R.string.export)) }
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
            if (!editExpanded && state.imageUri != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(state.imageUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Divider()
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (state.loading) {
                    Column(
                        Modifier.fillMaxSize().imePadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(0.6f))
                        Spacer(Modifier.height(12.dp))
                        Text(ctx.getString(R.string.processing))
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().imePadding()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isPreview = state.mode == OcrMode.PreviewPdf
                            val isEdit = state.mode == OcrMode.EditLatex

                            FilterChip(
                                selected = isPreview,
                                onClick = { vm.setMode(OcrMode.PreviewPdf) },
                                label = { Text(ctx.getString(R.string.view)) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isPreview,
                                    borderColor = primary,
                                    selectedBorderColor = primary
                                )
                            )
                            FilterChip(
                                selected = isEdit,
                                onClick = { vm.setMode(OcrMode.EditLatex) },
                                label = { Text(ctx.getString(R.string.edit)) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isEdit,
                                    borderColor = primary,
                                    selectedBorderColor = primary
                                )
                            )

                            if (isEdit && state.edited) {
                                Spacer(Modifier.width(4.dp))
                                TextButton(
                                    onClick = { screenScope.launch { vm.rebuild() } },
                                    enabled = !state.loading
                                ) { Text(ctx.getString(R.string.rebuild)) }
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
                                ) { Text(ctx.getString(R.string.pdf_missing)) }
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
                                        .verticalScroll(rememberScrollState())
                                        .onFocusChanged { st ->
                                            if (st.isFocused && !editExpanded) editExpanded = true
                                        },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    placeholder = { Text("LaTeX…") },
                                    keyboardActions = KeyboardActions(onDone = {  })
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showActionSheet) {
        ModalBottomSheet(onDismissRequest = { showActionSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(ctx.getString(R.string.action), style = MaterialTheme.typography.titleMedium)
                androidx.compose.material3.Button(
                    onClick = {
                        exportAction = ExportAction.Save
                        showActionSheet = false
                        showFormatSheet = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(ctx.getString(R.string.save_to_device)) }

                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        exportAction = ExportAction.Share
                        showActionSheet = false
                        showFormatSheet = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(ctx.getString(R.string.share_file)) }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showFormatSheet) {
        ModalBottomSheet(onDismissRequest = { showFormatSheet = false }) {
            val pdfAvailable = state.pdfFile != null
            val latexAvailable = state.latex.isNotBlank()
            val docxAvailable = state.docxUrl != null

            var exporting by remember { mutableStateOf(false) }

            fun onPick(fmt: ExportFormat) {
                if (exporting) return
                val action = exportAction ?: return
                exporting = true
                screenScope.launch {
                    try {
                        val item = buildExportItem(fmt)
                        when (action) {
                            ExportAction.Save -> {
                                pendingToSave = item
                                createDocument.launch(item.suggestedName)
                            }
                            ExportAction.Share -> shareFile(item)
                        }
                    } catch (t: Throwable) {
                        Toast.makeText(ctx, ctx.getString(R.string.error_file_make,t.message), Toast.LENGTH_SHORT).show()
                    } finally {
                        exporting = false
                        exportAction = null
                        showFormatSheet = false
                    }
                }
            }

            fun enabled(fmt: ExportFormat) = when (fmt) {
                ExportFormat.PDF   -> pdfAvailable
                ExportFormat.DOCX  -> docxAvailable
                ExportFormat.LATEX -> latexAvailable
            }
            fun label(fmt: ExportFormat) = when (fmt) {
                ExportFormat.PDF   -> ctx.getString(R.string.fmt_pdf)
                ExportFormat.DOCX  -> ctx.getString(R.string.fmt_docx)
                ExportFormat.LATEX -> ctx.getString(R.string.fmt_tex)
            }

            val ordered = listOf(ExportFormat.PDF, ExportFormat.DOCX, ExportFormat.LATEX)
                .sortedBy { if (it == defaultExport) 0 else 1 }

            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(ctx.getString(R.string.format), style = MaterialTheme.typography.titleMedium)

                ordered.forEachIndexed { idx, fmt ->
                    val isPrimary = idx == 0
                    if (isPrimary) {
                        Button(
                            enabled = !exporting && enabled(fmt),
                            onClick = { onPick(fmt) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label(fmt)) }
                    } else {
                        OutlinedButton(
                            enabled = !exporting && enabled(fmt),
                            onClick = { onPick(fmt) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label(fmt)) }
                    }
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
                        if (selectedStars in 1..5 && state.projectId != null) {
                            screenScope.launch {
                                try {
                                    vm.sendRating(selectedStars, ratingComment)
                                    snack.showSnackbar(ctx.getString(R.string.rate_thanks))
                                } catch (t: Throwable) {
                                    snack.showSnackbar(ctx.getString(R.string.rate_error))
                                } finally {
                                    askRating = false
                                    ratingComment = ""
                                    selectedStars = 0
                                }
                            }
                        } else {
                            askRating = false
                            ratingComment = ""
                            selectedStars = 0
                        }
                    }) { Text(ctx.getString(R.string.status_ready)) }
                }
            },
            title = { Text(ctx.getString(R.string.rate_title)) },
            text = {
                Column {
                    Text(ctx.getString(R.string.rate_prompt))
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
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = ratingComment,
                        onValueChange = { ratingComment = it },
                        placeholder = { Text(ctx.getString(R.string.rate_comment_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        )
    }
}
