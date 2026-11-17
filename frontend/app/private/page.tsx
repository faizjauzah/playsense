"use client";

import { useEffect, useState } from "react";
import { Unauthenticated, deleteSession, getMe, refreshDashboard } from "@/lib/api";

export default function PrivateLibrary() {
  const [checking, setChecking] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [player, setPlayer] = useState<{ name: string; avatar: string } | null>(null);

  useEffect(() => {
    getMe()
      .then((me) => setPlayer({ name: me.name, avatar: me.avatar }))
      .catch((failure) => {
        if (failure instanceof Unauthenticated) window.location.replace("/login/");
      });
  }, []);

  const checkAgain = async () => {
    setChecking(true);
    setError(null);
    try {
      const dashboard = await refreshDashboard();
      if (dashboard.player.libraryPrivate) {
        setError("Steam still returns no games. Give the setting a minute, then try again.");
        return;
      }
      window.location.replace("/");
    } catch (failure) {
      if (failure instanceof Unauthenticated) {
        window.location.replace("/login/");
        return;
      }
      setError("Could not reach Steam just now. Try again in a moment.");
    } finally {
      setChecking(false);
    }
  };

  const useAnotherAccount = async () => {
    setLeaving(true);
    await deleteSession().catch(() => {});
    window.location.replace("/login/");
  };

  return (
    <main className="center-page">
      {player && (
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img className="avatar" src={player.avatar} alt="" width={36} height={36} />
          <div className="who">
            <strong>{player.name}</strong>
            <span>signed in</span>
          </div>
        </div>
      )}
      <p className="eyebrow">Two settings to check</p>
      <h1>Steam is not showing us your games.</h1>
      <p>
        Steam sends us nothing when a profile hides its games, so there is nothing to chart. Both the profile and
        its game details have to be public — it takes about ten seconds:
      </p>
      <ol className="steps">
        <li>
          Open Steam → <strong>Privacy Settings</strong>
        </li>
        <li>
          Set <strong>Profile</strong> to <code>Public</code>
        </li>
        <li>
          Set <strong>Game details</strong> to <code>Public</code>
        </li>
        <li>Come back and press the button below</li>
      </ol>
      <button className="btn btn-primary" onClick={checkAgain} disabled={checking || leaving}>
        {checking ? "Checking…" : "I made it public — check again"}
      </button>
      {error && (
        <p className="landing-error" role="alert">
          {error}
        </p>
      )}
      <button className="btn" onClick={useAnotherAccount} disabled={checking || leaving}>
        {leaving ? "Signing out…" : "Use a different account"}
      </button>
      <p className="fine">
        Only playtime and game names are read. Nothing is posted to your profile, and nothing is shared. If both
        settings are already public, Steam returned no games for this account — try again in a moment.
      </p>
    </main>
  );
}
