import type { SyncRun } from "@/api/types";
import { formatMillis, formatTime, shortId } from "./format";
import { Pill, RunStatusPill } from "./Pill";

export function RunsTable({ runs }: { runs: SyncRun[] }) {
  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            <th>Run</th>
            <th>Feed</th>
            <th>Mode</th>
            <th>Status</th>
            <th>Started</th>
            <th className="num">Duration</th>
            <th className="num">Extracted</th>
            <th className="num">Loaded</th>
            <th className="num">Skipped</th>
            <th className="num">Quarantined</th>
            <th>Drift</th>
            <th>Watermark after</th>
            <th>Error</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <tr key={run.id}>
              <td className="mono" title={run.id}>
                {shortId(run.id)}
              </td>
              <td>{run.feed}</td>
              <td>
                {run.mode}
                <span className="muted"> · {run.trigger.toLowerCase()}</span>
              </td>
              <td>
                <RunStatusPill status={run.status} />
              </td>
              <td className="mono">{formatTime(run.startedAt)}</td>
              <td className="num">{formatMillis(run.durationMs)}</td>
              <td className="num">{run.extracted}</td>
              <td className="num">{run.loaded}</td>
              <td className="num">{run.skipped}</td>
              <td className="num">{run.quarantined}</td>
              <td>
                {run.driftDetected ? (
                  <Pill tone="drift">drift</Pill>
                ) : (
                  <span className="muted">–</span>
                )}
              </td>
              <td className="mono">{formatTime(run.watermarkAfter)}</td>
              <td className="cell-error">{run.errorMessage ?? ""}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
