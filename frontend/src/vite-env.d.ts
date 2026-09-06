/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_DELIVERY_RING?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
