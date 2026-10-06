package com.tickethub.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter in the chain. Gives every request an X-Request-Id (kept if the caller sent a sane one), returns it in
 * the response, and writes one access-log line per request so you can follow a call through the logs.
 */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(RequestIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String incoming = request.getHeaders().getFirst(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();

        ServerHttpRequest mutated = request.mutate().header(HEADER, requestId).build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();
        mutatedExchange.getResponse().getHeaders().set(HEADER, requestId);

        long startNanos = System.nanoTime();
        return chain.filter(mutatedExchange).doFinally(signal -> {
            HttpStatusCode status = mutatedExchange.getResponse().getStatusCode();
            long millis = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("{} {} -> {} ({} ms) requestId={} ip={}", request.getMethod(), request.getPath().value(),
                    status == null ? "-" : status.value(), millis, requestId, clientIp(request));
        });
    }

    static String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return remote.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return -300;
    }
}
