#!/usr/bin/env bash
# Uses the plug-in's REST API from the command line with curl.
# Start Eclipse with the plug-in first; the script finds the newest running
# instance in ~/.config/eclipse-mcp/instances and reads the token file there.
#
#   examples/api_client.sh

set -euo pipefail

CONFIG="${ECLIPSE_MCP_CONFIG:-$HOME/.config/eclipse-mcp}"
TOKEN="${ECLIPSE_MCP_TOKEN:-$(cat "$CONFIG/token")}"
INSTANCE="$(ls -t "$CONFIG"/instances/*.json 2>/dev/null | head -1)"
[[ -n "$INSTANCE" ]] || { echo "No running Eclipse announces an MCP server in $CONFIG/instances" >&2; exit 3; }
API="$(sed -n 's/.*"api":"\([^"]*\)".*/\1/p' "$INSTANCE")"
AUTH="Authorization: Bearer $TOKEN"

echo "== server and Eclipse status ($API/status)"
curl -s -H "$AUTH" "$API/status" | head -c 600
echo

echo -e "\n== tool names"
curl -s -H "$AUTH" "$API/tools" | grep -o '"name":"[a-z_]*"' | sed 's/"name":"//; s/"$//' | paste -sd' '

echo -e "\n== projects"
curl -s -H "$AUTH" -H 'Content-Type: application/json' -d '{}' "$API/tools/list_projects"
echo

echo -e "\n== errors, at most 5"
curl -s -H "$AUTH" -H 'Content-Type: application/json' -d '{"severity": "error", "limit": 5}' \
	"$API/tools/get_problems"
echo

echo -e "\n== a tool error is HTTP 422 with an \"error\" field"
curl -s -o /dev/null -w '%{http_code}\n' -H "$AUTH" -d '{"severity": "loud"}' "$API/tools/get_problems"
