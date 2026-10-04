package com.empresa.inventario.Repository;

import com.empresa.inventario.Model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepo extends JpaRepository<Product, Long> {

    // Verifica si ya existe un producto con el código dado, sin distinguir mayúsculas/minúsculas.
    // Usado en el flujo de creación para validar unicidad del código QR.
    boolean existsByCodeIgnoreCase(String code);

    // Busca un producto por su código, sin distinguir mayúsculas/minúsculas.
    // Usado para resolver el producto al escanear un QR.
    Optional<Product> findByCodeIgnoreCase(String code);

    // Devuelve todos los productos ordenados por id.
    // Usado por IProductService.list(query) cuando la consulta está vacía.
    List<Product> findAllByOrderByIdAsc();

    // Busca productos cuyo código o nombre contengan la query (case-insensitive).
    // Usado por IProductService.list(query) cuando hay un texto de búsqueda.
    @Query("SELECT p FROM Product p WHERE LOWER(p.code) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Product> searchByCodeOrName(@Param("query") String query);
}
