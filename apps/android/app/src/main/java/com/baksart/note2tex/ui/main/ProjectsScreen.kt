package com.baksart.note2tex.ui.main

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.baksart.note2tex.R
import com.baksart.note2tex.data.repo.ExportFormat
import com.baksart.note2tex.domain.model.ProjectItem
import com.baksart.note2tex.presentation.viewmodel.ProjectsViewModel
import com.baksart.note2tex.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private enum class BulkAction { Save, Share }

private var pendingEdit by mutableStateOf<ProjectItem?>(null)
private var editTitle by mutableStateOf("")
private var editDesc by mutableStateOf("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(vm: ProjectsViewModel = viewModel(), onOpenProject: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val settingsVm: SettingsViewModel = viewModel()
    val defaultExport by settingsVm.defaultExport.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selected by remember { mutableStateOf(setOf<String>()) }
    val selectionMode = selected.isNotEmpty()
    fun toggle(id: String) { selected = if (selected.contains(id)) selected - id else selected + id }
    fun clearSelection() { selected = emptySet() }

    var toDeleteSingle by remember { mutableStateOf<ProjectItem?>(null) }

    var showActionSheet by remember { mutableStateOf(false) }
    var showFormatSheet by remember { mutableStateOf(false) }
    var bulkAction by remember { mutableStateOf<BulkAction?>(null) }
    var exporting by remember { mutableStateOf(false) }

    var belowZipFile by remember { mutableStateOf<File?>(null) }

    val createZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { dest: Uri? ->
        if (dest == null) return@rememberLauncherForActivityResult
        belowZipFile?.let { file ->
            ctx.contentResolver.openOutputStream(dest)?.use { os ->
                file.inputStream().use { it.copyTo(os) }
            }
            Toast.makeText(ctx, ctx.getString(R.string.zip_saved), Toast.LENGTH_SHORT).show()
        }
        belowZipFile = null
        exporting = false
        bulkAction = null
        showFormatSheet = false
        clearSelection()
    }

    val client by remember {
        mutableStateOf(
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build()
        )
    }

    suspend fun downloadToCache(url: String, fileName: String): File = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        val out = File(ctx.cacheDir, fileName)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.source()?.use { src ->
                out.sink().buffer().use { sink -> sink.writeAll(src) }
            }
        }
        out
    }

    fun sanitizeName(s: String, fallback: String): String {
        val base = s.ifBlank { fallback }.trim()
        val preserved = base
            .replace("""[\\/:*?"<>|]""".toRegex(), "_")
            .replace("""\s+""".toRegex(), " ")
        return preserved.take(80)
    }

    suspend fun buildZip(
        projects: List<ProjectItem>,
        ids: Set<String>,
        format: ExportFormat
    ): Pair<File, Int> = withContext(Dispatchers.IO) {
        val zipFile = File(ctx.cacheDir, ctx.getString(R.string.zip_filename))
        if (zipFile.exists()) zipFile.delete()
        var skipped = 0
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            for (p in projects) {
                if (!ids.contains(p.id)) continue
                val ext = when (format) {
                    ExportFormat.PDF -> "pdf"
                    ExportFormat.DOCX -> "docx"
                    ExportFormat.LATEX -> "tex"
                }
                val url = when (format) {
                    ExportFormat.PDF -> p.pdfUrl
                    ExportFormat.DOCX -> p.docxUrl
                    ExportFormat.LATEX -> p.texUrl
                }
                if (url.isNullOrBlank()) { skipped++; continue }
                val safeName = sanitizeName(p.title ?: "", p.id)
                val tmp = runCatching { downloadToCache(url, "${p.id}.$ext") }.getOrNull()
                if (tmp == null) { skipped++; continue }
                val entryName = "$safeName.$ext"
                zip.putNextEntry(ZipEntry(entryName))
                tmp.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            if (ids.isNotEmpty() && ids.size == skipped) {
                val msg = ctx.getString(R.string.zip_build_readme)
                zip.putNextEntry(ZipEntry("README.txt"))
                zip.write(msg.toByteArray())
                zip.closeEntry()
            }
        }
        Pair(zipFile, skipped)
    }

    fun shareZip(file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.zip_share_subject))
        }
        ctx.startActivity(
            Intent.createChooser(intent, ctx.getString(R.string.zip_share_title))
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            snack.showSnackbar(msg)
            vm.consumeMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            if (selectionMode) {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = { clearSelection() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showActionSheet = true }, enabled = !exporting) {
                            Icon(Icons.Filled.Upload, contentDescription = stringResource(R.string.cd_export))
                        }
                        IconButton(
                            onClick = {
                                toDeleteSingle = ProjectItem(
                                    id = selected.first(),
                                    title = ctx.getString(R.string.several_projects),
                                    description = null,
                                    status = "ready"
                                )
                            },
                            enabled = !exporting
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete))
                        }
                    },
                    windowInsets = WindowInsets(0.dp),
                    modifier = Modifier.height(64.dp)
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.projects_title)) },
                    actions = {
                        IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                        }
                    },
                    windowInsets = WindowInsets(0.dp),
                    modifier = Modifier.height(64.dp)
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snack, modifier = Modifier.padding(bottom = 96.dp)) }
    ) { p ->
        when {
            state.loading && state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                    LinearProgressIndicator()
                }
            }
            state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.projects_empty))
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 12.dp,
                            top = p.calculateTopPadding(),
                            end = 12.dp,
                            bottom = 0.dp
                        ),
                    contentPadding = PaddingValues(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ProjectRow(
                            item = item,
                            selected = selected.contains(item.id),
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    toggle(item.id)
                                } else {
                                    onOpenProject(item.id)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) selected = setOf(item.id) else toggle(item.id)
                            },
                            onRename = {
                                pendingEdit = item
                                editTitle = item.title.orEmpty()
                                editDesc = item.description.orEmpty()
                            },
                            onDelete = { toDeleteSingle = item }
                        )
                    }
                    if (state.hasProcessing) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.Center
                            ) { LinearProgressIndicator(Modifier.fillMaxWidth(0.5f)) }
                        }
                    }
                }
            }
        }
    }

    if (toDeleteSingle != null) {
        val deletingMany = selectionMode && selected.size > 1
        AlertDialog(
            onDismissRequest = { toDeleteSingle = null },
            title = {
                Text(
                    if (deletingMany) stringResource(R.string.delete) else stringResource(R.string.delete)
                )
            },
            text = {
                val name = toDeleteSingle?.title ?: stringResource(R.string.untitled)
                Text(
                    if (deletingMany)
                        stringResource(R.string.really_delete_many, selected.size)
                    else
                        stringResource(R.string.really_delete_one, name)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = if (deletingMany) selected else setOf(toDeleteSingle!!.id)
                        toDeleteSingle = null
                        scope.launch {
                            try {
                                ids.forEach { vm.delete(it) }
                                clearSelection()
                            } catch (_: Throwable) { }
                        }
                    },
                    enabled = !state.loading
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { toDeleteSingle = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingEdit != null) {
        AlertDialog(
            onDismissRequest = { pendingEdit = null },
            title = { Text(stringResource(R.string.rename_project)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text(stringResource(R.string.description)) },
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = pendingEdit!!.id
                        val title = editTitle.ifBlank { null }
                        val desc = editDesc.ifBlank { null }
                        pendingEdit = null
                        vm.rename(id, title, desc)
                    },
                    enabled = !state.loading
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingEdit = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showActionSheet) {
        ModalBottomSheet(onDismissRequest = { showActionSheet = false }) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.action), style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        bulkAction = BulkAction.Save
                        showActionSheet = false
                        showFormatSheet = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.save_to_device)) }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        bulkAction = BulkAction.Share
                        showActionSheet = false
                        showFormatSheet = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.share_file)) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showFormatSheet) {
        ModalBottomSheet(onDismissRequest = { showFormatSheet = false }) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.format), style = MaterialTheme.typography.titleMedium)

                fun doExport(fmt: ExportFormat) {
                    val action = bulkAction ?: return
                    if (exporting) return
                    exporting = true
                    scope.launch {
                        try {
                            val (zip, skipped) = buildZip(state.items, selected, fmt)
                            belowZipFile = zip
                            if (action == BulkAction.Share) {
                                shareZip(zip)
                                exporting = false
                                bulkAction = null
                                showFormatSheet = false
                                clearSelection()
                            } else {
                                createZip.launch(ctx.getString(R.string.zip_filename))
                            }
                            if (skipped > 0) {
                                snack.showSnackbar(
                                    message = ctx.getString(R.string.skipped_n, skipped)
                                )
                            }
                        } catch (t: Throwable) {
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.export_error_toast, t.message ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                            exporting = false
                            bulkAction = null
                            showFormatSheet = false
                        }
                    }
                }

                fun formatLabel(fmt: ExportFormat): String = when (fmt) {
                    ExportFormat.PDF   -> ctx.getString(R.string.fmt_pdf)
                    ExportFormat.DOCX  -> ctx.getString(R.string.fmt_docx)
                    ExportFormat.LATEX -> ctx.getString(R.string.fmt_tex)
                }

                val ordered = listOf(ExportFormat.PDF, ExportFormat.DOCX, ExportFormat.LATEX)
                    .sortedBy { if (it == defaultExport) 0 else 1 }

                ordered.forEachIndexed { idx, fmt ->
                    val isPrimary = idx == 0
                    if (isPrimary) {
                        Button(
                            enabled = !exporting,
                            onClick = { doExport(fmt) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(formatLabel(fmt)) }
                    } else {
                        androidx.compose.material3.OutlinedButton(
                            enabled = !exporting,
                            onClick = { doExport(fmt) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(formatLabel(fmt)) }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}


@Composable
private fun ProjectRow(
    item: ProjectItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val (bg, fg) = statusColors(item.status)
    var menuOpen by remember { mutableStateOf(false) }

    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!item.imageUrl.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(item.imageUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(stringResource(R.string.no_image), style = MaterialTheme.typography.labelSmall)
                }
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.title?.ifBlank { stringResource(R.string.untitled) } ?: stringResource(R.string.untitled),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    StatusChip(text = statusText(item.status), bg = bg, fg = fg)

                    if (!selectionMode) {
                        Box {
                            TinyIconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename)) },
                                    onClick = { menuOpen = false; onRename() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = { menuOpen = false; onDelete() }
                                )
                            }
                        }
                    }
                }

                if (!item.description.isNullOrBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun statusText(raw: String): String = when (raw.lowercase()) {
    "ready" -> stringResource(R.string.status_ready)
    "processing" -> stringResource(R.string.status_processing)
    "failed" -> stringResource(R.string.status_failed)
    else -> raw
}
@Composable
private fun TinyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun StatusChip(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun statusColors(raw: String): Pair<Color, Color> = when (raw.lowercase()) {
    "ready" -> Color(0xFF2E7D32) to Color(0xFFFFFFFF)
    "processing" -> Color(0xFFF9A825) to Color(0xFF000000)
    "failed" -> Color(0xFFC62828) to Color(0xFFFFFFFF)
    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}
