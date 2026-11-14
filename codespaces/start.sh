#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f workshop.pid ] && kill -0 "$(cat workshop.pid)" 2>/dev/null; then
  echo 'Workshop portal already running.'; exit 0
fi
if [ ! -f target/workshop-registration-portal-1.0.0.jar ]; then mvn -B verify; fi
export SESSION_COOKIE_SECURE=true
# The private Codespaces port proxy supplies the external HTTPS host/protocol.
# Trust forwarded headers only in this Codespaces launcher, not the JAR defaults.
nohup java -jar target/workshop-registration-portal-1.0.0.jar \
  --server.forward-headers-strategy=framework > workshop.log 2>&1 &
echo $! > workshop.pid
echo 'Open private forwarded port 8080 in the browser. No Docker Desktop is needed.'
