package com.baksart.note2tex.domain.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProjectItem(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val status: String,
    @Json(name = "imageUrl") val imageUrl: String? = null,
    @Json(name = "texUrl")   val texUrl: String? = null,
    @Json(name = "pdfUrl")   val pdfUrl: String? = null,
    @Json(name = "docxUrl")  val docxUrl: String? = null
) {

}