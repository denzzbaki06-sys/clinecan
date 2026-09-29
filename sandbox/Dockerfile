FROM node:24-bookworm-slim
WORKDIR /opt/toolchain
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund && npm cache clean --force
COPY build.sh export.mjs vite.config.mjs tsconfig.json ./
USER 1000:1000
WORKDIR /workspace
CMD ["sleep", "600"]
