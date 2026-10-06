package com.tickethub.gateway.security;

import com.tickethub.gateway.config.GatewayProperties;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Decides which {@link AccessLevel} a (method, path) needs, using the rules from application.yml. First match wins. */
public class AccessRuleMatcher {

    private record Compiled(Set<String> methods, List<PathPattern> patterns, AccessLevel access) {
    }

    private final List<Compiled> rules = new ArrayList<>();
    private final AccessLevel defaultAccess;

    public AccessRuleMatcher(List<GatewayProperties.AccessRule> configured, AccessLevel defaultAccess) {
        PathPatternParser parser = new PathPatternParser();
        for (GatewayProperties.AccessRule rule : configured) {
            Set<String> methods = new HashSet<>();
            for (String method : rule.getMethods()) {
                methods.add(method.trim().toUpperCase(Locale.ROOT));
            }
            List<PathPattern> patterns = new ArrayList<>();
            for (String path : rule.getPaths()) {
                patterns.add(parser.parse(path.trim()));   // a typo in a pattern fails the start-up, not a request
            }
            rules.add(new Compiled(methods, patterns, rule.getAccess()));
        }
        this.defaultAccess = defaultAccess == null ? AccessLevel.AUTHENTICATED : defaultAccess;
    }

    public AccessLevel resolve(String method, String path) {
        String m = method == null ? "" : method.toUpperCase(Locale.ROOT);
        PathContainer container = PathContainer.parsePath(path == null ? "/" : path);
        for (Compiled rule : rules) {
            if (!rule.methods().isEmpty() && !rule.methods().contains(m)) {
                continue;
            }
            for (PathPattern pattern : rule.patterns()) {
                if (pattern.matches(container)) {
                    return rule.access();
                }
            }
        }
        return defaultAccess;
    }
}
