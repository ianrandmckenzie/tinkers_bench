#!/bin/bash

# Load environment variables
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
else
    echo -e "${RED}Error: .env file not found. Copy .env.example to .env and configure it.${NC}"
    exit 1
fi

# Configuration (defaults if not set in .env)
SERVER_IP="${SERVER_IP:-}"
SERVER_USER="${SERVER_USER:-root}"
REMOTE_PATH="${REMOTE_PATH:-/root/Server/mods/}"
JAR_NAME="${JAR_NAME:-tinkers-bench-1.0.3.jar}"
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

# 3. Deploy Plugin
if [ -z "$SERVER_IP" ]; then
    echo -e "${RED}Error: SERVER_IP is not set in .env${NC}"
    exit 1
fi

if [ ! -f "target/$JAR_NAME" ]; then
    echo -e "${RED}Jar not found at target/$JAR_NAME. Attempting auto-detect...${NC}"
    JAR_NAME=$(ls -1t target/*.jar 2>/dev/null | grep -vE "(sources|javadoc|original|tests)" | head -n 1 | xargs -n1 basename)
fi

if [ -z "$JAR_NAME" ] || [ ! -f "target/$JAR_NAME" ]; then
    echo -e "${RED}Error: Could not find a deployable jar in target/.${NC}"
    exit 1
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

