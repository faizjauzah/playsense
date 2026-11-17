export type GameMinutes = {
  appid: number;
  name: string;
  minutes: number;
  minutesTwoWeeks: number | null;
  headerUrl: string | null;
  priceCents: number | null;
};
export type GenreMinutes = { genre: string; minutes: number };
export type RecentGame = { appid: number; name: string; minutesTwoWeeks: number; headerUrl: string | null };
export type DayMinutes = { date: string; minutes: number };

export type BacklogGame = {
  appid: number;
  name: string;
  minutes: number;
  priceCents: number | null;
  lastPlayedAt: string | null;
};

export type Insight = {
  source: "gemini" | "template";
  headline: string;
  observations: { card: string; text: string }[];
  recommendation: string;
  generatedAt: string;
};

export type Dashboard = {
  player: { steamid: number; name: string; avatar: string; libraryPrivate: boolean };
  hero: {
    minutesLast30d: number;
    windowLabel: string;
    hoursLifetime: number;
    topGameSharePct: number;
    backlogCount: number;
  } | null;
  split: {
    byGame: GameMinutes[];
    byGenre: GenreMinutes[];
    genreCoveragePct: number | null;
    priceCurrency: string | null;
    value: { best: GameMinutes; worst: GameMinutes } | null;
  } | null;
  trend: {
    buckets: DayMinutes[];
    trackingDays: number;
    trackingSince: string;
    previousWindowMinutes: number | null;
  } | null;
  recent: RecentGame[];
  backlog: {
    neverLaunched: number;
    dropped: number;
    neverLaunchedTop: GameMinutes[];
    all: BacklogGame[];
  } | null;
  insight: Insight | null;
  meta: { lastRefreshAt: string | null; snapshotCount: number; libraryPrivate: boolean };
};
