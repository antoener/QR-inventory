package com.empresa.inventario.Services;

import com.empresa.inventario.DTOs.Request.CreateUserRequest;
import com.empresa.inventario.DTOs.Request.UpdateUserRequest;
import com.empresa.inventario.DTOs.Response.UserResponse;

import java.util.List;

public interface IUserService {

    UserResponse create(CreateUserRequest request);

    UserResponse findById(Long id);

    UserResponse findByUsername(String username);

    UserResponse findByEmail(String email);

    List<UserResponse> findAll();

    List<UserResponse> search(String query);

    UserResponse update(Long id, UpdateUserRequest request);

    void activate(Long id);

    void desactivate(Long id);
}
