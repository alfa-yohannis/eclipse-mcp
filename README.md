# Eclipse MCP

An Eclipse plug-in that lets AI assistants and other programs work in a running
Eclipse IDE: read the workspace and its problems, build, run commands and
menus, fill dialogs, type, click, take screenshots, launch programs, and edit
Graphiti diagrams such as BPMN2 models, with the palette and the mouse if you
want to watch. The plug-in is the engine; it runs inside Eclipse and serves
everything on one local port, protected by a token.

It comes in four forms, all backed by the same tools:

| Form     | For whom                                   | Reference                          |
|----------|--------------------------------------------|------------------------------------|
| MCP      | AI assistants (Claude Code, Codex, ...)    | [docs/MCP.md](docs/MCP.md)         |
| REST API | any program that speaks HTTP               | [docs/API.md](docs/API.md)         |
| Library  | Java programs and Eclipse plug-ins         | [docs/LIBRARY.md](docs/LIBRARY.md) |
| CLI      | the terminal and scripts                   | [docs/CLI.md](docs/CLI.md)         |

## Using it through an AI assistant

With the plug-in installed and Eclipse running, register the stdio launcher
with Claude Code:

```bash
claude mcp add eclipse -s user -- /path/to/eclipse-mcp/eclipse-mcp mcp
```

Then ask, for example: "Which projects in my workspace have errors?" or "In the
open BPMN diagram, add a task after the start event and connect them." Other
clients, the HTTP variant and permission advice are in
[docs/MCP.md](docs/MCP.md).

## Prerequisites

- Eclipse 2022-06 (4.24) or newer, running on Java 11 or newer
- Graphiti 0.18 or newer for the diagram tools (the BPMN2 Modeler brings it)
- an X11 session for screenshots and mouse and keyboard input; under Wayland
  start Eclipse with `GDK_BACKEND=x11`
- Java 11 or newer for the client, and Maven to build it as a library or to
  run its tests (the launcher falls back to `javac` without Maven)

## Install

### A. The plug-in

Build the two bundles against your Eclipse and copy them into its `dropins`
folder:

```bash
git clone https://github.com/alfa-yohannis/eclipse-mcp.git
cd eclipse-mcp
./build.sh /path/to/eclipse --install
```

Restart Eclipse. The menu bar now has an MCP menu, and the server starts on
`http://127.0.0.1:8765`. `./build.sh /path/to/eclipse` without `--install`
only builds the jars into `build/`:

- `io.github.alfayohannis.eclipse.mcp_<version>.jar`, the core: server, REST
  API, MCP endpoint, the 24 IDE tools, the MCP menu and the preference page;
- `io.github.alfayohannis.eclipse.mcp.graphiti_<version>.jar`, 9 diagram tools
  for any Graphiti editor. It is skipped when the Eclipse has no Graphiti, and
  the core works without it.

To work on the plug-in itself, import both folders as existing projects into an
Eclipse with PDE and launch an Eclipse Application.

### B. The client

```bash
./eclipse-mcp status       # the first run builds eclipse-mcp-client/target/*-all.jar
```

As a Maven dependency, install it once with `cd eclipse-mcp-client && mvn
install`; see [docs/LIBRARY.md](docs/LIBRARY.md).

## Running

### 1. MCP

Through the launcher, which finds the running Eclipse and reads the token
itself:

```bash
claude mcp add eclipse -s user -- /path/to/eclipse-mcp/eclipse-mcp mcp
```

Or straight to the plug-in over HTTP:

```bash
claude mcp add --transport http eclipse http://127.0.0.1:8765/mcp \
  --header "Authorization: Bearer $(cat ~/.config/eclipse-mcp/token)"
```

MCP > Server Status... in Eclipse shows both lines ready to paste, with Copy
Setup. To check the stdio server by hand, run `./eclipse-mcp mcp` and type a
JSON-RPC line such as `{"jsonrpc":"2.0","id":1,"method":"ping"}`.

### 2. REST API

The plug-in serves it next to MCP; there is nothing else to start:

```bash
TOKEN=$(cat ~/.config/eclipse-mcp/token)
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8765/api/status
curl -s -H "Authorization: Bearer $TOKEN" -d '{"severity": "error"}' \
  http://127.0.0.1:8765/api/tools/get_problems
```

Endpoints and status codes: [docs/API.md](docs/API.md).

### 3. Library

From any Java program, with the client library:

```java
EclipseClient eclipse = EclipseClient.connect();
System.out.println(eclipse.listProjects());
eclipse.screenshot("window", null, null, 0.5).save(Path.of("window.png"));
```

Inside Eclipse, plug-ins call the tools in-process and add their own through
the `io.github.alfayohannis.eclipse.mcp.tools` extension point:

```java
ToolResult result = ToolRegistry.getDefault().call("list_projects", new JsonObject());
```

Both are in [docs/LIBRARY.md](docs/LIBRARY.md), with
[examples/ClientExample.java](examples/ClientExample.java) and
[examples/ExampleToolProvider.java](examples/ExampleToolProvider.java).

### CLI

```bash
./eclipse-mcp projects
./eclipse-mcp problems --severity error
./eclipse-mcp click-menu "File > New > Project..."
./eclipse-mcp shells --widgets
./eclipse-mcp screenshot window.png --target window
./eclipse-mcp diagram
./eclipse-mcp diagram-create Task 300 200 --name "Check order" --mode mouse
./eclipse-mcp call <tool> key=value ...
```

All commands: [docs/CLI.md](docs/CLI.md), or `./eclipse-mcp help`.

## Settings

### The MCP menu

| Item | Does |
|------|------|
| Start Server | starts the server; enabled while it is stopped |
| Stop Server | stops it and removes its instance file; enabled while it runs; a request in progress still gets its answer |
| Restart Server | stops and starts it, which also applies a new port |
| Server Status... | shows whether it runs, the `/mcp` and `/api` addresses, the port, where the token comes from, the instance directory, and client setups ready to paste, with a Copy Setup button |

### Preferences

Window > Preferences > MCP Server, stored per workspace:

| Preference | Default | Meaning |
|------------|---------|---------|
| Start the server when Eclipse starts | on | off means the server waits for MCP > Start Server |
| Port | 8765 | the first port tried; when another Eclipse holds it, the next free one up to 8774 is used |

### Overrides

System properties (in `eclipse.ini` after `-vmargs`) or environment variables
win over the preferences:

| Property | Variable | Meaning |
|----------|----------|---------|
| `eclipse.mcp.port` | `ECLIPSE_MCP_PORT` | a fixed port; the server fails rather than move to another |
| `eclipse.mcp.token` | `ECLIPSE_MCP_TOKEN` | the token, instead of the token file |
| `eclipse.mcp.config` | `ECLIPSE_MCP_CONFIG` | the directory of the token and instance files |

### Files

All in `~/.config/eclipse-mcp`:

| File | Written by | Holds |
|------|------------|-------|
| `token` | the plug-in, on first start, readable only by you | the bearer token |
| `instances/<port>.json` | the plug-in, while its server runs | address, port, pid, workspace and start time of a running Eclipse; files of crashed instances are removed |
| `launcher` | `./eclipse-mcp` | the launcher's path, for the status dialog |
| `tools-cache.json` | `./eclipse-mcp mcp` | the last tool list, offered while Eclipse is not running |

The client picks the newest running instance, or the one `--port`,
`--workspace` or `--url` names. Several Eclipse instances can run at once, for
example an IDE and the Eclipse Application it launched for testing.

## Design

```
build.sh                                 builds the plug-in bundles against an Eclipse installation
eclipse-mcp                              launcher: builds the client on first use, then runs it
io.github.alfayohannis.eclipse.mcp/      the core plug-in, the engine
  src/.../mcp/api/                       public API: Tool, Schema, Args, ToolResult, ToolRegistry, ToolProvider
  src/.../mcp/internal/                  HTTP server, MCP protocol, REST API, tools, MCP menu, preferences
  schema/tools.exsd                      the extension point for contributed tools
io.github.alfayohannis.eclipse.mcp.graphiti/   diagram tools for Graphiti editors, contributed through the extension point
eclipse-mcp-client/                      Maven project: client library, CLI and stdio MCP server
  src/main/java/.../client/              EclipseClient, settings, instance discovery
  src/main/java/.../client/cli/          the CLI (Main and the command groups)
  src/main/java/.../client/mcp/          the stdio MCP server that forwards to the plug-in
  src/test/java/                         JUnit 5 tests against a fake plug-in server
docs/                                    MCP, REST API, CLI and library references
examples/                                Java client, tool provider, curl script, MCP client config, prompts
```

The plug-in keeps one `ToolRegistry`. The MCP endpoint (`/mcp`, JSON-RPC over
Streamable HTTP without sessions) and the REST API (`/api`) both call tools
through it, so they cannot drift apart, and contributed tools appear in both.
The server is a small HTTP/1.1 server on `127.0.0.1` that checks the token, the
`Origin` and the `Host` header on every request.

Tools run on server threads and move to the UI thread only for widget work, with
a time limit, so a busy Eclipse cannot hang a client. An action that may open a
modal dialog runs asynchronously: the tool waits `wait_ms`, then reports the
open shells while the dialog stays open, and other tools fill and close it.

Mouse input goes through the X server's XTest extension (via
`java.awt.Robot`), because GEF canvases and palettes ignore the synthetic GDK
events that SWT's `Display.post` produces on GTK; keys go through
`Display.post` to the shell, which the keyboard tools activate first.

The diagram tools read the business objects behind diagram elements through
EMF reflection (class name, `id`, `name`), so they need no compile-time
dependency on a modeler. In feature mode they run the editor's own Graphiti
features through its command stack; in mouse mode they find the palette tool's
figure and the canvas positions through GEF and draw2d and use the real mouse.

The client is a plain Java 11 library over the REST API. Its stdio MCP server
forwards JSON-RPC to the plug-in's `/mcp`, looking Eclipse up on every message,
and keeps answering when Eclipse is gone.

The code follows these rules: small classes with one responsibility, no
anonymous or local classes and no lambdas that hold logic (method references
and one-call lambdas only), shallow blocks, descriptive names, and a Javadoc
comment on every class and method that says what it does and why.

## Security and limits

- The server listens on `127.0.0.1` only and needs the token for every request.
  Anyone with the token controls Eclipse, including `launch` and `run_command`,
  which can run programs; treat it like a password.
- Requests from web pages on other origins and with foreign `Host` headers are
  refused, which blocks DNS rebinding.
- Screenshots and mouse and keyboard input need X11. Typed symbols assume a US
  keyboard layout; `set_text` and `type_text` with `method=insert` avoid the
  keyboard.
- The MCP endpoint answers with JSON only; it offers no SSE stream and no
  server-initiated messages. The stdio server adds `tools/list_changed`.
- A modal dialog blocks the action that opened it until it is closed, and some
  Eclipse operations ask questions (for example the BPMN2 Modeler about project
  natures on save). Tools report them as open shells.

## Testing

```bash
./eclipse-mcp test
```

28 JUnit 5 tests of the client run without Eclipse, against a fake plug-in
built on `com.sun.net.httpserver.HttpServer`: discovery, the typed methods,
error handling, the CLI's output and exit codes, and the stdio server with and
without Eclipse. The plug-in itself was tested against Eclipse 2022-06 with the
BPMN2 Modeler 1.5.4, in an Eclipse Application on a nested X display.

## License

MIT; see [LICENSE](LICENSE).
