import type { DriftStatus, FeedHealth, QuarantineStatus, RunStatus } from "@/api/types";

type Tone = "ok" | "warn" | "danger" | "drift" | "accent" | "neutral";

const feedTone: Record<FeedHealth, Tone> = {
  HEALTHY: "ok",
  STALE: "warn",
  DRIFT: "drift",
  FAILING: "danger",
  NEVER_RUN: "neutral",
};

const runTone: Record<RunStatus, Tone> = {
  RUNNING: "accent",
  SUCCEEDED: "ok",
  PARTIAL: "warn",
  FAILED: "danger",
};

const quarantineTone: Record<QuarantineStatus, Tone> = {
  OPEN: "warn",
  REPLAYED: "ok",
  SUPERSEDED: "ok",
  DISCARDED: "neutral",
};

const driftTone: Record<DriftStatus, Tone> = { OPEN: "drift", RESOLVED: "ok" };

export function Pill({
  tone = "neutral",
  live = false,
  children,
}: {
  tone?: Tone;
  live?: boolean;
  children: React.ReactNode;
}) {
  const className = ["pill", tone === "neutral" ? "" : `pill-${tone}`, live ? "pill-live" : ""]
    .filter(Boolean)
    .join(" ");
  return <span className={className}>{children}</span>;
}

export function FeedHealthPill({ health, running }: { health: FeedHealth; running?: boolean }) {
  return (
    <Pill tone={feedTone[health]} live={running}>
      {running ? "SYNCING" : health.replace("_", " ")}
    </Pill>
  );
}

export function RunStatusPill({ status }: { status: RunStatus }) {
  return (
    <Pill tone={runTone[status]} live={status === "RUNNING"}>
      {status}
    </Pill>
  );
}

export function QuarantineStatusPill({ status }: { status: QuarantineStatus }) {
  return <Pill tone={quarantineTone[status]}>{status}</Pill>;
}

export function DriftStatusPill({ status }: { status: DriftStatus }) {
  return <Pill tone={driftTone[status]}>{status}</Pill>;
}
