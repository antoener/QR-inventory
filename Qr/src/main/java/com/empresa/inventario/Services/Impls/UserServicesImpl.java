package com.empresa.inventario.Services.Impls;
import com.empresa.inventario.DTOs.Request.CreateUserRequest;
import com.empresa.inventario.DTOs.Request.UpdateUserRequest;
import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Exceptions.BusinessRuleException;
import com.empresa.inventario.Exceptions.DuplicateResourceException;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.UserRepo;
import com.empresa.inventario.Security.PasswordValidator;
import com.empresa.inventario.Services.IUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

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

        // Politica de complejidad (8..72, mayus/minus/digito/especial).
        PasswordValidator.validate(request.getPassword());

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
    @Transactional(readOnly = true)
    public UserResponse findByUsername(String username) {
        User user = userRepo.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        logger.info("User consulted by username: id={}", user.getId());
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        User user = userRepo.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        // Se loguea solo el id: el email es PII y no debe ir al log.
        logger.info("User consulted by email: id={}", user.getId());
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepo.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> search(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return userRepo.searchByUsernameOrName(q).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        // Unicidad excluyendo al propio usuario: permite conservar el mismo
        // username/email, pero rechaza si otro usuario ya lo tiene.
        if (userRepo.existsByUsernameAndIdNot(request.getUsername(), id)) {
            throw new DuplicateResourceException("username", request.getUsername());
        }
        if (userRepo.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new DuplicateResourceException("email", request.getEmail());
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setName(request.getName());

        User saved = userRepo.save(user);
        // Se loguea solo el id: nunca datos sensibles ni password.
        logger.info("User updated: id={}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        // Idempotente: si ya está activo, no se hace nada ni se vuelve a guardar.
        if (user.isActive()) {
            return;
        }

        user.setActive(true);
        User saved = userRepo.save(user);
        logger.info("User activated: id={}", saved.getId());
    }

    @Override
    @Transactional
    public void desactivate(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        // Idempotente: si ya está inactivo, no se hace nada ni se vuelve a guardar.
        if (!user.isActive()) {
            return;
        }

        // Guarda de seguridad: no permitir desactivar al último usuario activo,
        // porque dejaría el sistema sin nadie que pueda iniciar sesión.
        if (userRepo.countByActiveTrue() <= 1) {
            throw new BusinessRuleException("Cannot deactivate the last active user");
        }
        user.setActive(false);
        User saved = userRepo.save(user);
        logger.info("User deactivated: id={}", saved.getId());
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
