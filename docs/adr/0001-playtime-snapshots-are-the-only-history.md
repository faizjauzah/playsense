# Playtime snapshots are the only source of history

Steam's Web API exposes current lifetime playtime per game, a rolling two-week figure, and a last-played timestamp. It exposes no playtime history at any granularity, and Steam's own "Year in Review" is not reachable through the public API. Every trend, streak and delta in PlaySense therefore comes from our own repeated captures: a `snapshot` row per capture plus one `snapshot_entry` per owned game, written at most once per player every six hours on access and once daily by a job.

Status: accepted

## Consequences

- A brand-new player has no trends. The dashboard says so explicitly and shows the 14-day figure instead; it must never render an empty axis.
- Deleting snapshot rows destroys insight that cannot be re-fetched from Steam. Backups and a non-expiring database are load-bearing, which is why data lives in Neon rather than Render's 30-day free Postgres.
- Full per-game rows were chosen over storing only deltas. A 500-game library produces roughly 180k rows a year, which is nothing for Postgres, and full rows let us recompute any window exactly. Deltas-only can never recover from a single lost or misordered write.
- Timestamps are UTC and windowing is relative to now. Per-player timezones were deliberately dropped: they add correctness burdens to every chart for a label nobody reads.
