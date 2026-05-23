# Deployment guide

Last updated: Slice 11. Targets a single-container backend + nginx-served
frontend + managed Postgres.

## TL;DR

1. Copy `.env.prod.example` to `.env`, fill real values (see [env variables](#environment-variables)).
2. Pick a host: [Fly.io](#flyio), [generic VPS / DigitalOcean App Platform](#generic-vps), or local prod-like Docker Compose.
3. Boot the stack, then verify the smoke checks at the end.

The app runs as a non-root user (uid 10001), respects cgroup memory limits via
`-XX:MaxRAMPercentage=75`, and emits one JSON log per line on stdout when the
`prod` profile is active.

---

## Environment variables

Every variable in `.env.prod.example` is required for `SPRING_PROFILES_ACTIVE=prod`
unless noted otherwise.

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Enables JSON logs, tighter Hikari pool, SMTP mail, hidden error internals. |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Postgres connection. Use a managed instance for prod (Fly Postgres, Supabase, RDS). |
| `JWT_SECRET` | At least 32 high-entropy chars. `openssl rand -base64 48` works. |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated list of SPA origins. The frontend ships under one origin in the bundled compose - this only matters for split deployments. |
| `APP_MAIL_BASE_URL` | Public origin of the SPA. Mail links are built from this prefix. |
| `APP_MAIL_PROVIDER=smtp` | Switch from file to real SMTP delivery. |
| `APP_MAIL_FROM_ADDRESS` / `APP_MAIL_FROM_NAME` | The "From" header on outbound mail. |
| `SPRING_MAIL_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | Your SMTP provider's credentials. See examples in the env template. |
| `APP_AUTH_DEV_DELIVERY_ENABLED=false` | Defense in depth - the prod profile already disables the file delivery sink. |

---

## Local prod-like test with Docker Compose

The fastest sanity check before shipping anywhere is to run the full stack
locally with the production profile.

```bash
cp .env.prod.example .env
# fill in JWT_SECRET (mandatory), APP_MAIL_BASE_URL, and SMTP credentials.
# For mail testing, point SPRING_MAIL_HOST at a Mailtrap sandbox so you do
# not accidentally email real addresses.

docker compose -f docker-compose.full.yml up --build
open http://localhost:4200
```

Watch the backend logs - one JSON object per line confirms the prod profile is
active. Register a new user; the verification mail should land in Mailtrap.

---

## Fly.io

Fly.io fits this stack well: free Postgres, automatic TLS, single-binary
deploy, regional placement.

### 1. Provision

```bash
fly launch --no-deploy --name lifeorg --region gru     # pick your region
fly postgres create --name lifeorg-db --region gru
fly postgres attach --app lifeorg lifeorg-db
```

The attach step writes `DATABASE_URL` into the app's secrets. Convert it
into the format Spring expects:

```bash
fly secrets set \
  SPRING_PROFILES_ACTIVE=prod \
  DB_URL="jdbc:postgresql://lifeorg-db.internal:5432/lifeorg?sslmode=require" \
  DB_USERNAME="$(fly secrets list | grep DATABASE_URL | ...)"  # see fly docs
  DB_PASSWORD="..." \
  JWT_SECRET="$(openssl rand -base64 48)" \
  APP_MAIL_PROVIDER=smtp \
  APP_MAIL_FROM_ADDRESS="no-reply@yourdomain.example" \
  APP_MAIL_FROM_NAME="Life Organizer" \
  APP_MAIL_BASE_URL="https://lifeorg.fly.dev" \
  SPRING_MAIL_HOST=smtp.sendgrid.net \
  SPRING_MAIL_PORT=587 \
  SPRING_MAIL_USERNAME=apikey \
  SPRING_MAIL_PASSWORD="SG.xxxx"
```

### 2. Sample `fly.toml`

```toml
app = "lifeorg"
primary_region = "gru"

[build]
  dockerfile = "Dockerfile"

[http_service]
  internal_port = 8080
  force_https = true
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 1
  [http_service.checks]
    [[http_service.checks.http]]
      path = "/actuator/health"
      interval = "30s"
      timeout = "5s"

[[vm]]
  cpu_kind = "shared"
  cpus = 1
  memory_mb = 512
```

### 3. Deploy

```bash
fly deploy
fly logs               # confirm "Started LifeOrganizerApplication" + the prod profile
fly open               # opens the SPA in your browser
```

---

## Generic VPS

Any host that can run Docker works (DigitalOcean Droplet, Hetzner Cloud,
Linode, Raspberry Pi at home).

```bash
# On the host:
git clone https://github.com/juliolouzz/life-organizer.git
cd life-organizer
cp .env.prod.example .env
# fill values, especially JWT_SECRET, APP_MAIL_BASE_URL, SMTP credentials

docker compose -f docker-compose.full.yml up -d --build
```

Put a reverse proxy in front (Caddy / nginx) that terminates TLS and forwards
to port `4200`. Caddyfile:

```
your-app-domain.example {
  reverse_proxy localhost:4200
}
```

---

## Smoke checks after every deploy

1. `curl https://your-app-domain.example/actuator/health` returns `{"status":"UP"}`.
2. Register a new user; the verification email arrives at the test inbox.
3. Sign in; the dashboard loads and `/me` returns the user with `emailVerified: false` (or `true` after the link is clicked).
4. Trigger a password reset and confirm the link in the email works end-to-end.
5. Backend logs are JSON, one event per line - tail them through your host's logging panel.

---

## Mail-provider quick start

Don't have an SMTP server yet? Two zero-cost paths:

### Mailtrap (development sandbox)
Sign up at mailtrap.io, create a sandbox inbox, copy the SMTP credentials.
Every mail the app sends is captured there with a preview - perfect for
testing without real delivery.

### Gmail SMTP (low volume)
1. Enable 2FA on the Google account.
2. Generate an "App password" at <https://myaccount.google.com/apppasswords>.
3. Use `smtp.gmail.com:587`, your Gmail address as username, the 16-char app
   password as the SPRING_MAIL_PASSWORD.

For production volume (>500 mails/day), graduate to a transactional provider:
SendGrid, Mailgun, Postmark, or Amazon SES.
