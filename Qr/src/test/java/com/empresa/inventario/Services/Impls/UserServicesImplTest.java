package com.empresa.inventario.Services.Impls;

import com.empresa.inventario.DTOs.Request.CreateUserRequest;
import com.empresa.inventario.DTOs.Request.UpdateUserRequest;
import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Enums.Role;
import com.empresa.inventario.Exceptions.BusinessRuleException;
import com.empresa.inventario.Exceptions.DuplicateResourceException;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Tests unitarios de UserServicesImpl.create() — sin contexto de Spring ni BD.
@ExtendWith(MockitoExtension.class)
class UserServicesImplTest {

    @Mock
    private UserRepo userRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServicesImpl userServices;

    private CreateUserRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateUserRequest();
        request.setUsername("jperez");
        request.setEmail("jperez@empresa.com");
        request.setPassword("secreto123");
        request.setName("Juan Perez");
        request.setRole(Role.ADMIN);
    }

    @Test
    void create_deberiaHashearLaPasswordYGuardarElUsuario() {
        when(userRepo.existsByUsername("jperez")).thenReturn(false);
        when(userRepo.existsByEmail("jperez@empresa.com")).thenReturn(false);
        when(passwordEncoder.encode("secreto123")).thenReturn("$2a$12$hash");
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userServices.create(request);

        assertThat(response.getUsername()).isEqualTo("jperez");
        assertThat(response.getEmail()).isEqualTo("jperez@empresa.com");
        assertThat(response.getName()).isEqualTo("Juan Perez");
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.isActive()).isTrue();
        assertThat(response.isMustChangePassword()).isTrue();

        // La password se persiste hasheada, nunca en plano.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$12$hash");
        verify(passwordEncoder).encode("secreto123");
    }

    @Test
    void create_conUsernameDuplicado_deberiaLanzarSinGuardarNiHashear() {
        when(userRepo.existsByUsername("jperez")).thenReturn(true);

        assertThatThrownBy(() -> userServices.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("username");

        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void create_conEmailDuplicado_deberiaLanzarSinGuardarNiHashear() {
        when(userRepo.existsByUsername("jperez")).thenReturn(false);
        when(userRepo.existsByEmail("jperez@empresa.com")).thenReturn(true);

        assertThatThrownBy(() -> userServices.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("email");

        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void findById_deberiaRetornarUserResponseCuandoElUsuarioExiste() {
        Instant now = Instant.now();
        User user = new User();
        user.setId(1L);
        user.setUsername("jperez");
        user.setEmail("jperez@empresa.com");
        user.setName("Juan Perez");
        user.setActive(true);
        user.setMustChangePassword(true);
        user.setRole(Role.ADMIN);
        user.setCreatedAt(now);

        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userServices.findById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("jperez");
        assertThat(response.getEmail()).isEqualTo("jperez@empresa.com");
        assertThat(response.getName()).isEqualTo("Juan Perez");
        assertThat(response.isActive()).isTrue();
        assertThat(response.isMustChangePassword()).isTrue();
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.getCreatedAt()).isEqualTo(now);

        verify(userRepo).findById(1L);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void findById_deberiaLanzarResourceNotFoundCuandoElUsuarioNoExiste() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServices.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource not found");

        verify(userRepo).findById(99L);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void findByUsername_deberiaRetornarUserResponseSinImportarCase() {
        Instant now = Instant.now();
        User user = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);

        // Spring Data aplicará UPPER/LOWER; en Mockito verificamos que el servicio
        // pase el valor trimado al repo y devuelva el DTO mapeado.
        when(userRepo.findByUsernameIgnoreCase("JPEREZ")).thenReturn(Optional.of(user));

        UserResponse response = userServices.findByUsername("  JPEREZ  ");

        assertThat(response.getUsername()).isEqualTo("jperez");
        assertThat(response.getEmail()).isEqualTo("jperez@empresa.com");
        verify(userRepo).findByUsernameIgnoreCase("JPEREZ");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void findByUsername_deberiaLanzarResourceNotFoundCuandoNoExiste() {
        when(userRepo.findByUsernameIgnoreCase("noexiste")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServices.findByUsername("noexiste"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource not found");

        verify(userRepo).findByUsernameIgnoreCase("noexiste");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void search_deberiaRetornarListaMapeada() {
        Instant now = Instant.now();
        User user1 = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);
        User user2 = buildUser(2L, "jgarcia", "jgarcia@empresa.com", "Jose Garcia", now);

        when(userRepo.searchByUsernameOrName("jp")).thenReturn(List.of(user1, user2));

        List<UserResponse> result = userServices.search("jp");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("jperez");
        assertThat(result.get(1).getUsername()).isEqualTo("jgarcia");
        verify(userRepo).searchByUsernameOrName("jp");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void search_conQueryVaciaNoDeberiaConsultarLaBaseDeDatos() {
        List<UserResponse> result = userServices.search("   ");

        assertThat(result).isEmpty();
        verifyNoInteractions(userRepo);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void findByEmail_deberiaRetornarUserResponseSinImportarCase() {
        Instant now = Instant.now();
        User user = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);

        when(userRepo.findByEmailIgnoreCase("JPEREZ@EMPRESA.COM")).thenReturn(Optional.of(user));

        UserResponse response = userServices.findByEmail("  JPEREZ@EMPRESA.COM  ");

        assertThat(response.getEmail()).isEqualTo("jperez@empresa.com");
        assertThat(response.getUsername()).isEqualTo("jperez");
        verify(userRepo).findByEmailIgnoreCase("JPEREZ@EMPRESA.COM");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void findByEmail_deberiaLanzarResourceNotFoundCuandoNoExiste() {
        when(userRepo.findByEmailIgnoreCase("noexiste@empresa.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServices.findByEmail("noexiste@empresa.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource not found");

        verify(userRepo).findByEmailIgnoreCase("noexiste@empresa.com");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void findAll_deberiaRetornarListaOrdenadaMapeada() {
        Instant now = Instant.now();
        User user1 = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);
        user1.setActive(false);
        User user2 = buildUser(2L, "mgarcia", "mgarcia@empresa.com", "Maria Garcia", now);

        // Repositorio ya entrega ordenado por id ascendente.
        when(userRepo.findAllByOrderByIdAsc()).thenReturn(List.of(user1, user2));

        List<UserResponse> result = userServices.findAll();

        assertThat(result).hasSize(2);
        // Verifica orden por id ascendente (1, 2).
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        // Incluye al usuario inactivo (no se filtra por active).
        assertThat(result.get(0).isActive()).isFalse();
        // Verifica otros campos clave del DTO (nunca expone password: el campo
        // simplemente no existe en UserResponse).
        assertThat(result.get(0).getEmail()).isEqualTo("jperez@empresa.com");
        assertThat(result.get(1).getEmail()).isEqualTo("mgarcia@empresa.com");
        verify(userRepo).findAllByOrderByIdAsc();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void findAll_deberiaRetornarListaVaciaCuandoNoHayUsuarios() {
        when(userRepo.findAllByOrderByIdAsc()).thenReturn(List.of());

        List<UserResponse> result = userServices.findAll();

        assertThat(result).isEmpty();
        verify(userRepo).findAllByOrderByIdAsc();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void update_deberiaActualizarCamposSinTocarPasswordNiEstado() {
        Instant now = Instant.now();
        User existing = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);
        existing.setPassword("hashed-password");
        existing.setActive(true);
        existing.setMustChangePassword(true);
        existing.setRole(Role.ADMIN);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("jperez2");
        request.setEmail("nuevo@empresa.com");
        request.setName("Juan P. Perez");

        when(userRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepo.existsByUsernameAndIdNot("jperez2", 1L)).thenReturn(false);
        when(userRepo.existsByEmailAndIdNot("nuevo@empresa.com", 1L)).thenReturn(false);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userServices.update(1L, request);

        assertThat(response.getUsername()).isEqualTo("jperez2");
        assertThat(response.getEmail()).isEqualTo("nuevo@empresa.com");
        assertThat(response.getName()).isEqualTo("Juan P. Perez");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);

        // Password no se re-hashea ni cambia; role, active y mustChangePassword intactos.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("hashed-password");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getCreatedAt()).isEqualTo(now);

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void update_deberiaLanzarResourceNotFoundCuandoNoExiste() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("jperez");
        request.setEmail("jperez@empresa.com");
        request.setName("Juan Perez");

        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServices.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource not found");

        verify(userRepo).findById(99L);
        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void update_conUsernameDuplicado_deberiaLanzar409SinGuardar() {
        Instant now = Instant.now();
        User existing = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("otro");
        request.setEmail("nuevo@empresa.com");
        request.setName("Juan Perez");

        when(userRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepo.existsByUsernameAndIdNot("otro", 1L)).thenReturn(true);

        assertThatThrownBy(() -> userServices.update(1L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("username");

        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void update_conEmailDuplicado_deberiaLanzar409SinGuardar() {
        Instant now = Instant.now();
        User existing = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setUsername("jperez");
        request.setEmail("otro@empresa.com");
        request.setName("Juan Perez");

        when(userRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepo.existsByUsernameAndIdNot("jperez", 1L)).thenReturn(false);
        when(userRepo.existsByEmailAndIdNot("otro@empresa.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> userServices.update(1L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("email");

        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void desactivate_deberiaSetearActiveFalseYNoTocarNadaMas() {
        Instant now = Instant.now();
        User existing = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);
        existing.setPassword("hashed-password");
        existing.setActive(true);
        existing.setMustChangePassword(true);
        existing.setRole(Role.ADMIN);

        when(userRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepo.countByActiveTrue()).thenReturn(2L);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userServices.desactivate(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getPassword()).isEqualTo("hashed-password");
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getCreatedAt()).isEqualTo(now);
        assertThat(saved.getUsername()).isEqualTo("jperez");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void desactivate_deberiaSerNoOpCuandoYaEstaInactivo() {
        Instant now = Instant.now();
        User existing = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);
        existing.setActive(false);

        when(userRepo.findById(1L)).thenReturn(Optional.of(existing));

        userServices.desactivate(1L);

        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void desactivate_deberiaLanzar404CuandoNoExiste() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServices.desactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource not found");

        verify(userRepo).findById(99L);
        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void desactivate_deberiaRechazarAlUltimoActivo() {
        Instant now = Instant.now();
        User existing = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);
        existing.setActive(true);

        when(userRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepo.countByActiveTrue()).thenReturn(1L);

        assertThatThrownBy(() -> userServices.desactivate(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cannot deactivate the last active user");

        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void activate_deberiaSetearActiveTrueYNoTocarNadaMas() {
        Instant now = Instant.now();
        User existing = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);
        existing.setPassword("hashed-password");
        existing.setActive(false);
        existing.setMustChangePassword(true);
        existing.setRole(Role.ADMIN);

        when(userRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userServices.activate(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getPassword()).isEqualTo("hashed-password");
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getCreatedAt()).isEqualTo(now);
        assertThat(saved.getUsername()).isEqualTo("jperez");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void activate_deberiaSerNoOpCuandoYaEstaActivo() {
        Instant now = Instant.now();
        User existing = buildUser(1L, "jperez", "jperez@empresa.com", "Juan Perez", now);
        existing.setActive(true);

        when(userRepo.findById(1L)).thenReturn(Optional.of(existing));

        userServices.activate(1L);

        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void activate_deberiaLanzar404CuandoNoExiste() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userServices.activate(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource not found");

        verify(userRepo).findById(99L);
        verify(userRepo, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    private User buildUser(Long id, String username, String email, String name, Instant createdAt) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setName(name);
        user.setActive(true);
        user.setMustChangePassword(true);
        user.setRole(Role.ADMIN);
        user.setCreatedAt(createdAt);
        return user;
    }
}
