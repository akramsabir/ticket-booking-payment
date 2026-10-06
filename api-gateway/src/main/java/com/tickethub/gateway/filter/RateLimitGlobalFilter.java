package com.tickethub.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.gateway.config.GatewayProperties;
import com.tickethub.gateway.ratelimit.InMemoryRateLimiter;
import com.tickethub.gateway.web.GatewayResponses;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Limits how many requests one client IP may send per window (application.yml: gateway.rate-limit).
 * Login/register get a much lower limit than the rest, which slows down password guessing.
 * Over the limit -> 429 with a Retry-After header.
 *
 * The IP is the TCP peer address. Behind a load balancer/reverse proxy that would be the proxy's address for everybody.
 * We deliberately do NOT trust an X-Forwarded-For header sent by callers (it is trivial to fake).
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private record Rule(List<PathPattern> patterns, int limit) {
    }

    private final GatewayProperties.RateLimit config;
    private final ObjectMapper mapper;
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter();
    private final List<Rule> rules = new ArrayList<>();

    public RateLimitGlobalFilter(GatewayProperties properties, ObjectMapper mapper) {
        this.config = properties.getRateLimit();
        this.mapper = mapper;
        PathPatternParser parser = new PathPatternParser();
        for (GatewayProperties.RateLimitRule rule : config.getRules()) {
            List<PathPattern> patterns = new ArrayList<>();
            for (String path : rule.getPaths()) {
                patterns.add(parser.parse(path.trim()));
            }
            rules.add(new Rule(patterns, rule.getLimit()));
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!config.isEnabled() || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        PathContainer path = PathContainer.parsePath(exchange.getRequest().getPath().value());
        int limit = config.getDefaultLimit();
        String bucket = "default";
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (rule.patterns().stream().anyMatch(p -> p.matches(path))) {
                limit = rule.limit();
                bucket = "rule" + i;
                break;
            }
        }

        String key = RequestIdGlobalFilter.clientIp(exchange.getRequest()) + "|" + bucket;
        InMemoryRateLimiter.Decision decision = limiter.tryAcquire(key, limit, config.getWindowSeconds() * 1000L);
        if (decision.allowed()) {
            return chain.filter(exchange);
        }
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        return GatewayResponses.error(exchange, mapper, HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests, please slow down and try again in " + decision.retryAfterSeconds() + " seconds");
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
