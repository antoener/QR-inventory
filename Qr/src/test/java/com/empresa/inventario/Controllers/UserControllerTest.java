package com.empresa.inventario.Controllers;

import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Enums.Role;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import com.empresa.inventario.Security.SecurityConfig;
import com.empresa.inventario.Services.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Tests de la capa web para UserController. Solo carga el controller y sus
// dependencias, sin contexto completo de Spring ni base de datos.
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IUserService userService;

    @Test
    void findById_deberiaRetornar200CuandoElUsuarioExiste() throws Exception {
        Instant now = Instant.now();
        UserResponse response = new UserResponse(
                1L,
                "jperez",
                "jperez@empresa.com",
                "Juan Perez",
                true,
                true,
                Role.ADMIN,
                now);

        when(userService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/users/find/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.username", is("jperez")))
                .andExpect(jsonPath("$.email", is("jperez@empresa.com")))
                .andExpect(jsonPath("$.name", is("Juan Perez")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.mustChangePassword", is(true)))
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void findById_deberiaRetornar200ConUsuarioInactivo() throws Exception {
        Instant now = Instant.now();
        UserResponse response = new UserResponse(
                2L,
                "inactive",
                "inactive@empresa.com",
                "Usuario Inactivo",
                false,
                false,
                Role.ADMIN,
                now);

        when(userService.findById(2L)).thenReturn(response);

        mockMvc.perform(get("/api/users/find/2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.mustChangePassword", is(false)))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void findById_deberiaRetornar404CuandoElUsuarioNoExiste() throws Exception {
        when(userService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Resource not found"));

        mockMvc.perform(get("/api/users/find/99")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Resource not found")))
                .andExpect(jsonPath("$.field", emptyOrNullString()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }
}
