import { screen } from "@testing-library/react";
import { OverviewPage } from "./OverviewPage";
import { overview, page, run } from "@/test/fixtures";
import { stubFetch } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";

describe("OverviewPage", () => {
  it("shows feed health, KPI tiles and MTTD/MTTR from the overview endpoint", async () => {
    stubFetch({
      "/api/status/overview": overview(),
      "/api/status/runs": page([
        run(),
        run({ id: "aaaa1111-0000-0000-0000-000000000000", feed: "orders" }),
      ]),
    });

    renderWithProviders(<OverviewPage />);

    expect(await screen.findByLabelText("customers feed")).toBeInTheDocument();
    expect(screen.getByLabelText("orders feed")).toHaveTextContent("DRIFT");
    expect(screen.getByLabelText("Records loaded")).toHaveTextContent("200");
    expect(screen.getByLabelText("Quarantined")).toHaveTextContent("4 still open");
    expect(screen.getByLabelText("MTTD")).toHaveTextContent("1m 30s");
    expect(screen.getByLabelText("MTTR")).toHaveTextContent("–");
    expect(screen.getByLabelText("Change events")).toHaveTextContent("200");
  });

  it("reports an unreachable hub instead of an empty page", async () => {
    stubFetch({});

    renderWithProviders(<OverviewPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load overview");
  });
});
