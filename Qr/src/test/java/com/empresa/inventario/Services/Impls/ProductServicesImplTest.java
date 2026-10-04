package com.empresa.inventario.Services.Impls;

import com.empresa.inventario.DTOs.Request.CreateProductRequest;
import com.empresa.inventario.DTOs.Request.UpdateProductRequest;
import com.empresa.inventario.DTOs.Response.ProductResponse;
import com.empresa.inventario.Exceptions.DuplicateResourceException;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import com.empresa.inventario.Model.Product;
import com.empresa.inventario.Repository.ProductRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServicesImplTest {

    @Mock
    private ProductRepo productRepo;

    @InjectMocks
    private ProductServicesImpl productService;

    private CreateProductRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateProductRequest();
        request.setCode("BOL-001");
        request.setName("Bolso cuero marrón");
        request.setDescription("Talle único, mediano");
    }

    @Test
    void create_deberiaGuardarProductoNormalizadoYDevolverResponse() {
        request.setCode("  bol-001  ");
        request.setName("  Bolso cuero marrón  ");

        when(productRepo.existsByCodeIgnoreCase("BOL-001")).thenReturn(false);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        ProductResponse response = productService.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCode()).isEqualTo("BOL-001");
        assertThat(response.getName()).isEqualTo("Bolso cuero marrón");
        assertThat(response.getDescription()).isEqualTo("Talle único, mediano");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getCreatedAt()).isNull();

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepo).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("BOL-001");
        assertThat(saved.getName()).isEqualTo("Bolso cuero marrón");
        assertThat(saved.getDescription()).isEqualTo("Talle único, mediano");
        assertThat(saved.getStock()).isEqualTo(0);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void create_conCodigoDuplicado_deberiaLanzar409SinGuardar() {
        when(productRepo.existsByCodeIgnoreCase("BOL-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("code");

        verify(productRepo, never()).save(any());
    }

    @Test
    void create_conDescripcionEnBlanco_deberiaGuardarlaComoNull() {
        request.setDescription("   ");

        when(productRepo.existsByCodeIgnoreCase("BOL-001")).thenReturn(false);
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.create(request);

        assertThat(response.getDescription()).isNull();
    }

    @Test
    void findById_deberiaRetornarProductResponseCuandoElProductoExiste() {
        Instant now = Instant.now();
        Product product = buildProduct(1L, "BOL-001", "Bolso cuero marrón", "Talle único", now);

        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.findById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCode()).isEqualTo("BOL-001");
        assertThat(response.getName()).isEqualTo("Bolso cuero marrón");
        assertThat(response.getDescription()).isEqualTo("Talle único");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getCreatedAt()).isEqualTo(now);

        verify(productRepo).findById(1L);
    }

    @Test
    void findById_deberiaLanzarResourceNotFoundCuandoElProductoNoExiste() {
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource not found");

        verify(productRepo).findById(99L);
    }

    @Test
    void findByCode_deberiaRetornarProductResponseIgnorandoCaseYTrim() {
        Instant now = Instant.now();
        Product product = buildProduct(1L, "BOL-001", "Bolso cuero marrón", null, now);

        when(productRepo.findByCodeIgnoreCase("BOL-001")).thenReturn(Optional.of(product));

        ProductResponse response = productService.findByCode("  bol-001  ");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCode()).isEqualTo("BOL-001");
        assertThat(response.isActive()).isTrue();
        verify(productRepo).findByCodeIgnoreCase("BOL-001");
    }

    @Test
    void findByCode_deberiaRetornarProductoInactivoSinTratarComoNoEncontrado() {
        Instant now = Instant.now();
        Product product = buildProduct(1L, "BOL-001", "Bolso cuero marrón", null, now);
        product.setActive(false);

        when(productRepo.findByCodeIgnoreCase("BOL-001")).thenReturn(Optional.of(product));

        ProductResponse response = productService.findByCode("BOL-001");

        assertThat(response.isActive()).isFalse();
        verify(productRepo).findByCodeIgnoreCase("BOL-001");
    }

    @Test
    void findByCode_deberiaLanzarResourceNotFoundCuandoElProductoNoExiste() {
        when(productRepo.findByCodeIgnoreCase("BOL-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findByCode("BOL-999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource not found");

        verify(productRepo).findByCodeIgnoreCase("BOL-999");
    }

    @Test
    void list_conQueryVacia_deberiaRetornarTodosLosProductosOrdenados() {
        Instant now = Instant.now();
        Product product1 = buildProduct(1L, "BOL-001", "Bolso cuero", null, now);
        product1.setActive(false);
        Product product2 = buildProduct(2L, "BOL-002", "Bolso tela", null, now);

        when(productRepo.findAllByOrderByIdAsc()).thenReturn(List.of(product1, product2));

        List<ProductResponse> result = productService.list("   ");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).isActive()).isFalse();
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).isActive()).isTrue();

        verify(productRepo).findAllByOrderByIdAsc();
    }

    @Test
    void list_conQuery_deberiaRetornarCoincidencias() {
        Instant now = Instant.now();
        Product product = buildProduct(2L, "BOL-002", "Bolso tela azul", null, now);

        when(productRepo.searchByCodeOrName("tela")).thenReturn(List.of(product));

        List<ProductResponse> result = productService.list("tela");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("BOL-002");
        assertThat(result.get(0).getName()).isEqualTo("Bolso tela azul");

        verify(productRepo).searchByCodeOrName("tela");
    }

    @Test
    void list_conQueryNull_deberiaRetornarTodosLosProductos() {
        Instant now = Instant.now();
        Product product = buildProduct(1L, "BOL-001", "Bolso cuero", null, now);

        when(productRepo.findAllByOrderByIdAsc()).thenReturn(List.of(product));

        List<ProductResponse> result = productService.list(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("BOL-001");
        verify(productRepo).findAllByOrderByIdAsc();
        verify(productRepo, never()).searchByCodeOrName(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void update_deberiaActualizarNombreYDescripcionSinTocarCodigoNiStockNiEstado() {
        Instant now = Instant.now();
        Product existing = buildProduct(1L, "BOL-001", "Bolso cuero", "Talle único", now);
        existing.setStock(150);

        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setName("  Bolso cuero marrón  ");
        updateRequest.setDescription("  ");

        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.update(1L, updateRequest);

        assertThat(response.getName()).isEqualTo("Bolso cuero marrón");
        assertThat(response.getDescription()).isNull();
        assertThat(response.getCode()).isEqualTo("BOL-001");
        assertThat(response.isActive()).isTrue();

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepo).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("BOL-001");
        assertThat(saved.getStock()).isEqualTo(150);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void update_deberiaPermitirEditarProductoInactivo() {
        Instant now = Instant.now();
        Product existing = buildProduct(1L, "BOL-001", "Bolso cuero", null, now);
        existing.setActive(false);

        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setName("Bolso cuero negro");
        updateRequest.setDescription("Nuevo color");

        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.update(1L, updateRequest);

        assertThat(response.getName()).isEqualTo("Bolso cuero negro");
        assertThat(response.getDescription()).isEqualTo("Nuevo color");
        assertThat(response.isActive()).isFalse();
    }

    @Test
    void update_deberiaLanzarResourceNotFoundCuandoElProductoNoExiste() {
        UpdateProductRequest updateRequest = new UpdateProductRequest();
        updateRequest.setName("Bolso cuero");
        updateRequest.setDescription("Descripción");

        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource not found");

        verify(productRepo, never()).save(any());
    }

    @Test
    void setActive_deberiaDesactivarProductoYNoTocarCodigoNiStock() {
        Instant now = Instant.now();
        Product existing = buildProduct(1L, "BOL-001", "Bolso cuero", null, now);
        existing.setStock(50);

        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.setActive(1L, false);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepo).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getCode()).isEqualTo("BOL-001");
        assertThat(saved.getStock()).isEqualTo(50);
        assertThat(saved.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void setActive_deberiaActivarProductoInactivo() {
        Instant now = Instant.now();
        Product existing = buildProduct(1L, "BOL-001", "Bolso cuero", null, now);
        existing.setActive(false);

        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.setActive(1L, true);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepo).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void setActive_deberiaSerNoOpCuandoElEstadoYaEsElSolicitado() {
        Instant now = Instant.now();
        Product existing = buildProduct(1L, "BOL-001", "Bolso cuero", null, now);
        existing.setActive(true);

        when(productRepo.findById(1L)).thenReturn(Optional.of(existing));

        productService.setActive(1L, true);

        verify(productRepo, never()).save(any());
    }

    @Test
    void setActive_deberiaLanzarResourceNotFoundCuandoElProductoNoExiste() {
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.setActive(99L, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource not found");

        verify(productRepo, never()).save(any());
    }

    private Product buildProduct(Long id, String code, String name, String description, Instant createdAt) {
        Product product = new Product();
        product.setId(id);
        product.setCode(code);
        product.setName(name);
        product.setDescription(description);
        product.setStock(0);
        product.setActive(true);
        product.setCreatedAt(createdAt);
        return product;
    }
}
