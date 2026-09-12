import { NavLink, Outlet } from "react-router";
import { useOverview } from "@/api/hooks";
import { Pill } from "@/components/Pill";
import { timeAgo } from "@/components/format";

export function Layout() {
  const overview = useOverview();
  const connected = overview.isSuccess;
  return (
    <div className="layout">
      <header className="topbar">
        <NavLink to="/" className="brand" aria-label="sync-hub home">
          <span className="brand-mark" aria-hidden="true" />
          sync-hub
        </NavLink>
        <nav className="nav" aria-label="Main">
          <NavLink to="/" end>
            Overview
          </NavLink>
          <NavLink to="/runs">Runs</NavLink>
          <NavLink to="/drift">Drift &amp; quarantine</NavLink>
        </nav>
        <div className="topbar-right">
          {connected ? (
            <Pill tone="ok" live>
              hub connected
            </Pill>
          ) : overview.isError ? (
            <Pill tone="danger">hub unreachable</Pill>
          ) : (
            <Pill>connecting…</Pill>
          )}
          <span className="mono" title="Last status refresh">
            {connected ? timeAgo(overview.data.generatedAt) : ""}
          </span>
        </div>
      </header>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
