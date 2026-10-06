package com.empresa.inventario.Controllers;

import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Enums.Role;
import com.empresa.inventario.Exceptions.BusinessRuleException;
import com.empresa.inventario.Exceptions.DuplicateResourceException;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import com.empresa.inventario.Services.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Tests de la capa web para UserController. Solo carga el controller y sus
// dependencias, sin contexto completo de Spring ni base de datos.
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
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

    @Test
    void findByUsername_deberiaRetornar200CuandoElUsuarioExiste() throws Exception {
        Instant now = Instant.now();
        UserResponse response = new UserResponse(
                1L, "jperez", "jperez@empresa.com", "Juan Perez",
                true, true, Role.ADMIN, now);

        when(userService.findByUsername("jperez")).thenReturn(response);

        mockMvc.perform(get("/api/users/username/jperez")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.username", is("jperez")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void findByUsername_deberiaRetornar404CuandoNoExiste() throws Exception {
        when(userService.findByUsername("noexiste"))
                .thenThrow(new ResourceNotFoundException("Resource not found"));

        mockMvc.perform(get("/api/users/username/noexiste")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Resource not found")));
    }

    @Test
    void search_deberiaRetornar200ConListaDeUsuarios() throws Exception {
        Instant now = Instant.now();
        UserResponse response = new UserResponse(
                1L, "jperez", "jperez@empresa.com", "Juan Perez",
                true, true, Role.ADMIN, now);

        when(userService.search("jp")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/users/search")
                        .param("q", "jp")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(1)))
                .andExpect(jsonPath("$[0].username", is("jperez")))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void search_deberiaRetornar200ConListaVaciaCuandoNoHayCoincidencias() throws Exception {
        when(userService.search("xyz")).thenReturn(List.of());

        mockMvc.perform(get("/api/users/search")
                        .param("q", "xyz")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void findByEmail_deberiaRetornar200CuandoElUsuarioExiste() throws Exception {
        Instant now = Instant.now();
        UserResponse response = new UserResponse(
                1L, "jperez", "jperez@empresa.com", "Juan Perez",
                true, true, Role.ADMIN, now);

        when(userService.findByEmail("jperez@empresa.com")).thenReturn(response);

        mockMvc.perform(get("/api/users/email/jperez@empresa.com")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.email", is("jperez@empresa.com")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void findByEmail_deberiaRetornar404CuandoNoExiste() throws Exception {
        when(userService.findByEmail("noexiste@empresa.com"))
                .thenThrow(new ResourceNotFoundException("Resource not found"));

        mockMvc.perform(get("/api/users/email/noexiste@empresa.com")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Resource not found")));
    }

    @Test
    void findAll_deberiaRetornar200ConListaDeUsuarios() throws Exception {
        Instant now = Instant.now();
        UserResponse inactiveUser = new UserResponse(
                1L, "mgarcia", "mgarcia@empresa.com", "Maria Garcia",
                false, false, Role.ADMIN, now);
        UserResponse activeUser = new UserResponse(
                2L, "jperez", "jperez@empresa.com", "Juan Perez",
                true, true, Role.ADMIN, now);

        // El service devuelve ordenado por id ascendente.
        when(userService.findAll()).thenReturn(List.of(inactiveUser, activeUser));

        mockMvc.perform(get("/api/users/all")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(2)))
                // Orden por id: primero id=1 (inactivo), luego id=2 (activo).
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].username", is("mgarcia")))
                .andExpect(jsonPath("$[0].active", is(false)))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].username", is("jperez")))
                .andExpect(jsonPath("$[1].active", is(true)))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].password").doesNotExist());
    }

    @Test
    void findAll_deberiaRetornar200ConListaVacia() throws Exception {
        when(userService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/users/all")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void update_deberiaRetornar200CuandoSeActualiza() throws Exception {
        Instant now = Instant.now();
        UserResponse response = new UserResponse(
                1L, "jperez2", "nuevo@empresa.com", "Juan P. Perez",
                true, true, Role.ADMIN, now);

        when(userService.update(eq(1L), any(com.empresa.inventario.DTOs.Request.UpdateUserRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/users/update/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jperez2\",\"email\":\"nuevo@empresa.com\",\"name\":\"Juan P. Perez\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.username", is("jperez2")))
                .andExpect(jsonPath("$.email", is("nuevo@empresa.com")))
                .andExpect(jsonPath("$.name", is("Juan P. Perez")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void update_deberiaRetornar400ConPayloadInvalido() throws Exception {
        mockMvc.perform(put("/api/users/update/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"email\":\"no-es-email\",\"name\":\"\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void update_deberiaRetornar404CuandoNoExiste() throws Exception {
        when(userService.update(eq(99L), any(com.empresa.inventario.DTOs.Request.UpdateUserRequest.class)))
                .thenThrow(new ResourceNotFoundException("Resource not found"));

        mockMvc.perform(put("/api/users/update/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jperez\",\"email\":\"jperez@empresa.com\",\"name\":\"Juan Perez\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Resource not found")));
    }

    @Test
    void update_deberiaRetornar409CuandoElUsernameYaExiste() throws Exception {
        when(userService.update(eq(1L), any(com.empresa.inventario.DTOs.Request.UpdateUserRequest.class)))
                .thenThrow(new DuplicateResourceException("username", "otro"));

        mockMvc.perform(put("/api/users/update/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"otro\",\"email\":\"jperez@empresa.com\",\"name\":\"Juan Perez\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.field", is("username")));
    }

    @Test
    void desactivate_deberiaRetornar204CuandoExiste() throws Exception {
        mockMvc.perform(patch("/api/users/desactivate/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    void desactivate_deberiaRetornar404CuandoNoExiste() throws Exception {
        doThrow(new ResourceNotFoundException("Resource not found"))
                .when(userService).desactivate(99L);

        mockMvc.perform(patch("/api/users/desactivate/99")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Resource not found")));
    }

    @Test
    void desactivate_deberiaRetornar409CuandoEsElUltimoActivo() throws Exception {
        doThrow(new BusinessRuleException("Cannot deactivate the last active user"))
                .when(userService).desactivate(1L);

        mockMvc.perform(patch("/api/users/desactivate/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", is("Cannot deactivate the last active user")));
    }

    @Test
    void activate_deberiaRetornar204CuandoExiste() throws Exception {
        mockMvc.perform(patch("/api/users/activate/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    void activate_deberiaRetornar404CuandoNoExiste() throws Exception {
        doThrow(new ResourceNotFoundException("Resource not found"))
                .when(userService).activate(99L);

        mockMvc.perform(patch("/api/users/activate/99")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Resource not found")));
    }
}
