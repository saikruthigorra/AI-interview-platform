package com.interviewiq.web;

import com.interviewiq.domain.AppUser;
import com.interviewiq.domain.Role;
import com.interviewiq.repo.AppUserRepository;
import com.interviewiq.web.dto.UserResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account administration. ADMIN only — enforced by method security, not just the UI. */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final AppUserRepository users;

    public UserController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    @PatchMapping("/{id}/role")
    @Transactional
    public UserResponse changeRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
        Role role = Role.valueOf(body.getOrDefault("role", "").trim().toUpperCase(Locale.ROOT));
        user.setRole(role);
        return UserResponse.from(users.save(user));
    }

    @PatchMapping("/{id}/enabled")
    @Transactional
    public UserResponse setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
        user.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        return UserResponse.from(users.save(user));
    }
}
