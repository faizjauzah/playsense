# Insight reports are cached by stats hash and never required

The Insight Report is generated once per distinct set of statistics and stored: `insight_report` is keyed by player and carries an `input_hash` over the facts sent to the model. A request that finds a matching hash serves the stored report; a mismatch regenerates it with an 8-second timeout. Gemini is called from the backend only, with a payload stripped of name, SteamID and avatar, and the UI discloses that game names and playtimes are sent.

Status: accepted

## Consequences

- Gemini cannot block the product. The same contract is filled by a deterministic template renderer whenever the key is missing, the call times out, the response fails schema validation, or the free-tier quota is exhausted. `source` is `gemini` or `template`, and the UI renders both identically apart from one attribution line.
- Free-tier Gemini may train on inputs and be read by human reviewers, so no player-identifying field is ever included. The trade accepted here is that switching to a paid key is a config change, not a code change.
- Statistics that change every refresh (a single minute of playtime) must not invalidate the cache, so the hash is over rounded, structured facts — coarse totals, top games, backlog counts — not the raw payload.
- Structured output with a fixed schema is the contract. A model reply that does not validate is discarded rather than repaired; the template takes over.
