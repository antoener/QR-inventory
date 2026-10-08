package com.empresa.inventario.Security;

import com.empresa.inventario.Common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Rate limiting en memoria por IP con ventana deslizante (sin dependencias
// externas). Limita /api/auth/login y cualquier /api/** de forma global. La IP
// se resuelve considerando proxies inversos de confianza para evitar spoofing
// de X-Forwarded-For. Single-instance; con multiples replicas usar Redis.
// Se registra como @Bean en SecurityConfig (no @Component).
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String FORGOT_PASSWORD_PATH = "/api/auth/forgot-password";

    private final ObjectMapper objectMapper;
    private final int loginMaxRequests;
    private final long loginWindowSeconds;
    private final int forgotMaxRequests;
    private final long forgotWindowSeconds;
    private final int globalMaxRequests;
    private final long globalWindowSeconds;
    private final Set<String> trustedProxies;

    private final Map<String, LinkedList<Long>> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> forgotPasswordAttempts = new ConcurrentHashMap<>();
    private final Map<String, LinkedList<Long>> apiRequests = new ConcurrentHashMap<>();

    public RateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.rate-limit.login-attempts:5}") int loginMaxRequests,
            @Value("${app.security.rate-limit.login-window-seconds:60}") long loginWindowSeconds,
            @Value("${app.security.rate-limit.forgot-attempts:5}") int forgotMaxRequests,
            @Value("${app.security.rate-limit.forgot-window-seconds:60}") long forgotWindowSeconds,
            @Value("${app.security.rate-limit.global-attempts:200}") int globalMaxRequests,
            @Value("${app.security.rate-limit.global-window-seconds:60}") long globalWindowSeconds,
            @Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this.objectMapper = objectMapper;
        this.loginMaxRequests = loginMaxRequests;
        this.loginWindowSeconds = loginWindowSeconds;
        this.forgotMaxRequests = forgotMaxRequests;
        this.forgotWindowSeconds = forgotWindowSeconds;
        this.globalMaxRequests = globalMaxRequests;
        this.globalWindowSeconds = globalWindowSeconds;
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        long now = System.currentTimeMillis();

        if (isRateLimited(apiRequests, clientIp, now, globalMaxRequests, globalWindowSeconds)) {
            writeTooManyRequests(response);
            return;
        }

        if (LOGIN_PATH.equals(uri)
                && isRateLimited(loginAttempts, clientIp, now, loginMaxRequests, loginWindowSeconds)) {
            writeTooManyRequests(response);
            return;
        }

        if (FORGOT_PASSWORD_PATH.equals(uri)
                && isRateLimited(forgotPasswordAttempts, clientIp, now, forgotMaxRequests, forgotWindowSeconds)) {
            writeTooManyRequests(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimited(Map<String, LinkedList<Long>> map, String key, long now,
                                  int maxRequests, long windowSeconds) {
        LinkedList<Long> timestamps = map.computeIfAbsent(key, k -> new LinkedList<>());
        synchronized (timestamps) {
            long windowMillis = windowSeconds * 1000;
            while (!timestamps.isEmpty() && now - timestamps.getFirst() > windowMillis) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return true;
            }
            timestamps.addLast(now);
            return false;
        }
    }

    // Resolucion de IP: si el remoteAddr es un proxy de confianza, se usa el
    // primer valor de X-Forwarded-For (o X-Real-IP); si no, el remoteAddr.
    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.contains(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return remoteAddr;
    }

    // Limpia timestamps viejos y claves vacias para que los mapas no crezcan.
    @Scheduled(fixedRate = 600000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        cleanupMap(loginAttempts, now, loginWindowSeconds);
        cleanupMap(forgotPasswordAttempts, now, forgotWindowSeconds);
        cleanupMap(apiRequests, now, globalWindowSeconds);
    }

    private void cleanupMap(Map<String, LinkedList<Long>> map, long now, long windowSeconds) {
        long windowMillis = windowSeconds * 1000;
        map.entrySet().removeIf(entry -> {
            LinkedList<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && now - timestamps.getFirst() > windowMillis) {
                    timestamps.removeFirst();
                }
                return timestamps.isEmpty();
            }
        });
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new ApiError(429, "Demasiadas solicitudes. Espera un momento e intentalo de nuevo.", null, Instant.now()));
    }
}
