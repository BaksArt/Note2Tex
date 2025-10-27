package com.baksart.note2tex.di

import android.app.Application
import com.baksart.note2tex.data.network.ApiClient
import com.baksart.note2tex.data.repo.AuthRepository
import com.baksart.note2tex.data.storage.TokenStore

object ServiceLocator {
    // TODO: заменить на реальную юрлку
    private const val BASE_URL = "http://192.168.50.68:8080/"

    fun authRepository(app: Application): AuthRepository {
        val client = ApiClient(app, BASE_URL)
        val api = client.authApi
        val store = TokenStore(app)
        return AuthRepository(api, store, client)
    }
}
