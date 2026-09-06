/** Declares the immutable delivery-ring value injected by the Vite build boundary. */
/// <reference types="vite/client" />

/** Build-time environment values consumed by the reviewer-facing runtime label. */
interface ImportMetaEnv {
  readonly VITE_DELIVERY_RING?: string
}

/** Extends Vite's import metadata with the application-owned environment contract. */
interface ImportMeta {
  readonly env: ImportMetaEnv
}
