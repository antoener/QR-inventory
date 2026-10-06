package com.empresa.inventario.Security;

import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.UserRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.empresa.inventario.Common.ApiError;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Autentica requests con access token JWT (header Authorization: Bearer ...).
// En orden: valida firma, descarta tokens revocados (logout), verifica que el
// usuario exista y este activo, que el password no haya cambiado despues de
// emitir el token (claim pwdAt) y el inactivity timeout. El throttle persiste
// lastActivityAt como maximo una vez cada 5 minutos.
// Se registra como @Bean en SecurityConfig (no @Component) para no duplicarse
// como filtro de servlet y para no interferir con el slice de @WebMvcTest.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final long LAST_ACTIVITY_WRITE_THROTTLE_MINUTES = 5;

    private final JwtUtil jwtUtil;
    private final UserRepo userRepo;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final long inactivityTimeoutMinutes;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            UserRepo userRepo,
            TokenService tokenService,
            ObjectMapper objectMapper,
            @Value("${app.security.inactivity-timeout-minutes:15}") long inactivityTimeoutMinutes) {
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.inactivityTimeoutMinutes = inactivityTimeoutMinutes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        Claims claims;
        try {
            claims = jwtUtil.parseAccessToken(token);
        } catch (Exception e) {
            chain.doFilter(request, response);
            return;
        }

        String jti = jwtUtil.extractJti(claims);
        if (tokenService.isAccessTokenRevoked(jti)) {
            writeUnauthorized(response, "Token revocado");
            return;
        }

        Long userId = jwtUtil.extractUserId(claims);
        User user = userRepo.findById(userId).orElse(null);
        if (user == null || !user.isActive()) {
            writeUnauthorized(response, "Usuario inexistente o inactivo");
            return;
        }

        // Invalidacion por cambio de password: token emitido antes del ultimo cambio.
        Long tokenPwdAt = jwtUtil.extractPasswordChangedAt(claims);
        if (tokenPwdAt != null && user.getPasswordChangedAt() != null
                && tokenPwdAt < user.getPasswordChangedAt().toEpochMilli()) {
            writeUnauthorized(response, "Sesion invalida: el password fue cambiado");
            return;
        }

        // Inactivity timeout.
        Instant now = Instant.now();
        if (user.getLastActivityAt() != null) {
            long elapsedMinutes = Duration.between(user.getLastActivityAt(), now).toMinutes();
            if (elapsedMinutes >= inactivityTimeoutMinutes) {
                writeUnauthorized(response, "Sesion expirada por inactividad");
                return;
            }
        }

        // Throttle de escritura: solo se persiste la actividad cada 5 min.
        if (user.getLastActivityAt() == null
                || Duration.between(user.getLastActivityAt(), now).toMinutes() >= LAST_ACTIVITY_WRITE_THROTTLE_MINUTES) {
            user.setLastActivityAt(now);
            userRepo.save(user);
        }

        var auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiError(401, message, null, Instant.now()));
    }
}
