# Resumen: Sistema de Autenticación y Autorización — Backend Spring Boot

> Documento de referencia para replicar este sistema de auth en otro proyecto Spring Boot.
> **Adaptación clave**: el proyecto destino NO tiene roles de usuarios — existe un único tipo de usuario con permisos **ADMIN**. Donde este proyecto usa `SUPERADMIN`/`MANAGER` y checks por rol, el destino debe simplificar (ver sección 14).

---

## 1. Stack y dependencias (pom.xml)

- Java 17, Spring Boot 4.0.5 (parent)
- Starters: `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-mail`
- **JWT**: `io.jsonwebtoken` — `jjwt-api`, `jjwt-impl`, `jjwt-jackson`, versión **0.12.6**
  (API nueva: `Jwts.builder().signWith(key)` y `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`)
- **2FA TOTP**: `com.warrenstrange:googleauth:1.5.0`
- Base de datos: MySQL (`com.mysql:mysql-connector-j`)
- Lombok
- `@EnableScheduling` en la clase principal `@SpringBootApplication` — **obligatorio**, hay jobs `@Scheduled` de limpieza (tokens revocados, tokens de reset, rate-limit maps).

---

## 2. Arquitectura de paquetes

```
SecurityConfig/  → WebSecurityConfig, JwtUtil, JwtAuthenticationFilter,
                   RateLimitFilter, SecurityContextService, UserDetailsServiceImpl
Controllers/     → AuthController, TwoFactorAuthController, PasswordRecoveryController
Services/        → IAuthService + AuthServImpl
                   ITokenRevocationService + TokenRevocationServiceImpl
                   ITwoFactorService + TwoFactorServiceImpl
                   IPasswordRecoveryService + PasswordRecoveryServiceImpl
                   IEmailService + EmailServiceImpl
Models/          → User, RevokedToken, PasswordResetToken, Enums/UserRole
Repository/      → UserRepo, RevokedTokenRepo, PasswordResetTokenRepo
DTO/Request/AuthDTO + DTO/Response/AuthDTO
```

DTOs de auth:
- Request: `LoginRequest`, `RegisterRequest`, `ChangePasswordRequest`, `UpdateProfileRequest`, `Verify2FARequest`, `Activate2FARequest`, `Disable2FARequest`, `ForgotPasswordRequest`, `ResetPasswordRequest`
- Response: `LoginResponse`, `RegisterResponse`, `UserInfoResponse`, `Setup2FAResponse`, `TwoFAStatusResponse`, `ForgotPasswordResponse`, `ResetPasswordResponse`

**Patrón general**: Controller → Service (toda la lógica de negocio) → Repository. DTOs separados request/response con Bean Validation (`@NotBlank`, `@Email`, `@Size`, `@Pattern`). Nunca se exponen entidades JPA directamente. Errores de negocio se lanzan como `IllegalArgumentException` y el controller las convierte en `400 Bad Request` con body `{"Error": "<mensaje>"}`.

---

## 3. Modelo `User` (tabla `user`)

```java
@Entity @Table(name = "user")
public class User {
    Long id;                                  // @GeneratedValue IDENTITY
    String email;                             // unique, nullable=false, max 100
    String password;                          // hash BCrypt
    UserRole role;                             // @Enumerated(EnumType.STRING), nullable=false
    boolean active = true;                     // nullable=false
    boolean mustChangePassword = true;         // nullable=false
    Boolean twoFactorEnabled = false;
    String twoFactorSecret;                    // length 100
    LocalDateTime twoFactorSetupDate;
    LocalDateTime lastActivityAt;              // para inactivity timeout
    LocalDateTime passwordChangedAt;           // nullable=false — invalida JWTs viejos
    LocalDateTime createdAt, updatedAt;        // nullable=false
}
```

- `@PrePersist`: setea `createdAt`, `updatedAt` y `passwordChangedAt` (si null).
- `@PreUpdate`: setea `updatedAt`.
- `UserRepo` (JpaRepository): `findByEmail`, `existsByEmail`, `existsByEmailAndIdNot` (query JPQL con `id <> :id`).

`UserRole` (enum): en origen `SUPERADMIN`, `MANAGER` → **en destino solo `ADMIN`**.

---

## 4. JWT — `JwtUtil` (@Component)

- Secreto HS256 desde propiedad `jwt.secret` (mínimo 32 bytes), expiración `jwt.expiration=3600000` (1 hora).
- `SecretKey getSigningKey()` = `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`.

**Token final** (`generateToken(User)`):
```
sub    = userId (String)
role   = user.getRole().name()
pwdAt  = passwordChangedAt (String)   → invalida tokens emitidos antes de un cambio de password
jti    = UUID.randomUUID()            → permite revocación individual (logout)
iat    = now
exp    = now + jwt.expiration
```
(En origen también incluye `yardId` — eliminar en destino.)

**Temp token 2FA** (`generateTempToken(User)`):
```
sub    = userId
role   = role.name()
purpose = "2fa-pending"
iat    = now
exp    = now + 300000   (5 minutos)
```
Solo sirve para llamar a `POST /api/auth/2fa/verify`. No es un token de sesión.

**Métodos**: `generateToken`, `generateTempToken`, `validateToken` (try/catch `JwtException | IllegalArgumentException`), `isTempToken` (lee claim `purpose`), `extractUserId`, `extractRole`, `extractJti`, `extractExpiration`, `extractPasswordChangedAt`.

---

## 5. Filtros y pipeline

En el `SecurityFilterChain`:
```java
.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```
El rate limit corre antes que el filtro JWT.

### 5.1 `JwtAuthenticationFilter` (OncePerRequestFilter)

Pasos en orden:
1. Lee header `Authorization`; si falta o no empieza con `"Bearer "` → continúa sin autenticar (la cadena de reglas decide luego).
2. Si `jwtUtil.validateToken(token)` falla → continúa sin autenticar.
3. **Temp token**: si `isTempToken(token)` y la URI NO empieza con `/api/auth/2fa/verify` → **401**.
4. **Revocación**: si `tokenRevocationService.isRevoked(jti)` → **401**.
5. Carga `User` por ID (`userRepo.findById`); si no existe o `active == false` → **401**.
6. **Invalidación por cambio de password**: si claim `pwdAt` del token < `user.getPasswordChangedAt()` → **401**.
7. **Inactivity timeout**: si `lastActivityAt` != null y `Duration.between(lastActivity, now) >= app.security.inactivity-timeout-minutes` (default 15) → **401**.
8. Throttle de escritura: si `lastActivity == null` o pasaron ≥ 5 minutos, setea `lastActivityAt = now` y guarda en DB (evita un UPDATE por request).
9. Setea contexto:
```java
new UsernamePasswordAuthenticationToken(
    user.getEmail(),                       // principal = email
    null,                                  // credentials
    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
);
```

### 5.2 `RateLimitFilter` (OncePerRequestFilter, en memoria)

Límites por IP:
| Endpoint | Límite | Ventana |
|---|---|---|
| `/api/auth/login` | 5 | 60 s |
| `/api/auth/forgot-password` | 3 | 300 s |
| cualquier `/api/**` | 200 | 60 s |

- Implementación: `ConcurrentHashMap<String, LinkedList<Long>>` por mapa (login / forgot-password / api), ventana deslizante con `synchronized(timestamps)`; si la lista ya tiene `maxRequests` entradas → 429.
- Respuesta 429: `{"Error":"Too many requests. Please wait a moment and try again."}` con `Content-Type: application/json`.
- `@Scheduled(fixedRate = 600000)` limpia timestamps viejos y claves vacías.
- **Resolución de IP** (`getClientIp`): si `request.getRemoteAddr()` está en `app.security.trusted-proxies` (lista comma-separated, default vacío), usa `X-Forwarded-For` (primer valor) o `X-Real-IP`; si no, `remoteAddr`. Esto evita spoofing de XFF de clientes directos.
- Nota: single-instance; con múltiples réplicas usar Redis.

---

## 6. `WebSecurityConfig` (@Configuration @EnableWebSecurity)

```java
http
  .csrf(AbstractHttpConfigurer::disable)              // API stateless con JWT
  .cors(cors -> cors.configurationSource(corsConfigurationSource()))
  .headers(h -> h
      .contentSecurityPolicy(csp -> csp.policyDirectives(
          "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
          "img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'"))
      .frameOptions(deny)
      .xssProtection(block)
      .referrerPolicy(STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
      .contentTypeOptions(withDefaults()))
  .sessionManagement(STATELESS)
  .authorizeHttpRequests(auth -> auth
      .requestMatchers("/api/auth/2fa/verify").permitAll()      // se autentica con temp token
      .requestMatchers("/api/auth/logout").authenticated()
      .requestMatchers("/api/auth/2fa/**").authenticated()
      .requestMatchers("/api/auth/register").hasRole("SUPERADMIN")   // ver §14
      .requestMatchers("/api/admin/users/**").hasRole("SUPERADMIN")  // ver §14
      .requestMatchers("/api/auth/**").permitAll()              // login, forgot/reset-password
      .requestMatchers("/swagger-ui/**", "/api-docs/**")
          .hasRole(swaggerEnabled ? "SUPERADMIN" : "NONE")     // "NONE" nunca existe → bloqueado
      .anyRequest().authenticated())
  .exceptionHandling(ex -> ex
      .authenticationEntryPoint(→ 401 "Unauthorized")
      .accessDeniedHandler(→ 403 "Forbidden"))
  .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
  .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

**Beans**:
- `PasswordEncoder` = `new BCryptPasswordEncoder(12)`
- `AuthenticationManager` = `config.getAuthenticationManager()` desde `AuthenticationConfiguration`
- `CorsConfigurationSource`: orígenes desde `app.cors.allowed-origins` (comma-separated, default `http://localhost:3000`); métodos `HEAD, GET, PUT, POST, DELETE, PATCH, OPTIONS`; headers solo `Authorization, Content-Type`; `allowCredentials=true`; `maxAge=3600`.

---

## 7. `UserDetailsServiceImpl`

```java
loadUserByUsername(email):
    user = userRepo.findByEmail(email)  // o throw UsernameNotFoundException
    return new org.springframework.security.core.userdetails.User(
        user.getEmail(), user.getPassword(),
        user.isActive(),   // enabled  → cuenta desactivada = DisabledException en login
        true, true, true,  // accountNonExpired, credentialsNonExpired, accountNonLocked
        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
```
Esto permite que `AuthenticationManager.authenticate()` use el DaoAuthenticationProvider con el `PasswordEncoder` (BCrypt) automáticamente.

---

## 8. `SecurityContextService` (@Service, helper inyectable)

- `getCurrentUser()`: lee `SecurityContextHolder.getContext().getAuthentication()`; si autenticado, `userRepo.findByEmail(auth.getName())`; si no, `null`.
- En origen también: `getCurrentYardId()`, `getCurrentCompanyId()`, `isSuperAdmin()`, `isManager()` → **en destino eliminar todo lo de roles/yard/company**; opcionalmente dejar `isAdmin()`.

---

## 9. Flujos — `AuthServImpl`

### 9.1 Login (`POST /api/auth/login`)
1. `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))`
   - `DisabledException` → "Your account has been deactivated from the system, please contact the super admin"
   - `AuthenticationException` → "Invalid email or password" (mensaje genérico, no revela si el email existe)
2. Re-check `user.isActive()`.
3. Si `twoFactorEnabled == true` → responde:
```json
{ "requires2FA": true, "tempToken": "<jwt temp 5min>", "email": "..." }
```
(sin token final)
4. Si no → `buildFullLoginResponse(user)`:
   - `user.setLastActivityAt(now)` + save (marca sesión activa para el inactivity timeout)
   - genera JWT final
   - responde:
```json
{ "token": "...", "userId": 1, "email": "...", "role": "ADMIN",
  "mustChangePassword": false, "requires2FA": false, "twoFactorEnabled": true }
```

### 9.2 Completar 2FA (`POST /api/auth/2fa/verify`, permitAll)
- Valida firma del temp token + que sea temp (`purpose=2fa-pending`).
- Carga user por `sub`.
- Valida código de 6 dígitos con `new GoogleAuthenticator().authorize(user.getTwoFactorSecret(), code)`.
- Si OK → `buildFullLoginResponse(user)` (token final).

### 9.3 Gestión de 2FA (endpoints autenticados, `TwoFactorAuthController`)

| Endpoint | Body | Comportamiento |
|---|---|---|
| `GET /api/auth/2fa/setup` | — | Genera credenciales TOTP (`googleAuthenticator.createCredentials()`), guarda `twoFactorSecret` + `twoFactorSetupDate` (sin habilitar), retorna `{secret, qrCodeUrl}` con `otpauth://totp/<ISSUER>:<email>?secret=...&issuer=<ISSUER>` |
| `POST /api/auth/2fa/activate` | `{code}` | Valida código contra el secret guardado → `twoFactorEnabled=true` |
| `POST /api/auth/2fa/disable` | `{currentPassword, code}` | Requiere password actual (BCrypt matches) + código TOTP → limpia secret/fecha y `twoFactorEnabled=false` |
| `GET /api/auth/2fa/status` | — | `{enabled: boolean}` |

### 9.4 Logout (`POST /api/auth/logout`, autenticado)
- Extrae token del header `Authorization`, calcula `jti` y `exp`.
- `tokenRevocationService.revoke(jti, userEmail, expiresAt)` → el filtro rechaza ese token en adelante.
- Siempre responde 200 `{"message": "Logged out successfully"}`.

### 9.5 Change password (`PATCH /api/auth/change-password`, autenticado)
- Body: `{currentPassword, newPassword}` (validado con Bean Validation).
- Verifica `currentPassword` con `passwordEncoder.matches`.
- `PasswordValidator.validate(newPassword)` (ver §11).
- Setea hash nuevo, `mustChangePassword=false`, `passwordChangedAt=now` → **todos los JWT previos quedan inválidos** por el check `pwdAt` del filtro.

### 9.6 Update profile (`PATCH /api/auth/profile`, autenticado)
- Body: `{email?, currentPassword, newPassword?}` — siempre exige `currentPassword`.
- Email nuevo: verifica unicidad con `existsByEmailAndIdNot`.
- Password nueva: `PasswordValidator.validate` + hash + `passwordChangedAt=now` + `mustChangePassword=true`.
- Si no hay cambios → error "No changes to apply".

### 9.7 `GET /api/auth/me` (autenticado)
Retorna `UserInfoResponse`: `{id, email, role, mustChangePassword, active, twoFactorEnabled}`.

---

## 10. Recuperación de password — `PasswordRecoveryServiceImpl`

### `POST /api/auth/forgot-password` (permitAll, rate-limited 3/5min)
- **Respuesta SIEMPRE idéntica**: `"If that email exists in our system, a reset link has been sent."` (anti user-enumeration).
- Si el user existe y activo:
  - Borra tokens previos (`tokenRepo.deleteByUser(user)`).
  - Crea `PasswordResetToken`: `token=UUID.randomUUID()`, `expiresAt = now + app.password-reset.token-expiration-minutes` (default 10), `used=false`.
  - Envía email con `JavaMailSender` (SMTP, `SimpleMailMessage`) con link: `{app.password-reset.frontend-url}/reset-password?token=<uuid>`.
  - Fallos de envío solo se loguean, no rompen el flujo.

### `POST /api/auth/reset-password` (permitAll)
- Body: `{token, newPassword}`.
- Valida: token existe, no `used`, no expirado, user activo.
- `PasswordValidator.validate(newPassword)` → setea password, `mustChangePassword=false`, `passwordChangedAt=now`, marca token `used=true`.

### Entidad `PasswordResetToken` (tabla `password_reset_token`)
```
Long id; @ManyToOne(LAZY) User user; String token (unique, 36);
LocalDateTime expiresAt; boolean used = false; LocalDateTime createdAt (@PrePersist);
```
Índices en `token`, `user_id`, `expiresAt`. `@Scheduled` (cada `cleanup-rate-ms` = 1h) borra tokens expirados.

---

## 11. Política de contraseñas — `PasswordValidator` (clase estática)

```
- requerida (no null/blank)
- longitud 8..72  (72 = límite de BCrypt)
- ≥ 1 mayúscula, ≥ 1 minúscula, ≥ 1 dígito, ≥ 1 carácter especial
```
Se aplica en: register, change-password, reset-password, update-profile. Lanza `IllegalArgumentException` con mensaje específico.

---

## 12. Revocación de tokens — `TokenRevocationServiceImpl`

### Entidad `RevokedToken` (tabla `revoked_token`)
```
String jti (@Id, 36); String userEmail; LocalDateTime revokedAt; LocalDateTime expiresAt;
```
Índice en `expires_at`.

### Servicio
- `revoke(jti, userEmail, expiresAt)`: idempotente (`existsByJti`); guarda registro.
- `isRevoked(jti)`: `jti != null && !blank && existsByJti`.
- `@Scheduled(fixedRate = 3600000)` + `@Transactional`: `deleteByExpiresAtBefore(now)` → la tabla no crece indefinidamente.

---

## 13. Configuración (`application.properties`)

```properties
# JWT
jwt.secret=${JWT_SECRET:<hex de 64 chars>}       # ⚠️ en destino: SOLO env var, sin default
jwt.expiration=3600000

# CORS
app.cors.allowed-origins=${CORS_ORIGINS:http://localhost:3000}

# Security
app.security.inactivity-timeout-minutes=${INACTIVITY_TIMEOUT:15}
app.security.trusted-proxies=${TRUSTED_PROXIES:}   # comma-separated IPs de proxy inverso

# Swagger (bloqueado por defecto)
app.swagger.enabled=${SWAGGER_ENABLED:false}

# Password reset
app.password-reset.frontend-url=${FRONTEND_URL:http://localhost:3000}
app.password-reset.token-expiration-minutes=${RESET_TOKEN_EXPIRATION:10}
app.password-reset.cleanup-rate-ms=3600000

# Email (SMTP Gmail)
spring.mail.host=${MAIL_HOST:smtp.gmail.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}          # ⚠️ en destino: SOLO env var
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# Proxy inverso (Railway/Heroku)
server.forward-headers-strategy=framework
server.port=${PORT:8080}
```

---

## 14. Bootstrap del usuario inicial

Este proyecto **no tiene seeder**: el usuario admin se inserta manualmente en la BD (hash BCrypt generado aparte).

**Recomendación para el destino**: un `CommandLineRunner` que, si la tabla `user` está vacía, cree el usuario ADMIN desde variables de entorno (`ADMIN_EMAIL`, `ADMIN_PASSWORD`) con `passwordEncoder.encode(...)`, `role=ADMIN`, `active=true`, `mustChangePassword=true`.

---

## 15. Adaptaciones requeridas en el proyecto destino (sin roles)

1. `UserRole` enum → **solo `ADMIN`**. Eliminar toda la lógica MANAGER/SUPERADMIN, la relación `User.managerSY`, el claim `yardId`, y `SecurityContextService.getCurrentYardId()/getCurrentCompanyId()/isManager()`.
2. `POST /api/auth/register` y `/api/admin/users/**`: en origen eran `hasRole("SUPERADMIN")`. En destino **eliminar el registro público** (el usuario se crea por bootstrap) o protegerlo como `hasRole("ADMIN")` si se quiere permitir gestionar la cuenta.
3. `SecurityFilterChain`: simplificar los matchers de rol; `/api/auth/register` → eliminar o `hasRole("ADMIN")`. El resto queda igual.
4. `LoginResponse` / `UserInfoResponse`: sin `yardId` ni `managerName`.
5. **Mantener íntegro** (son los controles de seguridad core):
   - 2FA TOTP completa (setup/activate/disable/verify con temp token)
   - Revocación de tokens por `jti` (logout) + invalidación por `pwdAt`
   - Rate limiting por IP con trusted proxies
   - Inactivity timeout con throttle de writes
   - Security headers (CSP, frame deny, nosniff, referrer-policy)
   - CORS restringido
   - Password reset anti-enumeration con tokens de un solo uso
   - BCrypt(12) + PasswordValidator

---

## 16. Hallazgos de seguridad a corregir en el destino

1. **Secretos con defaults hardcodeados** en `application.properties` (JWT secret, credenciales SMTP/BD): en el destino usar **solo variables de entorno sin default**, o fallar al arranque si faltan.
2. `AuthServImpl.logout` tiene `catch (Exception ignored)` — loguear el error en vez de ignorarlo.
3. No hay lockout de cuenta por intentos fallidos (solo rate limit por IP) — opcional: contador por email + bloqueo temporal.
4. Rate limit en memoria no funciona con múltiples instancias — usar Redis si habrá réplicas.
5. El filtro JWT guarda el email como principal y `SecurityContextService` re-carga el user de la BD en cada request — aceptable para pocos usuarios; puede cachearse si crece.
6. `spring.jpa.hibernate.ddl-auto=update` solo para desarrollo — en producción usar migraciones (Flyway/Liquibase).

---

## 17. Contratos de API resumidos

| Método | Ruta | Auth | Rate limit | Descripción |
|---|---|---|---|---|
| POST | `/api/auth/login` | — | 5/min | Login; devuelve token o `requires2FA`+`tempToken` |
| POST | `/api/auth/2fa/verify` | temp token | — | Completa login 2FA |
| GET | `/api/auth/2fa/setup` | JWT | — | Genera secret TOTP + URL QR |
| POST | `/api/auth/2fa/activate` | JWT | — | Habilita 2FA |
| POST | `/api/auth/2fa/disable` | JWT | — | Deshabilita 2FA (password + código) |
| GET | `/api/auth/2fa/status` | JWT | — | Estado de 2FA |
| POST | `/api/auth/logout` | JWT | — | Revoca token actual (jti) |
| GET | `/api/auth/me` | JWT | — | Info del usuario actual |
| PATCH | `/api/auth/change-password` | JWT | — | Cambia password (invalida tokens) |
| PATCH | `/api/auth/profile` | JWT | — | Cambia email/password (requiere password actual) |
| POST | `/api/auth/forgot-password` | — | 3/5min | Envia email de reset (respuesta genérica) |
| POST | `/api/auth/reset-password` | — | — | Resetea password con token de un uso |
| POST | `/api/auth/register` | ADMIN | — | Crear usuario (en origen SUPERADMIN) |
| * | `/api/**` (resto) | JWT | 200/min | Endpoints de negocio |
