import { useState } from "react";
import { useFeeds, useRuns } from "@/api/hooks";
import { Pagination } from "@/components/Pagination";
import { RunsTable } from "@/components/RunsTable";
import { RunCountsChart } from "@/components/charts";
import { Empty, ErrorState, Loading } from "@/components/States";

const PAGE_SIZE = 20;

export function RunsPage() {
  const [feed, setFeed] = useState<string>("");
  const [page, setPage] = useState(0);
  const feeds = useFeeds();
  const runs = useRuns({ feed: feed || undefined, page, size: PAGE_SIZE });

  return (
    <>
      <h1 className="page-title">Sync runs</h1>
      <p className="page-subtitle">
        Every incremental, backfill and replay run with its counts and watermark.
      </p>
      <div className="toolbar">
        <label htmlFor="feed-filter">Feed</label>
        <select
          id="feed-filter"
          value={feed}
          onChange={(event) => {
            setFeed(event.target.value);
            setPage(0);
          }}
        >
          <option value="">all feeds</option>
          {(feeds.data ?? []).map((f) => (
            <option key={f.feed} value={f.feed}>
              {f.feed}
            </option>
          ))}
        </select>
      </div>
      {runs.isPending ? <Loading what="runs" /> : null}
      {runs.isError ? <ErrorState error={runs.error} what="runs" /> : null}
      {runs.data ? (
        runs.data.content.length ? (
          <>
            <section className="card section">
              <h2 className="card-title">Records per run (this page)</h2>
              <RunCountsChart runs={runs.data.content} />
            </section>
            <section className="card">
              <RunsTable runs={runs.data.content} />
              <Pagination page={runs.data} onPage={setPage} />
            </section>
          </>
        ) : (
          <Empty>No runs yet. Trigger one with POST /api/sync/customers/run.</Empty>
        )
      ) : null}
    </>
  );
}
