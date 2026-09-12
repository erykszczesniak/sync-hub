import { screen } from "@testing-library/react";
import { RunsPage } from "./RunsPage";
import { feed, page, run, stubFetch } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";

describe("RunsPage", () => {
  it("lists runs with status, counts and errors", async () => {
    stubFetch({
      "/api/status/feeds": [feed(), feed({ feed: "orders" })],
      "/api/status/runs": page([
        run(),
        run({
          id: "bbbb2222-0000-0000-0000-000000000000",
          status: "FAILED",
          errorMessage: "System A answered 503",
          loaded: 0,
        }),
      ]),
    });

    renderWithProviders(<RunsPage />);

    const rows = await screen.findAllByRole("row");
    expect(rows).toHaveLength(3);
    expect(screen.getByText("d469de59")).toBeInTheDocument();
    expect(screen.getByText("FAILED")).toBeInTheDocument();
    expect(screen.getByText("System A answered 503")).toBeInTheDocument();
    expect(screen.getByLabelText("Pagination")).toHaveTextContent("2 total");
    expect(screen.getByLabelText("Feed")).toBeInTheDocument();
  });
});
