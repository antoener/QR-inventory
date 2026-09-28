package com.empresa.inventario.DTOs.Response;

import com.empresa.inventario.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;

    private String username;

    private String email;

    private String name;

    private boolean active;

    private boolean mustChangePassword;

    private Role role;

    private Instant createdAt;
}
