package com.baksart.note2tex.ui.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PdfViewer(
    file: File,
    modifier: Modifier = Modifier
) {
    var renderer by remember(file) { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember(file) { mutableStateOf<ParcelFileDescriptor?>(null) }

    var pageIndex by remember { mutableStateOf(0) }
    var pageCount by remember { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { (config.screenWidthDp.dp).toPx() }.roundToInt()

    DisposableEffect(file) {
        try {
            Log.d("PdfViewer", "open file=${file.absolutePath}, size=${file.length()}")
            if (!file.exists() || file.length() == 0L) {
                error = "PDF пустой или не найден (${file.absolutePath})"
            } else {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd!!)
                pageCount = renderer?.pageCount ?: 0
                if (pageCount == 0) error = "PDF без страниц"
            }
        } catch (t: Throwable) {
            error = "Не удалось открыть PDF: ${t.message}"
            Log.e("PdfViewer", "open error", t)
        }

        onDispose {
            try {
                renderer?.close()
                pfd?.close()
            } catch (_: Throwable) {}
            renderer = null
            pfd = null
            bitmap = null
        }
    }

    LaunchedEffect(renderer, pageIndex, screenWidthPx) {
        val r = renderer ?: return@LaunchedEffect
        if (pageCount == 0) return@LaunchedEffect

        val idx = pageIndex.coerceIn(0, pageCount - 1)
        bitmap = withContext(Dispatchers.IO) {
            try {
                r.openPage(idx).use { page ->
                    val pw = max(page.width, 1)
                    val ph = max(page.height, 1)

                    val targetW = max(screenWidthPx, 1)
                    val targetH = max((ph.toFloat() / pw * targetW).roundToInt(), 1)

                    Log.d("PdfViewer", "render page=$idx pw=$pw ph=$ph -> target=$targetW x $targetH")

                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE) // белый фон
                    val dest = Rect(0, 0, targetW, targetH)
                    page.render(bmp, dest, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            } catch (t: Throwable) {
                Log.e("PdfViewer", "render error", t)
                error = "Ошибка рендера: ${t.message}"
                null
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                error != null -> Text("Ошибка: $error")
                bitmap != null -> Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
                else -> Spacer(Modifier.fillMaxSize())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (pageIndex > 0) pageIndex-- },
                    enabled = pageIndex > 0
                ) { Text("Пред.") }

                Text("Стр. ${if (pageCount == 0) 0 else pageIndex + 1} / $pageCount")

                Button(
                    onClick = { if (pageIndex < pageCount - 1) pageIndex++ },
                    enabled = pageIndex < pageCount - 1
                ) { Text("След.") }
            }
        }
    }
}
