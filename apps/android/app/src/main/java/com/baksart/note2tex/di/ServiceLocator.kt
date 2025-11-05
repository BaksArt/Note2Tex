package com.baksart.note2tex.di

import android.app.Application
import com.baksart.note2tex.data.network.ApiClient
import com.baksart.note2tex.data.network.PremiumApi
import com.baksart.note2tex.data.repo.AccountRepository
import com.baksart.note2tex.data.repo.AuthRepository
import com.baksart.note2tex.data.repo.PremiumRepository
import com.baksart.note2tex.data.repo.ProjectsRepository
import com.baksart.note2tex.data.storage.TokenStore

object ServiceLocator {
    private const val BASE_URL = "https://note2tex.baksart.ru"

    fun authRepository(app: Application): AuthRepository {
        val client = ApiClient(app, BASE_URL)
        val api = client.authApi
        val store = TokenStore(app)
        return AuthRepository(api, store, client)
    }

    fun projectsRepository(app: Application): ProjectsRepository {
        return ProjectsRepository(app, BASE_URL.trimEnd('/'))
    }

    fun accountRepository(app: Application): AccountRepository {
        val client = ApiClient(app, BASE_URL)
        val api = client.accountApi
        return AccountRepository(api)
    }

    fun premiumRepository(app: Application): PremiumRepository {
        val client = ApiClient(app, BASE_URL)
        val api: PremiumApi = client.premiumApi
        return PremiumRepository(api)
    }

}
