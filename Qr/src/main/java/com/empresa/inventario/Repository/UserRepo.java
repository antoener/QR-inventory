package com.empresa.inventario.Repository;
import com.empresa.inventario.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepo extends JpaRepository<User, Long> {


     // Busca un usuario por su username (único en el sistema).
    Optional<User> findByUsername(String username);


    // Busca un usuario por su email (único en el sistema).
    Optional<User> findByEmail(String email);


     // Verifica si existe un usuario con el username dado.
     // Usado en el flujo de creación (alta) para validar unicidad.
    boolean existsByUsername(String username);


      //Verifica si existe un usuario con el email dado.
     // Usado en el flujo de creación (alta) para validar unicidad.
     boolean existsByEmail(String email);


     // Verifica si existe otro usuario (distinto al de :id) con el username dado.
    //  Usado en el flujo de edición para validar unicidad sin compararse consigo mismo.
    boolean existsByUsernameAndIdNot(String username, Long id);

     // Verifica si existe otro usuario (distinto al de :id) con el email dado.
    //  Usado en el flujo de edición para validar unicidad sin compararse consigo mismo.
    boolean existsByEmailAndIdNot(String email, Long id);

     // Busca usuarios cuyo username o name contengan la query (case-insensitive).
    //  Usado por IUserService.search(query) para filtrar la lista de usuarios.
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT" +
            "('%', :query, '%')) OR LOWER(u.name) LIKE LOWER" +
            "(CONCAT('%', :query, '%'))")
    List<User> searchByUsernameOrName(@Param("query") String query);
}
