FROM node:20-alpine AS builder

WORKDIR /workspace/srmp-web-ui
ARG VITE_API_BASE_URL=
ARG VITE_TENANT_ID=default
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_TENANT_ID=${VITE_TENANT_ID}

COPY srmp-web-ui/package.json srmp-web-ui/package-lock.json ./
RUN npm ci

COPY srmp-web-ui ./
RUN npm run build

FROM nginx:1.25-alpine

COPY deploy/docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/srmp-web-ui/dist /usr/share/nginx/html

EXPOSE 80
