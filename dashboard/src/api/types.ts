// Mirrors the hub's status DTOs (hub/.../status). Timestamps are ISO-8601 strings.

export type FeedHealth = "HEALTHY" | "STALE" | "DRIFT" | "FAILING" | "NEVER_RUN";
export type RunStatus = "RUNNING" | "SUCCEEDED" | "PARTIAL" | "FAILED";
export type RunMode = "INCREMENTAL" | "BACKFILL" | "REPLAY";
export type QuarantineStatus = "OPEN" | "REPLAYED" | "SUPERSEDED" | "DISCARDED";
export type DriftStatus = "OPEN" | "RESOLVED";
export type DriftKind =
  "FIELD_ADDED" | "FIELD_MISSING" | "FIELD_RETYPED" | "FIELD_RENAMED" | "VALUE_OUT_OF_DOMAIN";

export interface SyncRun {
  id: string;
  feed: string;
  mode: RunMode;
  trigger: "SCHEDULED" | "MANUAL";
  status: RunStatus;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  extracted: number;
  transformed: number;
  loaded: number;
  skipped: number;
  quarantined: number;
  failed: number;
  driftDetected: boolean;
  watermarkBefore: string | null;
  watermarkAfter: string | null;
  backfillFrom: string | null;
  backfillTo: string | null;
  maxLagSeconds: number | null;
  errorMessage: string | null;
}

export interface FeedHealthView {
  feed: string;
  protocol: string;
  health: FeedHealth;
  running: boolean;
  watermark: string | null;
  lastRun: SyncRun | null;
  lastSuccessfulRun: SyncRun | null;
  freshnessSeconds: number | null;
  lagSeconds: number | null;
  openDriftEvents: number;
  openQuarantine: number;
  activeRecords: number;
}

export interface QuarantinedRecord {
  id: string;
  feed: string;
  businessKey: string;
  runId: string;
  reason: "DRIFT" | "VALIDATION" | "MAPPING";
  details: string;
  payload: string;
  sourceUpdatedAt: string | null;
  quarantinedAt: string;
  status: QuarantineStatus;
  resolvedAt: string | null;
  resolutionNote: string | null;
}

export interface DriftEvent {
  id: string;
  feed: string;
  kind: DriftKind;
  severity: "WARNING" | "BLOCKING";
  field: string;
  expected: string | null;
  actual: string | null;
  details: string;
  affectedRecords: number;
  firstRunId: string;
  lastRunId: string;
  sourceChangedAt: string | null;
  detectedAt: string;
  lastSeenAt: string;
  status: DriftStatus;
  resolvedAt: string | null;
  resolutionNote: string | null;
  timeToDetectSeconds: number | null;
  timeToResolveSeconds: number | null;
}

export interface Totals {
  runs: number;
  succeeded: number;
  partial: number;
  failed: number;
  loaded: number;
  skipped: number;
  quarantined: number;
  openQuarantine: number;
  openDriftEvents: number;
}

export interface Overview {
  generatedAt: string;
  feeds: FeedHealthView[];
  totals: Totals;
  mttdSeconds: number | null;
  mttrSeconds: number | null;
  outboxPending: number;
  outboxPublished: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
