package com.interviewiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the {@code app.*} section of application.yml / environment variables. */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Ai ai = new Ai();
    private final Seed seed = new Seed();
    private final Demo demo = new Demo();
    private final Cors cors = new Cors();
    private final Security security = new Security();

    public Ai getAi() {
        return ai;
    }

    public Seed getSeed() {
        return seed;
    }

    public Demo getDemo() {
        return demo;
    }

    public Cors getCors() {
        return cors;
    }

    public Security getSecurity() {
        return security;
    }

    public static class Security {
        /** 32+ chars. If blank, a random key is generated per boot (dev only). */
        private String jwtSecret = "";
        /** Access-token lifetime in minutes. */
        private long tokenTtlMinutes = 120;
        /** Seed/update the bootstrap admin account from APP_ADMIN_EMAIL/PASSWORD. */
        private String adminEmail = "admin@interviewiq.dev";
        /** Blank → a strong random password is generated and logged once at boot. */
        private String adminPassword = "";

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getTokenTtlMinutes() {
            return tokenTtlMinutes;
        }

        public void setTokenTtlMinutes(long tokenTtlMinutes) {
            this.tokenTtlMinutes = tokenTtlMinutes;
        }

        public String getAdminEmail() {
            return adminEmail;
        }

        public void setAdminEmail(String adminEmail) {
            this.adminEmail = adminEmail;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }
    }

    public static class Ai {
        /** "heuristic" (offline, default) or "openai-compatible". */
        private String provider = "heuristic";
        private String apiKey = "";
        private String model = "gpt-4o-mini";
        private String baseUrl = "https://api.openai.com/v1";
        private int timeoutSeconds = 45;

        /** True when a remote LLM should be used; otherwise the offline engine handles everything. */
        public boolean remoteEnabled() {
            return apiKey != null && !apiKey.isBlank()
                    && provider != null && !"heuristic".equalsIgnoreCase(provider);
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Seed {
        /** Populate demo data on first boot (no-op once the DB has rows). */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Shared password for the seeded demo accounts (recruiter@/student@). */
    public static class Demo {
        private String password = "Demo!Access2026";

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Cors {
        private String allowedOrigins = "*";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
