package com.empresa.inventario.Security;

import com.empresa.inventario.Controllers.ProductController;
import com.empresa.inventario.DTOs.Response.ProductResponse;
import com.empresa.inventario.Enums.Role;
import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.RefreshTokenRepo;
import com.empresa.inventario.Repository.RevokedTokenRepo;
import com.empresa.inventario.Repository.UserRepo;
import com.empresa.inventario.Services.IProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifica las reglas del SecurityFilterChain real: deny by default y acceso
// con access token valido. Requiere el filtro JWT y sus dependencias mockeadas.
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtUtil.class, TokenService.class})
@TestPropertySource(properties = {
        "jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "app.swagger.enabled=false"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepo userRepo;

    @MockitoBean
    private RevokedTokenRepo revokedTokenRepo;

    @MockitoBean
    private RefreshTokenRepo refreshTokenRepo;

    @MockitoBean
    private IProductService productService;

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("jperez");
        user.setEmail("jperez@empresa.com");
        user.setName("Juan Perez");
        user.setPassword("hashed");
        user.setRole(Role.ADMIN);
        user.setActive(true);
        user.setPasswordChangedAt(Instant.now());
        return user;
    }

    @Test
    void endpointProtegido_deberiaRetornar401SinToken() throws Exception {
        mockMvc.perform(get("/api/products/id/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void endpointProtegido_deberiaRetornar200ConTokenValido() throws Exception {
        User user = activeUser();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(productService.findById(1L))
                .thenReturn(new ProductResponse(1L, "BOL-001", "Bolso cuero", null, true, Instant.now()));

        String token = jwtUtil.generateAccessToken(user);

        mockMvc.perform(get("/api/products/id/1")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.code", is("BOL-001")));
    }
}
