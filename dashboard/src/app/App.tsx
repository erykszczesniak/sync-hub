import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes } from "react-router";
import { Layout } from "./Layout";
import { OverviewPage } from "@/pages/OverviewPage";
import { RunsPage } from "@/pages/RunsPage";
import { DriftPage } from "@/pages/DriftPage";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 2000 } },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<OverviewPage />} />
            <Route path="runs" element={<RunsPage />} />
            <Route path="drift" element={<DriftPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
