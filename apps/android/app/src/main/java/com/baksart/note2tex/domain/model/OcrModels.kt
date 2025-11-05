package com.baksart.note2tex.domain.model

data class OcrBlock(
    val idx: Int,
    val bbox: List<Int>,
    val latex: String,
    val crop_path: String? = null,
    val bin_path: String? = null
)

data class OcrResponse(
    val latex: String,
    val blocks: List<OcrBlock>? = null,
    val tex_url: String? = null,
    val csv_url: String? = null,
    val pdf_url: String? = null,
    val docx_url: String? = null,
    val tex_path: String? = null,
    val csv_path: String? = null,
    val pdf_path: String? = null,
    val time_ms: Long? = null,
    val model_version: String? = null,
    val detector_weights: String? = null
)
