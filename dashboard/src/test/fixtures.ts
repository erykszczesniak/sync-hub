import type { FeedHealthView, Overview, Page, SyncRun } from "@/api/types";

export function run(overrides: Partial<SyncRun> = {}): SyncRun {
  return {
    id: "d469de59-2a36-4146-b219-0afc2d576aa9",
    feed: "customers",
    mode: "INCREMENTAL",
    trigger: "SCHEDULED",
    status: "SUCCEEDED",
    startedAt: "2026-03-01T10:00:00Z",
    finishedAt: "2026-03-01T10:00:01Z",
    durationMs: 640,
    extracted: 50,
    transformed: 50,
    loaded: 48,
    skipped: 2,
    quarantined: 0,
    failed: 0,
    driftDetected: false,
    watermarkBefore: null,
    watermarkAfter: "2026-03-01T09:59:58Z",
    backfillFrom: null,
    backfillTo: null,
    maxLagSeconds: 12,
    errorMessage: null,
    ...overrides,
  };
}

export function feed(overrides: Partial<FeedHealthView> = {}): FeedHealthView {
  return {
    feed: "customers",
    protocol: "REST",
    health: "HEALTHY",
    running: false,
    watermark: "2026-03-01T09:59:58Z",
    lastRun: run(),
    lastSuccessfulRun: run(),
    freshnessSeconds: 30,
    lagSeconds: 12,
    openDriftEvents: 0,
    openQuarantine: 0,
    activeRecords: 50,
    ...overrides,
  };
}

export function overview(overrides: Partial<Overview> = {}): Overview {
  return {
    generatedAt: "2026-03-01T10:00:30Z",
    feeds: [
      feed(),
      feed({ feed: "orders", protocol: "GraphQL", health: "DRIFT", openDriftEvents: 1 }),
    ],
    totals: {
      runs: 4,
      succeeded: 3,
      partial: 1,
      failed: 0,
      loaded: 200,
      skipped: 7,
      quarantined: 4,
      openQuarantine: 4,
      openDriftEvents: 1,
    },
    mttdSeconds: 90,
    mttrSeconds: null,
    outboxPending: 0,
    outboxPublished: 200,
    ...overrides,
  };
}

export function page<T>(content: T[], totalElements = content.length): Page<T> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements,
    totalPages: Math.max(1, Math.ceil(totalElements / 20)),
  };
}

/** Stub `fetch` with a map of path → JSON body. */
export function stubFetch(routes: Record<string, unknown>) {
  vi.stubGlobal(
    "fetch",
    vi.fn((input: string | URL) => {
      const path = new URL(String(input), "http://localhost").pathname;
      const body = routes[path];
      if (body === undefined) {
        return Promise.resolve(
          new Response(JSON.stringify({ detail: `no stub for ${path}` }), { status: 404 }),
        );
      }
      return Promise.resolve(
        new Response(JSON.stringify(body), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
    }),
  );
}
