import type { FeedHealthView } from "@/api/types";
import { formatDuration, formatNumber, formatTime, shortId, timeAgo } from "./format";
import { FeedHealthPill, RunStatusPill } from "./Pill";

export function FeedCard({ feed }: { feed: FeedHealthView }) {
  const last = feed.lastRun;
  return (
    <section className="card" aria-label={`${feed.feed} feed`}>
      <div className="feed-card-head">
        <div>
          <span className="feed-name">{feed.feed}</span>
          <span className="feed-protocol">{feed.protocol}</span>
        </div>
        <FeedHealthPill health={feed.health} running={feed.running} />
      </div>
      <dl className="kv">
        <dt>Last run</dt>
        <dd>
          {last ? (
            <>
              <RunStatusPill status={last.status} />{" "}
              <span className="mono">{shortId(last.id)}</span>{" "}
              <span className="muted">{timeAgo(last.finishedAt ?? last.startedAt)}</span>
            </>
          ) : (
            "never"
          )}
        </dd>
        <dt>Freshness</dt>
        <dd>
          {feed.freshnessSeconds === null
            ? "no successful run yet"
            : `${formatDuration(feed.freshnessSeconds)} since last success`}
        </dd>
        <dt>Lag</dt>
        <dd>
          {feed.lagSeconds === null
            ? "–"
            : `max ${formatDuration(feed.lagSeconds)} source → System B`}
        </dd>
        <dt>Watermark</dt>
        <dd className="mono">{formatTime(feed.watermark)}</dd>
        <dt>Last counts</dt>
        <dd>
          {last
            ? `${formatNumber(last.extracted)} extracted · ${formatNumber(last.loaded)} loaded · ${formatNumber(last.skipped)} skipped · ${formatNumber(last.quarantined)} quarantined`
            : "–"}
        </dd>
        <dt>System B rows</dt>
        <dd>{formatNumber(feed.activeRecords)} active</dd>
        <dt>Open issues</dt>
        <dd>
          {feed.openDriftEvents} drift · {feed.openQuarantine} quarantined
        </dd>
      </dl>
    </section>
  );
}
