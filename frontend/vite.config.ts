import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./app"),
    },
  },
  server: {
    port: 3000,
    proxy: {
      "/__preview": { target: "http://127.0.0.1:4174" },
      "/api": {
        target: process.env.API_INTERNAL_URL || "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
