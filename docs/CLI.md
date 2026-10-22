# CLI reference

`./eclipse-mcp` is the command-line face of the client. It builds the client
jar on its first run (with Maven, or with plain `javac` when Maven is missing)
and then runs `java -jar` on it.

## Running

### 1. Build and check

Needs Java 11 or newer, and Eclipse running with the plug-in:

```bash
./eclipse-mcp status
```

```
Eclipse MCP 0.1.0.202610220721 at http://127.0.0.1:8765 (33 tools)
Eclipse 4.24.0.v20220607-0700, Java 17.0.20.1
Workspace /home/me/workspace with 1 projects
Window: workspace - demo/order.bpmn - Eclipse Platform
#0 workspace - demo/order.bpmn - Eclipse Platform [default]
```

### 2. Which Eclipse

Without options the client uses the newest running Eclipse that announces
itself in `~/.config/eclipse-mcp/instances`. Global options, before the
command, pick another one:

```bash
./eclipse-mcp --port 8766 status          # the instance on that port
./eclipse-mcp --workspace runtime status  # the newest whose workspace path contains "runtime"
./eclipse-mcp --url http://127.0.0.1:8765 status
./eclipse-mcp --json projects             # the plug-in's JSON as is, for jq
```

The environment variables `ECLIPSE_MCP_URL`, `ECLIPSE_MCP_PORT`,
`ECLIPSE_MCP_WORKSPACE`, `ECLIPSE_MCP_TOKEN` and `ECLIPSE_MCP_CONFIG` do the
same. `--timeout S` limits one call (default 600 s), and `--config DIR` reads
the token and instance files from another directory.

Exit codes: 0 success, 1 failure (the tool's message goes to stderr), 2 wrong
usage, 3 Eclipse not running.

## Commands

```bash
./eclipse-mcp help        # all commands with their arguments
```

Workspace and tools:

```bash
./eclipse-mcp status
./eclipse-mcp instances                      # running Eclipse instances
./eclipse-mcp tools                          # tool names; read-only ones are marked
./eclipse-mcp tools build                    # one tool's schema
./eclipse-mcp call get_problems severity=error limit=5    # any tool, key=value
./eclipse-mcp call open_file '{"path": "/demo/notes.txt", "line": 3}'
./eclipse-mcp projects
./eclipse-mcp problems --severity error --project demo
./eclipse-mcp refresh /demo
./eclipse-mcp build --kind clean
./eclipse-mcp open /demo/notes.txt --line 3
./eclipse-mcp select /demo/order.bpmn
```

`call` takes `key=value` words, whose values keep their JSON type (`limit=5` is
a number, `open=true` a boolean), or one JSON object.

Commands, menus and dialogs:

```bash
./eclipse-mcp commands save
./eclipse-mcp run org.eclipse.ui.file.saveAll
./eclipse-mcp run org.eclipse.ui.views.showView org.eclipse.ui.views.showView.viewId=org.eclipse.ui.console.ConsoleView
./eclipse-mcp menu "File > New"
./eclipse-mcp click-menu "File > New > Project..."
./eclipse-mcp shells --widgets
./eclipse-mcp tree "General > Project"
./eclipse-mcp press Next
./eclipse-mcp set-text demo
./eclipse-mcp press Finish
```

Screenshots, mouse and keyboard:

```bash
./eclipse-mcp screenshot window.png --target window --scale 0.5
./eclipse-mcp click 120 40 --shell 0
./eclipse-mcp drag 100 100 300 100
./eclipse-mcp type "Hello" --method keys
./eclipse-mcp keys ctrl+a delete
```

Launches:

```bash
./eclipse-mcp launches
./eclipse-mcp launch "My App" --mode debug
./eclipse-mcp terminate "My App"
```

Diagrams (with the Graphiti bundle and a diagram open):

```bash
./eclipse-mcp diagram
./eclipse-mcp diagram --palette
./eclipse-mcp diagram-create Task 300 200 --name "Check order"
./eclipse-mcp diagram-create "Exclusive Gateway" 450 200 --mode mouse
./eclipse-mcp diagram-connect Task_1 ExclusiveGateway_1
./eclipse-mcp diagram-move EndEvent_1 800 180 --mode mouse
./eclipse-mcp diagram-resize Task_1 140 60
./eclipse-mcp diagram-rename ExclusiveGateway_1 "Order valid?"
./eclipse-mcp diagram-set ExclusiveGateway_1 gatewayDirection Diverging
./eclipse-mcp diagram-delete Task_4
./eclipse-mcp diagram-select Task_1 Task_2
```

The MCP server:

```bash
./eclipse-mcp mcp         # JSON-RPC on stdin and stdout; Ctrl-D or Ctrl-C ends it
```

Repository tasks:

```bash
./eclipse-mcp test                                   # client unit tests (Maven)
./eclipse-mcp build-plugin /path/to/eclipse --install   # the same as ./build.sh
```

Without the launcher, the jar runs on its own:

```bash
java -jar eclipse-mcp-client/target/eclipse-mcp-client-0.1.0-all.jar status
```
