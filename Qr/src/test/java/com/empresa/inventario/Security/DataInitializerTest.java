package com.empresa.inventario.Security;

import com.empresa.inventario.Enums.Role;
import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataInitializerTest {

    @Test
    void run_deberiaCrearAdminConPasswordTemporalCuandoLaTablaEstaVacia() {
        UserRepo userRepo = mock(UserRepo.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(userRepo.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        DataInitializer initializer = new DataInitializer(userRepo, passwordEncoder, "admin", "", "");

        initializer.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());

        User saved = captor.getValue();
        assertEquals("admin", saved.getUsername());
        assertEquals(Role.ADMIN, saved.getRole());
        assertTrue(saved.isMustChangePassword());
        assertTrue(saved.isActive());
    }

    @Test
    void run_noDeberiaCrearNadaCuandoYaExistenUsuarios() {
        UserRepo userRepo = mock(UserRepo.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(userRepo.count()).thenReturn(3L);

        DataInitializer initializer = new DataInitializer(userRepo, passwordEncoder, "admin", "", "secret");

        initializer.run(null);

        verify(userRepo, never()).save(any());
    }
}
