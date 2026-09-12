import { useState } from "react";
import { useDrift, useOverview, useQuarantine } from "@/api/hooks";
import type { DriftStatus, QuarantineStatus } from "@/api/types";
import { DriftTable } from "@/components/DriftTable";
import { Pagination } from "@/components/Pagination";
import { QuarantineTable } from "@/components/QuarantineTable";
import { StatTile } from "@/components/StatTile";
import { formatDuration, formatNumber } from "@/components/format";
import { Empty, ErrorState, Loading } from "@/components/States";

const PAGE_SIZE = 20;

export function DriftPage() {
  const [driftStatus, setDriftStatus] = useState<DriftStatus | "">("OPEN");
  const [driftPage, setDriftPage] = useState(0);
  const [quarantineStatus, setQuarantineStatus] = useState<QuarantineStatus>("OPEN");
  const [quarantinePage, setQuarantinePage] = useState(0);

  const overview = useOverview();
  const drift = useDrift({ status: driftStatus || undefined, page: driftPage, size: PAGE_SIZE });
  const quarantine = useQuarantine({
    status: quarantineStatus,
    page: quarantinePage,
    size: PAGE_SIZE,
  });

  return (
    <>
      <h1 className="page-title">Drift &amp; quarantine</h1>
      <p className="page-subtitle">
        Schema drift is detected before mapping; blocking drift quarantines the record instead of
        guessing.
      </p>

      {overview.data ? (
        <div className="grid grid-4 section">
          <StatTile
            label="Open drift events"
            value={formatNumber(overview.data.totals.openDriftEvents)}
          />
          <StatTile
            label="Open quarantine"
            value={formatNumber(overview.data.totals.openQuarantine)}
          />
          <StatTile
            label="MTTD"
            value={formatDuration(overview.data.mttdSeconds)}
            hint="detected − source change, mean"
          />
          <StatTile
            label="MTTR"
            value={formatDuration(overview.data.mttrSeconds)}
            hint="resolved − detected, mean"
          />
        </div>
      ) : null}

      <section className="card section">
        <div className="toolbar">
          <h2 className="card-title">Drift events</h2>
          <label htmlFor="drift-status">Status</label>
          <select
            id="drift-status"
            value={driftStatus}
            onChange={(event) => {
              setDriftStatus(event.target.value as DriftStatus | "");
              setDriftPage(0);
            }}
          >
            <option value="OPEN">open</option>
            <option value="RESOLVED">resolved</option>
            <option value="">all</option>
          </select>
        </div>
        {drift.isPending ? <Loading what="drift events" /> : null}
        {drift.isError ? <ErrorState error={drift.error} what="drift events" /> : null}
        {drift.data ? (
          drift.data.content.length ? (
            <>
              <DriftTable events={drift.data.content} />
              <Pagination page={drift.data} onPage={setDriftPage} />
            </>
          ) : (
            <Empty>No drift events. The source is sending what the hub expects.</Empty>
          )
        ) : null}
      </section>

      <section className="card section">
        <div className="toolbar">
          <h2 className="card-title">Quarantined records</h2>
          <label htmlFor="quarantine-status">Status</label>
          <select
            id="quarantine-status"
            value={quarantineStatus}
            onChange={(event) => {
              setQuarantineStatus(event.target.value as QuarantineStatus);
              setQuarantinePage(0);
            }}
          >
            <option value="OPEN">open</option>
            <option value="SUPERSEDED">superseded</option>
            <option value="REPLAYED">replayed</option>
            <option value="DISCARDED">discarded</option>
          </select>
        </div>
        {quarantine.isPending ? <Loading what="quarantined records" /> : null}
        {quarantine.isError ? (
          <ErrorState error={quarantine.error} what="quarantined records" />
        ) : null}
        {quarantine.data ? (
          quarantine.data.content.length ? (
            <>
              <QuarantineTable records={quarantine.data.content} />
              <Pagination page={quarantine.data} onPage={setQuarantinePage} />
            </>
          ) : (
            <Empty>Nothing in quarantine with status {quarantineStatus.toLowerCase()}.</Empty>
          )
        ) : null}
      </section>
    </>
  );
}
