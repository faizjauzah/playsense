import type { Dashboard } from "./types";

export class Unauthenticated extends Error {}

export async function getDashboard(): Promise<Dashboard> {
  const response = await fetch("/api/dashboard", { credentials: "same-origin" });
  if (response.status === 401) {
    throw new Unauthenticated();
  }
  if (!response.ok) {
    throw new Error(`Dashboard request failed (${response.status})`);
  }
  return response.json();
}

export async function refreshDashboard(): Promise<Dashboard> {
  const response = await fetch("/api/refresh", {
    method: "POST",
    credentials: "same-origin",
  });
  if (response.status === 401) {
    throw new Unauthenticated();
  }
  if (!response.ok) {
    throw new Error(`Refresh failed (${response.status})`);
  }
  return response.json();
}

export async function deleteSession(): Promise<void> {
  await fetch("/api/session", { method: "DELETE", credentials: "same-origin" });
}

export function hours(minutes: number): string {
  const value = minutes / 60;
  return value >= 100 ? Math.round(value).toString() : value.toFixed(1);
}

export function shortDate(iso: string): string {
  return new Date(iso + "T00:00:00Z").toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  });
}

export function relativeTime(iso: string | null): string {
  if (!iso) return "never";
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hoursAgo = Math.round(minutes / 60);
  if (hoursAgo < 24) return `${hoursAgo}h ago`;
  return `${Math.round(hoursAgo / 24)}d ago`;
}
