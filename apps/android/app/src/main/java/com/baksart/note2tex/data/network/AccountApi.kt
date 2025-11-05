package com.baksart.note2tex.data.network

import com.baksart.note2tex.domain.model.AccountMe
import retrofit2.Response
import retrofit2.http.GET

interface AccountApi {
    @GET("/account/me")
    suspend fun me(): Response<AccountMe>
}
