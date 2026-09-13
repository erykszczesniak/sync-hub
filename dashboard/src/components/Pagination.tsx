import type { Page } from "@/api/types";

export function Pagination<T>({ page, onPage }: { page: Page<T>; onPage: (next: number) => void }) {
  const last = Math.max(page.totalPages - 1, 0);
  return (
    <nav className="pagination" aria-label="Pagination">
      <span>
        {page.totalElements.toLocaleString()} total · page {page.page + 1} of{" "}
        {Math.max(page.totalPages, 1)}
      </span>
      <button type="button" onClick={() => onPage(page.page - 1)} disabled={page.page <= 0}>
        Previous
      </button>
      <button type="button" onClick={() => onPage(page.page + 1)} disabled={page.page >= last}>
        Next
      </button>
    </nav>
  );
}
