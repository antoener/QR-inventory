package com.empresa.inventario.Services;

import com.empresa.inventario.DTOs.Request.CreateProductRequest;
import com.empresa.inventario.DTOs.Request.UpdateProductRequest;
import com.empresa.inventario.DTOs.Response.ProductResponse;

import java.util.List;

public interface IProductService {

    ProductResponse create(CreateProductRequest request);

    ProductResponse findById(Long id);

    ProductResponse findByCode(String code);

    List<ProductResponse> list(String query);

    ProductResponse update(Long id, UpdateProductRequest request);

    void setActive(Long id, boolean active);
}
