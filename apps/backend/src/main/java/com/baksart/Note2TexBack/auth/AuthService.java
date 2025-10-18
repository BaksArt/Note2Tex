package com.baksart.Note2TexBack.auth;

import com.baksart.Note2TexBack.auth.dto.AuthDtos.*;
import com.baksart.Note2TexBack.config.AppProps;
import com.baksart.Note2TexBack.user.User;
import com.baksart.Note2TexBack.user.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepo users;
    private final VerificationTokenRepo vtRepo;
    private final PasswordResetTokenRepo prRepo;
    private final PasswordEncoder pe;
    private final MailService mail;
    private final JwtService jwt;
    private final AppProps props;


    @Value("${app.frontend.verifyRedirect:app://auth/verify}")
    String verifyRedirect;
    @Value("${app.frontend.resetRedirect:http://localhost:8080/reset-ok}")
    String resetRedirect;

    public AuthService(UserRepo users, VerificationTokenRepo vtRepo, PasswordResetTokenRepo prRepo,
                       PasswordEncoder pe, MailService mail, JwtService jwt, AppProps props) {
        this.users=users; this.vtRepo=vtRepo; this.prRepo=prRepo; this.pe=pe; this.mail=mail; this.jwt=jwt; this.props = props;
    }

    @Transactional
    public void register(RegisterRequest req) {
        if (users.findByEmail(req.email()).isPresent()) throw conflict("Email занят");
        if (users.findByUsername(req.username()).isPresent()) throw conflict("Логин занят");

        var u = new User();
        u.setEmail(req.email());
        u.setUsername(req.username());
        u.setPasswordHash(pe.encode(req.password()));
        users.save(u);

        createAndSendVerificationToken(u);
    }

    @Transactional
    public void resendVerification(String email) {
        var u = users.findByEmail(email)
                .orElseThrow(() -> bad("Пользователь не найден"));
        if (u.isEmailVerified()) {
            throw conflict("Email уже подтверждён");
        }
        vtRepo.deleteAllByUserId(u.getId());
        createAndSendVerificationToken(u);
    }

    @Transactional
    public String verify(String token) {
        var vt = vtRepo.findByToken(token).orElseThrow(() -> bad("Неверный токен"));
        if (vt.isUsed() || vt.getExpiresAt().isBefore(Instant.now())) throw bad("Токен недействителен");
        var u = vt.getUser(); u.setEmailVerified(true); users.save(u);
        vt.setUsed(true); vtRepo.save(vt);
        return jwt.generateAccessToken(u.getId(), u.getUsername());
    }

    public String login(LoginRequest req) {
        var u = users.findByEmail(req.login()).or(() -> users.findByUsername(req.login()))
                .orElseThrow(() -> unauthorized());
        if (!pe.matches(req.password(), u.getPasswordHash())) throw unauthorized();
        if (!u.isEmailVerified()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email не подтвержден");
        return jwt.generateAccessToken(u.getId(), u.getUsername());
    }

    @Transactional
    public void forgot(ForgotRequest req) {
        var u = users.findByEmail(req.email()).orElse(null);
        if (u == null) return;
        var pr = new PasswordResetToken();
        pr.setUser(u);
        pr.setToken(UUID.randomUUID().toString());
        pr.setExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
        prRepo.save(pr);
        String link = props.getBackendPublicBaseUrl() + "/auth/reset/confirm?token=" + enc(pr.getToken());
        mail.send(u.getEmail(), "Сброс пароля", "<p>Сбросить пароль: <a href='"+link+"'>ссылка</a></p>");
    }

    @Transactional
    public void reset(ResetRequest req) {
        var pr = prRepo.findByToken(req.token()).orElseThrow(() -> bad("Неверный токен"));
        if (pr.isUsed() || pr.getExpiresAt().isBefore(Instant.now())) throw bad("Токен недействителен");
        var u = pr.getUser();
        u.setPasswordHash(pe.encode(req.newPassword()));
        users.save(u);
        pr.setUsed(true); prRepo.save(pr);
    }

    private static ResponseStatusException bad(String m){ return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException conflict(String m){ return new ResponseStatusException(HttpStatus.CONFLICT, m); }
    private static ResponseStatusException unauthorized(){ return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверные учетные данные"); }
    private static String enc(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }


    private void createAndSendVerificationToken(User u) {
        var vt = new VerificationToken();
        vt.setUser(u);
        vt.setToken(UUID.randomUUID().toString());
        vt.setExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
        vtRepo.save(vt);

        String link = props.getBackendPublicBaseUrl() + "/auth/verify?token=" + enc(vt.getToken());
        mail.send(u.getEmail(), "Подтверждение email",
                "<p>Нажмите для подтверждения: <a href='" + link + "'>Подтвердить</a></p>");
    }
}
