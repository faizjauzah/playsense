"use client";

import { useCallback, useEffect, useState } from "react";
import {
  BacklogCard,
  DashboardSkeleton,
  GenreCard,
  Hero,
  InsightCard,
  RecentCard,
  SplitCard,
  TopBar,
  TrendCard,
} from "@/components/dashboard";
import { Unauthenticated, deleteSession, getDashboard, refreshDashboard } from "@/lib/api";
import type { Dashboard } from "@/lib/types";

export default function Home() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [warming, setWarming] = useState(false);

  const load = useCallback(async () => {
    try {
      const dashboard = await getDashboard();
      if (dashboard.player.libraryPrivate) {
        window.location.replace("/private/");
        return;
      }
      setData(dashboard);
    } catch (failure) {
      if (failure instanceof Unauthenticated) {
        window.location.replace("/login/");
        return;
      }
      setError(failure instanceof Error ? failure.message : "Something went wrong");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (data || error) return;
    const timer = setTimeout(() => setWarming(true), 6000);
    return () => clearTimeout(timer);
  }, [data, error]);

  const onRefresh = async () => {
    setRefreshing(true);
    try {
      setData(await refreshDashboard());
    } catch (failure) {
      if (failure instanceof Unauthenticated) {
        window.location.replace("/login/");
      }
    } finally {
      setRefreshing(false);
    }
  };

  const onSignOut = async () => {
    await deleteSession().catch(() => {});
    window.location.replace("/login/");
  };

  if (error) {
    return (
      <main className="center-page">
        <h1>Could not load your playtime</h1>
        <p>{error}</p>
        <button className="btn" onClick={() => window.location.reload()}>
          Try again
        </button>
        <button className="btn" onClick={onSignOut}>
          Use a different account
        </button>
      </main>
    );
  }

  if (!data) {
    return (
      <>
        <DashboardSkeleton />
        {warming && (
          <p className="fine" style={{ textAlign: "center", padding: "0 22px" }}>
            Waking the server. On the free tier it sleeps when idle, so the first load can take up to a minute.
          </p>
        )}
      </>
    );
  }

  if (!data.hero) {
    return (
      <div className="shell">
        <TopBar
          player={data.player}
          lastRefreshAt={data.meta.lastRefreshAt}
          refreshing={refreshing}
          onRefresh={onRefresh}
          onSignOut={onSignOut}
        />
        <section className="panel">
          <h2 className="panel-title">Nothing captured yet</h2>
          <p className="empty" style={{ marginTop: 10 }}>
            Steam returned an empty library for this account. If your game details are public, try a refresh in
            a moment.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div className="shell">
      <TopBar
        player={data.player}
        lastRefreshAt={data.meta.lastRefreshAt}
        refreshing={refreshing}
        onRefresh={onRefresh}
        onSignOut={onSignOut}
      />

      <Hero hero={data.hero} />

      <div className="grid">
        {data.insight && (
          <div className="span-12">
            <InsightCard insight={data.insight} />
          </div>
        )}
        {data.trend && (
          <div className="span-7">
            <TrendCard trend={data.trend} />
          </div>
        )}
        {data.split && (
          <div className="span-5">
            <SplitCard split={data.split} />
          </div>
        )}
        {data.split && (
          <div className="span-4">
            <GenreCard split={data.split} />
          </div>
        )}
        <div className="span-4">
          <RecentCard recent={data.recent} />
        </div>
        {data.backlog && (
          <div className="span-4">
            <BacklogCard backlog={data.backlog} currency={data.split?.priceCurrency ?? null} />
          </div>
        )}
      </div>
    </div>
  );
}
