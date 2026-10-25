# Billetera Móvil Distribuida

Proyecto final de **Sistemas Distribuidos** (Unidad de Competencia 1). Cliente Android nativo
(Kotlin + Jetpack Compose) de una billetera virtual que funciona como **nodo de un sistema
distribuido**, con un servidor propio en Kotlin (Ktor) y base de datos MySQL.

**Autoras:** Pamela Carriel y Alisson Cuenca · **Docente:** Ing. Darwin René Arias Martínez
· Universidad de las Fuerzas Armadas ESPE.

---

## ¿Qué hace?

- **Depósito, retiro, transferencia P2P y pago de servicios** mediante una API REST.
- **Transferencias recibidas en tiempo real** por WebSocket: el saldo sube sin recargar.
- **Historial local con Room**: sobrevive al cierre de la app y funciona **sin internet**.
- **Concurrencia con corrutinas**: toda la red y la base de datos corren en `Dispatchers.IO`
  y la interfaz en `Dispatchers.Main`, evitando congelamientos (ANR).
- Ampliaciones: **login** con usuarios, **cierre de sesión por inactividad**, **facturas**
  reales de servicios y un **panel de pruebas de concurrencia**.
- **Comprobantes** de cada operación, que se pueden **compartir como imagen** (WhatsApp, correo...).
- **Pago de servicios por cédula** (validación de la cédula ecuatoriana, módulo 10).
- **Panel de administración** (usuario `laboratorio`, con interfaz propia de administrador): agregar servicios, emitir facturas y ver los pagos
  en tiempo real; además, contador de **nodos conectados**.
- **Acceso desde cualquier red** con Cloudflare Tunnel (`cloudflared tunnel --url http://localhost:8090`).

## Arquitectura

```
┌───────────────── Cliente Android (Kotlin) ─────────────────┐        ┌──── Servidor (PC) ────┐
│  UI (Compose) → ViewModel → Repository → Room (SQLite)      │  REST  │  Ktor (motor CIO)     │
│                                   │                         │ ◄────► │   ↓                   │
│                                   └── Retrofit/OkHttp ──────┼────────┤  MySQL (cuentas,      │
│                                   └── WebSocket (OkHttp) ───┼──WS───►│  transacciones,       │
└────────────────────────────────────────────────────────────┘        │  usuarios, contratos) │
                                                                        └───────────────────────┘
```

- **Patrón MVVM** con construcción manual de dependencias (sin Hilt/Dagger).
- El **servidor** tiene la verdad del saldo (MySQL); el **teléfono** guarda una copia local (Room).

## Tecnologías

| Capa | Tecnología |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Arquitectura | MVVM, StateFlow, corrutinas |
| Local | Room (SQLite) |
| Red | Retrofit + OkHttp (REST), OkHttp (WebSocket) |
| Servidor | Ktor 3 (motor CIO) |
| Base de datos | MySQL 8 (HikariCP) |

## Estructura del repositorio

```
app/                 Aplicación Android (Kotlin + Compose)
backend-billetera/   Servidor Ktor + MySQL
  └─ postman/        Colección de Postman para probar la API
docs/evidencias/     Capturas y registros de cada fase
entrega/             APK de release firmado
```

## Cómo ejecutar

### 1. Servidor (Ktor + MySQL)
Requisitos: JDK 17+, MySQL en ejecución.

```bash
cd backend-billetera
cp db.properties.example db.properties   # edita puerto, usuario y contraseña de tu MySQL
gradlew run
```
El servidor queda en `http://localhost:8090`. Crea solo la base `billetera`, las tablas y los
usuarios de demostración. **Las credenciales de MySQL no se suben al repositorio** (`db.properties`
está en `.gitignore`).

### 2. Aplicación Android
Abrir `app/` en Android Studio y ejecutar en un emulador (API 26+).
- **Emulador:** usa `10.0.2.2:8090` por defecto.
- **Teléfono real:** en la pantalla de inicio de sesión, botón *Servidor*, pon la IP de la PC
  en la red wifi (por ejemplo `192.168.1.199`) y abre el puerto 8090 en el firewall.

### Usuarios de demostración (contraseña: `demo123`)
| Usuario | Cuenta | Uso |
|---|---|---|
| `pamela` | 1001 | Cuenta principal |
| `usuarioa` | 1002 | Otro usuario (Postman) |
| `usuariob` | 1003 | Otro usuario (Postman) |
| `laboratorio` | 9999 | Panel de pruebas de concurrencia |

También se puede **crear una cuenta nueva** desde la app (recibe $100 de bienvenida).

## Probar la API con Postman
Importar `backend-billetera/postman/Billetera.postman_collection.json`. Ejecutar primero un
**login** (guarda el token solo) y luego las demás peticiones. Endpoints principales:
`POST /auth/login`, `GET /mi-cuenta`, `POST /depositar`, `POST /retirar`, `POST /transferir`,
`GET /contratos/{codigo}?servicio=`, `GET /servicios`, `GET /facturas?cedula=&servicio=`, `POST /pagar-servicio`, `WS /ws`.
Solo `laboratorio` (otras cuentas reciben 403): `GET /admin/facturas?estado=&servicio=`, `POST /admin/facturas`, `POST /admin/servicios`.

Cédulas **ficticias** de demostración (cumplen el algoritmo): `1712345675` (pamela),
`0901234567` (usuarioa), `0102030400` (usuariob).

## Compilar el APK firmado
```bash
cp keystore.properties.example keystore.properties   # pon los datos de tu keystore
gradlew assembleRelease
# resultado: app/build/outputs/apk/release/BilleteraCC-v1.4-release.apk
```
El APK ya compilado está en `entrega/BilleteraCC-v1.4.apk`.

## Demostración (resumen)
1. Encender MySQL y el servidor; abrir la app e iniciar sesión.
2. Depositar, retirar y provocar un error 409 (retiro sin fondos) sin que la app se cierre.
3. Desde Postman, transferir dinero a la cuenta de la app: el saldo sube **en tiempo real**.
4. En el Logcat, filtrar por `HILOS` para ver red y base de datos en `Dispatchers.IO` y la UI en `main`.
5. Con el usuario `laboratorio`, lanzar 50 transferencias y ver el panel de concurrencia.
6. Apagar el servidor o activar el modo avión: el historial y el saldo siguen disponibles (Room).

## Criterios de la rúbrica
| Criterio | Dónde |
|---|---|
| Persistencia local (Room) | `app/.../data/local` |
| Integración REST | `app/.../data/remote` (Retrofit) + servidor Ktor |
| Tiempo real (WebSocket) | `WebSocketCliente` + `NotificadorWebSocket` |
| Concurrencia e hilos | `withContext(Dispatchers.IO)` / `flowOn`, panel de pruebas |
| Interfaz y UX | `app/.../ui` (Compose, estilo fintech) |
