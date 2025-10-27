package com.baksart.note2tex.presentation.viewmodel

import java.io.File

data class ExportItem(
    val file: File,
    val mime: String,
    val suggestedName: String
)
