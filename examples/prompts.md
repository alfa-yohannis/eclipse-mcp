# Prompts for an AI assistant

Ready to paste once the MCP server is registered (see [../docs/MCP.md](../docs/MCP.md)).

Workspace and builds:

```
What projects are in my Eclipse workspace, and which ones have errors?
```

```
Build the workspace and list the errors with their files and lines.
```

Dialogs and menus:

```
Create a new general project called "demo" through File > New > Project,
then take a screenshot of the Project Explorer.
```

```
Open Window > Preferences, find the MCP Server page and tell me which port it uses.
Cancel the dialog afterwards.
```

Diagrams (needs the Graphiti bundle and an open BPMN2 model):

```
In the open BPMN2 diagram, add a task "Check order" after the start event,
then an exclusive gateway "Order valid?" with two tasks, "Ship order" and
"Reject order", each followed by an end event. Connect everything with
sequence flows, save, and show me a screenshot.
```

```
Use mouse mode to drag the end event 200 pixels to the right, so I can see it move.
```
