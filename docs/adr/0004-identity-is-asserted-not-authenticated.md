# Identity is asserted, not authenticated

Entering the dashboard takes a Steam vanity name or SteamID64, which we resolve with `ResolveVanityURL`. We never see a password and there is no Steam sign-in step. Steam OpenID was the obvious alternative and was built first; it was removed because it turns a "glance at my stats" tool into a password prompt on Valve's domain, and it buys ownership proof this product has no use for.

Status: accepted

## Consequences

- Anyone who knows a Steam ID can open that player's dashboard. Everything shown is already public on that player's Steam profile; the only new artefact is the derived history, which is computed from public data. Nothing is written back to Steam.
- The `POST /api/session` endpoint spends Steam API quota without authentication, so it is rate limited per IP. The session cookie is a convenience for returning visitors, not a security boundary.
- A player with private game details cannot use the product at all — Steam returns nothing and no amount of authentication changes that. The gate in `/private` exists for this reason and stays.
- If dashboards ever need to be private, or if any write operation is added, this decision must be reversed to Steam OpenID. The `player` table is keyed by SteamID64 either way, so the data model survives the reversal.
