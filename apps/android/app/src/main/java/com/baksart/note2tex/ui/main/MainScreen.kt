package com.baksart.note2tex.ui.main

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen(
    onCreateProject: () -> Unit
) {
    var tab by remember { mutableStateOf(MainTab.Home) }

    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateProject,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.offset(y = 10.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Создать проект")
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
                        label = "Проекты",
                        selected = tab == MainTab.Home,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor
                    ) { tab = MainTab.Home }

                    Spacer(Modifier.width(56.dp))

                    NavItem(
                        icon = Icons.Filled.Settings,
                        label = "Настройки",
                        selected = tab == MainTab.Settings,
                        selectedColor = selectedColor,
                        unselectedColor = unselectedColor
                    ) { tab = MainTab.Settings }
                }
            }
        }
    ) { innerPadding ->
        when (tab) {
            MainTab.Home -> ProjectsScreen()
            MainTab.Settings -> SettingsScreen()
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
