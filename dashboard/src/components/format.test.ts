import { formatDuration, formatMillis, shortId, timeAgo } from "./format";

describe("format helpers", () => {
  it("renders durations in the largest sensible unit", () => {
    expect(formatDuration(null)).toBe("–");
    expect(formatDuration(42)).toBe("42s");
    expect(formatDuration(125)).toBe("2m 5s");
    expect(formatDuration(3725)).toBe("1h 2m");
    expect(formatDuration(90000)).toBe("1d 1h");
  });

  it("renders milliseconds and relative times", () => {
    expect(formatMillis(640)).toBe("640 ms");
    expect(formatMillis(2100)).toBe("2.1 s");
    const now = Date.parse("2026-03-01T10:00:00Z");
    expect(timeAgo("2026-03-01T09:59:30Z", now)).toBe("30s ago");
    expect(timeAgo(null, now)).toBe("never");
    expect(shortId("d469de59-2a36-4146-b219-0afc2d576aa9")).toBe("d469de59");
  });
});
