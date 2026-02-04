#!/bin/bash

# Load environment variables
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
else
    echo "Error: .env file not found. Copy .env.example to .env and configure it."
    exit 1
fi

# Configuration (defaults if not set in .env)
SERVER_IP="${SERVER_IP:-}"
SERVER_USER="${SERVER_USER:-root}"
REMOTE_LOG_DIR="${REMOTE_LOG_DIR:-~/Server/logs}"

if [ -z "$SERVER_IP" ]; then
    echo "Error: SERVER_IP is not set in .env"
    exit 1
fi

LATEST_LOG=$(ssh "$SERVER_USER@$SERVER_IP" "ls -1t ${REMOTE_LOG_DIR}/*_server.log 2>/dev/null | head -n 1")

if [ -z "$LATEST_LOG" ]; then
    echo "Error: No log files found in ${REMOTE_LOG_DIR}"
    exit 1
fi

echo "Showing latest log: $LATEST_LOG"
ssh "$SERVER_USER@$SERVER_IP" "cat '$LATEST_LOG'"
