/// <reference types="vite/client" />
/// <reference types="chrome" />
interface ImportMetaEnv {
  readonly VITE_BMC_DASHBOARD_URL?: string;
  readonly VITE_BMC_WEB_URL?: string;
  readonly VITE_BMC_API_URL?: string;
  readonly VITE_BMC_EXTENSION_CLIENT_ID?: string;
  readonly VITE_EXTENSION_CALLBACK_URI?: string;
}
interface ImportMeta { readonly env: ImportMetaEnv }
