import { useQuery } from "@tanstack/react-query";
import { statusApi } from "./client";
import type { DriftStatus, QuarantineStatus } from "./types";

/** Everything refreshes on a short interval: the point of the page is to watch the hub work. */
export const REFRESH_MS = 5000;

export function useOverview() {
  return useQuery({
    queryKey: ["overview"],
    queryFn: statusApi.overview,
    refetchInterval: REFRESH_MS,
  });
}

export function useFeeds() {
  return useQuery({ queryKey: ["feeds"], queryFn: statusApi.feeds, refetchInterval: REFRESH_MS });
}

export function useRuns(params: { feed?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ["runs", params],
    queryFn: () => statusApi.runs(params),
    refetchInterval: REFRESH_MS,
    placeholderData: (previous) => previous,
  });
}

export function useQuarantine(params: {
  feed?: string;
  status?: QuarantineStatus;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: ["quarantine", params],
    queryFn: () => statusApi.quarantine(params),
    refetchInterval: REFRESH_MS,
    placeholderData: (previous) => previous,
  });
}

export function useDrift(params: { status?: DriftStatus; page?: number; size?: number }) {
  return useQuery({
    queryKey: ["drift", params],
    queryFn: () => statusApi.drift(params),
    refetchInterval: REFRESH_MS,
    placeholderData: (previous) => previous,
  });
}
