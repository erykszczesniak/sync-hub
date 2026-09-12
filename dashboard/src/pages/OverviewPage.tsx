import { useOverview } from "@/api/hooks";
import { FeedHealthPill } from "@/components/Pill";
import { ErrorState, Loading } from "@/components/States";

/** Placeholder until feature/dashboard-health-and-runs fills it with cards and charts. */
export function OverviewPage() {
  const overview = useOverview();
  if (overview.isPending) return <Loading what="overview" />;
  if (overview.isError) return <ErrorState error={overview.error} what="overview" />;
  return (
    <>
      <h1 className="page-title">Overview</h1>
      <p className="page-subtitle">System A → hub → System B, at a glance.</p>
      <div className="grid grid-2">
        {overview.data.feeds.map((feed) => (
          <section className="card" key={feed.feed}>
            <h2 className="card-title">
              {feed.feed} · {feed.protocol}
            </h2>
            <FeedHealthPill health={feed.health} running={feed.running} />
          </section>
        ))}
      </div>
    </>
  );
}
