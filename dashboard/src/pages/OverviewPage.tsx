import { useOverview, useRuns } from "@/api/hooks";
import { FeedCard } from "@/components/FeedCard";
import { StatTile } from "@/components/StatTile";
import { LagChart, RunCountsChart } from "@/components/charts";
import { formatDuration, formatNumber } from "@/components/format";
import { Empty, ErrorState, Loading } from "@/components/States";

const RECENT_RUNS = 30;

export function OverviewPage() {
  const overview = useOverview();
  const recent = useRuns({ page: 0, size: RECENT_RUNS });
  if (overview.isPending) return <Loading what="overview" />;
  if (overview.isError) return <ErrorState error={overview.error} what="overview" />;
  const { totals, feeds } = overview.data;
  const runs = recent.data?.content ?? [];
  return (
    <>
      <h1 className="page-title">Overview</h1>
      <p className="page-subtitle">System A → hub → System B. Refreshes every few seconds.</p>

      <div className="grid grid-2 section">
        {feeds.map((feed) => (
          <FeedCard key={feed.feed} feed={feed} />
        ))}
      </div>

      <div className="grid grid-4 section">
        <StatTile
          label="Records loaded"
          value={formatNumber(totals.loaded)}
          hint="applied to System B, all runs"
        />
        <StatTile
          label="Skipped"
          value={formatNumber(totals.skipped)}
          hint="unchanged, stale or duplicate"
        />
        <StatTile
          label="Quarantined"
          value={formatNumber(totals.quarantined)}
          hint={`${totals.openQuarantine} still open`}
        />
        <StatTile
          label="Open drift events"
          value={formatNumber(totals.openDriftEvents)}
          hint="blocking or warning"
        />
      </div>

      <div className="grid grid-4 section">
        <StatTile
          label="MTTD"
          value={formatDuration(overview.data.mttdSeconds)}
          hint="mean time to detect drift"
        />
        <StatTile
          label="MTTR"
          value={formatDuration(overview.data.mttrSeconds)}
          hint="mean time to resolve drift"
        />
        <StatTile
          label="Runs"
          value={formatNumber(totals.runs)}
          hint={`${totals.succeeded} succeeded · ${totals.partial} partial · ${totals.failed} failed`}
        />
        <StatTile
          label="Change events"
          value={formatNumber(overview.data.outboxPublished)}
          hint={`${overview.data.outboxPending} pending in the outbox`}
        />
      </div>

      <div className="grid grid-2 section">
        <section className="card">
          <h2 className="card-title">Records per run (last {RECENT_RUNS})</h2>
          {runs.length ? <RunCountsChart runs={runs} /> : <Empty>No runs yet.</Empty>}
        </section>
        <section className="card">
          <h2 className="card-title">Max lag per run</h2>
          {runs.length ? (
            <LagChart runs={runs} feeds={feeds.map((f) => f.feed)} />
          ) : (
            <Empty>No runs yet.</Empty>
          )}
        </section>
      </div>
    </>
  );
}
