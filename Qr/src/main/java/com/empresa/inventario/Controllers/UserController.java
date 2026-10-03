package com.empresa.inventario.Controllers;

import com.empresa.inventario.DTOs.Request.CreateUserRequest;
import com.empresa.inventario.DTOs.Request.UpdateUserRequest;
import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Services.IUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Endpoints REST de gestión de usuarios. Sin lógica: todo delega a IUserService.
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    // Alta de usuario. @Valid activa la Bean Validation del DTO (400) y los
    // duplicados llegan como 409 desde el GlobalExceptionHandler.
    @PostMapping("/save")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Búsqueda de usuario por ID. Incluye usuarios inactivos: el front los
    // distingue por el campo active del UserResponse. Si no existe, el servicio
    // lanza ResourceNotFoundException y el GlobalExceptionHandler responde 404.
    @GetMapping("/find/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        UserResponse user = userService.findById(id);
        return ResponseEntity.ok(user);
    }

    // Búsqueda exacta de usuario por username (case-insensitive).
    // Útil para login/lookup y para validar unicidad previa.
    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponse> findByUsername(@PathVariable String username) {
        UserResponse user = userService.findByUsername(username);
        return ResponseEntity.ok(user);
    }

    // Búsqueda tipo autocompletado: filtra por username o name a medida que
    // el usuario escribe. Query vacía → lista vacía (sin golpear la BD).
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> search(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(userService.search(q));
    }

    // Búsqueda exacta de usuario por email (case-insensitive).
    // Útil para el flujo de recuperación de contraseña (forgot-password).
    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponse> findByEmail(@PathVariable String email) {
        UserResponse user = userService.findByEmail(email);
        return ResponseEntity.ok(user);
    }

    // Lista todos los usuarios (activos e inactivos) ordenados por id.
    // El front distingue el estado por el campo active.
    @GetMapping("/all")
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    // Actualización completa de username, email y name. No modifica password,
    // role ni estado (active/mustChangePassword) — esos tienen flujos propios.
    // @Valid activa Bean Validation del DTO; duplicados llegan como 409.
    @PutMapping("/update/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse updated = userService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    // Desactiva un usuario (baja lógica). No borra nada; conserva el historial
    // de movimientos. No-op si ya está inactivo. Protege al último activo.
    @PatchMapping("/desactivate/{id}")
    public ResponseEntity<Void> desactivate(@PathVariable Long id) {
        userService.desactivate(id);
        return ResponseEntity.noContent().build();
    }

    // Reactiva un usuario previamente desactivado. No-op si ya está activo.
    @PatchMapping("/activate/{id}")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        userService.activate(id);
        return ResponseEntity.noContent().build();
    }

}
