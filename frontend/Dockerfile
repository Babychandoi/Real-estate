# syntax=docker/dockerfile:1.7
FROM node:22.20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

FROM deps AS build
WORKDIR /app
COPY . .
ARG VITE_PUBLIC_API_BASE_URL=/api/v1
ENV VITE_PUBLIC_API_BASE_URL=$VITE_PUBLIC_API_BASE_URL
# CI builds the E2E image with the UI kit catalog (/__ui) for the a11y suite; production images leave it out.
ARG VITE_ENABLE_UI_CATALOG=false
ENV VITE_ENABLE_UI_CATALOG=$VITE_ENABLE_UI_CATALOG
ARG VITE_KYC_RETENTION_NOTICE
ENV VITE_KYC_RETENTION_NOTICE=$VITE_KYC_RETENTION_NOTICE
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Includes shared by every location (security headers, backend proxy headers), see nginx.conf.
COPY nginx/ /etc/nginx/bds/
# Canonical host of the HTTP->HTTPS redirect, rendered by the image's envsubst step at start (empty = request Host).
COPY nginx-templates/ /etc/nginx/templates/
COPY --chmod=755 docker-entrypoint.d/ /docker-entrypoint.d/
ENV BDS_PUBLIC_HOST=""
EXPOSE 3000
HEALTHCHECK --interval=20s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1:3000/healthz || exit 1
CMD ["nginx", "-g", "daemon off;"]
