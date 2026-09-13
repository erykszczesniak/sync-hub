import { Fragment, useState } from "react";
import type { QuarantinedRecord } from "@/api/types";
import { formatTime, shortId } from "./format";
import { Pill, QuarantineStatusPill } from "./Pill";

function prettyJson(payload: string): string {
  try {
    return JSON.stringify(JSON.parse(payload) as unknown, null, 2);
  } catch {
    return payload;
  }
}

const reasonTone = { DRIFT: "drift", VALIDATION: "warn", MAPPING: "warn" } as const;

export function QuarantineTable({ records }: { records: QuarantinedRecord[] }) {
  const [open, setOpen] = useState<string | null>(null);
  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            <th aria-label="Expand" />
            <th>Feed</th>
            <th>Business key</th>
            <th>Reason</th>
            <th>Details</th>
            <th>Source updated</th>
            <th>Quarantined</th>
            <th>Run</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {records.map((record) => {
            const expanded = open === record.id;
            return (
              <Fragment key={record.id}>
                <tr>
                  <td>
                    <button
                      type="button"
                      className="expander"
                      aria-expanded={expanded}
                      aria-label={`${expanded ? "Hide" : "Show"} payload of ${record.businessKey}`}
                      onClick={() => setOpen(expanded ? null : record.id)}
                    >
                      {expanded ? "▾" : "▸"}
                    </button>
                  </td>
                  <td>{record.feed}</td>
                  <td className="mono">{record.businessKey}</td>
                  <td>
                    <Pill tone={reasonTone[record.reason]}>{record.reason}</Pill>
                  </td>
                  <td className="cell-wrap muted">{record.details}</td>
                  <td className="mono">{formatTime(record.sourceUpdatedAt)}</td>
                  <td className="mono">{formatTime(record.quarantinedAt)}</td>
                  <td className="mono" title={record.runId}>
                    {shortId(record.runId)}
                  </td>
                  <td>
                    <QuarantineStatusPill status={record.status} />
                    {record.resolutionNote ? (
                      <div className="muted">{record.resolutionNote}</div>
                    ) : null}
                  </td>
                </tr>
                {expanded ? (
                  <tr className="payload-row">
                    <td colSpan={9}>
                      <pre className="payload">{prettyJson(record.payload)}</pre>
                    </td>
                  </tr>
                ) : null}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
