package com.baksart.note2tex.domain.model

data class AccountMe(
    val id: String,
    val email: String,
    val username: String,
    val plan: String,
    val planExpiresAt: String?,
    val monthProjects: Int,
    val totalProjects: Int
)
