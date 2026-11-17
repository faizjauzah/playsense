# Ship one image: the Next.js export served by Spring Boot

PlaySense deploys to Render's free tier, which grants 750 instance-hours per workspace per month. Two always-on services (Next.js and Spring Boot) need about 1460 hours, so both cannot stay warm — and the point of a dashboard that leads with a number is that it loads immediately. We build the frontend with `output: 'export'` and copy it into the backend's static resources, producing a single Docker image where page and API share one origin.

Status: accepted

## Consequences

- No CORS configuration, no cross-site cookie semantics, no refresh-token dance. The session cookie is httpOnly, `SameSite=Lax`, and the browser only ever sees one host.
- No server-side rendering and no `next/image` optimisation. Both are worthless here: every page except the login landing is behind auth and fetches its data client-side from one composed endpoint. This decision reverses the moment public, indexable profiles appear.
- One deploy, one health check, one log stream, one cold start. Next starts in seconds; a JVM on 512 MB takes 30-60s, which is why the service is pre-warmed and the UI has a "warming up" state regardless.
- Local development is unaffected: `next dev` proxies `/api/*` to `localhost:8080`, so the frontend still hot-reloads against a real backend.
- The Dockerfile is three stages (node build, maven build, JRE runtime) and the JVM must be capped near `-Xmx350m` or the free instance is OOM-killed.
