package com.baksart.note2tex.data.network

import com.baksart.note2tex.domain.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("/auth/register")
    suspend fun register(@Body body: RegisterReq): Response<Unit>

    @POST("/auth/resend-verification")
    suspend fun resendVerification(@Body body: ResendVerificationReq): Response<Unit>

    @POST("/auth/login")
    suspend fun login(@Body body: LoginReq): Response<TokenRes>

    @POST("/auth/forgot")
    suspend fun forgot(@Body body: ForgotReq): Response<Unit>

    @POST("/auth/reset")
    suspend fun reset(@Body body: ResetPwdReq): Response<Unit>
}
