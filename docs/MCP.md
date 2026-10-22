# MCP reference

The plug-in lets an AI assistant work in a running Eclipse: read the workspace
and its problems, build, run commands and menus, fill dialogs, type, click,
take screenshots, launch programs and, with the Graphiti bundle, edit diagrams
such as BPMN2 models. Clients reach it in one of two ways:

- stdio through the launcher `./eclipse-mcp mcp`, which finds the running
  Eclipse and reads the token itself, so the token stays out of the client's
  configuration;
- Streamable HTTP straight to the plug-in at `http://127.0.0.1:8765/mcp`, with
  the token in an `Authorization: Bearer` header.

Both see the same tools, because the stdio server forwards every request to the
plug-in and takes the tool list from it at request time.

## Running

### 1. Install the plug-in

Build the bundles against your Eclipse and copy them into its `dropins`
folder, then restart Eclipse:

```bash
./build.sh /path/to/eclipse --install
```

The server starts with Eclipse (the MCP menu and the preference page control
that; see the README). Check it from a terminal:

```bash
./eclipse-mcp status
```

### 2. Register it with your client

For Claude Code, stdio through the launcher:

```bash
claude mcp add eclipse -s user -- /path/to/eclipse-mcp/eclipse-mcp mcp
claude mcp list                      # shows "✔ Connected"
```

Or HTTP straight to the plug-in:

```bash
claude mcp add --transport http eclipse http://127.0.0.1:8765/mcp \
  --header "Authorization: Bearer $(cat ~/.config/eclipse-mcp/token)"
```

The HTTP entry holds the token, and it names a fixed port; the stdio entry
works with whichever Eclipse runs, on any port. In Eclipse, MCP > Server
Status... shows both lines with the real path, port and token, and Copy Setup
copies them.

For Codex, in `~/.codex/config.toml`:

```toml
[mcp_servers.eclipse]
command = "/path/to/eclipse-mcp/eclipse-mcp"
args = ["mcp"]
```

For Antigravity, in `~/.gemini/config/mcp_config.json` or a workspace's
`.agents/mcp_config.json`:

```json
{
  "mcpServers": {
    "eclipse": {
      "command": "/path/to/eclipse-mcp/eclipse-mcp",
      "args": ["mcp"]
    }
  }
}
```

Other clients take the same command, or, if they speak Streamable HTTP, the
URL and the header; see [../examples/mcp_client.json](../examples/mcp_client.json).

### 3. Try it

```
What projects are in my Eclipse workspace, and which ones have errors?
```

More prompts in [../examples/prompts.md](../examples/prompts.md).

## After registering

Clients read their server list when they start, so open a new session (Claude
Code) or restart the client (Codex, Antigravity) before the tools appear.

When the stdio server starts while Eclipse is not running, it still connects:
it offers the tool list it saw last (or one placeholder tool), every call
answers that Eclipse is not running, and once Eclipse is up it tells the client
with `notifications/tools/list_changed` so the real list loads.

These tools only read and are safe to allow without asking: `eclipse_status`,
`list_projects`, `get_problems`, `list_commands`, `list_menu`, `list_shells`,
`list_tree_items`, `screenshot`, `list_launch_configurations` and
`diagram_info`. Their MCP annotations say so (`readOnlyHint`). The others
change the IDE or the workspace; `run_command`, `click_menu` and `launch` can do
anything Eclipse can, including running programs, so keep them on ask. In
Claude Code the permission names look like `mcp__eclipse__list_projects`.

## Tools

The core plug-in has 24 tools; the Graphiti bundle adds 9 diagram tools.
Every tool returns MCP content: a text item, plus `structuredContent` with the
same data as JSON for tools that return data. Expected failures, such as a
dialog that is not open, come back with `isError: true` and a message that says
what to do.

| Tool | Arguments | Does |
|------|-----------|------|
| `eclipse_status` | | versions, workspace, window, perspective, active part, editors, shells, running jobs |
| `list_projects` | | projects with location, natures, error and warning counts |
| `get_problems` | `severity?`, `project?`, `limit?` | problem markers, most severe first |
| `refresh` | `path?` | refreshes resources from disk |
| `build` | `project?`, `kind?`, `timeout_s?` | builds (incremental, full, clean) and waits |
| `open_file` | `path`, `line?`, `editor_id?`, `wait_ms?` | opens a workspace or file system path |
| `select_resource` | `path` | selects and reveals a resource in the Project Explorer |
| `list_commands` | `filter?`, `limit?` | command ids, names, categories, parameters |
| `run_command` | `id`, `parameters?`, `wait_ms?` | runs a command as its key binding would |
| `list_menu` | `path?`, `depth?` | the menu bar or a submenu |
| `click_menu` | `path`, `wait_ms?` | clicks a menu item by label path |
| `list_shells` | `shell?`, `widgets?`, `types?`, `limit?` | open shells, and the widgets of one |
| `press_button` | `label` or `widget`, `shell?`, `wait_ms?` | presses a button or tool bar item |
| `set_text` | `value`, `shell?`, `index?`, `widget?` | fills an input field and fires its events |
| `select_tree_item` | `path`, `shell?`, `view?`, `widget?`, `open?`, `wait_ms?` | selects (and opens) a tree or table item |
| `list_tree_items` | `path?`, `shell?`, `view?`, `widget?`, `depth?`, `limit?` | lists tree or table items |
| `screenshot` | `target?`, `shell?`, `widget?`, `scale?`, `file?`, `inline?` | PNG of a shell, the window, a widget or the screen |
| `mouse_click` | `x`, `y`, `shell?`, `widget?`, `button?`, `count?`, `modifiers?`, `wait_ms?` | real mouse click |
| `mouse_drag` | `from_x`, `from_y`, `to_x`, `to_y`, `shell?`, `widget?`, `button?`, `steps?`, `modifiers?`, `wait_ms?` | real drag and drop |
| `type_text` | `text`, `shell?`, `widget?`, `method?`, `wait_ms?` | types into the focused widget |
| `press_keys` | `keys`, `shell?`, `widget?`, `wait_ms?` | key combinations such as `ctrl+s` |
| `list_launch_configurations` | | launch configurations and launches with their processes |
| `launch` | `name`, `mode?`, `wait_ms?` | launches a configuration in run or debug mode |
| `terminate_launch` | `name` | terminates a configuration's launches |
| `diagram_info` | `editor?`, `palette?` | the open diagram: palette, shapes, connections, canvas bounds |
| `diagram_create` | `tool`, `x`, `y`, `container?`, `name?`, `width?`, `height?`, `mode?`, `editor?`, `wait_ms?` | creates a shape with a palette tool |
| `diagram_connect` | `source`, `target`, `tool?`, `mode?`, `editor?`, `wait_ms?` | connects two elements, Sequence Flow by default |
| `diagram_move` | `id`, `x`, `y`, `container?`, `mode?`, `editor?`, `wait_ms?` | moves a shape |
| `diagram_resize` | `id`, `width`, `height`, `x?`, `y?`, `editor?`, `wait_ms?` | resizes a shape |
| `diagram_delete` | `id`, `editor?`, `wait_ms?` | deletes an element without the confirmation dialog |
| `diagram_rename` | `id`, `name`, `editor?`, `wait_ms?` | renames an element |
| `diagram_set_property` | `id`, `attribute`, `value`, `editor?`, `wait_ms?` | sets an attribute of the element's business object |
| `diagram_select` | `ids`, `editor?` | selects elements in the editor |

`tools/list` gives each tool's full JSON Schema, and `./eclipse-mcp tools <name>`
prints it.

### Dialogs

A tool whose action opens a modal dialog (`run_command`, `click_menu`,
`press_button`, `open_file`, `launch`, `select_tree_item` with `open`,
`mouse_click`, `press_keys`) waits `wait_ms`, then answers while the dialog
stays open:

```json
{ "action": "click File > New > Project...", "finished": false,
  "note": "Still running, most likely because it opened a modal dialog. ...",
  "shells": [
    { "index": 0, "title": "ws - Eclipse Platform", "workbenchWindow": true, "bounds": { ... } },
    { "index": 1, "title": "New Project", "default": true, "modal": true,
      "defaultButton": "Finish (disabled)", "bounds": { ... } } ] }
```

Then `list_shells` with `widgets: true` reads the dialog, `set_text`,
`select_tree_item` and `press_button` fill it, and `press_button` with OK,
Finish or Cancel closes it. Without a `shell` argument the tools act on the
shell marked `default`: the innermost modal dialog, else the active window.

### Coordinates

`mouse_click` and `mouse_drag` take points relative to the top-left corner of
the shell, or of the widget when `widget` is given. A `screenshot` of that
shell or widget at scale 1 has the same origin, so a point read off the image
can be clicked as it is. Widget indexes come from `list_shells` and stay valid
until the dialog changes.

### Diagrams

The diagram tools work on any Graphiti editor; for the BPMN2 Modeler the
element ids are the BPMN ids, such as `Task_1` and `SequenceFlow_2`. Positions
are diagram coordinates, as `diagram_info` reports them. Each change reports
the elements it created, changed and removed:

```json
{ "created": [ { "id": "Task_2", "type": "Task", "name": "Ship order",
                 "bounds": { "x": 565, "y": 75, "width": 110, "height": 50 } } ],
  "changed": [], "removed": [], "dirty": true,
  "action": "create Task", "result": "Created Task; Set name of Task_2" }
```

`mode` picks how a change happens. `feature` (the default) runs the editor's
own Graphiti feature through its command stack, the same code its palette runs,
so Edit > Undo reverts it. `mouse` uses the real mouse: it clicks the palette
tool and the canvas, or drags the shape, which shows in screenshots and demos.
The BPMN2 Modeler places a new shape centred on the drop point and refuses some
connections by its own rules (for example a second incoming sequence flow on an
end event, by default); the tool reports such a refusal as an error.

## Notes for agents

- Start with `eclipse_status`: it shows the open editors and any dialog that
  waits for an answer.
- Prefer `run_command` with a command id over menus and keys; ids do not change
  with the language or the layout. `list_commands` finds them.
- After a tool reports `"finished": false`, a dialog is open. Read it and close
  it before doing anything else in the IDE.
- Saving a BPMN2 model in a plain project can ask whether to add the BPMN2
  project nature or validation; answer with `press_button`.
- Mouse and keyboard tools need X11. Under Wayland start Eclipse with
  `GDK_BACKEND=x11`.
