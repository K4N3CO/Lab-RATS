FROM node:20-alpine

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=10000

# Copy root-level dependency manifests
COPY package.json package-lock.json ./

# Install production dependencies and verify critical packages
RUN npm ci --omit=dev \
    && node -e "console.log('express-rate-limit:', require.resolve('express-rate-limit'))" \
    && npm cache clean --force

# Copy source code from the root build context
COPY --chown=node:node . .

# Prepare persistent upload directory
RUN mkdir -p /app/uploads \
    && ln -sf /app/uploads/devices.json /app/devices.json \
    && chown node:node /app/uploads

EXPOSE 10000

USER node

CMD ["node", "c2-server/server.js"]
