/** Small formatting helpers shared by every page. */

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) return "–";
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  if (seconds < 86400)
    return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
  return `${Math.floor(seconds / 86400)}d ${Math.floor((seconds % 86400) / 3600)}h`;
}

export function formatMillis(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return "–";
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return "–";
  const date = new Date(iso);
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function timeAgo(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return "never";
  const seconds = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 1000));
  return `${formatDuration(seconds)} ago`;
}

export function shortId(id: string): string {
  return id.slice(0, 8);
}

export function formatNumber(value: number): string {
  return value.toLocaleString();
}
