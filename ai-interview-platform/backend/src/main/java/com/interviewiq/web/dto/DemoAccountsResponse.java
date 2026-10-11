package com.interviewiq.web.dto;

import java.util.List;

/**
 * The seeded demo logins, for the sign-in screen's "try it now" panel.
 *
 * Only ever advertises accounts that exist <em>and</em> whose stored BCrypt hash
 * actually matches the reported password, so the panel can never show credentials
 * that fail to sign in.
 */
public record DemoAccountsResponse(
        /** False when seeding is off or no demo account resolved. */
        boolean enabled,
        List<Account> accounts
) {
    public record Account(String role, String name, String email, String password) {
    }
}
