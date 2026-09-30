package com.empresa.inventario.Services.Impls;
import com.empresa.inventario.DTOs.Request.CreateUserRequest;
import com.empresa.inventario.DTOs.Request.UpdateUserRequest;
import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Exceptions.DuplicateResourceException;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.UserRepo;
import com.empresa.inventario.Services.IUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UserServicesImpl implements IUserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServicesImpl.class);

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserServicesImpl(UserRepo userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }


    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        // Unicidad: username y email son únicos en el sistema.
        if (userRepo.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("username", request.getUsername());
        }
        if (userRepo.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("email", request.getEmail());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        // Nunca se persiste la password en plano: se hashea con BCrypt(12).
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setRole(request.getRole());
        // active=true y mustChangePassword=true son defaults de la entidad.

        User saved = userRepo.save(user);
        // Se loguea solo el id: nunca la password ni datos sensibles.
        logger.info("User created: id={}", saved.getId());

        return toResponse(saved);
    }


    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        logger.info("User consulted: id={}", user.getId());
        return toResponse(user);
    }

    @Override
    public UserResponse findByUsername(String username) {
        return null;
    }

    @Override
    public UserResponse findByEmail(String email) {
        return null;
    }

    @Override
    public List<UserResponse> findAll() {
        return List.of();
    }

    @Override
    public List<UserResponse> search(String query) {
        return List.of();
    }

    @Override
    public UserResponse update(Long id, UpdateUserRequest request) {
        return null;
    }

    @Override
    public void activate(Long id) {

    }

    @Override
    public void desactivate(Long id) {

    }

    // Mapea la entidad al DTO de respuesta (nunca se expone la entity ni el password).
    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getName(),
                user.isActive(),
                user.isMustChangePassword(),
                user.getRole(),
                user.getCreatedAt());
    }
}
