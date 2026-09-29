package com.empresa.inventario.Controllers;

import com.empresa.inventario.DTOs.Request.CreateUserRequest;
import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Services.IUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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


}
