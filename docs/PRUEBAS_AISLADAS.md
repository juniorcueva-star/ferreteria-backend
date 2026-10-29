# Pruebas aisladas del Sprint 16.1

Las pruebas de integracion y E2E no usan la base de demostracion `ferreteria` ni su puerto `5434`.
El unico entorno permitido para escritura automatizada es el definido en `backend/docker-compose.test.yml`.

## Recursos autorizados

| Recurso | Valor |
|---|---|
| Contenedor | `ivera-s16-test-postgres` |
| Base | `ivera_sprint16_1_test` |
| Puerto | `5545` |
| Volumen | `ivera_s16_test_postgres_data` |
| Backend E2E | `http://localhost:8081` |
| Frontend E2E | `http://localhost:5174` |

## Preparacion

1. Copia `.env.test.example` a `.env.test` y genera valores exclusivos para `TEST_DB_PASSWORD`, `TEST_JWT_SECRET`, `E2E_ADMIN_PASSWORD` y `E2E_DEMO_PASSWORD`.
2. Copia `ferreteria-frontend/.env.e2e.example` a `.env.e2e` con las mismas contrasenas E2E y deja las URLs fijas en 5174 y 8081.
3. Verifica que Docker Desktop este disponible. No reinicies ni detengas el contenedor de demostracion.

## Base y pruebas backend

Desde `backend/`:

```powershell
docker compose --env-file .env.test -f docker-compose.test.yml config --quiet
docker compose --env-file .env.test -f docker-compose.test.yml up -d
```

Verifica que el contenedor sea `ivera-s16-test-postgres` y que la base sea `ivera_sprint16_1_test` antes de continuar. El perfil `test` verifica el nombre de la base antes de que Flyway migre.

```powershell
$env:TEST_DB_HOST = "127.0.0.1"
$env:TEST_DB_PORT = "5545"
$env:TEST_DB_NAME = "ivera_sprint16_1_test"
$env:TEST_DB_USER = "ivera_s16_test_app"
$env:TEST_DB_PASSWORD = "<valor de .env.test>"
$env:TEST_JWT_SECRET = "<valor de .env.test>"
.\mvnw.cmd test
```

## E2E aislado

En una terminal del backend, con las mismas variables `TEST_DB_*` y `TEST_JWT_SECRET`:

```powershell
$env:SPRING_PROFILES_ACTIVE = "e2e"
$env:E2E_BACKEND_PORT = "8081"
$env:E2E_RESET_SCENARIO = "true"
$env:E2E_ADMIN_PASSWORD = "<valor de .env.test>"
$env:E2E_DEMO_PASSWORD = "<valor de .env.test>"
.\mvnw.cmd spring-boot:run
```

En otra terminal, desde `ferreteria-frontend/`:

```powershell
npm run e2e
```

Playwright exige `E2E_ALLOW_WRITE=true` y rechaza cualquier URL distinta de `http://localhost:5174` y `http://localhost:8081`. No ejecutar `npm run e2e:capturas`: modifica imágenes y documentación.

## Detencion segura

```powershell
docker compose --env-file .env.test -f docker-compose.test.yml stop
```

No usar `docker compose down -v`. La eliminacion del volumen aislado requiere una autorizacion separada.
