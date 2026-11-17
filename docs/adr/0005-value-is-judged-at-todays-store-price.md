# Value is judged at today's store price

Steam exposes what a Player owns and how long they played it, but never what they paid: no receipts, no spend history, no purchase price. A dashboard that answers "was this game worth it" therefore has to choose a stand-in, and the only one Steam offers is the store's current price.

We show **Cost per hour**: today's sale-aware Store price divided by lifetime hours, in the Player's Steam store currency, and against the US dollar when their profile hides its country. The price is cached per Game and currency and refetched when it is more than 30 days old and the Game is being displayed. The panel names the currency and says "today's Steam price" rather than implying it is what was paid.

Status: accepted

## Considered Options

- **Price history recorded alongside playtime snapshots**, judged only where a price was observed before or during play. The only version that is literally true, but it cannot be backfilled: every existing Game would stay unjudged for months. Revisit if purchase-price truth ever matters more than coverage.
- **List price ignoring discounts.** Stable, but permanently overstates the value of a Game that is discounted year-round, and understates what sale-buyers actually paid.
- **Hours per dollar.** Rejected as the unit: a free Game is infinite, and no reader has intuition for `1.67`.

## Consequences

- Any sale moves the number, and a Player who bought at full price is judged at the discount they never got. This is the accepted cost of coverage.
- Steam regional prices are not currency conversions of one another, so no exchange rate is ever applied. A Game with no price in the Player's currency shows hours only, never a converted estimate.
- Free Games show $0/hour; delisted and region-blocked ones show no rate at all.
- Cost per hour is deliberately not part of the Insight Report's facts. Extending that schema would regenerate every cached report and hand the model a number whose flaw it cannot express.
