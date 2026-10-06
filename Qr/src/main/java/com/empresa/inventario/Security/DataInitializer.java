package com.empresa.inventario.Security;

import com.empresa.inventario.Enums.Role;
import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Bootstrap del usuario ADMIN inicial: solo corre si la tabla de usuarios esta
// vacia. El password viene de APP_ADMIN_PASSWORD (nunca hardcodeado); si falta,
// se genera uno temporal y se loguea en un warn para poder entrar la primera vez.
// Nace con mustChangePassword=true, de modo que ese password no sobrevive en prod.
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;

    public DataInitializer(
            UserRepo userRepo,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username:admin}") String adminUsername,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepo.count() > 0) {
            return;
        }

        String password = adminPassword;
        boolean generated = false;
        if (password == null || password.isBlank()) {
            password = "Adm1n$" + UUID.randomUUID().toString().substring(0, 6);
            generated = true;
        }

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail == null || adminEmail.isBlank() ? adminUsername + "@localhost" : adminEmail);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setName("Administrador");
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setMustChangePassword(true);

        userRepo.save(admin);

        if (generated) {
            logger.warn("Usuario admin inicial creado. Password temporal (debe cambiarse): {}", password);
        } else {
            logger.info("Usuario admin inicial creado con password desde APP_ADMIN_PASSWORD.");
        }
    }
}
