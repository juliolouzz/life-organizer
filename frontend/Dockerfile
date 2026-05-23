# Multi-stage build for the Angular frontend.
# Stage 1: install + ng build with Node 20.
# Stage 2: serve the static dist/ with nginx, proxying /api to the backend container.

# ---- build stage ----
FROM node:20-alpine AS build
WORKDIR /workspace

# Cache deps in their own layer.
COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps --no-audit --no-fund

COPY . .
RUN npm run build

# ---- runtime stage ----
FROM nginx:1.27-alpine AS runtime

# Replace the default site with our SPA + API-proxy config.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# The Angular CLI puts the built site under dist/frontend/browser in v17.
COPY --from=build /workspace/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -q --spider http://localhost/ || exit 1
