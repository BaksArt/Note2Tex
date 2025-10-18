package com.baksart.note2tex.data.repo

import com.baksart.note2tex.data.network.ApiClient
import com.baksart.note2tex.data.network.AuthApi
import com.baksart.note2tex.data.storage.TokenStore
import com.baksart.note2tex.domain.model.*
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed interface AuthResult<out T> {
    data class Ok<T>(val data: T) : AuthResult<T>
    data class Err(val code: Int?, val error: String?, val message: String?) : AuthResult<Nothing>
}

class AuthRepository(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
    apiClient: ApiClient
) {
    private val errorAdapter: JsonAdapter<ErrorRes> =
        apiClient.moshi().adapter(ErrorRes::class.java)

    private fun <T> Response<T>.toErr(): AuthResult.Err {
        val txt = errorBody()?.string().orEmpty()
        val parsed = runCatching { errorAdapter.fromJson(txt) }.getOrNull()
        return AuthResult.Err(code(), parsed?.error, parsed?.message ?: txt)
    }

    suspend fun register(email: String, username: String, password: String): AuthResult<Unit> = safe {
        val r = api.register(RegisterReq(email, username, password))
        if (r.isSuccessful) AuthResult.Ok(Unit) else r.toErr()
    }

    suspend fun resendVerification(email: String): AuthResult<Unit> = safe {
        val r = api.resendVerification(ResendVerificationReq(email))
        if (r.isSuccessful) AuthResult.Ok(Unit) else r.toErr()
    }

    suspend fun login(login: String, password: String): AuthResult<Unit> = safe {
        val r = api.login(LoginReq(login, password))
        if (r.isSuccessful) {
            val token = r.body()?.accessToken.orEmpty()
            tokenStore.setToken(token)
            AuthResult.Ok(Unit)
        } else r.toErr()
    }

    suspend fun forgot(email: String): AuthResult<Unit> = safe {
        val r = api.forgot(ForgotReq(email))
        if (r.isSuccessful) AuthResult.Ok(Unit) else r.toErr()
    }

    suspend fun reset(token: String, newPassword: String): AuthResult<Unit> = safe {
        val r = api.reset(ResetPwdReq(token, newPassword))
        if (r.isSuccessful) AuthResult.Ok(Unit) else r.toErr()
    }

    suspend fun saveAccessToken(accessToken: String): AuthResult<Unit> = safe {
        tokenStore.setToken(accessToken)
        AuthResult.Ok(Unit)
    }

    private inline fun <T> safe(block: () -> AuthResult<T>): AuthResult<T> = try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        AuthResult.Err(null, "network_error", t.message)
    }

    suspend fun hasToken(): Boolean = !tokenStore.getToken().isNullOrBlank()
}
