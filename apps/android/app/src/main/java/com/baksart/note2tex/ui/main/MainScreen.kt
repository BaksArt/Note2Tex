package com.baksart.note2tex.ui.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.baksart.note2tex.util.ImageUris
import com.yalantis.ucrop.UCrop
import com.baksart.note2tex.R
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onCreateProject: () -> Unit = {},
    onImportReady: (Uri) -> Unit,
    onOpenProject: (String) -> Unit,
    onChangePassword: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenSubscription: () -> Unit,
    vmSettings: com.baksart.note2tex.presentation.viewmodel.SettingsViewModel
) {

    val cropPrimary     = MaterialTheme.colorScheme.primary.toArgb()
    val cropOnPrimary   = MaterialTheme.colorScheme.onPrimary.toArgb()
    val cropSurface     = MaterialTheme.colorScheme.surface.toArgb()
    val cropOnSurface   = MaterialTheme.colorScheme.onSurface.toArgb()
    val cropBackground  = MaterialTheme.colorScheme.background.toArgb()

    val isDarkTheme = isSystemInDarkTheme()

    var tab by remember { mutableStateOf(MainTab.Home) }
    val fabVisible = tab == MainTab.Home

    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    var showSourceSheet by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val activity = ctx as Activity

    LaunchedEffect(tab) {
        if (!fabVisible) showSourceSheet = false
    }

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraLaunch by remember { mutableStateOf(false) }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val result = UCrop.getOutput(res.data!!)
            result?.let { onImportReady(it) }
        }
    }

    val startCrop: (Activity, Uri) -> Unit = { act, source ->
        val dst = ImageUris.newCroppedUri(act)

        val options = UCrop.Options().apply {
            setToolbarTitle(ctx.getString(R.string.crop_title))
            setToolbarColor(cropSurface)
            setToolbarWidgetColor(cropOnSurface)

            setRootViewBackgroundColor(cropBackground)
            setActiveControlsWidgetColor(cropPrimary)

            setHideBottomControls(true)
            setFreeStyleCropEnabled(true)
            setShowCropGrid(true)
            setStatusBarLight(!isDarkTheme)
        }

        val u = UCrop.of(source, dst).withOptions(options)

        val intent = u.getIntent(act).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        cropLauncher.launch(intent)
    }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { startCrop(activity, it) }
    }

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok: Boolean ->
        if (ok) cameraOutputUri?.let { startCrop(activity, it) }
    }

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCameraLaunch) {
            pendingCameraLaunch = false
            cameraOutputUri?.let { takePicture.launch(it) }
        } else {
            pendingCameraLaunch = false
        }
    }

    fun openCamera() {
        val out = ImageUris.newCameraUri(ctx)
        cameraOutputUri = out
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            takePicture.launch(out)
        } else {
            pendingCameraLaunch = true
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (fabVisible) {
                FloatingActionButton(
                    onClick = { showSourceSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.offset(y = 10.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = ctx.getString(R.string.add_image))
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            BottomAppBar(
                tonalElevation = 3.dp,
                contentPadding = PaddingValues(horizontal = 36.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavItem(
                        icon = Icons.Filled.Home,
                        label = ctx.getString(R.string.projects_title),
                        selected = tab == MainTab.Home,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor
                    ) { tab = MainTab.Home }

                    Spacer(Modifier.width(if (fabVisible) 56.dp else 0.dp))

                    NavItem(
                        icon = Icons.Filled.Settings,
                        label = ctx.getString(R.string.settings_title),
                        selected = tab == MainTab.Settings,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor
                    ) { tab = MainTab.Settings }
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                MainTab.Home -> ProjectsScreen(
                    onOpenProject = onOpenProject
                )
                MainTab.Settings -> SettingsScreen(
                    vm = vmSettings,
                    onChangePassword = onChangePassword,
                    onLoggedOut = onLoggedOut,
                    onOpenSubscription = onOpenSubscription
                )
            }
        }
    }

    if (showSourceSheet && fabVisible) {
        ModalBottomSheet(onDismissRequest = { showSourceSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(ctx.getString(R.string.image_source), style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        showSourceSheet = false
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(ctx.getString(R.string.from_file)) }

                OutlinedButton(
                    onClick = {
                        showSourceSheet = false
                        openCamera()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(ctx.getString(R.string.from_camera)) }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) selectedColor else unselectedColor
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) selectedColor else unselectedColor,
            textAlign = TextAlign.Center
        )
    }
}

enum class MainTab { Home, Settings }
