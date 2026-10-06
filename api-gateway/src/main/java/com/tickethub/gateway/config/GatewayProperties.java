package com.tickethub.gateway.config;

import com.tickethub.gateway.security.AccessLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Everything under "gateway.*" in application.yml. */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private final Security security = new Security();
    private final RateLimit rateLimit = new RateLimit();

    public Security getSecurity() { return security; }
    public RateLimit getRateLimit() { return rateLimit; }

    public static class Security {
        /** Must be EXACTLY the secret user-service signs tokens with (app.jwt.secret / env JWT_SECRET). */
        private String jwtSecret;
        /** Used when no rule matches a URL. */
        private AccessLevel defaultAccess = AccessLevel.AUTHENTICATED;
        /** Checked top to bottom, the first matching rule wins. */
        private List<AccessRule> rules = new ArrayList<>();

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
        public AccessLevel getDefaultAccess() { return defaultAccess; }
        public void setDefaultAccess(AccessLevel defaultAccess) { this.defaultAccess = defaultAccess; }
        public List<AccessRule> getRules() { return rules; }
        public void setRules(List<AccessRule> rules) { this.rules = rules; }
    }

    public static class AccessRule {
        /** Empty = every HTTP method. */
        private List<String> methods = new ArrayList<>();
        private List<String> paths = new ArrayList<>();
        private AccessLevel access = AccessLevel.AUTHENTICATED;

        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> methods) { this.methods = methods; }
        public List<String> getPaths() { return paths; }
        public void setPaths(List<String> paths) { this.paths = paths; }
        public AccessLevel getAccess() { return access; }
        public void setAccess(AccessLevel access) { this.access = access; }
    }

    public static class RateLimit {
        private boolean enabled = true;
        /** Requests allowed per client IP per window when no rule matches. */
        private int defaultLimit = 300;
        private int windowSeconds = 60;
        private List<RateLimitRule> rules = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getDefaultLimit() { return defaultLimit; }
        public void setDefaultLimit(int defaultLimit) { this.defaultLimit = defaultLimit; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
        public List<RateLimitRule> getRules() { return rules; }
        public void setRules(List<RateLimitRule> rules) { this.rules = rules; }
    }

    public static class RateLimitRule {
        private List<String> paths = new ArrayList<>();
        private int limit = 60;

        public List<String> getPaths() { return paths; }
        public void setPaths(List<String> paths) { this.paths = paths; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
    }
}
