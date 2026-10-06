package com.tickethub.gateway.security;

import java.util.Locale;

/**
 * The gateway decides "who may call this URL" by looking at the path. If the gateway and the target service read the
 * same path differently (/api/events/../admin, //, ;x=1, %2e%2e, %2f), a caller could slip past that decision.
 * None of those forms is ever needed by TicketHub, so such requests are refused outright.
 */
public final class PathSanitizer {

    private PathSanitizer() {
    }

    public static boolean isSuspicious(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return false;
        }
        String lower = rawPath.toLowerCase(Locale.ROOT);
        if (lower.contains("//") || lower.contains("\\") || lower.contains(";")
                || lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("%00") || lower.contains("%25")) {
            return true;
        }
        for (String segment : lower.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                return true;
            }
        }
        return false;
    }
}
