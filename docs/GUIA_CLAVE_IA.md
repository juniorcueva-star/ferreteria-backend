# Guía: conseguir la clave de Claude (Haiku 5.5) para el asistente de IA

El asistente de la ferretería usa **Claude Haiku 5.5**, el modelo más económico de Anthropic. Para usarlo hace
falta una **clave de API** (API key): una contraseña larga que el backend usa para hablar con Claude. Se paga
**por uso** (lo que se pregunta), no es una suscripción.

> Costo esperado: menos de **S/ 0.01 por pregunta**; con unas 100 preguntas al día, unos **S/ 10 al mes**.

---

## Paso 1. Crear la cuenta

1. Abre **https://platform.claude.com** (es la "Claude Console", la página para programadores; no es la misma
   que el chat claude.ai).
2. Presiona **Sign up** (registrarse) y entra con un correo del negocio o con Google.
3. Confirma el correo (llega un enlace) y completa los datos que pida (nombre, nombre de la organización: puedes
   poner **Bonifaz & Cia**).

---

## Paso 2. Poner saldo (pago por uso)

1. Entra a **https://platform.claude.com/settings/billing** (página **Billing**).
2. Agrega una **tarjeta de crédito o débito** (Visa o Mastercard que permita compras por internet en dólares).
3. Compra créditos: con **US$ 5 a 10** alcanza para empezar (varios meses con el uso esperado).
   El saldo se descuenta solo con cada pregunta.

> Si la tarjeta es rechazada, revisa con el banco que esté habilitada para **compras por internet en el
> extranjero**.

---

## Paso 3. Poner un tope de gasto (recomendado)

En la misma página **Billing**, sección **Spend limits**, presiona **Set limit** (o **Adjust limit**) y pon un
tope mensual, por ejemplo **US$ 10**. Si se llega al tope, la IA deja de responder hasta el mes siguiente y el
resto del sistema sigue funcionando normal.

Además, el sistema tiene su propio límite de preguntas por día (se configura en el `.env`).

---

## Paso 4. Crear la clave

1. Entra a **https://platform.claude.com/settings/keys** (página **API keys**).
2. Presiona **Create key**.
3. Ponle un nombre, por ejemplo `ferreteria-backend`.
4. En la vigencia (expiración) elige la que prefieras; si la clave vence, se crea otra y se cambia en el `.env`.
5. Presiona crear. Aparece la clave: empieza con **`sk-ant-`** y es larga.
6. **Cópiala en ese momento**: la página la muestra **una sola vez**. Si la pierdes, no se puede volver a ver:
   se borra y se crea otra.

> ⚠️ La clave es como la contraseña de una tarjeta: **no la mandes por WhatsApp, correo ni chat** (tampoco a
> Claude), no la pegues en el frontend ni la subas a GitHub. Solo va en el archivo `.env` del backend.

---

## Paso 5. Poner la clave en el sistema

1. Abre el archivo `.env` del backend:
   ```
   cd C:\ferreteria\ferreteria-backend\backend
   notepad .env
   ```
2. Busca la línea `ANTHROPIC_API_KEY=` (si no está, agrégala al final) y pega la clave después del `=`, sin
   espacios ni comillas:
   ```
   ANTHROPIC_API_KEY=sk-ant-...tu clave completa...
   ```
3. Guarda (Ctrl + S) y **reinicia el backend** (Ctrl + C en su ventana y otra vez `mvnw.cmd spring-boot:run`).

Si la línea queda vacía, el sistema funciona igual, pero el botón **Asistente** avisa que la IA no está
configurada.

---

## Si algo falla

| Mensaje del asistente | Qué hacer |
|---|---|
| "El asistente no está configurado" | Falta la clave en `.env` o no reiniciaste el backend (paso 5) |
| "La clave de la IA no es válida" | La clave está mal copiada, vencida o borrada: crea otra (paso 4) |
| "Se acabó el saldo o se llegó al tope de gasto" | Recarga créditos o sube el tope en **Billing** (pasos 2 y 3) |
| "Se llegó al límite de preguntas de hoy" | Es el límite diario del sistema; mañana se reinicia, o súbelo en el `.env` |
