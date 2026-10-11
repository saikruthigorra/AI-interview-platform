package com.interviewiq.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewiq.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security posture:
 *   - stateless bearer-token auth (no server sessions, no auth cookies to CSRF);
 *   - everything under /api requires a valid token except the auth endpoints;
 *   - BCrypt(strength 12) password hashing;
 *   - JSON 401/403 responses instead of redirects, so SPAs behave;
 *   - CORS locked to configured origins, not "*", whenever APP_CORS_ORIGINS is set;
 *   - the H2 console is only reachable on the local dev profile.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppProperties props;
    private final Environment environment;
    private final ObjectMapper mapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, AppProperties props,
                          Environment environment, ObjectMapper mapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.props = props;
        this.environment = environment;
        this.mapper = mapper;
    }

    private boolean localProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local")
                || environment.getActiveProfiles().length == 0;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())                 // stateless tokens; nothing cookie-authenticated
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())   // H2 console (local only)
                        .contentTypeOptions(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers("/api/auth/register", "/api/auth/login").permitAll();
                    // OA links are meant to be shared and taken without an account.
                    // The attempt token returned by POST /attempts is the capability.
                    auth.requestMatchers("/api/public/**").permitAll();
                    auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                    if (localProfile()) {
                        auth.requestMatchers("/h2-console/**").permitAll();
                    } else {
                        auth.requestMatchers("/h2-console/**").denyAll();   // dev tool, local profile only
                    }
                    // Everything with data behind it needs a token...
                    auth.requestMatchers("/api/**", "/actuator/**").authenticated();
                    // ...everything else is the built React app: index.html, hashed assets and
                    // client-side routes (/login, /oa/CODE, /assessments). No secrets, no data —
                    // every byte of data still comes from /api behind the bearer token.
                    auth.anyRequest().permitAll();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> write(res, 401,
                                "Authentication required — provide a valid bearer token"))
                        .accessDeniedHandler((req, res, e) -> write(res, 403,
                                "Your account does not have access to this resource")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String origins = props.getCors().getAllowedOrigins();
        if (origins == null || origins.isBlank() || "*".equals(origins.trim())) {
            // Pattern form keeps wildcard usable while still allowing credentials-free bearer calls.
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void write(HttpServletResponse response, int status, String message) throws java.io.IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", message);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), body);
    }
}
