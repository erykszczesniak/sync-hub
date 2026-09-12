import { ApiError } from "@/api/client";

export function Loading({ what }: { what: string }) {
  return (
    <div className="state" role="status">
      Loading {what}…
    </div>
  );
}

export function ErrorState({ error, what }: { error: unknown; what: string }) {
  const message = error instanceof ApiError ? `${error.status}: ${error.message}` : String(error);
  return (
    <div className="state state-error" role="alert">
      Could not load {what}.<code>{message}</code>
    </div>
  );
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <div className="state">{children}</div>;
}
