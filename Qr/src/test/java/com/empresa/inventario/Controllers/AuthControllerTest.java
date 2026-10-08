package com.empresa.inventario.Controllers;

import com.empresa.inventario.DTOs.Response.AuthResponse;
import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Enums.Role;
import com.empresa.inventario.Exceptions.InvalidCredentialsException;
import com.empresa.inventario.Services.IAuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAuthService authService;

    private UserResponse userResponse() {
        return new UserResponse(
                1L, "jperez", "jperez@empresa.com", "Juan Perez",
                true, false, Role.ADMIN, Instant.now());
    }

    @Test
    void login_deberiaRetornar200ConTokenYUsuario() throws Exception {
        AuthResponse response = new AuthResponse("jwt-token", userResponse());
        when(authService.login(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"jperez\",\"password\":\"Abcd1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("jwt-token")))
                .andExpect(jsonPath("$.user.username", is("jperez")))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void login_deberiaRetornar401ConCredencialesInvalidas() throws Exception {
        when(authService.login(any(), any()))
                .thenThrow(new InvalidCredentialsException("Credenciales invalidas"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"jperez\",\"password\":\"mal\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void login_deberiaRetornar400ConPayloadInvalido() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void refresh_deberiaRetornar200ConCookie() throws Exception {
        AuthResponse response = new AuthResponse("nuevo-token", userResponse());
        when(authService.refresh(eq("refresh-abc"), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "refresh-abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("nuevo-token")));
    }

    @Test
    void logout_deberiaRetornar204() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }

    @Test
    void me_deberiaRetornar200ConUsuario() throws Exception {
        when(authService.me()).thenReturn(userResponse());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("jperez")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void changePassword_deberiaRetornar204ConPasswordValida() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Vieja1234!\",\"newPassword\":\"Nueva1234!\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePassword_deberiaRetornar400ConPasswordCorta() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Vieja1234!\",\"newPassword\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void forgotPassword_deberiaRetornar204Siempre() throws Exception {
        doNothing().when(authService).forgotPassword(anyString());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jperez@empresa.com\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void forgotPassword_deberiaRetornar400ConEmailInvalido() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void resetPassword_deberiaRetornar204ConTokenValido() throws Exception {
        doNothing().when(authService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc123\",\"newPassword\":\"NuevaPass123!\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void resetPassword_deberiaRetornar401ConTokenInvalido() throws Exception {
        doThrow(new InvalidCredentialsException("Token inválido o expirado"))
                .when(authService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"mal\",\"newPassword\":\"NuevaPass123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void resetPassword_deberiaRetornar400ConPasswordDebil() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"newPassword\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }
}
