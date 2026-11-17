"use client";

import { useState } from "react";

export default function PrivateLibrary() {
  const [checking, setChecking] = useState(false);

  const checkAgain = async () => {
    setChecking(true);
    try {
      const response = await fetch("/api/refresh", { method: "POST", credentials: "same-origin" });
      if (response.ok) {
        const dashboard = await response.json();
        window.location.replace(dashboard.player?.libraryPrivate ? "/private/" : "/");
        return;
      }
      window.location.replace("/login/");
    } finally {
      setChecking(false);
    }
  };

  return (
    <main className="center-page">
      <p className="eyebrow">One setting to change</p>
      <h1>Your game details are private.</h1>
      <p>
        Steam sends us nothing when a profile hides its game details, so there is nothing to chart. A public
        profile is not enough — the setting below is what matters. It takes about ten seconds:
      </p>
      <ol className="steps">
        <li>
          Open Steam → <strong>Privacy Settings</strong>
        </li>
        <li>
          Set <strong>Game details</strong> to <code>Public</code>
        </li>
        <li>Come back and press the button below</li>
      </ol>
      <button className="btn btn-primary" onClick={checkAgain} disabled={checking}>
        {checking ? "Checking…" : "I made it public — check again"}
      </button>
      <p className="fine">
        Only playtime and game names are read. Nothing is posted to your profile, and nothing is shared.
      </p>
    </main>
  );
}
