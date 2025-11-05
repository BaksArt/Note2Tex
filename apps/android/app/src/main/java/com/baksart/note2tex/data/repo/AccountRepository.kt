package com.baksart.note2tex.data.repo

import com.baksart.note2tex.data.network.AccountApi
import com.baksart.note2tex.domain.model.AccountMe
import retrofit2.Response

sealed interface AccountResult<out T> {
    data class Ok<T>(val data: T) : AccountResult<T>
    data class Err(val code: Int?, val message: String?) : AccountResult<Nothing>
}

class AccountRepository(
    private val api: AccountApi
) {
    suspend fun me(): AccountResult<AccountMe> = try {
        val r: Response<AccountMe> = api.me()
        if (r.isSuccessful) {
            AccountResult.Ok(r.body()!!)
        } else {
            AccountResult.Err(r.code(), r.errorBody()?.string())
        }
    } catch (t: Throwable) {
        AccountResult.Err(null, t.message)
    }
}
