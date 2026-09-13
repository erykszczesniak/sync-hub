import type {
  DriftEvent,
  DriftStatus,
  FeedHealthView,
  Overview,
  Page,
  QuarantinedRecord,
  QuarantineStatus,
  SyncRun,
} from "./types";

/** Thrown for non-2xx answers; carries the RFC 9457 detail when the hub sent one. */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

const BASE = import.meta.env.VITE_HUB_BASE_URL ?? "";

async function get<T>(
  path: string,
  params?: Record<string, string | number | undefined>,
): Promise<T> {
  const url = new URL(BASE + path, window.location.origin);
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== "") url.searchParams.set(key, String(value));
  });
  const response = await fetch(url.toString(), { headers: { Accept: "application/json" } });
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const problem: unknown = await response.json();
      if (isProblem(problem) && problem.detail) detail = problem.detail;
    } catch {
      // not a problem-details body
    }
    throw new ApiError(response.status, detail);
  }
  return (await response.json()) as T;
}

function isProblem(value: unknown): value is { detail?: string } {
  return typeof value === "object" && value !== null && "detail" in value;
}

/** The dashboard is read-only: every call here is a GET against /api/status. */
export const statusApi = {
  overview: () => get<Overview>("/api/status/overview"),
  feeds: () => get<FeedHealthView[]>("/api/status/feeds"),
  feed: (feed: string) => get<FeedHealthView>(`/api/status/feeds/${encodeURIComponent(feed)}`),
  runs: (params: { feed?: string; page?: number; size?: number }) =>
    get<Page<SyncRun>>("/api/status/runs", params),
  run: (id: string) => get<SyncRun>(`/api/status/runs/${encodeURIComponent(id)}`),
  quarantine: (params: {
    feed?: string;
    status?: QuarantineStatus;
    page?: number;
    size?: number;
  }) => get<Page<QuarantinedRecord>>("/api/status/quarantine", params),
  drift: (params: { status?: DriftStatus; page?: number; size?: number }) =>
    get<Page<DriftEvent>>("/api/status/drift", params),
};
