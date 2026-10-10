package com.empresa.inventario.Security;

import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.UserRepo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

// Helper inyectable para obtener el usuario autenticado actual desde cualquier
// servicio. El principal del SecurityContext es el username; aca se recarga la
// entidad desde la BD para reflejar siempre el estado vigente.
@Service
public class SecurityContextService {

    private final UserRepo userRepo;

    public SecurityContextService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String username = authentication.getName();
        // Username viene del token JWT (generado con case-sensitive correcto).
        // Validación case-sensitive por seguridad extra.
        return userRepo.findByUsernameIgnoreCase(username)
                .filter(u -> u.getUsername().equals(username));
    }
}
