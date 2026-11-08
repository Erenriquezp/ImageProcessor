package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.model.ClippingOverlay;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.LatestWins;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.LiveHistogram;
import java.util.function.Function;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Histograma en vivo y superposición de recorte: se calculan del frame mostrado, sin render. */
public final class HistogramViewModel<F> implements AutoCloseable {

  private final Messages messages;
  private final LatestWins<Img, LiveHistogram> histogramWork;
  private final LatestWins<Img, F> overlayWork;
  private final ObjectProperty<LiveHistogram> data = new SimpleObjectProperty<>();
  private final ObjectProperty<F> overlay = new SimpleObjectProperty<>();
  private final BooleanProperty overlayEnabled = new SimpleBooleanProperty(false);
  private final BooleanProperty approximate = new SimpleBooleanProperty(false);
  private final StringProperty highlightsText = new SimpleStringProperty("");
  private final StringProperty shadowsText = new SimpleStringProperty("");
  private Img lastPixels;

  HistogramViewModel(Env<F> env, Function<Img, F> overlayConverter) {
    this.messages = env.messages();
    this.histogramWork =
        new LatestWins<>(
            "histogram", env.background(), env.publisher(), LiveHistogram::compute);
    this.overlayWork =
        new LatestWins<>(
            "overlay",
            env.background(),
            env.publisher(),
            (img, token) -> overlayConverter.apply(ClippingOverlay.render(img, token)));
    overlayEnabled.addListener((obs, was, on) -> refreshOverlay());
  }

  public ObjectProperty<LiveHistogram> dataProperty() {
    return data;
  }

  public ObjectProperty<F> overlayProperty() {
    return overlay;
  }

  public BooleanProperty overlayEnabledProperty() {
    return overlayEnabled;
  }

  public BooleanProperty approximateProperty() {
    return approximate;
  }

  public StringProperty highlightsTextProperty() {
    return highlightsText;
  }

  public StringProperty shadowsTextProperty() {
    return shadowsText;
  }

  void accept(EditSession.Frame<F> frame) {
    lastPixels = frame.pixels();
    approximate.set(frame.approximate());
    histogramWork.submit(frame.pixels(), this::publish, e -> {});
    refreshOverlay();
  }

  private void publish(LiveHistogram h) {
    data.set(h);
    highlightsText.set(messages.format("ui.histogram.highlights", round(h.highlightsPct())));
    shadowsText.set(messages.format("ui.histogram.shadows", round(h.shadowsPct())));
  }

  private static double round(double v) {
    return Math.round(v * 10) / 10.0;
  }

  private void refreshOverlay() {
    if (overlayEnabled.get() && lastPixels != null) {
      overlayWork.submit(lastPixels, overlay::set, e -> {});
    } else {
      overlayWork.cancelAll();
      overlay.set(null);
    }
  }

  void clear() {
    lastPixels = null;
    histogramWork.cancelAll();
    overlayWork.cancelAll();
    data.set(null);
    overlay.set(null);
    highlightsText.set("");
    shadowsText.set("");
  }

  @Override
  public void close() {
    histogramWork.close();
    overlayWork.close();
  }
}
