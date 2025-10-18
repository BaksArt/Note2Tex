package com.baksart.Note2TexBack.auth;

import com.baksart.Note2TexBack.auth.dto.AuthDtos.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;
    @Value("${app.frontend.verifyRedirect:app://auth/verify}") String verifyRedirect;
    @Value("${app.frontend.resetRedirect:app://auth/reset-ok}") String resetRedirect;

    public AuthController(AuthService auth){ this.auth=auth; }

    @PostMapping("/register") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody RegisterRequest req) { auth.register(req); }

    @GetMapping("/verify")
    public void verify(@RequestParam String token, HttpServletResponse resp) throws IOException {
        var access = auth.verify(token);
        var url = verifyRedirect + "?accessToken=" + URLEncoder.encode(access, StandardCharsets.UTF_8);
        resp.sendRedirect(url);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req){ return new TokenResponse(auth.login(req)); }

    @PostMapping("/forgot") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgot(@Valid @RequestBody ForgotRequest req){ auth.forgot(req); }

    @GetMapping("/reset/confirm")
    public void resetConfirm(@RequestParam String token, HttpServletResponse resp) throws IOException {
        resp.sendRedirect(resetRedirect + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }

    @PostMapping("/reset") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest req){ auth.reset(req); }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@RequestBody Map<String, String> body) {
        var email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email обязателен");
        }
        auth.resendVerification(email);
    }


}
