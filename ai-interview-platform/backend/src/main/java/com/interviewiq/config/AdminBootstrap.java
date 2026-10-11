package com.interviewiq.config;

import com.interviewiq.domain.AppUser;
import com.interviewiq.domain.Role;
import com.interviewiq.repo.AppUserRepository;
import com.interviewiq.security.AuthService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures exactly one administrator exists so the first person through the door
 * can administer the platform.
 *
 * Password resolution order:
 *   1. APP_ADMIN_PASSWORD (use this in any shared/production environment);
 *   2. otherwise a strong random password is generated and logged ONCE at boot —
 *      copy it from the log, sign in, then change it.
 *
 * An existing admin is never modified, so restarts never reset a changed password.
 */
@Component
@Order(10)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder, AppProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = props.getSecurity().getAdminEmail().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        String configured = props.getSecurity().getAdminPassword();
        boolean generated = configured == null || configured.isBlank();
        String password = generated ? AuthService.generatePassword() : configured;

        String policyError = com.interviewiq.security.PasswordPolicy.validate(password);
        if (policyError != null) {
            // A weak env password is a configuration error worth shouting about.
            log.error("APP_ADMIN_PASSWORD rejected by the password policy ({}); generating a strong one instead", policyError);
            password = AuthService.generatePassword();
            generated = true;
        }

        users.save(new AppUser("Platform Admin", email, passwordEncoder.encode(password), Role.ADMIN));
        if (generated) {
            log.warn("""
                    ┌─────────────────────────────────────────────────────────────────────┐
                    │  Bootstrap admin created                                            │
                    │  email:    {}                                  │
                    │  password: {}                      │
                    │  This password is shown once. Sign in and change it immediately.    │
                    └─────────────────────────────────────────────────────────────────────┘
                    """, email, password);
        } else {
            log.info("Bootstrap admin {} created from APP_ADMIN_PASSWORD", email);
        }
    }
}
