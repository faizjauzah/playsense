# PlaySense

Your Steam playtime, and what it says about you. Enter your Steam name, get one number, one trend line, and a plain-language read on where the hours went.

## How it fits together

```
Browser ──► Next.js static export (served by Spring Boot) ──► Spring Boot API ──► Steam Web API
                    same origin, httpOnly cookie                 │                Store appdetails
                                                                └──► Gemini (optional, cached)
                                                                └──► Postgres (Neon)
```

Steam exposes **no playtime history**, so every trend is built from snapshots this service takes of your own library. See `docs/adr/0001`.

Decisions worth knowing before you change things:

- `docs/adr/0001-playtime-snapshots-are-the-only-history.md`
- `docs/adr/0002-one-image-next-static-export-inside-spring.md`
- `docs/adr/0003-insight-reports-are-cached-and-optional.md`
- `GLOSSARY.md` — the words the code uses (Player, Playtime Snapshot, Playtime Delta, Insight Report…)

## Run it locally

You need Docker. Two ways:

**Everything in containers**

```bash
cp .env.example .env        # add STEAM_API_KEY and GEMINI_API_KEY
docker compose up --build   # http://localhost:8080
```

**Frontend hot reload against the containerised API**

```bash
docker compose up -d db api
cd frontend && npm install && npm run dev   # http://localhost:3000
```

`next dev` proxies `/api/*` to `localhost:8080`. The session cookie ignores ports, so signing in on either origin works for both.

Get a Steam key at <https://steamcommunity.com/dev/apikey>. The app runs without a Gemini key — the Insight Report is written from a template instead.

## Deploy (free)

1. Create a free Postgres at [Neon](https://neon.tech) and copy the `postgresql://…` connection string. Render's own free Postgres deletes itself after 30 days, which would take your history with it.
2. In Render, create a Blueprint from this repo (`render.yaml` builds the single Docker image).
3. Set `DATABASE_URL`, `STEAM_API_KEY`, `GEMINI_API_KEY`. `COOKIE_SECURE=true` is already in the blueprint.
4. Set a GitHub repository variable `PLAYSENSE_URL` to your service URL; `.github/workflows/keep-warm.yml` pings it every 14 minutes so the free instance rarely sleeps.

Free tier maths: 750 instance-hours per workspace per month. One service kept warm by a 14-minute ping is ~720 hours, so there is deliberately only one service. If it does sleep, the UI says "waking the server" instead of pretending to be broken.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/session` | Resolve a vanity name or SteamID64, start a session |
| DELETE | `/api/session` | Forget the session |
| GET | `/api/me` | Current player |
| GET | `/api/dashboard` | Everything the page needs, one call |
| POST | `/api/refresh` | Re-poll Steam (60s floor) |

`GET /api/dashboard` returns `player`, `hero`, `split`, `trend`, `recent`, `backlog`, `insight`, `meta`. When `meta.libraryPrivate` is true the rest is null and the UI sends you to `/private`.

## Layout

```
frontend/     Next.js 16 App Router, static export, plain CSS
backend/      Spring Boot 3.3, JdbcClient (no JPA), Flyway
Dockerfile    node build → maven build → JRE
render.yaml   one free docker web service
```

Backend tests: `docker run --rm -v "$PWD/backend":/app -w /app maven:3.9-eclipse-temurin-21 mvn test`
