package com.baksart.note2tex.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class PremiumGrantReq(val period: String)
interface PremiumApi {
    @POST("/premium/grant")
    suspend fun grant(
        @Query("secret") secret: String,
        @Body body: PremiumGrantReq
    ): Response<Unit>
    @POST("/premium/revoke")
    suspend fun revoke(@Query("secret") secret: String): Response<Unit>
}
