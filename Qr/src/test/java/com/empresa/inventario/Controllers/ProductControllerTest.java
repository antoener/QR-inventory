package com.empresa.inventario.Controllers;

import com.empresa.inventario.DTOs.Response.ProductResponse;
import com.empresa.inventario.Exceptions.DuplicateResourceException;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import com.empresa.inventario.Services.IProductService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IProductService productService;

    @Test
    void findById_deberiaRetornar200CuandoElProductoExiste() throws Exception {
        Instant now = Instant.now();
        ProductResponse response = new ProductResponse(
                1L, "BOL-001", "Bolso cuero", "Talle único", true, now);

        when(productService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/products/id/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.code", is("BOL-001")))
                .andExpect(jsonPath("$.name", is("Bolso cuero")))
                .andExpect(jsonPath("$.description", is("Talle único")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    void findById_deberiaRetornar200ConProductoInactivo() throws Exception {
        Instant now = Instant.now();
        ProductResponse response = new ProductResponse(
                1L, "BOL-001", "Bolso cuero", null, false, now);

        when(productService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/products/id/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    void findById_deberiaRetornar404CuandoElProductoNoExiste() throws Exception {
        when(productService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Resource not found"));

        mockMvc.perform(get("/api/products/id/99")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Resource not found")))
                .andExpect(jsonPath("$.field", emptyOrNullString()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void findByCode_deberiaRetornar200CuandoElProductoExiste() throws Exception {
        Instant now = Instant.now();
        ProductResponse response = new ProductResponse(
                1L, "BOL-001", "Bolso cuero", null, true, now);

        when(productService.findByCode("BOL-001")).thenReturn(response);

        mockMvc.perform(get("/api/products/code/BOL-001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.code", is("BOL-001")))
                .andExpect(jsonPath("$.name", is("Bolso cuero")));
    }

    @Test
    void findByCode_deberiaRetornar404CuandoNoExiste() throws Exception {
        when(productService.findByCode("BOL-999"))
                .thenThrow(new ResourceNotFoundException("Resource not found"));

        mockMvc.perform(get("/api/products/code/BOL-999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Resource not found")));
    }

    @Test
    void list_deberiaRetornar200ConListaDeProductos() throws Exception {
        Instant now = Instant.now();
        ProductResponse product1 = new ProductResponse(
                1L, "BOL-001", "Bolso cuero", null, true, now);
        ProductResponse product2 = new ProductResponse(
                2L, "BOL-002", "Bolso tela", null, false, now);

        when(productService.list(null)).thenReturn(List.of(product1, product2));

        mockMvc.perform(get("/api/products/list")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].code", is("BOL-001")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].active", is(false)));
    }

    @Test
    void list_deberiaRetornar200ConResultadosDeBusqueda() throws Exception {
        Instant now = Instant.now();
        ProductResponse product = new ProductResponse(
                2L, "BOL-002", "Bolso tela azul", null, true, now);

        when(productService.list("tela")).thenReturn(List.of(product));

        mockMvc.perform(get("/api/products/list")
                        .param("q", "tela")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(1)))
                .andExpect(jsonPath("$[0].code", is("BOL-002")))
                .andExpect(jsonPath("$[0].name", is("Bolso tela azul")));
    }

    @Test
    void list_deberiaRetornar200ConListaVacia() throws Exception {
        when(productService.list("xyz")).thenReturn(List.of());

        mockMvc.perform(get("/api/products/list")
                        .param("q", "xyz")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void create_deberiaRetornar201CuandoSeCrea() throws Exception {
        Instant now = Instant.now();
        ProductResponse response = new ProductResponse(
                1L, "BOL-001", "Bolso cuero", "Talle único", true, now);

        when(productService.create(any(com.empresa.inventario.DTOs.Request.CreateProductRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/products/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BOL-001\",\"name\":\"Bolso cuero\",\"description\":\"Talle único\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.code", is("BOL-001")))
                .andExpect(jsonPath("$.name", is("Bolso cuero")))
                .andExpect(jsonPath("$.active", is(true)));
    }

    @Test
    void create_deberiaRetornar400ConPayloadInvalido() throws Exception {
        mockMvc.perform(post("/api/products/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"name\":\"\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void create_deberiaRetornar409CuandoElCodigoYaExiste() throws Exception {
        when(productService.create(any(com.empresa.inventario.DTOs.Request.CreateProductRequest.class)))
                .thenThrow(new DuplicateResourceException("code", "BOL-001"));

        mockMvc.perform(post("/api/products/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BOL-001\",\"name\":\"Bolso cuero\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.field", is("code")));
    }

    @Test
    void update_deberiaRetornar200CuandoSeActualiza() throws Exception {
        Instant now = Instant.now();
        ProductResponse response = new ProductResponse(
                1L, "BOL-001", "Bolso cuero marrón", "Talle grande", true, now);

        when(productService.update(eq(1L), any(com.empresa.inventario.DTOs.Request.UpdateProductRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/products/update/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bolso cuero marrón\",\"description\":\"Talle grande\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Bolso cuero marrón")))
                .andExpect(jsonPath("$.description", is("Talle grande")))
                .andExpect(jsonPath("$.code", is("BOL-001")));
    }

    @Test
    void update_deberiaRetornar400ConPayloadInvalido() throws Exception {
        mockMvc.perform(put("/api/products/update/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void update_deberiaRetornar404CuandoNoExiste() throws Exception {
        when(productService.update(eq(99L), any(com.empresa.inventario.DTOs.Request.UpdateProductRequest.class)))
                .thenThrow(new ResourceNotFoundException("Resource not found"));

        mockMvc.perform(put("/api/products/update/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bolso cuero\",\"description\":\"Talle\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Resource not found")));
    }

    @Test
    void setActive_deberiaRetornar204CuandoExiste() throws Exception {
        mockMvc.perform(patch("/api/products/1/set-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    void setActive_deberiaRetornar400CuandoElPayloadEsInvalido() throws Exception {
        mockMvc.perform(patch("/api/products/1/set-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void setActive_deberiaRetornar404CuandoNoExiste() throws Exception {
        doThrow(new ResourceNotFoundException("Resource not found"))
                .when(productService).setActive(99L, false);

        mockMvc.perform(patch("/api/products/99/set-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Resource not found")));
    }
}
