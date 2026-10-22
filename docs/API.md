# REST API reference

The plug-in serves a small REST API next to its MCP endpoint, on the same port
and with the same token, for programs that do not speak MCP. There is no
separate REST server to run: when Eclipse runs the plug-in, the API is up.
Both front ends call the same tool objects, so a REST call and an MCP
`tools/call` give the same result JSON.

## Running

### 1. Find the address and the token

The server listens on `127.0.0.1`, by default on port 8765 (Window >
Preferences > MCP Server). Every running Eclipse announces itself in
`~/.config/eclipse-mcp/instances/<port>.json`:

```json
{ "name": "eclipse-mcp", "version": "0.1.0.202610220721", "url": "http://127.0.0.1:8765",
  "mcp": "http://127.0.0.1:8765/mcp", "api": "http://127.0.0.1:8765/api", "port": 8765,
  "pid": 13758, "workspace": "/home/me/workspace", "started": "2026-10-22T07:21:35Z" }
```

The token is in `~/.config/eclipse-mcp/token`, readable only by you.

### 2. Check it is up

```bash
TOKEN=$(cat ~/.config/eclipse-mcp/token)
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8765/api/status
```

### 3. Call a tool

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"severity": "error", "limit": 5}' http://127.0.0.1:8765/api/tools/get_problems
```

A runnable script: [../examples/api_client.sh](../examples/api_client.sh).
From Java, the client library wraps these calls; see [LIBRARY.md](LIBRARY.md).

## Access

Every request needs `Authorization: Bearer <token>`; without it the answer is
401. The server only listens on the loopback interface, and it refuses
requests whose `Origin` header names a web page that is not on this computer,
or whose `Host` header is not `localhost`, `127.0.0.1` or `[::1]` (HTTP 403).
That keeps web pages from reaching it through DNS rebinding. Local pages, such
as the MCP Inspector, get CORS headers and may send `OPTIONS` preflights
without the token.

The token gives full control over Eclipse, including launching programs, so
treat it like a password. Set your own with the system property
`eclipse.mcp.token` or the environment variable `ECLIPSE_MCP_TOKEN` when Eclipse
starts.

## Endpoints

### `GET /api/status`

The server's description (as in the instance file), the number of tools, and
the result of `eclipse_status`:

```json
{ "name": "eclipse-mcp", "version": "0.1.0.202610220721", "url": "http://127.0.0.1:8765", "port": 8765,
  "pid": 13758, "workspace": "/home/me/workspace", "started": "...", "tools": 33,
  "eclipse": { "product": "Eclipse Platform (org.eclipse.platform.ide)", "platformVersion": "4.24.0.v20220607-0700",
               "java": "17.0.20.1", "workspace": "/home/me/workspace", "projects": 1,
               "windowTitle": "workspace - Eclipse Platform", "perspective": "Resource (org.eclipse.ui.resourcePerspective)",
               "editors": [ { "title": "order", "id": "org.eclipse.bpmn2.modeler.ui.bpmn2editor",
                              "input": "/demo/order.bpmn", "active": true } ],
               "shells": [ ... ], "runningJobs": [] } }
```

### `GET /api/tools`

The tool descriptions, as MCP `tools/list` returns them: `name`, `title`,
`description`, `inputSchema` and `annotations` (with `readOnlyHint`).

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8765/api/tools
```

### `GET /api/tools/{name}`

One tool's description, or 404.

### `POST /api/tools/{name}`

Calls the tool. The body is the arguments object; an empty body means no
arguments. The answer is the MCP `CallToolResult`:

```json
{ "content": [ { "type": "text", "text": "{\n  \"projects\": [ ... ]\n}" } ],
  "structuredContent": { "projects": [ { "name": "demo", "open": true, "errors": 0, "warnings": 2,
                                         "location": "/home/me/workspace/demo", "natures": [] } ] },
  "isError": false }
```

The screenshot tool adds an image item with the PNG in base64:

```json
{ "type": "image", "data": "iVBORw0KGgo...", "mimeType": "image/png" }
```

### `GET /api/` and `GET /api`

The same as `GET /api/status`.

## Status codes

| Status | When | Body |
|--------|------|------|
| 200 | the tool ran | the result |
| 400 | the body is not a JSON object | `{"error": "..."}` |
| 401 | missing or wrong token | `{"error": "..."}` |
| 403 | foreign `Origin` or `Host` | `{"error": "..."}` |
| 404 | unknown path or tool | `{"error": "..."}` |
| 405 | wrong method; `Allow` lists the right ones | `{"error": "..."}` |
| 422 | the tool reported an error, such as a wrong argument | the result with `isError: true` and `"error": "..."` |
| 500 | a bug in a tool, logged in Eclipse's Error Log | the result with `isError: true` and `"error": "..."` |

```bash
curl -s -w ' %{http_code}\n' -H "Authorization: Bearer $TOKEN" -d '{"severity": "loud"}' \
  http://127.0.0.1:8765/api/tools/get_problems
# {"content":[...],"isError":true,"error":"severity must be error, warning or info"} 422
```

## The MCP endpoint

`POST /mcp` takes MCP JSON-RPC messages (single or batch) and answers with
JSON; notifications get 202. `GET /mcp` answers 405, since the server offers no
SSE stream, and `DELETE /mcp` answers 200, since there are no sessions to end.
The supported protocol versions are 2025-06-18, 2025-03-26 and 2024-11-05.

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_projects","arguments":{}}}' \
  http://127.0.0.1:8765/mcp
```
