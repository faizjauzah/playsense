export type GameMinutes = { appid: number; name: string; minutes: number; headerUrl: string | null };
export type GenreMinutes = { genre: string; minutes: number };
export type RecentGame = { appid: number; name: string; minutesTwoWeeks: number; headerUrl: string | null };
export type DayMinutes = { date: string; minutes: number };

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
  } | null;
  trend: {
    buckets: DayMinutes[];
    trackingDays: number;
    trackingSince: string;
    previousWindowMinutes: number | null;
  } | null;
  recent: RecentGame[];
  backlog: { neverLaunched: number; dropped: number; neverLaunchedTop: GameMinutes[] } | null;
  insight: Insight | null;
  meta: { lastRefreshAt: string | null; snapshotCount: number; libraryPrivate: boolean };
};
