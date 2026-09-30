package com.empresa.inventario.Services.Impls;

import com.empresa.inventario.DTOs.Request.CreateUserRequest;
import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Enums.Role;
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
}
