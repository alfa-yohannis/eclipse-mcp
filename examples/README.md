# Examples

Runnable examples for each form. They need Eclipse running with the plug-in.

| File | Form | Shows |
|------|------|-------|
| `prompts.md` | MCP | prompts to paste into an AI assistant |
| `mcp_client.json` | MCP | server entries for clients other than Claude Code |
| `api_client.sh` | REST API | the REST API from `curl` |
| `ClientExample.java` | Library | the client library from a Java program |
| `ExampleToolProvider.java` | Library | a tool contributed by another Eclipse plug-in |

## 1. MCP

```bash
claude mcp add eclipse -s user -- /path/to/eclipse-mcp/eclipse-mcp mcp
```

Then ask your assistant, for example:

> What projects are in my Eclipse workspace, and which ones have errors?

More in [prompts.md](prompts.md). Other clients: [mcp_client.json](mcp_client.json).

## 2. REST API

```bash
examples/api_client.sh
```

Prints the server status, the tool names, the projects and the errors, and
shows that a tool error is HTTP 422.

## 3. Library

```bash
./eclipse-mcp status     # builds the client on first run
java -cp eclipse-mcp-client/target/eclipse-mcp-client-0.1.0-all.jar examples/ClientExample.java
```

Prints the projects and the errors, and saves `eclipse-window.png`.

`ExampleToolProvider.java` belongs in a plug-in that requires
`io.github.alfayohannis.eclipse.mcp`; its Javadoc shows the `plugin.xml`
extension that registers it.

## CLI

```bash
./eclipse-mcp projects
./eclipse-mcp problems --severity error
./eclipse-mcp screenshot window.png --target window
```
