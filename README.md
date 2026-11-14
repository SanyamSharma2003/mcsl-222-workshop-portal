# MCSL-222 Q1(ii): Web Technologies Workshop Portal

A Spring Boot MVC application for workshop registration and fee-payment workflow. The project uses Java 17, Spring Boot 3.5.16, Spring Security, Thymeleaf, Hibernate/JPA and Flyway. The default demo uses an embedded H2 file database; no separate database installation or Docker Desktop is needed.

**Public project link for the assignment:** [SanyamSharma2003/mcsl-222-workshop-portal](https://github.com/SanyamSharma2003/mcsl-222-workshop-portal)

Refer to the public repository link above for the full working code. Evaluators can inspect the source, schema and tests or download them without signing in. The [v1.0.0 submission release](https://github.com/SanyamSharma2003/mcsl-222-workshop-portal/releases/tag/v1.0.0) supplies the executable JAR and versioned source downloads. See [submission notes](docs/SUBMISSION.txt) for a concise review guide.

**Run in your browser:** [Create your own GitHub Codespace](https://codespaces.new/SanyamSharma2003/mcsl-222-workshop-portal). This requires your GitHub account and available Codespaces quota. It installs Java and Maven in the remote environment; no local Docker Desktop is needed. The demo uses public sample accounts and simulates payments; it collects no banking/card details.

## Assumptions

- There is one workshop, “Web Technologies”, with a fixed fee of **INR 2,500.00** per registration. The server sets the amount and currency. This version does not calculate VAT/tax or enforce a capacity limit or registration deadline.
- An authenticated account may register multiple attendees; attendee email and mobile do not have to be unique. Accounts are provisioned separately; public account sign-up is outside scope.
- A participant can create, read, update, logically delete and pay only their own registrations. An administrator can manage registrations across owners, subject to the same payment locks. Finance can read registrations and payment attempts and resolve demo payments. An administrator can read payment attempts but cannot settle them or initiate participant payments.
- Only `UNPAID` registrations can be updated or logically deleted. `PENDING` and `PAID` registrations are locked, including for administrators. A failed attempt returns its registration to `UNPAID`, permitting correction or a new attempt; the previous attempt remains in history.
- The lab payment controls simulate a provider result and move no money. A genuine provider order/checkout adapter and institution account provisioning are deployment tasks. No banking/card details are collected by the demo.
- The default `demo` profile uses H2 storage in `./data/workshop`. The `prod` profile is a PostgreSQL configuration template and has not been live verified against PostgreSQL.

## Prebuilt executable

Download [`workshop-registration-portal-1.0.0.jar` from release v1.0.0](https://github.com/SanyamSharma2003/mcsl-222-workshop-portal/releases/download/v1.0.0/workshop-registration-portal-1.0.0.jar). It runs with Java 17 or later:

```sh
java -jar workshop-registration-portal-1.0.0.jar
```

The JAR includes the embedded web server and H2 database. Open `http://localhost:8080/registrations`. Its SHA256 is recorded in [`checks/VERIFICATION.txt`](checks/VERIFICATION.txt). The binary is a release attachment rather than a Git-tracked source file.

## Run locally

Prerequisites: **JDK 17** and **Maven 3.6.3 or later**. Open a terminal in this project directory:

```sh
mvn -B verify
java -jar target/workshop-registration-portal-1.0.0.jar
```

Open `http://localhost:8080/registrations`. The server binds to loopback by default. The first command compiles the application, runs the integration tests and packages its executable JAR. Stop the server with Ctrl+C. Demo data persists under `data/`; do not delete this directory if you want to retain registrations.

### Demo accounts

All four accounts use the deliberately public lab password **`LabDemo!2026`**:

| Username | Role | Purpose |
| --- | --- | --- |
| `participant1` | PARTICIPANT | Own registration CRUD and payment order creation |
| `participant2` | PARTICIPANT | Demonstrate cross-owner access restrictions |
| `admin` | ADMIN | Manage unpaid registrations across owners; read payments |
| `finance` | FINANCE | Read registrations/payments; simulate terminal payment outcomes |

These accounts are seeded only for `demo` and `test`, never for `prod`.

### Browser-only Codespaces

1. Open [the Codespaces launch link](https://codespaces.new/SanyamSharma2003/mcsl-222-workshop-portal), sign in with your own GitHub account and create a Codespace for this repository.
2. Wait for the initial `mvn -B verify` build to finish. The repository's `.devcontainer/devcontainer.json` configures Java 17 and Maven; `codespaces/start.sh` starts the application when the Codespace starts.
3. Open the **Ports** panel, then open forwarded port **8080** in your browser. Keep visibility **Private** and the forwarded-port protocol **HTTP**. GitHub provides the outer HTTPS browser connection.
4. Sign in with one of the public demo accounts above. There is no account sign-up step.

If the application is stopped, run `bash codespaces/start.sh` in the Codespace terminal. The script builds the JAR if necessary and starts it in the background. To rebuild after changing Java code, stop the process identified in `workshop.pid`, run `mvn -B verify`, then run the start script again.

The Codespaces launcher sets secure session cookies and passes `--server.forward-headers-strategy=framework` so login redirects use the external HTTPS address supplied by GitHub's port proxy. This assumes the application is reached through the trusted, private Codespaces proxy; keep port visibility **Private**. Use this launcher only for that environment. For direct local execution, use the plain `java -jar` command above. The command-line setting does not change the packaged application defaults or the production configuration template.

Log output is in `workshop.log`. Stop a Codespace when finished to avoid unnecessary quota usage. This project does not include an owner-specific, permanent public live-demo URL.

## Demonstrate the workflow

1. Sign in as `participant1`, create a registration and inspect its `UNPAID` state. Edit it, or create another unpaid record and delete that second record.
2. On the retained registration, choose **Create payment order**. Its status becomes `PENDING`; edit and delete actions are locked.
3. Sign out and sign in as `finance`. Open **Payment desk**, enter a unique reference of 8–100 permitted characters, and simulate `SETTLED` or `FAILED`. Allowed reference characters are letters, digits, `.`, `_`, `:`, and `-`.
4. A settled attempt makes the registration `PAID` and permanently locks these CRUD actions. A failed attempt returns it to `UNPAID`; the participant can correct it or retry with a new payment attempt.
5. Sign in as `participant2` to confirm that another participant's records are absent from the list and cannot be accessed by guessing a registration ID. Administrators also cannot change or delete pending/paid records.

## Layering and data model

```text
Browser / Thymeleaf forms
  -> Spring Security filter chain
  -> MVC controllers + validated RegistrationForm DTO
  -> transactional RegistrationService / PaymentService
  -> Spring Data JPA repositories -> Hibernate -> relational database
```

Schema: [`src/main/resources/db/migration/V1__workshop_schema.sql`](src/main/resources/db/migration/V1__workshop_schema.sql). Flyway creates the four tables; Hibernate validates the schema rather than creating it.

| Table | Important data and relationships |
| --- | --- |
| `app_users` | UUID primary key, unique username, BCrypt hash, constrained role |
| `workshop_registrations` | UUID PK; owner FK to users; attendee details; fixed amount/currency; status; `@Version`; creation/update/deletion timestamps |
| `payment_attempts` | UUID PK; registration FK; unique order reference; server-copied amount/currency; terminal status, unique receipt and resolution timestamp |
| `audit_events` | UUID PK; registration FK; actor, action and timestamp; retained on logical deletion |

`app_users (1) -> (many) workshop_registrations (1) -> (many) payment_attempts`; registrations also have many audit events. Database CHECK constraints enforce the fee, role/status values, terminal payment fields and unpaid-only logical deletion.

## MVC and callback routes

State-changing browser routes use POST and CSRF tokens. They are MVC actions, rather than a claim of a separate REST CRUD API.

| Method and route | Function | Role/scope |
| --- | --- | --- |
| `GET /login`, `POST /login` | Spring Security form login | Visitor |
| `GET /registrations`, `GET /registrations/{id}` | Read | Participant: own; admin/finance: all active |
| `GET /registrations/new`, `POST /registrations` | Create | Participant/admin; owner is signed-in account |
| `GET /registrations/{id}/edit`, `POST /registrations/{id}/edit` | Update | Own participant/admin; `UNPAID` and matching version |
| `POST /registrations/{id}/delete` | Logical delete | Own participant/admin; `UNPAID` and matching version |
| `POST /registrations/{id}/pay` | Create payment attempt | Owning participant; `UNPAID` and matching version |
| `GET /finance/payments` | Read payment attempts | Finance/admin |
| `POST /finance/payments/{id}/resolve` | Simulate payment result | Finance; demo profile only |
| `POST /gateway/callback` | JSON provider result | Exact-body HMAC authentication |
| `POST /logout` | End session | Authenticated browser; CSRF protected |

## Security and settlement rules

- BCrypt cost 12 password hashes; authenticated sessions; session ID migration after login; HttpOnly and SameSite=Lax cookies; CSRF on browser mutations; request and service method authorization; deny unmatched routes.
- Validated DTO binding exposes only attendee name, email, Indian mobile (`+91` and a valid 10-digit number), organisation and record version. The owner, amount, currency and payment status are never trusted from browser input. Thymeleaf escapes displayed data.
- Registration mutation takes a database `PESSIMISTIC_WRITE` lock, checks ownership, payment state and submitted version, and uses JPA `@Version`. Payment resolution takes the same parent lock. Audit, registration and payment changes are committed together.
- The gateway callback alone is exempted from browser CSRF and session authentication. It requires `X-Payment-Signature`: **64 lowercase hexadecimal characters containing HMAC-SHA256 over the exact raw JSON bytes**, using the shared signing secret. The body is limited to 8,192 bytes. Unknown JSON fields, stale events older than five minutes, and events over 30 seconds in the future are rejected.
- A callback must match the stored attempt UUID, order reference, amount and currency. Terminal outcomes are `SETTLED` or `FAILED`; a unique valid receipt is mandatory for both. Repeating the same final outcome and receipt is idempotent; a conflicting final result returns 409. A browser “payment successful” redirect cannot settle a registration.
- CSP, denied framing and secure production cookies reduce browser exposure. These controls do not substitute for a production security assessment, a genuine provider adapter, institution identity management, rate limiting, backups or operational monitoring.

Callback body shape:

```json
{
  "attemptId": "<stored payment-attempt UUID>",
  "orderReference": "<stored order reference>",
  "amount": 2500.00,
  "currency": "INR",
  "outcome": "SETTLED",
  "receipt": "PROVIDER-RECEIPT-001",
  "issuedAt": "<current UTC timestamp>"
}
```

Sign the actual bytes sent, including their whitespace; do not sign a reserialized variant. The timestamp placeholder must be replaced with a fresh ISO-8601 UTC instant.

## Production configuration template

Use `--spring.profiles.active=prod` only after configuring:

```text
DATABASE_URL=jdbc:postgresql://<database-host>:5432/<database-name>
DATABASE_USERNAME=<least-privilege application user>
DATABASE_PASSWORD=<application password>
MIGRATION_USERNAME=<separate schema-migration user>
MIGRATION_PASSWORD=<migration password>
GATEWAY_SIGNING_SECRET=<at least 32 bytes of strong secret material>
```

Then run:

```sh
java -jar target/workshop-registration-portal-1.0.0.jar --spring.profiles.active=prod
```

The production profile requires HTTPS and secure cookies. Place the private application behind a trusted TLS reverse proxy, configure trusted forwarding at the deployment boundary and prevent direct public access that could spoof forwarded headers. Configure the gateway's own authenticated order/checkout API adapter, actual signed callback protocol, database least privileges, institution accounts and operational controls before real use. There are no seeded production users. The PostgreSQL template and external gateway have not been live verified.

## Verification

**Verified in GitHub Codespaces on 14 November 2026:** `mvn -B verify` completed with BUILD SUCCESS. All 18 tests passed with zero failures and errors: 17 integration tests and one concurrent edit/payment test. The packaged JAR also started and served its login page over HTTP with a CSRF field. Maven used OpenJDK 25 and compiled the application for Java 17.

The suite covers unpaid CRUD, ownership isolation, administrator access, pending/paid locks, failed retries, finance restrictions, stale versions, input validation, CSRF, forbidden mass assignment, real template rendering and signed callback validation/idempotency. See [`PortalIntegrationTest.java`](src/test/java/edu/university/workshop/PortalIntegrationTest.java), [`PaymentRaceTest.java`](src/test/java/edu/university/workshop/PaymentRaceTest.java), and [`checks/VERIFICATION.txt`](checks/VERIFICATION.txt). The [Maven verification workflow](.github/workflows/maven-verify.yml) repeats `mvn -B verify` on Java 17 for pushes and pull requests; its current result is visible in the repository's Actions tab. No live PostgreSQL or real-money payment test is claimed.

## Official references

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring Security 6.5 CSRF protection](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)
- [Spring Security 6.5 method authorization](https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/method-security.html)
- [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)
