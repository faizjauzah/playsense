# PlaySense

A read-only analytics dashboard for one Steam gamer: their own playtime, over time, with a generated interpretation.

## Language

### Identity

**Player**:
A Steam account whose dashboard is being viewed, identified by its SteamID64. The identity is asserted by whoever typed it and never proven: PlaySense holds no credentials.
_Avoid_: User, account, gamer

**Library**:
The set of Games a Player owns, as Steam reports it at one moment. A Library is an observation, not a stored collection — it is replaced by the next observation.
_Avoid_: Collection, catalogue, backlog

**Private Library**:
The state in which Steam reports no Library for a Player. It covers a profile that is not public, game details that are not public, and an account that owns no Games; PlaySense treats all three as one state because Steam gives it nothing to chart.
_Avoid_: Empty library, hidden profile

### Playtime

**Game**:
A Steam application, identified by its appid, plus any store metadata we have cached for it.
_Avoid_: Title, app, product

**Genre**:
A Game's kind, taken from Steam's own genre labels. A Game has at most one Genre in PlaySense, so genre hours never double count; store states (Free To Play, Early Access), software categories and content warnings are not Genres.
_Avoid_: Tag, category, label, classification

**Playtime Snapshot**:
One timestamped capture of a Player's per-Game lifetime minutes. The only source of history in the system, because Steam exposes no historical playtime.
_Avoid_: Reading, sample, sync

**Playtime Delta**:
Minutes added for one Game between two consecutive Playtime Snapshots. Every trend in the product is a sum of Playtime Deltas.
_Avoid_: Difference, growth, gain

**Lifetime minutes**:
A Game's total playtime as Steam reports it, tracked by Steam only since 2009. Never derived, always copied from a Playtime Snapshot.
_Avoid_: Total hours, all-time

**Hero stat**:
The single number the dashboard leads with: hours played in the last 30 days. Chosen because it changes, not because it is large.
_Avoid_: Headline, KPI, total

**Backlog**:
Owned Games with zero lifetime minutes (never launched), or with playtime but no activity for 90 days (dropped).
_Avoid_: Unplayed, queue, wishlist

### Value

**Store price**:
A Game's price on Steam's store at the moment PlaySense observed it, in the Player's Steam store currency. It is today's asking price, never what the Player paid.
_Avoid_: Price paid, spend, purchase price

**Cost per hour**:
A Game's Store price divided by its lifetime hours. It exists only for Games with playtime; an unplayed Game has a price tag, not a rate.
_Avoid_: Hours per dollar, value, ROI, worth

### Insight

**Insight Report**:
Text generated from a Player's statistics and validated against a fixed schema. Cached against a hash of the stats it describes, so identical stats never regenerate.
_Avoid_: Summary, analysis, AI output

**Template fallback**:
The same Insight Report contract, filled deterministically without Gemini. Rendered identically in the UI; only the attribution line differs.
_Avoid_: Mock, dummy, placeholder

**Coverage**:
The share of a Player's Lifetime minutes that a given chart actually accounts for. Displayed whenever a chart covers a subset, such as the top 25 Games.
_Avoid_: Completeness, sample size
