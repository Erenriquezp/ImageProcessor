package io.github.erenriquezp.rora.lab;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.imaging.SourceFormat;
import io.github.erenriquezp.rora.app.lab.LabBundle;
import io.github.erenriquezp.rora.app.lab.LabContext;
import io.github.erenriquezp.rora.app.lab.LabTool;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.lab.session.CurrentImage;
import io.github.erenriquezp.rora.lab.ui.BlendingPane;
import io.github.erenriquezp.rora.lab.ui.GradientGeneratorPane;
import io.github.erenriquezp.rora.lab.ui.ImageFileChooserFactory;
import io.github.erenriquezp.rora.lab.ui.TripleBlendingPane;
import io.github.erenriquezp.rora.service.LegacySteps;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import javafx.scene.Node;

/** Operaciones didácticas y herramientas del Laboratorio. */
public final class LegacyLabBundle implements LabBundle {

  private static final String LABELS = "io.github.erenriquezp.rora.lab.labels";

  /** Id de catálogo y operación de cada filtro didáctico. */
  private static final Map<String, String> FILTERS =
      Map.ofEntries(
          Map.entry("frosted", "legacy.frosted"),
          Map.entry("alpha-global", "legacy.alpha-global"),
          Map.entry("stretch4", "legacy.stretch4"),
          Map.entry("convolution", "legacy.convolution"),
          Map.entry("z-buffer", "legacy.z-buffer"),
          Map.entry("raster-grid", "legacy.raster-grid"),
          Map.entry("depth-interpolate", "legacy.depth-interpolate"),
          Map.entry("w-buffer", "legacy.w-buffer"),
          Map.entry("multisample", "legacy.multisample"),
          Map.entry("alpha-test", "legacy.alpha-test"),
          Map.entry("stencil-test", "legacy.stencil-test"),
          Map.entry("logic-op", "legacy.logic-op"),
          Map.entry("point-interpolate", "legacy.point-interpolate"),
          Map.entry("point-extrapolate", "legacy.point-extrapolate"),
          Map.entry("color-space", "legacy.color-space"));

  @Override
  public List<Operation> operations(SourceFormat.NativeSource nativeSource) {
    return LegacySteps.all(nativeSource);
  }

  @Override
  public List<Catalog.Entry> catalogEntries() {
    List<Catalog.Entry> entries = new ArrayList<>();
    FILTERS.keySet().stream()
        .sorted()
        .forEach(
            id ->
                entries.add(
                    new Catalog.Entry(
                        id,
                        FILTERS.get(id),
                        Catalog.Category.LAB,
                        "fas-flask",
                        Map.of(),
                        Set.of())));
    return entries;
  }

  @Override
  public ResourceBundle labels(Locale locale) {
    return ResourceBundle.getBundle(LABELS, Locale.of("es"));
  }

  @Override
  public List<LabTool> tools() {
    return List.of(
        new Tool<>(
            "Generador",
            this::gradient,
            GradientGeneratorPane::getView,
            GradientGeneratorPane::close),
        new Tool<>("Blending", this::blending, BlendingPane::getView, BlendingPane::close),
        new Tool<>(
            "Triple Blending",
            this::triple,
            TripleBlendingPane::getView,
            TripleBlendingPane::close));
  }

  private GradientGeneratorPane gradient(LabContext c) {
    return new GradientGeneratorPane(c::useImage, c::status);
  }

  private static CurrentImage current(LabContext c) {
    return new CurrentImage() {
      @Override
      public boolean isAvailable() {
        return c.hasImage();
      }

      @Override
      public void fetch(
          java.util.function.Consumer<java.awt.image.BufferedImage> onImage,
          java.util.function.Consumer<io.github.erenriquezp.rora.app.session.RenderFailure>
              onFailed) {
        c.currentImage(onImage, onFailed);
      }
    };
  }

  private static java.io.File choose(LabContext c) {
    return ImageFileChooserFactory.createOpenImageChooser().showOpenDialog(c.owner());
  }

  private BlendingPane blending(LabContext c) {
    return new BlendingPane(current(c), () -> choose(c), c::useImage, c::status);
  }

  private TripleBlendingPane triple(LabContext c) {
    return new TripleBlendingPane(
        current(c), () -> choose(c), () -> choose(c), c::useImage, c::status);
  }

  /** Crea el panel la primera vez que se abre la ventana. */
  private static final class Tool<T> implements LabTool {
    private final String title;
    private final Function<LabContext, T> factory;
    private final Function<T, Node> view;
    private final java.util.function.Consumer<T> closer;
    private T pane;

    Tool(
        String title,
        Function<LabContext, T> factory,
        Function<T, Node> view,
        java.util.function.Consumer<T> closer) {
      this.title = title;
      this.factory = factory;
      this.view = view;
      this.closer = closer;
    }

    @Override
    public String title() {
      return title;
    }

    @Override
    public Node view(LabContext context) {
      pane = factory.apply(context);
      return view.apply(pane);
    }

    @Override
    public void close() {
      if (pane != null) {
        closer.accept(pane);
        pane = null;
      }
    }
  }
}
