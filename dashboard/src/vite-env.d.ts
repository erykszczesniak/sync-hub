/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the hub when the dashboard is not served behind the same origin (empty = same origin). */
  readonly VITE_HUB_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
