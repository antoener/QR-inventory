# PLAN — Sistema de Inventario con Códigos QR

> Documento vivo. Última actualización: **2026-09-27** (4ª vuelta) — **modelo de datos implementado** (entidades JPA en inglés: `User`, `Product`, `StockMovement` + enums en `enums/`), **se eliminó `AJUSTE`** (solo `INBOUND`/`OUTBOUND`), y **se agregó `email` a `User`** para recuperación de contraseña.
>
> **Estado:** modelo de datos creado, DTOs de movimientos (`CreateStockMovementRequest`, `StockMovementResponse`) y enum `MovementPeriod` definidos, e interfaz `IStockMovement` con sus métodos acordados. **Última planeación:** el usuario elegirá `origin` (`MANUAL` / `MACHINE`) desde el frontend y el backend lo validará y guardará. El actor del movimiento se obtendrá del principal autenticado, nunca del request.
>
> **Nota de naming:** el código está **en inglés** (paquetes `model/`, `enums/`); este documento conserva la prosa en español pero usa los nombres reales del código.
>
> **Único punto abierto:** si el QR va en el estante (por tipo) o en la bolsa (por unidad) — ver sección 5.1. Depende de la reunión con la empresa y **no bloquea** el desarrollo.

---

## 1. Idea general (concepto)

Sistema **web de inventario por códigos QR** para una empresa que **fabrica sus propios bolsos**.

- **~80 tipos de bolso**, todos producidos a **escala similar**. La producción es masiva: una máquina puede hacer **~5000 unidades en una mañana**.
- Cada tipo de producto tiene un **QR único** impreso y pegado como referencia permanente.
- Empleados entran con su cuenta desde el celular o la PC.
- Al **escanear el QR** (o buscar por nombre/código) el sistema abre el producto y muestra su **stock actual** con 3 opciones:
  1. **Agregar (entrada)** → suma cantidad, queda registro `+N`.
  2. **Salida (descuento)** → resta cantidad, queda registro `−N`. **NO es un delete de BD.**
  3. **Consultar stock** → cantidad al instante, sin registrar nada.
- Servidor central online → las entradas/salidas se actualizan **al instante** para todos.
- **Historial** de cada movimiento: fecha, producto, cantidad, tipo, motivo, origen y **quién lo hizo**.

**En una línea:** *"Escaneás el QR de un bolso, la app te dice cuántos hay, sumás o restás cuántos, queda registrado quién y cuándo, y con 80 tipos todo se ve en una pantalla."*

### La fuente del stock: la máquina de producción

Este es el punto central del sistema y lo que lo distingue de un inventario común:

1. La máquina produce el lote del día (ej: **5000 bolsos**).
2. La máquina muestra ese número en su **pantallita**.
3. Un empleado **lee ese número y lo carga** en el sistema como `INBOUND` con motivo `PRODUCTION`.

Es decir: **la entrada no es un conteo del depósito, es la Producción reportada por la fábrica.** El stock es "cuánto fabricamos", no "cuánto hay en el estante".

### Valor que aporta
- Stock real de cualquier producto en cualquier momento, sin planillas desactualizadas.
- Trazabilidad completa de entradas y salidas, con autor, motivo y origen.
- Trazabilidad de la producción: qué día se fabricó cuántos de qué modelo.
- Cero dependencia de proveedores: la empresa fabrica, no hay terceros.

---

## 2. Decisiones de producto CONFIRMADAS

Estas decisiones quedaron cerradas en la sesión de planificación. **No reabrir sin motivo.**

| Tema | Decisión | Nota |
|---|---|---|
| Proyecto | **Nuevo** (Opción B) | Se reusan *patrones* de management, no código |
| Roles | **Uno solo: `ADMIN`** | La columna `role` existe pero no se usa para filtrar nada |
| Recuperación de contraseña | **Por email** | Reversa la decisión previa de "no forgot-password". Campo `email` en `User` (ver sección 5) |
| Identificación | Un **QR por producto** (referencia), no por unidad | El QR apunta al producto, no al empaque |
| Contenido del QR | **URL**: `https://inv.tuempresa.com/p/{code}` | Escaneo con cámara nativa abre directo |
| Cantidades | **Enteras** | Sin decimales. Cero discusión de unidades/kg/litros |
| Precios | **No.** El sistema solo guarda stock | Sin tablas de costos ni de ventas |
| Vencimiento / lotes | **No** | Un producto = un stock |
| Stock mínimo / alertas | **No** | Decidido: no se necesita |
| Categorías / clasificación | **No** | Con 70–80 productos no hace falta |
| Proveedores | **No existe** | La empresa fabrica sus propios productos |
| `description` en producto | **Opcional** | Texto libre: material, talle, medidas. No se indexa ni se busca |
| Motivo del movimiento | **Obligatorio**, de lista cerrada | Con `detail` de texto libre opcional |
| Salida | Descuento con registro, nunca delete | |
| Dar de baja | **Desactivar** (`activo = false`), conserva historial | |
| Historial | Sí, cada movimiento | |
| Stock negativo | **Bloqueado** | Si querés restar 10 y hay 5, se rechaza |

### Fuente del stock y producción

| Tema | Decisión | Nota |
|---|---|---|
| **Fuente del stock** | La **máquina de producción** | ~5000 unidades por modelo en una mañana, mostrado en la pantallita de la máquina |
| **Cómo entra el número** | **Un empleado lo lee y lo tipea** | Sin integración por API. Si más adelante se integra, el diseño ya lo contempla |
| **Significado del stock** | "Cuánto **fabricamos**", no "cuánto hay en el estante" | Sin AJUSTE: las correcciones se hacen con INBOUND/OUTBOUND y el motivo correspondiente |
| **Carga de producción** | **Atajo de 2 toques** desde la pantalla del producto | Botón grande arriba de todo → cantidad + confirmar, con `PRODUCTION` preseleccionado |
| **`origin` del movimiento** | `MANUAL` \| `MACHINE`, elegido por el usuario | En producción se preselecciona `MACHINE`, pero el backend recibe y valida el valor enviado. Un movimiento de máquina **no se borra desde la app**: es la evidencia de producción |
| Escala de producción | Los **80 modelos a volumen similar** | Por eso el QR por unidad no se sostiene (ver 5.1) |

### Lo que se descartó explícitamente
`categorías` · `proveedores` · `precios/costos` · `lotes` · `vencimiento` · `stock mínimo` · `alertas` · `empaque/caja` · `cantidades decimales` · `múltiples roles/permisos` · `paginación` (80 productos entran en una pantalla) · `ZXing en backend` · **integración API con la máquina** (por ahora) · **tabla `unidad`** (ver 5.1)

---

## 3. Relación con "management" (reutilización)

El usuario advirtió que esto es una pequeña porción de funciones ya existentes en management.

### Lo que YA existe en management (reutilizable como patrón)
- **Motor de stock + movimientos**: `Movement` (INBOUND/OUTBOUND) suma/resta con validación de stock insuficiente y registra log.
- **Seguridad completa**: JWT, BCrypt(12), rate-limit en login, scoping por rol.
- **UI de movimientos**: tabla con badges entrada/salida + formulario "New Movement".
- **Impresión PDF** (jsPDF) y `qrcode.react`.
- **Rate limit**: `RateLimitFilter` custom en `SecurityConfig/` (in-memory, sin dependencia externa) — se copia el patrón, 0 deps nuevas.

### Lo que NO existe (lo nuevo en este proyecto)
| Necesidad | Estado en management |
|---|---|
| Entidad "Producto" con SKU/nombre | No existe — "productos" son enum de chatarra sobre containers |
| Stock por producto (entero) | No — hoy es peso de chatarra por contenedor |
| Escaneo de QR con cámara | No (sin ZXing ni scanner frontend) |
| QR propio por producto + etiqueta imprimible | No (solo URL de 2FA) |
| Pantalla "escanear → 3 opciones" | No |
| Historial por producto | Parcial (log existe pero ligado al dominio chatarra) |
| Cuentas de empleados | Parcial (hoy SUPERADMIN/MANAGER) |

### Opción A (reusar management como base) — DESCARTADA
Hereda todo el dominio chatarra (invoices, cashflow, reports, companies) que no aplica, y arriesga romper la app que ya funciona. **Es otro cliente.**

### Opción B (ELEGIDA)
Proyecto **nuevo pequeño** con el mismo stack, que reutiliza los *patrones* de management (motor de movimientos, seguridad) sin arrastrar el dominio chatarra.

---

## 4. Stack

- **Backend**: Spring Boot **4.1.1**, Java **17**, `spring-boot-starter-webmvc` + `data-jpa`, `security`, `validation`, springdoc-openapi, jjwt 0.12.6, MySQL 8, Lombok.
- **Frontend**: React 18 + TypeScript (strict), Vite 5, Tailwind 3, React Router 6, TanStack Query 5, React Hook Form + Zod, lucide-react, clsx + tailwind-merge, jsPDF + jsPDF-AutoTable, `qrcode.react`, **@zxing/browser** (escaneo con cámara), PWA manifest.

### Dependencias backend (cerradas)

**Checkboxes de Spring Initializr (10):**
`web` · `springdoc-openapi` · `data-jpa` · `mysql` · `security` · `validation` · `lombok` · `devtools` · `configuration-processor` · `h2`

**A ajustar a mano en el `pom.xml` (4):**
1. Subir `springdoc-openapi-starter-webmvc-ui` a **3.1.1** (Initializr emite 3.1.0)
2. H2 a `<scope>test</scope>` (Initializr lo deja en `runtime`)
3. Agregar **jjwt-api / jjwt-impl / jjwt-jackson 0.12.6** (Initializr no lo ofrece)
4. `<excludes>` de Lombok en `spring-boot-maven-plugin`

**Excluidas a propósito:**
- `googleauth` (2FA) → era de management, no necesario acá
- `com.google.zxing` → el QR es 100% frontend
- `testcontainers` → se usa H2 para tests
- `actuator` → no pedido
- Bucket4j o similar → se copia el `RateLimitFilter` custom (0 deps)

**Agregada respecto al plan original:**
- `spring-boot-starter-mail` → para la recuperación de contraseña por email. En dev el link de reset se **loguea** (sin SMTP real); en prod SMTP por env vars.

### ⚠️ Inconsistencia detectada en management
`management/pom.xml` usa **springdoc 2.8.9**, cuyo parent en Maven Central es `spring-boot-starter-parent:3.5.0` — está pensado para **Boot 3.x, no 4.x**. Es probable que el `/swagger-ui` de management esté roto o corriendo por accidente. **Regla para el proyecto nuevo:** Boot `4.1.x` → springdoc `3.1.1`; Boot `4.0.x` → springdoc `3.0.3`. Nunca 2.x con Boot 4.

### Trampa de Spring Initializr
Los IDs del selector de versión llevan sufijo `.RELEASE` (`4.1.1.RELEASE`) que **no existe en Maven Central** (404). Si se pasa ese parámetro, el `pom.xml` generado queda irresoluble. **Dejar el selector en el default** o pinear la versión limpia a mano.

### Base de datos — cómo se crea el esquema

**Decisión:** la base MySQL se **crea a mano** y **Hibernate crea las tablas** desde el modelo de datos al levantar la app. Sin Flyway, sin Liquibase, sin `docker-compose` de infra.

| Entorno | `ddl-auto` | Por qué |
|---|---|---|
| **dev** | `update` | Iterás rápido: agregás un campo a la entidad y la tabla se actualiza sola |
| **prod** | `validate` | Hibernate **no toca** el esquema. Los cambios van por scripts `.sql` a mano, guardados en el repo |

**Por qué el corte dev/prod:** `ddl-auto=update` no solo crea tablas, **también las altera en silencio**, sin dejar registro de cómo quedó la base. En desarrollo es una comodidad; en producción es pérdida de datos sin historial. Con `validate` + scripts SQL versionados se conserva la velocidad de iterar y a la vez queda el registro de qué se ejecutó.

**Scripts de cambio de esquema:** carpeta `backend/db/` con un `.sql` por cambio, aplicado a mano y commiteado. Sin framework de migraciones.

**Archivos de config:**
- `application.yml` + `application-dev.yml` + `application-prod.yml` (YAML, no `.properties`)
- **Sin claves duplicadas** — management tiene el bloque de datasource y JPA escrito dos veces en el mismo archivo (líneas 6–25 y 103–118), y gana el último. Si se edita el primero, no pasa nada y no te enterás
- Secrets **solo** por variables de entorno, con `.env` en `.gitignore` y un `.env.example` con los campos vacíos

### ⚠️ Dos cosas de management que NO se copian

**1. Password de BD con default hardcodeado**
`management/src/main/resources/application.properties:8`
```properties
spring.datasource.password=${DB_PASSWORD:Spring2026.*}
```
Si `DB_PASSWORD` no está definida, la app **arranca igual** con una contraseña que está en el código y en el historial de git. En el proyecto nuevo **va sin default**: si falta la variable, la app no arranca. Es preferible que falle en el arranque a exponer un secreto.

**2. Bloque de configuración duplicado**
El `application.properties` de management tiene 166 líneas con datasource y JPA **escritos dos veces**. El segundo gana silenciosamente. Se evita usando `application.yml` con profiles separados.

### Bootstrap del usuario ADMIN

Se **copia el patrón de `management/SecurityConfig/DataInitializer.java`**, que ya está bien hecho:

| Línea de management | Qué hace | Se copia |
|---|---|---|
| `:20` | Lee el password de `${app.admin.default-password}`, nunca hardcodeado | ✅ |
| `:40-42` | Sin password configurado → genera uno aleatorio (`Adm1n$` + 6 chars de UUID) | ✅ |
| `:48` | Nace con `mustChangePassword = true` | ✅ |
| `:50-56` | Loguea el password temporal en un `warn` para verlo al arrancar | ✅ |
| `:39` | Solo crea si el usuario **no existe** | ✅ |
| `:31-37` | Migración de email legacy (`admin@syms.com` → `superadminsyms@gmail.com`) | ❌ es historia de otro proyecto |
| `:58-60` | `try/catch` que se traga la excepción y solo loguea un `warn` | ❌ si el seeding falla, seguís como si nada |

**En el proyecto nuevo:** password inicial desde `${APP_ADMIN_PASSWORD}`, `mustChangePassword = true`, seed solo si la tabla de usuarios está vacía, **sin** la lógica de email legacy y **sin** el `catch` que silencia errores.

### Carga de productos
Los ~80 productos entran **a mano** desde la pantalla de alta. Sin importación CSV.
- **Validación obligatoria de `code`:** único y con formato. El código es lo que va dentro del QR — si al cargar a mano se escribe `BOL-OO1` con dos eles en vez de `BOL-001`, la etiqueta que se imprime después apunta a un código inexistente y el producto queda inaccesible salvo por búsqueda.

---

## 5. Modelo de datos — 3 tablas

```
users           (id, username UNIQUE, email UNIQUE, password, name,
                 active BOOL, must_change_password BOOL, role ENUM,
                 created_at)

products        (id, code UNIQUE, name, description NULL,
                 stock INT, active BOOL, version BIGINT,
                 created_at)

stock_movement  (id, product_id FK, type ENUM, quantity INT,
                 reason ENUM, detail NULL, origin ENUM,
                 user_id FK, created_at)
```

**Notas:**
- `code` es el texto que va dentro del QR. Único. Ej: `BOL-001`
- `quantity` es **siempre positiva**; el signo lo da `type` (`INBOUND`/`OUTBOUND`)
- `version` es para **concurrencia optimista** (`@Version`)
- `stock` es un número cacheado; la **verdad** es `Σ movimientos`. Se puede recalcular
- `email` es `UNIQUE` y `NOT NULL` — es la vía de recuperación de contraseña
- **`origin`**: `MANUAL` (lo cargó una persona) o `MACHINE` (reportó producción). Por ahora los dos los carga una persona, pero el campo separa la trazabilidad de la producción real y deja la puerta abierta a la integración por API sin migración
- Con ~5000 unidades por lote, `INT` alcanza de sobra (límite ~2.1 mil millones). Si algún día el histórico de movimientos se acumula muchísimo, migrar a `BIGINT` es trivial. Decisión: `INT` por ahora, es holgado
- **Relaciones:** `Product 1—N StockMovement` y `User 1—N StockMovement` (sin relación directa `Product↔User`). Las FKs `product_id` y `user_id` son `NOT NULL`. Mapeo unidireccional (`@ManyToOne` en `StockMovement`), sin colección inversa.

### Enum de tipo — clase `MovementType`

| Valor | Significado |
|---|---|
| `INBOUND` | Entrada (suma stock) |
| `OUTBOUND` | Salida (resta stock) |

### Enum de origen — clase `Origin`

| Valor | Significado |
|---|---|
| `MANUAL` | Movimientos cargados por una persona (salidas, devoluciones) |
| `MACHINE` | Reporte de producción. **No se puede borrar desde la app** |

### Enum de motivo — clase `Reason` (obligatorio, lista cerrada)

| INBOUND | OUTBOUND |
|---|---|
| `PRODUCTION` | `SALE` |
| `MATERIAL_PURCHASE` | `WASTE` |
| `CUSTOMER_RETURN` | `INTERNAL_USE` |
| — | `GIFT` |
| — | `LOSS` |

En la UI son **botones grandes**; el teclado es opcional (solo con "otro"). Motivo controlado y no texto libre evita el problema "merma / Merma / MERMA / se rompió" que después impide filtrar.

### Enum de rol — clase `Role`

| Valor |
|---|
| `ADMIN` |

---

## 5.1 ⚠️ PUNTO ABIERTO — ¿QR por tipo o por unidad?

**Este es el único punto del plan que NO está resuelto.** Depende de la reunión con la empresa.

### Las dos alternativas

**A) QR por tipo / estante** ← *la evidencia apunta acá*
- Un QR pegado en el estante que representa "bolsos cuero marrón, tipo 3"
- Escaneás el estante → ves cuántos hay de ese tipo → `+5000` o `−300`
- El stock se mueve **por lotes**, no de a uno
- **~80 etiquetas en total**, impresas una vez
- **El modelo actual sirve sin cambios**

**B) QR por unidad**
- Cada bolsa individual lleva su QR
- Requiere una tabla `unidad` con **miles de filas por lote**, y el movimiento tiene que decidir si mueve el tipo o una unidad
- Implica pegar 5000 etiquetas por día: trabajo infinito, y se caen
- Solo tiene sentido para producción baja o piezas únicas

### Por qué probablemente sea A
Los **80 modelos se producen a volumen similar** (~5000/morning cada uno). Con esa escala, un QR por unidad no se sostiene ni operativamente ni por sentido común: nadie escanea 5000 bolsas una por una para contarlas, y pegar 2000 etiquetas por día es una fábrica de trabajo.

### Preguntas para la reunión
1. **"¿El QR va en la bolsa o en el estante?"** — y contá que el sistema puede hacer las dos.
2. **"Cuando dicen 'hay 5000 de este modelo', ¿de qué hablan?"** — ¿lo fabricado? ¿lo que hay en el depósito? ¿lo vendido y no entregado? Si es la tercera, falta un estado más y ya es otro sistema.
3. **"¿Cómo saben hoy cuántos hay?"** — ¿cuentan en el depósito? ¿anotan lo que entra y sale? ¿más o menos a ojo? Esto define si el stock es "cuanto fabricamos" o "cuanto hay" y cómo se corrigen las diferencias.

### Por qué se puede empezar a construir igual
`Producto` = **el tipo de producto** es cierto en ambos casos. Lo único que cambia es *a qué apunta* el QR y si más adelante hay que agregar la tabla `unidad`. Si al final necesitan unidad, se agrega la tabla y un endpoint más; **el movimiento por tipo sigue sirviendo y no hay que rehacer nada.**

### Ejercicio en vivo (el objetivo de la reunión)
Elegir **2–3 modelos reales** y hacer la cuenta juntos: *"de este modelo, ¿cuánto entró la semana pasada?, ¿cuánto salió?, ¿cuánto hay hoy?"*. Si los tres números cierran solos, el sistema funciona. Si no cierran, se descubre qué falta **antes** de construirlo.

---

## 6. Reglas de negocio

1. **Stock nunca negativo.** Se rechaza la salida si excede el stock disponible.
2. **No se borra nada.** Un producto se **desactiva**. Un movimiento mal cargado se corrige con un movimiento inverso, nunca editando el pasado.
3. **Motivo obligatorio** en todo movimiento, con `detail` libre opcional.
4. **`stock` cacheado, historial como verdad.** `stock = Σ INBOUND − Σ OUTBOUND`.
5. **Todos los usuarios son ADMIN.** El único control de acceso real es: **sin login no se mueve stock**. El login existe para que el historial diga *quién*.
6. **`User` es la única entidad de autenticación.** No existe `Manager` ni `Admin` como entidades separadas: con un solo rol no aportan nada, y si aparecieran más roles se resuelve extendiendo el enum `Role`, no creando tablas nuevas.
7. **La columna `role` existe** con un único valor `ADMIN`, para que el día que aparezca otro rol (supervisor, operario, etc.) no haya que migrar datos ni replantear auth. No se usa para filtrar nada hoy.
8. **Un admin puede crear otros admins.** `POST /api/users` permite crear cuentas `ADMIN`. Como hoy todos son `ADMIN`, cualquier cuenta logueada puede hacerlo; cuando existan más roles, la regla se extiende (p. ej. solo `ADMIN` crea `ADMIN`).
9. **`mustChangePassword` bloquea el uso.** El admin bootstrap nace con el flag en `true` y no puede operar ningún endpoint de stock hasta que lo cambie. Es lo que evita que un password temporal sobreviva en producción.

---

## 7. Endpoints

**Auth** — `POST /api/auth/login` · `POST /api/auth/refresh` · `POST /api/auth/logout` · `GET /api/auth/me` · `POST /api/auth/forgot-password` · `POST /api/auth/reset-password` *(recuperación por email — pendiente de implementar)*

**Products** — `GET /api/products?q=` (busca por código o nombre; `q` vacío o ausente devuelve todo el catálogo) · `GET /api/products/{id}` · `GET /api/products/code/{code}` ← *lo llama el escaneo* · `POST` · `PUT /{id}` · `PATCH /{id}/status` (activar/desactivar)

> **Nota para el frontend:** `/api/products?q=` se usa para buscar o listar productos por nombre o código de forma parcial; por ejemplo, `/api/products?q=cuero`. Si `q` está vacío o no se envía, se devuelve todo el catálogo. Al escanear un QR se debe usar `GET /api/products/code/{code}` para resolver el código exacto del producto; esa búsqueda no pasa por `q`.

**Movements** — `POST /api/movements/inbound` · `POST /api/movements/outbound` · `GET /api/movements?productId=&type=` · `GET /api/movements/recent`

**Users** — `GET/POST/PUT /api/users` · `PATCH /api/users/{id}/status`

**Dashboard** — `GET /api/dashboard/summary`

---

## 8. Estructura de carpetas

### Backend — paquetes por dominio (NO por capa)
```
/mnt/c/SpringBoot Projects/Qr/Qr/
  src/main/java/com/empresa/inventario/
    enums/                       # MovementType, Reason, Origin, Role
    model/                       # User, Product, StockMovement  (entidades JPA)
    auth/        user/           # (futuro: services, repos, controllers, DTOs)
    product/     movement/
    dashboard/   security/
    common/      → GlobalExceptionHandler, ApiError
  src/main/resources/
    application.properties       # secrets por ${ENV} + .env (gitignored)
  frontend/                      # React SPA (futuro)
```
> Regla de `AGENTS.md` (management): lógica de negocio en `Service`, entidades JPA nunca expuestas en la API, DTOs para request/response, sin lógica en controllers.

### Frontend — features por dominio
```
frontend/src/
  api/  components/  config/  context/  features/  hooks/
  lib/  pages/  routes/  types/  utils/
  features/
    auth/  products/  labels/  scanner/  movements/  dashboard/  users/
```

---

## 9. Feature: Generar y descargar etiquetas QR

> **Pendiente de la sección 5.1:** si el QR va en el estante (por tipo) o en la bolsa (por unidad), cambia **dónde se pega** la etiqueta y **cuántas se imprimen** (80 una vez, vs. miles por día). El diseño de la etiqueta en sí no cambia.

### Qué lleva la etiqueta
| Elemento | ¿Sí? | Nota |
|---|---|---|
| El QR | ✅ | Contenido = la URL del producto |
| Nombre del producto | ✅ | Para que un humano sepa qué es sin escanear |
| Código del producto | ✅ | `BOL-001`, texto grande y legible |
| **Stock** | ❌ | **Nunca.** La etiqueta es permanente y el stock cambia diario. Si se imprime, al día siguiente miente |
| Logo de la empresa | Opcional | Si lo mandan, entra arriba |

### Dos modos de generación
- **A) Etiqueta individual** — desde la ficha del producto, botón "Descargar etiqueta". Un PDF con esa.
- **B) Lote** — pantalla `/etiquetas` con los 80 productos y casillas. Se tildan los deseados y un botón genera **un solo PDF con todas**, en grilla lista para imprimir y pegar. **Es lo que se usa el primer día para imprimir las 80 de una.**

### Implementación
100% navegador, **sin dependencia backend**:
- `qrcode.react` dibuja el QR → se exporta a dataURL
- `jspdf` arma el PDF (grilla de etiquetas, no tabla → no hace falta AutoTable)
- Tamaños a ofrecer: **3×3 cm**, **5×5 cm**, **10×10 cm**

### Por qué URL y no código plano
- ✅ Escanear con la **cámara nativa** del celular abre directo el producto, sin buscar la app. Se siente "mágico"
- ❌ **El dominio queda grabado en la etiqueta impresa** (si se muda de hosting, hay que reimprimir)
- Mitigación: subdominio corto y estable que uno controle (`inv.tuempresa.com`), y el backend lee **solo el código** después del `/p/`

---

## 10. Feature: Escanear

### Flujo
```
[Botón "Escanear"]  →  se abre la cámara  →  apunta al QR
                                             ↓
                                   se reconoce el código
                                             ↓
                                  pantalla del producto
                                   ┌──────────────────────┐
                                   │  Bolso cuero         │
                                   │      12.480          │  ← stock grande
                                   ├──────────────────────┤
                                   │  [⬆ Cargar producción]│  ← atajo, ver abajo
                                   ├──────────────────────┤
                                   │  [Agregar]           │
                                   │  [Restar]            │
                                   │  [Consultar]         │
                                   └──────────────────────┘
```

### El atajo "Cargar producción" — la acción más frecuente del sistema

Como la entrada viene de la máquina, cargar producción es lo que se va a hacer **varias veces por día**. Por eso tiene un botón propio arriba de todo, que lleva directo a:

```
[Cargar producción]  →  pantalla de cantidad con teclado numérico grande
                          motivo: PRODUCTION  (preseleccionado, no se toca)
                          origen: MACHINE       (preseleccionado, editable)
                          → Confirmar  →  vuelve a la cámara
```

**Dos clics en total.** No es una pantalla nueva con lógica propia: reutiliza el mismo endpoint `POST /api/movements/inbound`. Lo único que hace el atajo es saltear la pantalla de "elegir acción" y la de "elegir motivo", porque en este caso siempre son las mismas.

El `detail` libre queda disponible por si quieren anotar algo (ej: "turno mañana", "lote con error en el cierre").

### Decisiones de diseño
- **El botón "Escanear" es siempre visible**, no una pantalla a la que se entra por menú. Es la acción que el empleado va a hacer 200 veces por día. Botón flotante o barra inferior fija.
- **Fallback manual siempre visible:** link *"¿Tenés el código? Ingresalo a mano"* en la pantalla de escaneo. Las etiquetas se manchan, se rasgan, se caen. Sin esto, un QR roto deja el producto inaccesible.
- **Código inexistente:** mensaje claro *"El código `BOL-999` no corresponde a ningún producto"*, con botón de buscar por nombre.
- **Ruta `/p/:code`:** entrada directa desde el QR nativo. Redirige a la ficha del producto; si no hay sesión, **recuerda el destino** y va a login primero.
- **Después de confirmar un movimiento** vuelve **directo a la cámara** para el siguiente, sin pasar por el menú. Es el flujo real: escanear → restar → escanear → restar.

---

## 11. Fases de construcción

| # | Fase | Contenido |
|---|---|---|
| 0 | **Bootstrap** | `pom.xml` (10 deps Initializr + jjwt + fix springdoc + H2 a test), `docker-compose` con MySQL 8, `application.yml` con secrets por `${ENV}`, `.env.example` |
| 1 | **Auth** | login, refresh en cookie httpOnly con **rotación + detección de reuso**, blacklist de access tokens, rate-limit en login, BCrypt 12, filtro `JwtAuthenticationFilter` + `WebSecurityConfig` con CSP/headers |
| 2 | **Productos** | CRUD, activar/desactivar, búsqueda por código y nombre, `description` opcional |
| 3 | **Movimientos** | Motor de stock: inbound/outbound, validación de stock insuficiente, `@Version` para concurrencia, enums de motivos y `origin` |
| 4 | **QR lectura** | Escaneo con cámara + fallback manual, ruta `/p/:code`, pantalla de producto con 3 acciones |
| 5 | **Carga de producción** | Atajo de 2 toques, teclado numérico grande, `PRODUCTION` preseleccionado, `origin=MACHINE` preseleccionado (editable), protección contra borrado |
| 6 | **QR escritura** | Etiqueta individual + descarga en lote, selector de tamaño |
| 7 | **Dashboard** | Vista general de stock + movimientos recientes + historial filtrable |
| 8 | **Producción** | Tests, hardening (CSP/CORS), escaneo de dependencias OWASP, despliegue con HTTPS |

**Dependencias entre fases:** la 4 y la 6 son independientes entre sí. La 1 bloquea a todas las demás. La 3 bloquea a la 4 y a la 5. La 5 y la 4 van juntas en la práctica: son las dos pantallas que se usan todo el día.

**¿Se puede empezar sin la reunión?** Sí. Las fases **0, 1, 2, 3, 7 y 8** no dependen del punto abierto 5.1. Solo la definición final del *contenido* de la etiqueta (sección 9) necesita saber si va en el estante o en la bolsa.

---

## 12. Riesgos y cosas a confirmar

### Riesgos técnicos
1. **⚠️ Transcripción del número de la máquina** *(el riesgo más serio)*. Si alguien lee `5000` de la pantallita y lo tipea, un dígito mal deja el stock corrido **y nadie se entera hasta el próximo conteo**. Con lotes de 5000, el error relativo es chico pero el absoluto es grande. *Mitigaciones:* confirmación explícita de la cantidad antes de guardar y `origin` en el movimiento para poder auditar. *Solución de fondo:* que la máquina reporte por API, si es que tiene salida de datos.
2. **HTTPS es obligatorio para la cámara.** En iPhone el bloqueo es duro, sin excepción. Antes de la Fase 4 hace falta un túnel (ngrok / Cloudflare Tunnel) para probar en el celular. Afecta el cronograma, no la arquitectura.
3. **El dominio queda grabado en la etiqueta.** Si se cambia, hay que reimprimir las 80.
4. **Concurrencia de stock.** Dos empleados escanean el mismo producto a la vez. Resuelto con `@Version` (optimistic lock) + reintento con mensaje claro. *Alternativa: bloqueo pesimista. Pendiente de confirmación.*
5. **Imprimir el stock en la etiqueta** → la etiqueta es permanente, el stock cambia diario. La plantilla no lo incluye, por diseño.
6. **El `stock` es "cuánto fabricamos", no "cuánto hay".** Si el depósito tiene mermas, pérdidas o roturas que nadie registra, el stock teórico se va a alejar del físico. Como no existe el `AJUSTE`, esas diferencias deben registrarse con un `OUTBOUND` (merma/pérdida) con su motivo correspondiente. **Es la pregunta 3 de la sección 5.1.**

### Pendiente de la reunión con la empresa
Ver **sección 5.1** — las tres preguntas del punto abierto (QR tipo vs. unidad, significado del stock, cómo lo cuentan hoy). Antes de la primera impresión de etiquetas en papel conviene además validar:
- ¿El código interno se parece a `BOL-001` o la empresa ya tiene otro criterio de codificación?
- ¿Confirmar que no manejan vencimiento ni lotes en ningún producto?
- ¿Cuántos empleados van a usar el sistema? (define si el rate-limit y el refresh token aguantan)
- ¿Dónde se hospeda? (define el subdominio del QR y si hay HTTPS)
- **¿La máquina tiene salida de datos?** (Ethernet, RS-232, USB) — define si la integración por API es viable algún día
- **Ejercicio en vivo:** 2–3 modelos reales, hacer la cuenta de entrada/salida/stockactual juntos

### Pendientes de infraestructura
- Dominio/subdominio para el QR (`inv.tuempresa.com`)
- Servidor de hosting + HTTPS
- ¿Necesita el logo de la empresa para las etiquetas?
- Tamaño de etiqueta preferido (3×3 / 5×5 / 10×10 cm)

---

## 13. Checklist de release (de la skill `springboot-security`)

- [x] Access/refresh tokens validados y expirando correctamente
- [x] Refresh token con rotación + detección de reuso
- [x] Rate limiting en `/api/auth/login`
- [x] BCrypt(12) como `PasswordEncoder` bean
- [x] CSRF deshabilitado (API stateless con Bearer) + `STATELESS` session policy
- [x] CORS con orígenes restringidos por env var (nunca `*`)
- [x] Headers de seguridad: CSP, frame-deny, no-referrer, nosniff
- [x] DTOs validados con Bean Validation, entidades nunca expuestas
- [x] Cero SQL por concatenación
- [ ] Secrets 100% por env vars, nada en `application.yml` — **y `DB_PASSWORD` sin default**: si falta, la app no arranca
- [ ] `ddl-auto=validate` en prod (no `update`)
- [ ] Sin claves duplicadas en el archivo de config
- [x] Admin bootstrap: password desde env var, `mustChangePassword=true`, seed solo si la tabla está vacía
- [ ] Endpoints de stock bloqueados mientras `mustChangePassword = true`
- [ ] `code` de producto único y con formato validado (es lo que va en el QR)
- [ ] `@Version` para concurrencia de stock
- [x] Campo `origin` validado: `MANUAL` / `MACHINE` enviado desde el cliente y validado por el backend
- [ ] Movimientos con `origin=MACHINE` **no borrables** por la API
- [ ] `detail` saneado y con largo máximo (nada de HTML crudo)
- [x] `email` único y validado en `User`; admin bootstrap con `email` desde `APP_ADMIN_EMAIL`
- [ ] Recuperación de contraseña: token hasheado de un solo uso, con expiración, respuesta genérica (no revela si el email existe)
- [ ] Rate limiting en `/api/auth/forgot-password`
- [ ] H2 console deshabilitado fuera de dev
- [ ] Dependencias escaneadas (OWASP Dependency Check) sin CVEs críticos
- [x] Logs sin datos sensibles

---

## 14. Próximos pasos

1. [x] Definición de variables de producto → **cerrado en sección 2**
2. [x] Documento de producto cerrado (secciones 1–2)
3. [x] Fuente del stock definida: la máquina de producción (secciones 1, 2, 5)
4. [x] Detalles técnicos de QR definidos (secciones 9–10)
5. [x] Plan técnico final y dependencias backend (secciones 4–8)
6. [x] Infraestructura de BD y bootstrap de admin definidos (sección 4)
7. [x] **Crear el modelo de datos** — entidades JPA de las 3 tablas (`User`, `Product`, `StockMovement` + enums)
8. [ ] **Reunión con la empresa** → resolver el punto abierto de la sección 5.1 (QR tipo vs. unidad, significado del stock)
9. [ ] **Arrancar Fase 0** (bootstrap del proyecto) — no depende de la reunión
10. [ ] Validar con 2–3 modelos reales antes de imprimir etiquetas
11. [ ] Definir hosting + dominio del QR
12. [ ] Verificar si la máquina tiene salida de datos para una futura integración por API

13. [x] Empezar la impl de IUserService
14. [x] Definir DTOs y enum `MovementPeriod` para movimientos
15. [x] Definir métodos de `IStockMovement`
16. [x] **Implementar Fase 1a — Auth (nucleo)**: login, refresh en cookie httpOnly con rotación + detección de reuso, blacklist de access tokens, rate-limit, BCrypt 12, `JwtAuthenticationFilter` + `WebSecurityConfig` con CSP/headers, `DataInitializer` (admin bootstrap) y `change-password`
    - [ ] **Fase 1b — 2FA + recuperación de password**: 2FA TOTP opt-in por usuario (`googleauth`) y `forgot/reset-password` (mail) encima del núcleo validado
17. [x] **Implementar `registerInbound`** (primera operación de `IStockMovement`): validación, transacción, actualización de `Product.stock` y registro completo del movimiento con usuario autenticado y `origin` elegido por el cliente
18. [x] Implementar `registerOutbound` y `getCurrentStock`
19. [ ] Implementar `findByPeriod` y `recent` de `IStockMovement` + endpoints `GET /api/movements?productId=&type=` y `GET /api/movements/recent`

---

## 15. Estado de la sesión de trabajo — Frontend

> Última actualización: **2026-10-06**. El backend de movimientos (inbound/outbound/stock) y su controller están listos y con tests. El frontend se construyó por fases; **la fase 5 quedó escrita pero sin cerrar**.

### Hecho
| Fase | Contenido | Estado |
|---|---|---|
| 0 | Scaffold Vite/React/TS/Tailwind, proxy `/api`, alias `@/`, PWA manifest, `.env` | ✅ build OK |
| 1 | Capa API: `client.ts` (token en memoria + auto-refresh en 401), endpoints, hooks TanStack Query, `AuthContext` | ✅ build OK |
| 2 | UI auth: `Button`/`Input`/`Card`, `LoginPage`, `ChangePasswordPage`, `AuthGuard`/`PublicOnlyGuard`, `AppLayout`, router | ✅ build OK |
| 3 | Productos: `ProductsPage` (listado + búsqueda + modal alta/edición + activar/desactivar) | ✅ build OK |
| 4 | Escáner: `Scanner` (`@zxing/browser` + fallback manual), `ScannerPage`, `ProductDetailPage` (stock grande + atajo producción + 3 acciones), `MovementFormPage` | ✅ build OK |
| 5 | `MovementsPage` (entrada/salida + selector de producto + form con reintento 409 + últimos movimientos locales) | ⚠️ **escrita, ruta sin montar y build sin verificar** |

### Lo inmediato (retomar acá)
1. Importar `MovementsPage` en `frontend/src/routes/index.tsx` y reemplazar el placeholder `<div>Movimientos</div>` (línea 27).
2. Correr `npm run build` y verificar tipos.
3. Opcional: reemplazar el `window.location.href = '/scanner'` de `HomePage.tsx` por `useNavigate`.
4. El historial de "últimos movimientos" de Fase 5 es `useState` local; se pierde al recargar. Al implementar `GET /api/movements/recent` (ítem 19) pasarlo a TanStack Query.

### Fases restantes
| Fase | Contenido | Punto de partida |
|---|---|---|
| 6 | **Etiquetas QR**: etiqueta individual desde ficha + pantalla `/etiquetas` modo lote con casillas, PDF en grilla, tamaños 3×3 / 5×5 / 10×10 cm. Usa `qrcode.react` + `jspdf` (ya en deps). 100% navegador, sin backend. | `features/labels/` vacío |
| 7 | **Usuarios**: pantalla listado, alta de admin, activar/desactivar. Hooks ya listos. | `features/users/hooks.ts` existe, falta UI |
| 8 | **Dashboard**: stock general + movimientos recientes + historial filtrable. Depende del ítem 19. | `features/dashboard/` vacío |

### Backend pendiente que habilitan fases
- **Movements** (ítem 19): `GET /api/movements?productId=&type=` y `GET /api/movements/recent`.
- **Dashboard**: `GET /api/dashboard/summary` — no existe el controller.
- **Auth**: `POST /api/auth/forgot-password` y `POST /api/auth/reset-password` (email; en dev el link se loguea) + rate-limit en forgot-password.
- Bloquear endpoints de stock mientras `mustChangePassword = true`.

### Orden sugerido
1. Cerrar Fase 5 (router + build).
2. Fase 6 (etiquetas) — independiente de la reunión y del backend.
3. Fase 7 (usuarios) — backend listo.
4. Backend `recent`/`findByPeriod` → Fase 8 (dashboard).

### Notas de entorno
- Maven requiere `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` (proyecto apunta a Java 17, solo hay JDK 21).
- Tests backend: correr solo el test de la clase recién tocada (`-Dtest="StockMovementImplTest#..."`); suite completa con `DB_URL='jdbc:mysql://192.168.112.1:3306/qr_db?serverTimezone=UTC'`.
- Bundle frontend ~890 kB por `@zxing` + `jspdf` + `qrcode.react`; evaluar code-split en Fases 6/8.
- `VITE_QR_BASE_URL=http://localhost:3000` es placeholder hasta definir dominio del QR.
- **HTTPS obligatorio** para la cámara en iPhone (probar con túnel ngrok/Cloudflare).

---

## 16. Por Implementar inmediato

Backend de movimientos y fases 0–5 del frontend listas (build OK). Falta:

1. **Fase 6 — Etiquetas QR** (`features/labels/` vacío)
   - Etiqueta individual desde la ficha del producto.
   - Pantalla `/etiquetas` modo lote con casillas, PDF en grilla.
   - Tamaños 3×3 / 5×5 / 10×10 cm con `qrcode.react` + `jspdf`. Sin backend.

2. **Fase 7 — Usuarios** (hooks listos en `features/users/hooks.ts`, falta la UI)
   - Listado de usuarios, alta de admin, activar/desactivar.

3. **Fase 8 — Dashboard** (`features/dashboard/` vacío)
   - Stock general + movimientos recientes + historial filtrable por período.
   - Depende del backend `recent`/`findByPeriod`.

4. **Backend pendiente**
   - `GET /api/movements?productId=&type=` y `GET /api/movements/recent` (ítem 19 del §14).
   - `GET /api/dashboard/summary` (no existe el controller).
   - `POST /api/auth/forgot-password` y `POST /api/auth/reset-password` (email; en dev el link se loguea) + rate-limit.
   - Bloquear endpoints de stock mientras `mustChangePassword = true`.

**Orden sugerido:** Fase 6 → Fase 7 → backend `recent`/`findByPeriod` → Fase 8.