package io.github.mikolajkakol.easymarkdown

val SampleDocument = OpenedDocument(
    title = "Easy Markdown",
    content = """
# Easy Markdown

A simple viewer for **Markdown** files with *Mermaid* diagrams.
Tap **Open** to view a `.md` file from your device.
Tap a table, a diagram or a code block to open it full screen.

## Text formatting

- **Bold**, *italic*, ~~strikethrough~~ and `inline code`
- [Links](https://mermaid.js.org)
- Nested lists
  1. First
  2. Second

> Blockquotes are supported too.

| Feature  | Status |
|----------|--------|
| Markdown | ✅     |
| Mermaid  | ✅     |

Wide tables scroll sideways and wrap long cells:

| Tool | Purpose | Notes | Platforms |
|------|:-------:|-------|-----------|
| **Compose Multiplatform** | Shared UI | Declarative UI framework from JetBrains, sharing one codebase across platforms. | Android, iOS, Desktop, Web |
| `mermaid.js` | Diagrams | Renders flowcharts, sequence diagrams, pie charts and more from text.<br>Bundled, works offline. | Any WebView |
| [Roborazzi](https://github.com/takahirom/roborazzi) | Snapshot tests | Captures Compose UI screenshots on the JVM with Robolectric. | JVM |

```kotlin
fun main() = println("Hello, Markdown!")
```

## Flowchart

```mermaid
flowchart LR
    A[Open file] --> B{Is markdown?}
    B -- Yes --> C[Render]
    B -- No --> D[Show as text]
    C --> E((Done))
```

## Sequence diagram

```mermaid
sequenceDiagram
    participant U as User
    participant A as App
    U->>A: Open document
    A->>A: Split markdown & mermaid
    A-->>U: Rendered view
```

## Pie chart

```mermaid
pie title Time spent
    "Writing" : 45
    "Diagrams" : 30
    "Reviewing" : 25
```

## ASCII diagrams

Code blocks don't wrap, so ASCII art keeps its shape – scroll sideways:

```
┌──────────────┐     ┌────────────────────┐     ┌──────────────────┐
│  notes.md    │ ──► │   Easy Markdown    │ ──► │  Rendered view   │
│  (any app)   │     │  split & render    │     │  tap to zoom     │
└──────────────┘     └─────────┬──────────┘     └──────────────────┘
                               │
                    ┌──────────┴──────────┐
                    ▼                     ▼
             ┌─────────────┐       ┌─────────────┐
             │  Markdown   │       │   Mermaid   │
             └─────────────┘       └─────────────┘
```
""".trimIndent(),
)
