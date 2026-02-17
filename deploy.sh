#!/bin/bash

# Load environment variables
if [ -f .env ]; then
    # Load .env file while handling lines with spaces correctly
    while IFS= read -r line || [ -n "$line" ]; do
        # Skip comments and empty lines
        [[ "$line" =~ ^#.*$ ]] && continue
        [[ -z "$line" ]] && continue
        export "$line"
    done < .env
else
    echo -e "${RED}Error: .env file not found. Copy .env.example to .env and configure it.${NC}"
    exit 1
fi

# Configuration (defaults if not set in .env)
SERVER_IP="${SERVER_IP:-}"
SERVER_USER="${SERVER_USER:-root}"
REMOTE_PATH="${REMOTE_PATH:-/root/Server/mods/}"
LOCAL_PATH="${LOCAL_PATH:-}"
JAR_NAME="${JAR_NAME:-tinkers-bench-1.0.4.jar}"
HYTALE_JAR_PATH="${HYTALE_JAR_PATH:-../HytaleServer.jar}"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}Starting build and deployment for Tinkers Bench...${NC}"

# 1. Ensure HytaleServer.jar exists (pom uses systemPath)
if [ ! -f "$HYTALE_JAR_PATH" ]; then
    echo -e "${RED}Error: HytaleServer.jar not found at $HYTALE_JAR_PATH${NC}"
    exit 1
fi

# 2. Build the plugin
echo "Building the plugin..."
mvn clean package
if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

# 3. JAR Selection
if [ ! -f "target/$JAR_NAME" ]; then
    echo -e "${RED}Jar not found at target/$JAR_NAME. Attempting auto-detect...${NC}"
    JAR_NAME=$(ls -1t target/*.jar 2>/dev/null | grep -vE "(sources|javadoc|original|tests)" | head -n 1 | xargs -n1 basename)
fi

if [ -z "$JAR_NAME" ] || [ ! -f "target/$JAR_NAME" ]; then
    echo -e "${RED}Error: Could not find a deployable jar in target/.${NC}"
    exit 1
fi

# 4. Local Deployment
if [ -n "$LOCAL_PATH" ]; then
    echo "Deploying to local path: $LOCAL_PATH"
    mkdir -p "$LOCAL_PATH"
    cp "target/$JAR_NAME" "$LOCAL_PATH/"
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Local deployment successful!${NC}"
    else
        echo -e "${RED}Local deployment failed!${NC}"
    fi
fi

# 5. Remote Deployment
if [ -z "$SERVER_IP" ]; then
    if [ -z "$LOCAL_PATH" ]; then
        echo -e "${RED}Error: Neither SERVER_IP nor LOCAL_PATH is set in .env${NC}"
        exit 1
    else
        echo "Skipping remote deployment (SERVER_IP not set)."
        exit 0
    fi
fi

echo "Removing old versions on remote server..."
ssh "$SERVER_USER@$SERVER_IP" "rm -f ${REMOTE_PATH}tinkers-bench-*.jar"

echo "Deploying $JAR_NAME to $SERVER_IP..."
scp "target/$JAR_NAME" "$SERVER_USER@$SERVER_IP:$REMOTE_PATH"
if [ $? -ne 0 ]; then
    echo -e "${RED}Deployment failed!${NC}"
    exit 1
fi

echo -e "${GREEN}Deployment successful! Restarting server...${NC}"
bash ../restart_server.sh

