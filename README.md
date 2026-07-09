# 📄 Notepact

A modern, professional desktop text editor built with **Java 21 + JavaFX 21 + RichTextFX**.

---

## ✨ Features

| Category | Features |
|---|---|
| **Editor** | Syntax highlighting (10 languages), line numbers, active line highlight, word wrap, zoom |
| **File** | Open/Save/Save As, recent files (50), encoding selection, large file support |
| **Tabs** | Multi-tab, drag reorder, pin, duplicate, close all |
| **Search** | Find/Replace with Regex, case-sensitive, whole-word, match counter |
| **Line Ops** | Duplicate, delete, move up/down, copy up/down, join, sort, remove empty/duplicates |
| **Case** | UPPERCASE, lowercase, Title Case, Sentence, camelCase, PascalCase, snake_case, kebab-case |
| **Themes** | Dark (VS Code), Light, Midnight, Solarized, Monokai |
| **Auto-Save** | Configurable interval (10s–1h) |
| **Backup** | Timestamped backups, last 20 versions, configurable interval |
| **Session** | Restore open tabs on next launch |
| **Bookmarks** | Toggle (Ctrl+F2), Navigate (F2/Shift+F2), persisted to SQLite |
| **Snippets** | Built-in templates, insert with double-click |
| **Clipboard** | 100-entry history with preview |
| **Export** | TXT, HTML, PDF, Markdown |
| **Statistics** | Characters, words, lines, paragraphs, reading time |
| **Date/Time** | Insert in 7 formats (F5) |

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| UI | JavaFX 21 |
| Editor | RichTextFX 0.11.3 |
| Build | Maven |
| Database | SQLite (xerial/sqlite-jdbc 3.45) |
| Settings | Jackson JSON |
| PDF Export | Apache PDFBox 3.0 |

---

## 🚀 Getting Started

### Prerequisites

- **JDK 21** (or higher)
- **Maven 3.9+**
- Internet connection for first build (downloads dependencies)

### Build & Run

```bash
# Clone / navigate to project folder
cd Notepact

# Run directly (recommended for development)
mvn javafx:run

# Or build a fat JAR first, then run
mvn clean package
java -jar target/notepact-1.0.0.jar
```

### Build on Windows

```powershell
# In the project root
mvn clean javafx:run
```

---

## ⌨️ Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+N` | New File |
| `Ctrl+O` | Open File |
| `Ctrl+S` | Save |
| `Ctrl+Shift+S` | Save As |
| `Ctrl+W` | Close Tab |
| `Ctrl+Z / Ctrl+Y` | Undo / Redo |
| `Ctrl+F` | Find |
| `Ctrl+H` | Replace |
| `F3 / Shift+F3` | Find Next / Previous |
| `F5` | Insert Date/Time |
| `Ctrl+D` | Duplicate Line |
| `Ctrl+L` | Delete Line |
| `Ctrl+J` | Join Lines |
| `Alt+Up/Down` | Move Line Up/Down |
| `Ctrl+F2` | Toggle Bookmark |
| `F2 / Shift+F2` | Next/Prev Bookmark |
| `Ctrl+= / Ctrl+-` | Zoom In/Out |
| `Ctrl+0` | Reset Zoom |
| `Ctrl+,` | Settings |

---

## 📁 Project Structure

```
Notepact/
├── pom.xml
└── src/main/java/com/notepact/
    ├── Main.java              ← Launcher
    ├── App.java               ← JavaFX Application
    ├── controller/
    │   ├── MainController.java    ← Full UI (700+ lines)
    │   ├── FindReplaceBar.java    ← Find/Replace widget
    │   └── SettingsDialog.java    ← Settings panel
    ├── editor/
    │   ├── EditorPane.java        ← CodeArea wrapper
    │   ├── SyntaxHighlighter.java ← Multi-language regex highlighter
    │   ├── AutoSaveManager.java
    │   └── BookmarkManager.java
    ├── model/
    │   ├── AppSettings.java
    │   ├── DocumentModel.java
    │   ├── BookmarkModel.java
    │   ├── SnippetModel.java
    │   └── Theme.java
    ├── file/
    │   ├── FileManager.java       ← Async I/O
    │   ├── BackupManager.java
    │   ├── RecentFilesManager.java
    │   └── SessionManager.java
    ├── search/
    │   ├── SearchEngine.java
    │   └── SearchResult.java
    ├── export/
    │   └── ExportManager.java     ← TXT/HTML/MD/PDF
    ├── db/
    │   └── DatabaseManager.java   ← SQLite
    ├── settings/
    │   └── SettingsManager.java
    ├── theme/
    │   └── ThemeManager.java
    └── util/
        ├── TextUtils.java         ← 20+ text transformations
        └── ClipboardHistory.java

src/main/resources/com/notepact/css/
    ├── base.css      ← Shared layout
    ├── syntax.css    ← Syntax highlight colors
    ├── dark.css      ← VS Code Dark+
    ├── light.css     ← Clean Light
    ├── midnight.css  ← Abyss/GitHub Dark
    ├── solarized.css ← Solarized Dark
    └── monokai.css   ← Monokai
```

---

## 📦 Data Storage

All persistent data is stored in `~/.notepact/`:

| File/Folder | Purpose |
|---|---|
| `notepact.db` | SQLite: bookmarks, snippets, clipboard, recent files, sessions |
| `settings.json` | JSON: all user preferences |
| `backups/<filename>/` | Timestamped backup files (`.bak`) |

---

## 🧪 Development Notes

- **Syntax highlighting** runs on a background thread with 100ms debounce to prevent UI jank
- **File I/O** is async using a cached thread pool; files are written atomically via temp-file-then-move
- **Backups** are triggered both on save and on a separate configurable timer
- **Sessions** are saved to SQLite on window close; tabs are restored on next launch
- **Theme switching** is instant via CSS stylesheet hot-swap on the Scene
