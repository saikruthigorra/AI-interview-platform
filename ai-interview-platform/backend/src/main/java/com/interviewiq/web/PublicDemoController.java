package com.interviewiq.web;

import com.interviewiq.config.AppProperties;
import com.interviewiq.domain.AppUser;
import com.interviewiq.repo.AppUserRepository;
import com.interviewiq.web.dto.DemoAccountsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public helper that tells a first-time visitor which demo logins exist, so the
 * sign-in screen can offer one-click "sign in as recruiter / as student".
 *
 * Deliberately defensive:
 *   - inactive unless seeding is enabled (production sets APP_SEED=false → empty);
 *   - an account is reported only if it exists with the expected role AND the
 *     password we would print actually matches its BCrypt hash, so the panel can
 *     never advertise a credential that fails to sign in.
 */
@RestController
@RequestMapping("/api/public")
public class PublicDemoController {

    private static final String FALLBACK_PASSWORD = "Demo!Access2026";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public PublicDemoController(AppUserRepository users, PasswordEncoder passwordEncoder, AppProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @GetMapping("/demo-accounts")
    public DemoAccountsResponse demoAccounts() {
        if (!props.getSeed().isEnabled()) {
            return new DemoAccountsResponse(false, List.of());
        }

        // The configured password, plus the built-in fallback the seeder uses when
        // APP_DEMO_PASSWORD fails the password policy.
        List<String> candidates = new ArrayList<>();
        String configured = props.getDemo().getPassword();
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured.trim());
        }
        if (!candidates.contains(FALLBACK_PASSWORD)) {
            candidates.add(FALLBACK_PASSWORD);
        }

        List<DemoAccountsResponse.Account> accounts = new ArrayList<>();
        for (String[] spec : List.of(
                new String[]{"RECRUITER", "recruiter@interviewiq.dev"},
                new String[]{"STUDENT", "student@interviewiq.dev"})) {
            Optional<AppUser> found = users.findByEmailIgnoreCase(spec[1]);
            if (found.isEmpty() || !found.get().getRole().name().equals(spec[0]) || !found.get().isEnabled()) {
                continue;
            }
            AppUser user = found.get();
            for (String password : candidates) {
                if (passwordEncoder.matches(password, user.getPasswordHash())) {
                    accounts.add(new DemoAccountsResponse.Account(
                            user.getRole().name(), user.getName(), user.getEmail(), password));
                    break;
                }
            }
        }
        return new DemoAccountsResponse(!accounts.isEmpty(), accounts);
    }
}
