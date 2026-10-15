# ---------- 构建层：Node 22 ----------
FROM node:22 AS build
WORKDIR /app

# 先拷依赖清单：命中层缓存，加速重复构建
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---------- 运行层：nginx 托管静态产物 + 反代 API ----------
FROM nginx:latest
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
