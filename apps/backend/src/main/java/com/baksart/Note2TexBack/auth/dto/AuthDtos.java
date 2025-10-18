package com.baksart.Note2TexBack.auth.dto;

import jakarta.validation.constraints.*;

public class AuthDtos {
    public record RegisterRequest(
            @Email String email,
            @NotBlank String username,
            @Pattern(regexp="^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$",
                    message="Пароль: минимум 8 символов, буквы и цифры") String password
    ) {}

    public record LoginRequest(@NotBlank String login, @NotBlank String password) {}

    public record TokenResponse(String accessToken) {}

    public record ForgotRequest(@Email String email) {}
    public record ResetRequest(@NotBlank String token,
                               @Pattern(regexp="^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$") String newPassword) {}
}
