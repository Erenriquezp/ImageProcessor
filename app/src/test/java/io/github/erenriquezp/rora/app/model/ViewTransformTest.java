package io.github.erenriquezp.rora.app.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ViewTransformTest {

  private static final double EPS = 1e-9;

  private static ViewTransform fit(int w, int h, double dpi) {
    return ViewTransform.fitted(w, h, 1000, 600, dpi);
  }

  @Test
  void fitCentersAndLeavesTheMargin() {
    ViewTransform t = fit(4000, 2000, 1);
    assertThat(t.scale()).isCloseTo((1000 - 32) / 4000.0, within(EPS));
    assertThat(t.imageToScreen(2000, 1000).x()).isCloseTo(500, within(EPS));
    assertThat(t.imageToScreen(2000, 1000).y()).isCloseTo(300, within(EPS));
  }

  @Test
  void fitDoesNotEnlargeAPictureSmallerThanTheViewport() {
    assertThat(fit(100, 50, 1).zoomPercent()).isCloseTo(100, within(EPS));
  }

  @ParameterizedTest
  @ValueSource(doubles = {1, 1.25, 2})
  void hundredPercentIsOneImagePixelPerPhysicalPixel(double dpi) {
    ViewTransform t = fit(4000, 2000, dpi).actualSize();
    assertThat(t.zoomPercent()).isCloseTo(100, within(EPS));
    assertThat(t.scale() * dpi).isCloseTo(1, within(EPS));
  }

  @Test
  void zoomKeepsThePointUnderTheCursorFixed() {
    ViewTransform t = fit(4000, 2000, 1).actualSize();
    ViewTransform.Point before = t.screenToImage(321, 123);
    ViewTransform z = t.zoomAt(321, 123, 1.5);
    ViewTransform.Point after = z.screenToImage(321, 123);
    assertThat(after.x()).isCloseTo(before.x(), within(1e-6));
    assertThat(after.y()).isCloseTo(before.y(), within(1e-6));
  }

  @Test
  void imageAndScreenCoordinatesRoundTrip() {
    ViewTransform t = fit(4000, 2000, 1.25).zoomAt(300, 200, 3);
    ViewTransform.Point s = t.imageToScreen(1234.5, 678.25);
    ViewTransform.Point i = t.screenToImage(s.x(), s.y());
    assertThat(i.x()).isCloseTo(1234.5, within(1e-6));
    assertThat(i.y()).isCloseTo(678.25, within(1e-6));
  }

  @Test
  void zoomIsLimited() {
    ViewTransform t = fit(4000, 2000, 1);
    assertThat(t.zoomAt(0, 0, 1e9).zoomPercent()).isCloseTo(3200, within(EPS));
    assertThat(t.zoomAt(0, 0, 1e-9).scale()).isCloseTo(t.minScale(), within(EPS));
  }

  @Test
  void panCannotLeaveGapsWhenTheImageIsLarger() {
    ViewTransform t = fit(4000, 2000, 1).actualSize();
    assertThat(t.panBy(1e6, 0).tx()).isCloseTo(0, within(EPS));
    assertThat(t.panBy(-1e6, 0).tx()).isCloseTo(1000 - 4000, within(EPS));
  }

  @Test
  void anAxisWhereTheImageFitsStaysCentered() {
    ViewTransform t = fit(100, 50, 1).panBy(77, 88);
    assertThat(t.tx()).isCloseTo((1000 - 100) / 2.0, within(EPS));
    assertThat(t.ty()).isCloseTo((600 - 50) / 2.0, within(EPS));
  }

  @Test
  void aFittedViewRefitsWhenTheViewportChanges() {
    ViewTransform t = fit(4000, 2000, 1).withViewport(500, 400, 1);
    assertThat(t.fitted()).isTrue();
    assertThat(t.scale()).isCloseTo((500 - 32) / 4000.0, within(EPS));
  }

  @Test
  void aZoomedViewIsNoLongerFitted() {
    assertThat(fit(4000, 2000, 1).zoomAt(10, 10, 2).fitted()).isFalse();
  }

  @Test
  void aNewImageSizeRefits() {
    ViewTransform t = fit(4000, 2000, 1).zoomAt(10, 10, 4).withImage(1000, 1000);
    assertThat(t.fitted()).isTrue();
    assertThat(t.scale()).isCloseTo((600 - 32) / 1000.0, within(EPS));
  }

  @Test
  void zoomOverFitIsNeverBelowOne() {
    ViewTransform t = fit(4000, 2000, 1);
    assertThat(t.zoomOverFit()).isCloseTo(1, within(EPS));
    assertThat(t.zoomAt(500, 300, 2).zoomOverFit()).isCloseTo(2, within(1e-6));
  }

  @Test
  void realPixelsAreShownFromOneHundredPercent() {
    ViewTransform t = fit(4000, 2000, 1);
    assertThat(t.showsRealPixels()).isFalse();
    assertThat(t.actualSize().showsRealPixels()).isTrue();
  }
}
