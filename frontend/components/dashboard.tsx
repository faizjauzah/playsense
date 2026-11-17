"use client";

import { useEffect, useState } from "react";
import { hours, relativeTime, shortDate } from "@/lib/api";
import type { Dashboard } from "@/lib/types";

type Hero = NonNullable<Dashboard["hero"]>;
type Split = NonNullable<Dashboard["split"]>;
type Trend = NonNullable<Dashboard["trend"]>;
type Backlog = NonNullable<Dashboard["backlog"]>;
type Insight = NonNullable<Dashboard["insight"]>;

const GENRE_COLORS = [
  "oklch(0.85 0.12 190)",
  "oklch(0.78 0.13 220)",
  "oklch(0.72 0.13 165)",
  "oklch(0.66 0.12 250)",
  "oklch(0.6 0.11 285)",
  "oklch(0.54 0.09 130)",
];

export function TopBar({
  player,
  lastRefreshAt,
  refreshing,
  onRefresh,
  onSignOut,
}: {
  player: Dashboard["player"];
  lastRefreshAt: string | null;
  refreshing: boolean;
  onRefresh: () => void;
  onSignOut: () => void;
}) {
  return (
    <header className="bar">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className="avatar" src={player.avatar} alt="" width={36} height={36} />
      <div className="who">
        <strong>{player.name}</strong>
        <span>synced {relativeTime(lastRefreshAt)}</span>
      </div>
      <div className="bar-actions">
        <button className="btn" onClick={onRefresh} disabled={refreshing}>
          {refreshing ? "Syncing…" : "Refresh"}
        </button>
        <button className="btn" onClick={onSignOut}>
          Sign out
        </button>
      </div>
    </header>
  );
}

export function Hero({ hero }: { hero: Hero }) {
  const animated = useCountUp(hero.minutesLast30d / 60);

  return (
    <section className="hero">
      <p className="eyebrow">Playtime · {hero.windowLabel}</p>
      <p className="hero-number num">
        {animated >= 100 ? Math.round(animated) : animated.toFixed(1)}
        <small>hours</small>
      </p>
      <p className="hero-sub">
        {hero.minutesLast30d === 0
          ? "Nothing logged in this window yet."
          : `${hero.minutesLast30d.toLocaleString()} minutes across your library.`}
      </p>
      <div className="stats">
        <div className="stat">
          <span className="stat-value num">{hero.hoursLifetime.toLocaleString()}</span>
          <span className="stat-label">hours lifetime</span>
        </div>
        <div className="stat">
          <span className="stat-value num">{hero.topGameSharePct}%</span>
          <span className="stat-label">of hours in one game</span>
        </div>
        <div className="stat">
          <span className="stat-value num">{hero.backlogCount}</span>
          <span className="stat-label">games untouched</span>
        </div>
      </div>
    </section>
  );
}

export function InsightCard({ insight }: { insight: Insight }) {
  return (
    <section className="panel insight">
      <div className="panel-head">
        <h2 className="panel-title">Insight</h2>
        <span className="panel-note">{insight.source === "gemini" ? "Gemini" : "From your stats"}</span>
      </div>
      <p className="insight-headline">{insight.headline}</p>
      <ul className="observations">
        {insight.observations.map((observation) => (
          <li key={observation.card + observation.text}>
            <span className="tag">{observation.card}</span>
            <span>{observation.text}</span>
          </li>
        ))}
      </ul>
      <p className="recommendation">{insight.recommendation}</p>
      {insight.source === "gemini" && (
        <p className="attribution">
          Generated from game names and playtimes sent to Google. Reproduced from a cached report.
        </p>
      )}
    </section>
  );
}

export function TrendCard({ trend }: { trend: Trend }) {
  const total = trend.buckets.reduce((sum, bucket) => sum + bucket.minutes, 0);
  const startedToday = trend.trackingDays === 0;

  return (
    <section className="panel">
      <div className="panel-head">
        <h2 className="panel-title">Last 30 days</h2>
        <span className="panel-note">
          {startedToday
            ? "tracking started today"
            : total === 0
              ? "no playtime in this window"
              : `${trend.trackingDays} day${trend.trackingDays === 1 ? "" : "s"} tracked`}
        </span>
      </div>

      {startedToday ? (
        <p className="empty">
          Tracking started today — your trend line begins on your next visit. Steam keeps no playtime history,
          so this chart is built from snapshots we take for you.
        </p>
      ) : (
        <>
          <TrendChart trend={trend} />
          <div className="chart-axis">
            <span className="num">{shortDate(trend.buckets[0].date)}</span>
            <span className="num">{shortDate(trend.buckets[trend.buckets.length - 1].date)}</span>
          </div>
        </>
      )}
    </section>
  );
}

function TrendChart({ trend }: { trend: Trend }) {
  const buckets = trend.buckets;
  const max = Math.max(1, ...buckets.map((bucket) => bucket.minutes));
  const width = 720;
  const height = 170;
  const pad = 6;

  const x = (index: number) => pad + (index * (width - pad * 2)) / Math.max(1, buckets.length - 1);
  const y = (minutes: number) => height - pad - (minutes / max) * (height - pad * 2);

  const line = buckets
    .map((bucket, index) => `${index === 0 ? "M" : "L"}${x(index).toFixed(1)},${y(bucket.minutes).toFixed(1)}`)
    .join(" ");
  const area = `${line} L${x(buckets.length - 1).toFixed(1)},${height} L${x(0).toFixed(1)},${height} Z`;

  return (
    <svg className="chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Daily playtime over 30 days">
      <defs>
        <linearGradient id="trend-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="oklch(0.85 0.12 190)" stopOpacity="0.28" />
          <stop offset="100%" stopColor="oklch(0.85 0.12 190)" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill="url(#trend-fill)" />
      <path d={line} fill="none" stroke="oklch(0.85 0.12 190)" strokeWidth="1.8" strokeLinejoin="round" />
      {buckets.map((bucket, index) => (
        <rect
          key={bucket.date}
          x={x(index) - width / buckets.length / 2}
          y={0}
          width={width / buckets.length}
          height={height}
          fill="transparent"
        >
          <title>{`${shortDate(bucket.date)} · ${bucket.minutes} min`}</title>
        </rect>
      ))}
    </svg>
  );
}

export function SplitCard({ split }: { split: Split }) {
  const max = Math.max(1, ...split.byGame.map((game) => game.minutes));

  return (
    <section className="panel">
      <div className="panel-head">
        <h2 className="panel-title">Where the hours go</h2>
        <span className="panel-note">lifetime</span>
      </div>
      {split.byGame.length === 0 ? (
        <p className="empty">No playtime recorded on any game yet.</p>
      ) : (
        <ul className="bars">
          {split.byGame.map((game) => (
            <li key={game.appid}>
              <div className="bar-row">
                <span className="bar-name">{game.name}</span>
                <span className="bar-value num">{hours(game.minutes)}h</span>
                <div className="bar-track">
                  <div className="bar-fill" style={{ width: `${(game.minutes / max) * 100}%` }} />
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export function GenreCard({ split }: { split: Split }) {
  const genres = split.byGenre;
  const total = genres.reduce((sum, genre) => sum + genre.minutes, 0);

  if (genres.length === 0) {
    return (
      <section className="panel">
        <div className="panel-head">
          <h2 className="panel-title">Genres</h2>
        </div>
        <p className="empty">
          Still mapping genres for your top games. Steam only exposes them one game at a time, so this fills in
          over the next few visits.
        </p>
      </section>
    );
  }

  let cursor = 0;
  const stops = genres.map((genre, index) => {
    const start = cursor;
    cursor += (genre.minutes / total) * 100;
    return `${GENRE_COLORS[index % GENRE_COLORS.length]} ${start.toFixed(2)}% ${cursor.toFixed(2)}%`;
  });

  return (
    <section className="panel">
      <div className="panel-head">
        <h2 className="panel-title">Genres</h2>
        <span className="panel-note">
          top 25 games · {split.genreCoveragePct === null ? "—" : `${split.genreCoveragePct}%`} of hours
        </span>
      </div>
      <div className="donut-wrap">
        <div className="donut" style={{ background: `conic-gradient(${stops.join(", ")})` }}>
          <div className="donut-center">{genres.length} genres</div>
        </div>
        <ul className="legend">
          {genres.map((genre, index) => (
            <li key={genre.genre}>
              <span className="swatch" style={{ background: GENRE_COLORS[index % GENRE_COLORS.length] }} />
              <span className="legend-name">{genre.genre}</span>
              <span className="legend-value num">{Math.round((genre.minutes / total) * 100)}%</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

export function RecentCard({ recent }: { recent: Dashboard["recent"] }) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2 className="panel-title">Last two weeks</h2>
      </div>
      {recent.length === 0 ? (
        <p className="empty">Nothing played in the last 14 days.</p>
      ) : (
        <ul className="rows">
          {recent.map((game) => (
            <li className="row" key={game.appid}>
              <span className="row-name">{game.name}</span>
              <span className="row-value num">{hours(game.minutesTwoWeeks)}h</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export function BacklogCard({ backlog }: { backlog: Backlog }) {
  return (
    <section className="panel warn">
      <div className="panel-head">
        <h2 className="panel-title">Backlog</h2>
      </div>
      <div className="big-counts">
        <div className="big-count">
          <span className="num">{backlog.neverLaunched}</span>
          <span>never launched</span>
        </div>
        <div className="big-count">
          <span className="num">{backlog.dropped}</span>
          <span>idle 90+ days</span>
        </div>
      </div>
      {backlog.neverLaunchedTop.length > 0 && (
        <ul className="rows">
          {backlog.neverLaunchedTop.map((game) => (
            <li className="row" key={game.appid}>
              <span className="row-name">{game.name}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export function DashboardSkeleton() {
  return (
    <div className="shell" aria-busy="true" aria-label="Loading your playtime">
      <div className="bar">
        <div className="skeleton" style={{ width: 36, height: 36, borderRadius: 999 }} />
        <div className="skeleton" style={{ width: 140, height: 32 }} />
      </div>
      <div className="skeleton" style={{ height: 150, marginBottom: 14 }} />
      <div className="grid">
        <div className="span-12 skeleton" style={{ height: 190 }} />
        <div className="span-7 skeleton" style={{ height: 240 }} />
        <div className="span-5 skeleton" style={{ height: 240 }} />
      </div>
    </div>
  );
}

function useCountUp(target: number): number {
  const [value, setValue] = useState(0);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setValue(target);
      return;
    }
    let frame = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / 600);
      setValue(target * (1 - Math.pow(1 - progress, 3)));
      if (progress < 1) {
        frame = requestAnimationFrame(tick);
      }
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [target]);

  return value;
}
