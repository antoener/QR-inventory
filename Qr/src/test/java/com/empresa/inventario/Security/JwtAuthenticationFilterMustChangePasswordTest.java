package com.empresa.inventario.Security;

import com.empresa.inventario.Model.User;
import com.empresa.inventario.Enums.Role;
import com.empresa.inventario.Repository.UserRepo;
import com.empresa.inventario.Security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterMustChangePasswordTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private UserRepo userRepo;
    @Mock private TokenService tokenService;

    private JwtAuthenticationFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private User testUser;
    private Claims claims;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtil, userRepo, tokenService, objectMapper, 15);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("jperez");
        testUser.setEmail("jperez@empresa.com");
        testUser.setName("Juan Perez");
        testUser.setActive(true);
        testUser.setPassword("hashed");
        testUser.setMustChangePassword(true);
        testUser.setRole(Role.ADMIN);
        testUser.setCreatedAt(Instant.now());
        testUser.setPasswordChangedAt(Instant.now());

        claims = mock(Claims.class);
        lenient().when(jwtUtil.parseAccessToken("valid.token.here")).thenReturn(claims);
        lenient().when(jwtUtil.extractJti(claims)).thenReturn("jti-123");
        lenient().when(tokenService.isAccessTokenRevoked("jti-123")).thenReturn(false);
        lenient().when(jwtUtil.extractUserId(claims)).thenReturn(1L);
        lenient().when(jwtUtil.extractPasswordChangedAt(claims)).thenReturn(Instant.now().toEpochMilli());
        lenient().when(userRepo.findById(1L)).thenReturn(Optional.of(testUser));
    }

    private MockHttpServletRequest createRequest(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "Bearer valid.token.here");
        return request;
    }

    @Test
    void mustChangePassword_true_endpointNoPermitido_retorna403() throws ServletException, IOException {
        MockHttpServletRequest request = createRequest("GET", "/api/products/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Debes cambiar tu contraseña temporal");
    }

    @Test
    void mustChangePassword_true_endpointChangePassword_permite() throws ServletException, IOException {
        MockHttpServletRequest request = createRequest("POST", "/api/auth/change-password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void mustChangePassword_true_endpointResetPassword_permite() throws ServletException, IOException {
        MockHttpServletRequest request = createRequest("POST", "/api/auth/reset-password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void mustChangePassword_true_endpointLogout_permite() throws ServletException, IOException {
        MockHttpServletRequest request = createRequest("POST", "/api/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void mustChangePassword_true_endpointMe_permite() throws ServletException, IOException {
        MockHttpServletRequest request = createRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void mustChangePassword_false_permiteCualquierEndpoint() throws ServletException, IOException {
        testUser.setMustChangePassword(false);

        MockHttpServletRequest request = createRequest("GET", "/api/products/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }
}