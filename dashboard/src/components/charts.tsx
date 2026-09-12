import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { SyncRun } from "@/api/types";
import { formatTime, shortId } from "./format";

/** Series colours come from tokens.css so light/dark and brand swaps happen in one place. */
const SERIES = {
  loaded: "var(--series-loaded)",
  skipped: "var(--series-skipped)",
  quarantined: "var(--series-quarantined)",
} as const;

const FEED_SERIES = ["var(--series-feed-1)", "var(--series-feed-2)"];
const SURFACE = "var(--color-surface)";
const GRID = "var(--chart-grid)";
const AXIS = "var(--chart-axis)";

interface RunPoint {
  id: string;
  label: string;
  startedAt: string;
  feed: string;
  loaded: number;
  skipped: number;
  quarantined: number;
  lag: number | null;
}

function toPoints(runs: SyncRun[]): RunPoint[] {
  return [...runs]
    .sort((a, b) => a.startedAt.localeCompare(b.startedAt))
    .map((run) => ({
      id: run.id,
      label: shortId(run.id),
      startedAt: run.startedAt,
      feed: run.feed,
      loaded: run.loaded,
      skipped: run.skipped,
      quarantined: run.quarantined,
      lag: run.maxLagSeconds,
    }));
}

function RunTooltip({ active, payload }: { active?: boolean; payload?: { payload: RunPoint }[] }) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div className="chart-tooltip" role="tooltip">
      <strong>
        {point.feed} · <span className="mono">{point.label}</span>
      </strong>
      <div>
        <span>started</span>
        <span>{formatTime(point.startedAt)}</span>
      </div>
      <div>
        <span>loaded</span>
        <span>{point.loaded}</span>
      </div>
      <div>
        <span>skipped</span>
        <span>{point.skipped}</span>
      </div>
      <div>
        <span>quarantined</span>
        <span>{point.quarantined}</span>
      </div>
      {point.lag !== null ? (
        <div>
          <span>max lag</span>
          <span>{point.lag}s</span>
        </div>
      ) : null}
    </div>
  );
}

export function Legend({ items }: { items: { label: string; swatch: string }[] }) {
  return (
    <div className="chart-legend" aria-hidden="true">
      {items.map((item) => (
        <span key={item.label} style={{ "--swatch": item.swatch } as React.CSSProperties}>
          {item.label}
        </span>
      ))}
    </div>
  );
}

/** Records per run, stacked: loaded / skipped / quarantined. Oldest on the left. */
export function RunCountsChart({ runs }: { runs: SyncRun[] }) {
  const points = toPoints(runs);
  return (
    <>
      <Legend
        items={[
          { label: "loaded", swatch: SERIES.loaded },
          { label: "skipped", swatch: SERIES.skipped },
          { label: "quarantined", swatch: SERIES.quarantined },
        ]}
      />
      <div className="chart">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={points}
            barCategoryGap={6}
            margin={{ top: 4, right: 8, left: -16, bottom: 0 }}
          >
            <CartesianGrid vertical={false} stroke={GRID} strokeWidth={1} />
            <XAxis
              dataKey="label"
              tick={{ fontSize: 11, fill: AXIS }}
              tickLine={false}
              axisLine={false}
              minTickGap={24}
            />
            <YAxis
              tick={{ fontSize: 11, fill: AXIS }}
              tickLine={false}
              axisLine={false}
              allowDecimals={false}
            />
            <Tooltip content={<RunTooltip />} cursor={{ fill: GRID }} />
            <Bar
              dataKey="loaded"
              stackId="run"
              fill={SERIES.loaded}
              stroke={SURFACE}
              strokeWidth={2}
              maxBarSize={24}
            />
            <Bar
              dataKey="skipped"
              stackId="run"
              fill={SERIES.skipped}
              stroke={SURFACE}
              strokeWidth={2}
              maxBarSize={24}
            />
            <Bar
              dataKey="quarantined"
              stackId="run"
              fill={SERIES.quarantined}
              stroke={SURFACE}
              strokeWidth={2}
              maxBarSize={24}
              radius={[4, 4, 0, 0]}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </>
  );
}

/** Max lag per run, one line per feed. Runs that loaded nothing have no lag and leave a gap. */
export function LagChart({ runs, feeds }: { runs: SyncRun[]; feeds: string[] }) {
  const sorted = [...runs].sort((a, b) => a.startedAt.localeCompare(b.startedAt));
  const rows = sorted.map((run) => ({
    label: shortId(run.id),
    startedAt: run.startedAt,
    feed: run.feed,
    loaded: run.loaded,
    skipped: run.skipped,
    quarantined: run.quarantined,
    lag: run.maxLagSeconds,
    [run.feed]: run.maxLagSeconds ?? undefined,
  }));
  return (
    <>
      <Legend
        items={feeds.map((feed, i) => ({
          label: feed,
          swatch: FEED_SERIES[i % FEED_SERIES.length],
        }))}
      />
      <div className="chart">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={rows} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke={GRID} strokeWidth={1} />
            <XAxis
              dataKey="label"
              tick={{ fontSize: 11, fill: AXIS }}
              tickLine={false}
              axisLine={false}
              minTickGap={24}
            />
            <YAxis
              tick={{ fontSize: 11, fill: AXIS }}
              tickLine={false}
              axisLine={false}
              tickFormatter={(v: number) => `${v}s`}
            />
            <Tooltip content={<RunTooltip />} cursor={{ stroke: GRID }} />
            {feeds.map((feed, i) => (
              <Line
                key={feed}
                type="monotone"
                dataKey={feed}
                stroke={FEED_SERIES[i % FEED_SERIES.length]}
                strokeWidth={2}
                dot={{
                  r: 4,
                  stroke: SURFACE,
                  strokeWidth: 2,
                  fill: FEED_SERIES[i % FEED_SERIES.length],
                }}
                connectNulls
                isAnimationActive={false}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </>
  );
}
