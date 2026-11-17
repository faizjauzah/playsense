"use client";

import { useState } from "react";

// One entry per game service. Drop the mark in frontend/public and point src at it.
// angle: degrees around the ring, 0 = 3 o'clock, growing clockwise. radius: px from the ring centre.
const SERVICES = [
  { name: "Steam", src: "/steam-logo.png", angle: 206, radius: 480 },
];

export default function Login() {
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const response = await fetch("/api/session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ steamIdOrVanity: value }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        setError(
          body.error ?? "That did not work. Check the name and try again.",
        );
        return;
      }
      window.location.replace("/");
    } catch {
      setError("Could not reach the server. Try again in a moment.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="landing">
      <div className="landing-frame">
        {SERVICES.map((service) => (
          <span
            key={service.name}
            className="landing-orbit"
            style={
              {
                "--angle": `${service.angle}deg`,
                "--radius": `${service.radius}px`,
              } as React.CSSProperties
            }
            aria-hidden="true"
          >
            <span className="landing-trail" />
            <span className="landing-badge">
              <img src={service.src} alt="" width={52} height={52} />
            </span>
          </span>
        ))}

        <div className="landing-content">
          <p className="eyebrow">PlaySense</p>
          <h1 className="landing-title">
            See what your playtime actually says.
          </h1>
          <p className="landing-sub">
            Enter your Steam vanity name or SteamID64. No password, no Steam
            sign-in — everything we read is already public on your profile.
          </p>

          <form className="landing-form" onSubmit={submit}>
            <input
              className="landing-input"
              value={value}
              onChange={(event) => setValue(event.target.value)}
              placeholder="yourname or 7656119…"
              aria-label="Steam vanity name or SteamID64"
              autoFocus
              autoComplete="off"
              spellCheck={false}
            />
            <button
              className="btn btn-primary"
              type="submit"
              disabled={busy || value.trim().length < 2}
            >
              {busy ? "Making sense…" : "Make sense of my playtime"}
            </button>
            {error && <p className="landing-error">{error}</p>}
          </form>

          <p className="fine">
            Your vanity name is the last part of your profile URL:{" "}
            <code>steamcommunity.com/id/yourname</code>
          </p>
          <p className="fine">
            If you have never set one, use the 17-digit number from{" "}
            <code>steamcommunity.com/profiles/…</code> instead. Game details
            must be public for anything to show.
          </p>
        </div>
      </div>
    </main>
  );
}
