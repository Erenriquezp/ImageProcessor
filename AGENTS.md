# AGENTS.md — ImageProcessor

## Project Overview
Desktop image editor built with **Java 21 + JavaFX 21**, modeled after Adobe Lightroom/DaVinci Resolve. Uses a strict three-layer architecture: `app` (UI), `service` (pure logic), `domain` (types).

---

## Developer Commands

```powershell
# Compile
.\mvnw.cmd clean compile

# Run the application
.\mvnw.cmd javafx:run

# Run tests
.\mvnw.cmd test
```

> JVM warning suppression (`--enable-native-access=javafx.graphics`, `--sun-misc-unsafe-memory-access=allow`) is already configured in `pom.xml`. Do not add them manually elsewhere.

---

## Architecture

```
app/      → UI coordination, session state (ImageProcessorApp), UI components (ui/)
service/  → Pure stateless logic — all methods are static; no UI imports allowed
domain/   → Enums only: FilterType, ConvolutionKernel, GradientType, StretchMode
```

**Critical rule:** `app` talks to `service` exclusively through `ImageProcessor.java` (the public façade). Never call `ColorFilters`, `ArtisticFilters`, `ConvolutionFilters`, or `GradientGenerator` directly from UI code.

---

## Key Files

| File | Role |
|---|---|
| `service/ImageProcessor.java` | Public façade — only entry point from `app` to `service` |
| `app/ImageProcessorApp.java` | Session state (`originalImage`, `processedImage`), UI wiring, filter dispatch (`processFilter` switch) |
| `app/ui/EditorFilterPane.java` | Left panel; exposes typed getters (e.g., `getBrightnessValue()`) — no pixel logic |
| `app/ui/EditorPreviewPane.java` | Preview area: compare mode, independent zoom per panel |
| `app/ui/TopBar.java` | Toolbar; receives all actions as `Runnable` callbacks — no direct app reference |
| `domain/FilterType.java` | Enum with display label + category dot color (hex string) |
| `service/PixelMath.java` | Internal math utilities (`clamp`, `lerp`, `quantize`) — use these, don't reinvent |

---

## Non-Destructive Editing Pattern

`originalImage` is **never mutated**. All filters read from `originalImage` and return a new `BufferedImage`. Session state in `ImageProcessorApp`:

```java
private BufferedImage originalImage;   // never modified after load
private BufferedImage processedImage;  // replaced on each filter apply
```

`setSessionImages(image)` sets both to the same instance (on first load or gradient import).

---

## Adding a New Filter — Checklist

1. **`domain/FilterType.java`** — add enum constant with display label and category dot color:
   - Basic → `"#4a8fd6"`, Transparency → `"#4acc88"`, Retro → `"#e8913a"`, Convolution → `"#9a6adc"`
2. **`service/`** — implement the pure static method in the appropriate class (`ColorFilters`, `ArtisticFilters`, or `ConvolutionFilters`).
3. **`service/ImageProcessor.java`** — add a public static delegation method.
4. **`app/ui/EditorFilterPane.java`** — add UI controls (slider/spinner/color picker) and a typed getter method.
5. **`app/ImageProcessorApp.java`** — add a case to the `processFilter` switch statement.

---

## UI Wiring Pattern

`TopBar` and `GradientGeneratorPane` are wired via `Runnable`/`Consumer` callbacks injected at construction — they hold no reference to `ImageProcessorApp`:

```java
TopBar topBar = new TopBar(
    () -> openImage(stage),   // Runnable
    () -> saveImage(stage),
    this::resetImage,
    ...
);
```

---

## Styling

CSS is split across `src/main/resources/com/example/imageprocessor/`:
- `styles.css` — master import file (loads all partials from `css/`)
- `css/base.css`, `controls.css`, `panel-left.css`, `preview.css`, `toolbar.css`, `color-picker.css`

The dark theme color is `#17171a`. New sub-windows (e.g., `ColorPicker` dialogs) get the stylesheet injected automatically via the `Window.getWindows()` listener in `ImageProcessorApp.start()`.

---

## Module System

`module-info.java` lives at `src/main/java/`. Only `com.example.imageprocessor.app` is exported. When adding new public types needed by external modules, update `module-info.java` accordingly.

---

## Planned Features (from `PLAN.md`)

- **Filter pipeline / undo stack** — `Deque<BufferedImage>` in `ImageProcessorApp`, undo button in `TopBar`
- **Instant preview** — `boolean instantPreview` field on `FilterType` + `setOnParamChanged(Runnable)` in `EditorFilterPane`
- **Sepia** (`ArtisticFilters`, no params), **Temperature** slider (`ColorFilters`)

See `src/main/java/com/example/imageprocessor/PLAN.md` for full implementation specs.

