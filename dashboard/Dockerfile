# Build stage
FROM node:26-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Serve stage: static files plus a reverse proxy of /api to the hub, so the browser talks to one origin.
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
ENV HUB_UPSTREAM=hub:8080
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
