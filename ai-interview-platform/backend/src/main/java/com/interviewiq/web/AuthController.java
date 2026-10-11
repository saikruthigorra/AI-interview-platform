package com.interviewiq.web;

import com.interviewiq.security.AuthService;
import com.interviewiq.security.PasswordPolicy;
import com.interviewiq.web.dto.AuthResponse;
import com.interviewiq.web.dto.LoginRequest;
import com.interviewiq.web.dto.RegisterRequest;
import com.interviewiq.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final com.interviewiq.repo.AppUserRepository users;

    public AuthController(AuthService authService, PasswordEncoder passwordEncoder,
                          com.interviewiq.repo.AppUserRepository users) {
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return authService.register(request, clientIp(http));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, clientIp(http));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }

    /** Rotate the password for the signed-in account. */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @RequestBody Map<String, String> body) {
        String current = body.getOrDefault("currentPassword", "");
        String next = body.getOrDefault("newPassword", "");
        String confirm = body.getOrDefault("confirmPassword", "");
        if (!next.equals(confirm)) {
            throw new IllegalArgumentException("New passwords do not match");
        }
        String policyError = PasswordPolicy.validate(next);
        if (policyError != null) {
            throw new IllegalArgumentException(policyError);
        }
        var user = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AuthService.BadCredentialsException(0));
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new AuthService.BadCredentialsException(0);
        }
        if (passwordEncoder.matches(next, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(next));
        users.save(user);
    }

    /** Stateless logout: the client discards its token. Present for API completeness. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Nothing server-side to invalidate — tokens are short-lived and client-held.
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
