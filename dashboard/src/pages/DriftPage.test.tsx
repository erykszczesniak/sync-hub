import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DriftPage } from "./DriftPage";
import type { DriftEvent, QuarantinedRecord } from "@/api/types";
import { overview, page, stubFetch } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";

const event: DriftEvent = {
  id: "e1",
  feed: "customers",
  kind: "FIELD_RENAMED",
  severity: "BLOCKING",
  field: "email",
  expected: "email",
  actual: "emailAddress",
  details: "field 'email' appears to be renamed to 'emailAddress'",
  affectedRecords: 4,
  firstRunId: "856e9911-0000-0000-0000-000000000000",
  lastRunId: "b484c1ce-0000-0000-0000-000000000000",
  sourceChangedAt: "2026-03-01T09:58:30Z",
  detectedAt: "2026-03-01T10:00:00Z",
  lastSeenAt: "2026-03-01T10:00:20Z",
  status: "OPEN",
  resolvedAt: null,
  resolutionNote: null,
  timeToDetectSeconds: 90,
  timeToResolveSeconds: null,
};

const record: QuarantinedRecord = {
  id: "q1",
  feed: "customers",
  businessKey: "cus_00007",
  runId: "856e9911-0000-0000-0000-000000000000",
  reason: "DRIFT",
  details: "field 'email' appears to be renamed to 'emailAddress'",
  payload: '{"id":"cus_00007","emailAddress":"a@b.c"}',
  sourceUpdatedAt: "2026-03-01T09:58:30Z",
  quarantinedAt: "2026-03-01T10:00:00Z",
  status: "OPEN",
  resolvedAt: null,
  resolutionNote: null,
};

describe("DriftPage", () => {
  it("shows drift events, quarantined records and expands the raw payload", async () => {
    stubFetch({
      "/api/status/overview": overview(),
      "/api/status/drift": page([event]),
      "/api/status/quarantine": page([record]),
    });

    renderWithProviders(<DriftPage />);

    expect(await screen.findByText("FIELD_RENAMED")).toBeInTheDocument();
    expect(screen.getByText("email → emailAddress")).toBeInTheDocument();
    expect(screen.getByText("BLOCKING")).toBeInTheDocument();
    expect(screen.getAllByText("1m 30s").length).toBeGreaterThanOrEqual(2);
    expect(await screen.findByText("cus_00007")).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText("Show payload of cus_00007"));
    expect(screen.getByText(/"emailAddress": "a@b.c"/)).toBeInTheDocument();
  });
});
