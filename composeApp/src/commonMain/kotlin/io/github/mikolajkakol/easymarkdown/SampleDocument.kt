package io.github.mikolajkakol.easymarkdown

val SampleDocument = OpenedDocument(
    title = "Easy Markdown",
    content = """
# Easy Markdown

A simple viewer for **Markdown** files with *Mermaid* diagrams.
Tap **Open** to view a `.md` file from your device.

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
""".trimIndent(),
)
