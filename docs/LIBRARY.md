# Library reference

There are two Java APIs. The client library talks to a running Eclipse over
HTTP from any Java 11 program. The in-process API is for Eclipse plug-ins that
call the tools directly or add their own.

## The client library

### 1. Get it

Build and install it into your local Maven repository:

```bash
cd eclipse-mcp-client
mvn install
```

Then depend on it:

```xml
<dependency>
  <groupId>io.github.alfayohannis</groupId>
  <artifactId>eclipse-mcp-client</artifactId>
  <version>0.1.0</version>
</dependency>
```

Its only dependency is Gson. Without Maven, put
`eclipse-mcp-client/target/eclipse-mcp-client-0.1.0-all.jar`, which has Gson
inside, on the classpath.

### 2. Connect

```java
EclipseClient eclipse = EclipseClient.connect();   // the newest running Eclipse
```

`connect()` reads the instance files in `~/.config/eclipse-mcp/instances` and
the token file next to them. To choose, pass settings:

```java
EclipseClient eclipse = EclipseClient.connect(ClientSettings.builder()
        .port(8766)                      // or .url("http://127.0.0.1:8766"), or .workspace("runtime")
        .timeout(Duration.ofMinutes(2))
        .build());
```

The environment variables `ECLIPSE_MCP_URL`, `ECLIPSE_MCP_PORT`,
`ECLIPSE_MCP_WORKSPACE`, `ECLIPSE_MCP_TOKEN` and `ECLIPSE_MCP_CONFIG` apply when
the builder leaves a value unset.

### 3. Call tools

Every tool has a typed method that returns its structured result:

```java
JsonObject projects = eclipse.listProjects();
JsonObject errors = eclipse.getProblems("error", null, 10);       // null means the tool's default
eclipse.runCommand("org.eclipse.ui.file.saveAll", null, null);
eclipse.screenshot("window", null, null, 0.5).save(Path.of("window.png"));

JsonObject created = eclipse.diagramCreate("Task", 300, 200, "Check order", null, "feature");
String taskId = created.getAsJsonArray("created").get(0).getAsJsonObject().get("id").getAsString();
eclipse.diagramConnect("StartEvent_1", taskId, null, "mouse");
```

`call` reaches any tool, including tools other plug-ins contribute:

```java
ToolResult result = eclipse.call("count_files", Arguments.create().with("project", "demo").toJson());
System.out.println(result.structured());
```

`tools()` lists them with their schemas, and `serverStatus()` returns
`GET /api/status`.

### 4. Failures

All failures are unchecked and extend `EclipseMcpException`:

- `EclipseNotRunningException`: no instance file, or nothing answers on the
  port. Start Eclipse, or the server with MCP > Start Server.
- `ToolFailedException`: the tool reported an error. `result().text()` has its
  message, `status()` is 422 for a failure the caller can fix and 500 for a bug
  in the tool.
- `EclipseMcpException` itself: a wrong token, a timeout, an unexpected answer.

A complete program: [../examples/ClientExample.java](../examples/ClientExample.java).

## The in-process API

Plug-ins inside Eclipse use the package `io.github.alfayohannis.eclipse.mcp.api`
of the core bundle; require the bundle `io.github.alfayohannis.eclipse.mcp`,
which also re-exports Gson.

### Call tools

```java
ToolRegistry registry = ToolRegistry.getDefault();
ToolResult result = registry.call("list_projects", new JsonObject());
JsonObject projects = result.structuredContent();
```

`call` never throws for a failing tool: expected failures come back as error
results, and unexpected ones are logged in the Error Log and come back as
internal errors (`isInternalError()`). An unknown name throws
`NoSuchElementException`. Tools run on the calling thread and switch to the UI
thread themselves, so do not call them from the UI thread when they may open a
dialog.

### Add tools

A tool is a name, a description, an input schema and a handler:

```java
Tool tool = Tool.builder("count_files").title("Count files")
        .description("Counts the files of a workspace project.")
        .input(Schema.object().string("project", "Project name").required("project"))
        .readOnly(true)
        .handler(MyTools::countFiles)
        .build();
```

The handler receives `Args`, typed accessors over the arguments, and returns a
`ToolResult` (`text`, `json`, `error`, plus `addImage`). Throw `ToolException`
(or `IllegalArgumentException`) for a failure the caller should see as the
tool's error message.

Contribute tools through the extension point, so they appear without code that
runs at start-up:

```xml
<extension point="io.github.alfayohannis.eclipse.mcp.tools">
   <provider class="com.example.mcptools.ExampleToolProvider"/>
</extension>
```

The provider implements `ToolProvider` and returns its tools; see
[../examples/ExampleToolProvider.java](../examples/ExampleToolProvider.java).
The Graphiti bundle of this repository adds its diagram tools the same way.

Or register a tool while the plug-in runs, and remove it again:

```java
ToolRegistry.Registration registration = ToolRegistry.getDefault().register(tool);
// ...
registration.close();
```

The MCP endpoint and the REST API read the registry on every request, so a new
tool shows up in the next `tools/list`.
