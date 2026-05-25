# Use the official Dendrite image as a base
FROM golang:1.23-alpine AS builder

# Install build dependencies
RUN apk add --no-cache git build-base

WORKDIR /build

# Copy dendrite source
COPY . .

# Build Dendrite
RUN go build -o /build/dendrite ./cmd/dendrite

# Runtime stage
FROM alpine:latest

RUN apk add --no-cache ca-certificates

WORKDIR /

COPY --from=builder /build/dendrite /dendrite

# Create required directories
RUN mkdir -p /etc/dendrite /var/dendrite/media /var/dendrite/jetstream /var/dendrite/searchindex

# Expose ports
EXPOSE 8008 8448

ENTRYPOINT ["/dendrite"]

