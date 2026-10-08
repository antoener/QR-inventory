package com.empresa.inventario.Security;

import com.empresa.inventario.Common.ApiError;
import com.empresa.inventario.Repository.UserRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

// Configuracion de seguridad: API stateless con JWT. Deny by default: solo
// login, refresh y health son publicos; el resto exige sesion. Incluye CORS
// restringido, security headers (CSP, frame-deny, nosniff, no-referrer) y los
// filtros de rate-limit y autenticacion JWT en ese orden.
@Configuration
public class SecurityConfig {

    // ObjectMapper local para escribir los cuerpos de error (401/403) desde los
    // filtros y handlers. No se inyecta el de Spring para no acoplar el config al
    // contexto y mantenerlo usable en slices de test (@WebMvcTest).
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Value("${app.swagger.enabled:false}")
    private boolean swaggerEnabled;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    // BCrypt con cost factor 12 (lo fija el plan).
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtUtil jwtUtil,
            UserRepo userRepo,
            TokenService tokenService,
            @Value("${app.security.inactivity-timeout-minutes:15}") long inactivityTimeoutMinutes) {
        return new JwtAuthenticationFilter(jwtUtil, userRepo, tokenService, objectMapper, inactivityTimeoutMinutes);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(
            @Value("${app.security.rate-limit.login-attempts:5}") int loginMaxRequests,
            @Value("${app.security.rate-limit.login-window-seconds:60}") long loginWindowSeconds,
            @Value("${app.security.rate-limit.forgot-attempts:5}") int forgotMaxRequests,
            @Value("${app.security.rate-limit.forgot-window-seconds:60}") long forgotWindowSeconds,
            @Value("${app.security.rate-limit.global-attempts:200}") int globalMaxRequests,
            @Value("${app.security.rate-limit.global-window-seconds:60}") long globalWindowSeconds,
            @Value("${app.security.trusted-proxies:}") String trustedProxies) {
        return new RateLimitFilter(objectMapper, loginMaxRequests, loginWindowSeconds,
                forgotMaxRequests, forgotWindowSeconds,
                globalMaxRequests, globalWindowSeconds, trustedProxies);
    }

    // Evita que Spring Boot registre los filtros ademas como filtros de servlet
    // (se ejecutan solo dentro del SecurityFilterChain).
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)   // API stateless con JWT
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
                                "img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Swagger: con swagger.enabled=true solo para ADMIN; si no, "NONE" nunca existe → bloqueado.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**")
                        .hasRole(swaggerEnabled ? "ADMIN" : "NONE")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, 401, "No autenticado"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonError(response, 403, "Acceso denegado")))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("HEAD", "GET", "PUT", "POST", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiError(status, message, null, Instant.now()));
    }
}
