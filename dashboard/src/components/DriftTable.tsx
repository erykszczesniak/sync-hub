import type { DriftEvent } from "@/api/types";
import { formatDuration, formatTime, shortId } from "./format";
import { DriftStatusPill, Pill } from "./Pill";

export function DriftTable({ events }: { events: DriftEvent[] }) {
  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            <th>Feed</th>
            <th>Kind</th>
            <th>Severity</th>
            <th>Field</th>
            <th>Expected → actual</th>
            <th className="num">Affected</th>
            <th>Detected</th>
            <th className="num">Time to detect</th>
            <th>Status</th>
            <th className="num">Time to resolve</th>
            <th>Note</th>
          </tr>
        </thead>
        <tbody>
          {events.map((event) => (
            <tr key={event.id}>
              <td>{event.feed}</td>
              <td className="mono">{event.kind}</td>
              <td>
                <Pill tone={event.severity === "BLOCKING" ? "danger" : "warn"}>
                  {event.severity}
                </Pill>
              </td>
              <td className="mono">{event.field}</td>
              <td className="mono" title={event.details}>
                {event.expected ?? "–"} → {event.actual ?? "–"}
              </td>
              <td className="num">{event.affectedRecords}</td>
              <td
                className="mono"
                title={`first run ${shortId(event.firstRunId)}, last run ${shortId(event.lastRunId)}`}
              >
                {formatTime(event.detectedAt)}
              </td>
              <td className="num">{formatDuration(event.timeToDetectSeconds)}</td>
              <td>
                <DriftStatusPill status={event.status} />
              </td>
              <td className="num">{formatDuration(event.timeToResolveSeconds)}</td>
              <td className="cell-wrap muted">{event.resolutionNote ?? ""}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
