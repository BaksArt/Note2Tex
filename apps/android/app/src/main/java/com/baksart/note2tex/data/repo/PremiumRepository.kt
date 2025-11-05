package com.baksart.note2tex.data.repo

import com.baksart.note2tex.data.network.PremiumApi
import com.baksart.note2tex.data.network.PremiumGrantReq

sealed interface PremiumResult {
    data object Ok : PremiumResult
    data class Err(val code: Int?, val message: String?) : PremiumResult
}

class PremiumRepository(private val api: PremiumApi) {
    suspend fun grant(period: String, secret: String = "ivrtime"): PremiumResult = try {
        val body = PremiumGrantReq(period)
        val r = api.grant(secret, body)
        if (r.isSuccessful) PremiumResult.Ok else PremiumResult.Err(r.code(), r.errorBody()?.string())
    } catch (t: Throwable) {
        PremiumResult.Err(null, t.message)
    }

    suspend fun revoke(secret: String = "ivrtime"): PremiumResult = try {
        val r = api.revoke(secret)
        if (r.isSuccessful) PremiumResult.Ok else PremiumResult.Err(r.code(), r.errorBody()?.string())
    } catch (t: Throwable) {
        PremiumResult.Err(null, t.message)
    }
}
