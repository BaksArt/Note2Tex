package com.baksart.note2tex.domain.model
data class RegisterReq(val email: String, val username: String, val password: String)
data class ResendVerificationReq(val email: String)
data class LoginReq(val login: String, val password: String)
data class ForgotReq(val email: String)
data class ResetPwdReq(val token: String, val newPassword: String)

data class TokenRes(val accessToken: String)
data class ErrorRes(val error: String, val message: String?)
