# Agenda — backend

Monolito modular en Spring Boot 3.3 + PostgreSQL. Pensado para varias empresas
desde el primer día, aunque hoy solo haya un cliente.

## Cómo levantarlo

```bash
docker compose up -d          # PostgreSQL
cp .env.ejemplo .env          # y edite los valores
mvn spring-boot:run
```

Flyway crea el esquema solo, en el primer arranque.

## Correr las pruebas del motor de disponibilidad

```bash
mvn test
```

Son las pruebas que importan. Si pasan, el sistema no cruza citas.

## Crear el primer superadministrador

No hay registro público a propósito. El primer usuario se inserta a mano una
sola vez. La clave va cifrada con BCrypt: genérela con

```bash
mvn -q exec:java -Dexec.mainClass=... # o desde cualquier generador BCrypt
```

y luego:

```sql
INSERT INTO usuario (empresa_id, email, clave_hash, nombre, rol)
VALUES (NULL, 'oscar@sudominio.com', '$2a$10$...', 'Oscar', 'SUPERADMIN');
```

## Los módulos

| Carpeta | Qué hace |
|---|---|
| `common` | Contexto de empresa, errores, manejo de excepciones |
| `security` | JWT, roles, filtro, login |
| `empresa` | Alta de clientes y planes. Es tu panel de superadministrador |
| `usuario` | Usuarios y los tres roles |
| `catalogo` | Profesionales, servicios y la duración de cada servicio por profesional |
| `agenda` | Horarios, excepciones, citas y clientes |
| `disponibilidad` | El motor que calcula los cupos reales |
| `notificaciones` | Correo por SMTP |

## Los tres roles

- **SUPERADMIN** — tú. Crea empresas y define su plan. No pertenece a ninguna empresa.
- **ADMIN** — el dueño del negocio. Ve y maneja todo lo suyo.
- **TRABAJADORA** — solo ve sus propias citas y las marca como finalizadas.

## Endpoints principales

```
POST   /api/auth/login
GET    /api/superadmin/empresas
POST   /api/superadmin/empresas
PATCH  /api/superadmin/empresas/{id}/plan
GET    /api/disponibilidad?servicioId=&fecha=&profesionalId=
GET    /api/profesionales           POST /api/profesionales
GET    /api/servicios               POST /api/servicios
POST   /api/servicios/{id}/profesionales     (duración por profesional)
GET    /api/profesionales/{id}/horarios
PUT    /api/profesionales/{id}/horarios      (reemplaza la semana)
POST   /api/profesionales/{id}/excepciones   (permisos y ausencias)
GET    /api/reasignacion?profesionalId=&fecha=
POST   /api/reasignacion/{citaId}
GET    /api/lista-espera            POST /api/lista-espera
POST   /api/webhook/whatsapp        (lo llama Meta)
GET    /api/citas?fecha=&profesionalId=
POST   /api/citas
PATCH  /api/citas/{id}/reprogramar
PATCH  /api/citas/{id}/estado?valor=FINALIZADA
```

## Dos decisiones que conviene entender

**Duración por servicio y por profesional.** No hay una duración única por
servicio. La tabla `servicio_profesional` guarda cuánto se demora cada persona
en cada cosa. Es la causa más común de citas cruzadas cuando se ignora.

**La base de datos también protege.** Además de validar en el código, la tabla
`cita` tiene una restricción de exclusión que impide físicamente dos citas vivas
que se pisen para la misma profesional. Aunque el código falle, la base no deja.

## El bot de WhatsApp

El flujo va con listas y botones, nunca con frases abiertas. Desde octubre
de 2026 Meta cobra también los mensajes de servicio, así que cada respuesta
del bot cuesta: mientras menos vueltas dé la conversación, mejor. Y una lista
se equivoca mucho menos que intentar adivinar lo que escribió la clienta.

Pasos: servicio → día → hora → nombre → confirmación. Si no hay cupos en dos
semanas, la clienta entra sola a la lista de espera. Si escribe «asesor» o
«hablar con alguien», el bot se hace a un lado.

### Antes de conectarlo

1. Verificar el negocio con Meta. **Empiece por aquí, toma días o semanas.**
2. Investigar *Coexistence* antes de tocar el número: el negocio no puede
   perder el WhatsApp que ya usa todos los días.
3. Configurar el webhook apuntando a `POST /api/webhook/whatsapp` con el
   mismo `WA_VERIFY_TOKEN` del `.env`.
4. Guardar en la tabla `empresa` el `wa_phone_number_id` y el `wa_token`.
5. Crear y hacer aprobar tres plantillas: `recordatorio_cita_24h`,
   `recordatorio_cita_2h` y `cupo_disponible`.

## Lo que falta

- Frontend en Angular
- Reportes de ocupación y no-shows
- Que la clienta cancele o reprograme desde el mismo bot

## Advertencia

Este código no se compiló al generarlo. Corra `mvn clean test` antes de tocar
nada más y corrija lo que salte; es normal que aparezcan detalles de imports o
de configuración en el primer arranque.
