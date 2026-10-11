package com.interviewiq.security;

import com.interviewiq.config.AppProperties;
import com.interviewiq.domain.AppUser;
import com.interviewiq.domain.Role;
import com.interviewiq.repo.AppUserRepository;
import com.interviewiq.web.dto.AuthResponse;
import com.interviewiq.web.dto.LoginRequest;
import com.interviewiq.web.dto.RegisterRequest;
import com.interviewiq.web.dto.UserResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle: registration, login, current-user lookup.
 *
 * Security properties worth calling out:
 *   - passwords are BCrypt-hashed (strength 12) and never logged or returned;
 *   - login errors are deliberately vague ("Invalid email or password") so the
 *     endpoint can't be used to enumerate accounts;
 *   - the same vague error is returned for disabled accounts;
 *   - failed attempts feed the rate limiter, which locks the email+IP pair.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_LOGIN_ERROR = "Invalid email or password";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final AppProperties props;

    public AuthService(AppUserRepository users,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LoginRateLimiter rateLimiter,
                       AppProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("An account with that email already exists");
        }
        String policyError = PasswordPolicy.validate(request.password());
        if (policyError != null) {
            throw new IllegalArgumentException(policyError);
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        // Signup can never mint an ADMIN — the highest role it can request is RECRUITER.
        Role role = request.role() == null || request.role().isBlank()
                ? Role.RECRUITER
                : Role.valueOf(request.role().trim().toUpperCase(Locale.ROOT));
        if (role == Role.ADMIN) {
            role = Role.RECRUITER;
        }

        AppUser user = users.save(new AppUser(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                role));
        log.info("Registered {} account for {}", role, mask(email));
        return tokenFor(user, clientIp);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);

        LoginRateLimiter.Status status = rateLimiter.checkAllowed(email, clientIp);
        if (!status.allowed()) {
            throw new RateLimitedException(status.reason(), status.retryAfterSeconds());
        }

        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);
        // Always run a hash comparison so response time doesn't reveal whether the account exists.
        boolean passwordOk = passwordEncoder.matches(
                request.password() == null ? "" : request.password(),
                user != null ? user.getPasswordHash() : DUMMY_HASH);

        if (user == null || !passwordOk || !user.isEnabled()) {
            rateLimiter.recordFailure(email, clientIp);
            int left = rateLimiter.remainingAttempts(email, clientIp);
            log.warn("Failed login for {} from {} ({} attempt(s) left before lockout)", mask(email), clientIp, left);
            throw new BadCredentialsException(left);
        }

        rateLimiter.recordSuccess(email, clientIp);
        user.setLastLoginAt(Instant.now());
        users.save(user);
        log.info("Login OK for {} ({})", mask(email), user.getRole());
        return tokenFor(user, clientIp);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String email) {
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException(0));
        return UserResponse.from(user);
    }

    public long tokenTtlSeconds() {
        return Math.max(5, props.getSecurity().getTokenTtlMinutes()) * 60;
    }

    private AuthResponse tokenFor(AppUser user, String clientIp) {
        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getName(), user.getRole().name());
        return new AuthResponse(token, jwtService.expiryFor(token), UserResponse.from(user), tokenTtlSeconds());
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    /** A valid BCrypt hash of a random value, used to equalise timing on unknown emails. */
    private static final String DUMMY_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEeO1sYq7d9wZmD1n3v0aKx0hHqQ3jJ4E2u";

    public static class BadCredentialsException extends RuntimeException {
        private final int attemptsRemaining;

        public BadCredentialsException(int attemptsRemaining) {
            super(GENERIC_LOGIN_ERROR);
            this.attemptsRemaining = attemptsRemaining;
        }

        public int getAttemptsRemaining() {
            return attemptsRemaining;
        }
    }

    public static class RateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;

        public RateLimitedException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** Generates a policy-compliant random password (used for the bootstrap admin). */
    public static String generatePassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String core = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return "Iq!" + core + random.nextInt(90_000);
    }
}
