import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// The React app always calls the backend on the same origin under /api.
// In development Vite forwards /api to the Spring Boot backend; in Docker nginx does the same.
const apiTarget = process.env.VITE_API_TARGET || "http://localhost:8080";
const proxy = { "/api": { target: apiTarget, changeOrigin: true } };

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    host: true,
    // GitHub Codespaces opens forwarded ports on *.app.github.dev
    allowedHosts: [".app.github.dev", "localhost"],
    proxy,
  },
  preview: { port: 4173, host: true, allowedHosts: [".app.github.dev", "localhost"], proxy },
});
