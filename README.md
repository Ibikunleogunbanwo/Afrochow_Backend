# Afrochow Backend

Spring Boot 4 API that powers Afrochow, a marketplace for African restaurants, caterers, and grocers operating in Canada. It handles the vendor side (onboarding, menu and promotion management, Stripe payouts), the customer side (browsing, ordering, reviews), and the plumbing that keeps them in sync (auth, notifications, image storage, an outbox for reliable event delivery).

## What's actually in here

The code is organised by feature slice, not by layer, so everything that touches "orders" lives under `com.afrochow.order`, everything that touches "vendors" under `com.afrochow.vendor`, and so on. Each slice usually has its own `controller`, `service`, `repository`, `model`, and `dto` sub-packages.

The slices that do the heaviest lifting:

* **`auth`** and **`security`** hold the full credential flow. JWTs are encrypted (JWE), refresh tokens rotate on every use, login attempts are tracked per-account with a 15-minute lockout after five failures, and every auth event writes a row to `security_events` for audit.
* **`order`** and **`orderline`** model the order lifecycle. An order moves through placement, preparation, ready, out-for-delivery, delivered, and cancelled, with role-scoped visibility so customers see their own history, vendors see their incoming queue, and admins see everything.
* **`payment`** integrates Stripe for both charges and vendor payouts. Refunds go through the same service so status history stays on one row.
* **`outbox`** is a transactional outbox. Anything that needs to fire an external side-effect (emails, push notifications, webhook deliveries) writes to the outbox table inside the same DB transaction as the domain change, then a scheduled worker drains it. This is how we avoid the "email sent but order rollback" class of bug.
* **`notification`** is the fan-out. One `NotificationService.publish(...)` call writes the in-app row, hands the email off to Resend's HTTP API (we use HTTP rather than SMTP because Railway's egress blocks port 587), and leaves extension points for SMS and push.
* **`search`** powers the customer-facing discovery endpoints. Base path is `/api/search`, not `/api`, which trips people up if they're reading old docs.
* **`seeder`** spins up a realistic dev dataset: categories, a handful of vendors, menus with images, sample orders. It only runs when `SEED_ON_STARTUP=true`.

## Stack

Spring Boot 4.0 on Java 21. Spring Security 7, Spring Data JPA (Hibernate 7 under the hood), MySQL 8, Flyway for schema migrations, JJWT 0.13 for token signing and encryption, SpringDoc for the OpenAPI spec, Caffeine for in-process caching, Thymeleaf for email templates, Lombok to keep boilerplate tolerable, and Resend for transactional email. Image uploads go to Cloudinary; Stripe handles payments. Google OAuth is wired in for customer sign-in.

Production runs on Railway behind `api.afrochow.ca`.

## Prerequisites

* JDK 21 (Temurin is what we test against)
* Maven 3.9 or the bundled `./mvnw`
* MySQL 8.0+
* A Resend API key if you want emails to actually send (otherwise set `SPRING_MAIL_ENABLED=false`)
* Stripe test keys if you want to exercise the payment flow
* Cloudinary credentials if you want image uploads to persist somewhere durable

## Getting it running locally

Clone, create a database, set environment variables, boot it.

```bash
git clone git@github.com:Ibikunleogunbanwo/Afrochow-Backend.git
cd Afrochow-Backend

mysql -u root -p -e "CREATE DATABASE afrochow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

The app reads configuration from environment variables. The quickest way to not hate your life is to drop an `.env` file in the project root and use `direnv` or IntelliJ's EnvFile plugin to load it. A minimal working set:

```properties
SPRING_PROFILES_ACTIVE=dev

# DB
DB_HOST=localhost
DB_PORT=3306
DB_NAME=afrochow
DB_USERNAME=root
DB_PASSWORD=

# JWT (generate 32+ byte secrets, don't reuse across envs)
APP_JWT_SECRET=change-me-at-least-32-bytes-please
APP_JWT_ENCRYPTION_KEY=32-byte-base64-key-for-jwe
APP_JWT_EXPIRATION=900000
APP_JWT_REFRESH_EXPIRATION=604800000
APP_TOKEN_SECRET=used-for-password-reset-tokens

# Security
SECURITY_MAX_LOGIN_ATTEMPTS=5
SECURITY_LOCKOUT_DURATION=900000

# Email (leave disabled if you don't have Resend)
SPRING_MAIL_ENABLED=false
RESEND_API_KEY=
RESEND_FROM=no-reply@afrochow.ca

# Stripe (test mode)
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Cloudinary
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=

# Dev conveniences
CORS_ALLOWED_ORIGINS=http://localhost:3000
SEED_ON_STARTUP=true
```

Then:

```bash
./mvnw spring-boot:run
```

First boot runs every Flyway migration in `src/main/resources/db/migration`, and if `SEED_ON_STARTUP=true`, the seeder populates the database. Watch the logs: you'll see the seeder print a summary of what it created, including the test vendor and customer credentials.

The API listens on `http://localhost:8080/api`. Swagger UI is at `http://localhost:8080/swagger-ui.html` in dev only (it's disabled under `prod`).

## Configuration knobs worth knowing about

* `SPRING_PROFILES_ACTIVE` switches which `application-*.properties` file gets layered on top of the base. `dev` turns on `ddl-auto=create` (destructive, but faster to iterate), `prod` uses `validate` and expects Flyway to own the schema.
* `SEED_ON_STARTUP` should stay off in prod. It's idempotent enough not to nuke real data, but it's not zero-cost on boot.
* Upload size limits live on `APP_UPLOAD_MAX_FILE_SIZE` (default 10 MB). Cloudinary caps kick in above that regardless.
* CORS origins are a comma-separated list. Vercel preview URLs won't work unless you explicitly add the pattern.

## Database migrations

Flyway is the source of truth for schema. We don't let Hibernate auto-generate anything in prod. If you need a schema change:

1. Add a new `V<n>__description.sql` file under `src/main/resources/db/migration`. Don't reuse numbers, don't edit existing migrations after they've been applied.
2. Run the app locally; Flyway will apply the new script automatically.
3. If something goes sideways mid-migration, fix it forward with a new `V<n+1>__fix.sql`. Don't `flyway repair` against a real database without a backup.

Current migrations top out at `V26__Backfill_version_not_null_on_optimistic_locked_tables.sql`.

## Running the tests

```bash
./mvnw test
```

We don't have full integration coverage yet. Auth, order placement, and the outbox worker are the best-covered slices; the rest relies more on the seeder and a set of curl-based smoke checks in `test-api.sh`.

## API surface

The full list lives at `/swagger-ui.html` when the app is running, and a human-readable version is in `API_QUICK_REFERENCE.md`. A few shapes to be aware of:

* Every response is wrapped in `ApiResponse<T>` with `success`, `message`, `data`, and `errors` fields. Don't expect bare payloads.
* Public IDs (`VEN-<hash>`, `USR-<hash>`, etc.) are what the frontend uses. The numeric primary keys never leave the database.
* Search lives at `/api/search/*`, not `/api/*`. `/api/search/vendors/{publicVendorId}` is the vendor detail endpoint.
* Image upload endpoints accept `multipart/form-data` with the field name `file`. They return the Cloudinary URL, not a local path.

## Deployment

Production deploys to Railway from the `main` branch. Railway builds with Nixpacks; no Dockerfile is required at the moment (a containerised setup is on the roadmap alongside Redis for geo-search). Config is injected via Railway environment variables using the same names as the local `.env`.

The frontend (`afrochow.ca`) and admin console (`admin.afrochow.ca`) reach the API through `api.afrochow.ca`, which is a Cloudflare CNAME pointed at Railway's generated hostname.

## Things that commonly trip people up

* **Empty response from `/api/vendors/{id}`**: wrong path. It's `/api/search/vendors/{id}`. The controller has `@RequestMapping("/search")` at the class level.
* **"No static resource" 404**: Spring is falling through to its static handler because nothing matched. Usually a method-level path typo or a missing role in `@PreAuthorize`.
* **JWT "invalid signature" locally after rotating `APP_JWT_SECRET`**: tokens signed with the old secret are now gibberish. Log out, log back in.
* **Flyway refuses to start**: either a checksum mismatch (someone edited a migration that was already applied) or an out-of-order version. Don't `flyway repair` blindly; figure out which migration changed and whether you can safely reset locally.
* **Image uploads return 200 but the URL is null**: Cloudinary credentials missing or wrong. The service falls back to a no-op in dev rather than failing loudly.

## Repo layout

```
src/
├── main/
│   ├── java/com/afrochow/
│   │   ├── address/       vendor and customer addresses, geocoding
│   │   ├── admin/         admin user CRUD and profile
│   │   ├── auth/          registration, login, token rotation, OAuth
│   │   ├── category/      store and product categories
│   │   ├── common/        shared response wrappers, enums, exceptions
│   │   ├── config/        Spring beans wired by hand (CORS, Stripe, caches)
│   │   ├── customer/      customer profiles, favourites
│   │   ├── email/         Resend client + Thymeleaf template rendering
│   │   ├── favorite/      favourited products and vendors
│   │   ├── image/         Cloudinary upload service
│   │   ├── notification/  in-app + email fan-out
│   │   ├── order/         order lifecycle, status transitions
│   │   ├── orderline/     line items
│   │   ├── outbox/        transactional outbox + drain worker
│   │   ├── payment/       Stripe charges and refunds
│   │   ├── product/       menu and catalogue
│   │   ├── promotion/     vendor-run discount codes
│   │   ├── review/        product and vendor reviews
│   │   ├── search/        customer-facing discovery endpoints
│   │   ├── security/      JWT filter, login-attempt tracking, audit events
│   │   ├── seeder/        dev-only data seeding
│   │   ├── stats/         admin dashboard aggregates
│   │   ├── user/          shared user model
│   │   └── vendor/        vendor profiles and store settings
│   └── resources/
│       ├── db/migration/              Flyway SQL
│       ├── templates/email/           HTML email templates
│       ├── application.properties
│       ├── application-dev.properties
│       └── application-prod.properties
```

Supporting docs in the repo root:

* `API_QUICK_REFERENCE.md` is the cheat-sheet for common endpoints.
* `BACKEND_DOCUMENTATION.md` is the deeper architecture write-up.
* `NOTIFICATION_ARCHITECTURE.md` explains the outbox and notification interaction.
* `SEEDER_*.md` covers what the seeder creates and how to control it.

## Contact

Questions, bug reports, or PRs: Ibikunle Ogunbanwo, `ibikunleogunbanwo@gmail.com`.
