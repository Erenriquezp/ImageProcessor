package io.github.erenriquezp.rora.app.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Comprueba, leyendo {@code tokens.css}, el contraste AA del texto y de los bordes de control. */
class TokenContrastTest {

  private static final double AA_TEXT = 4.5;
  private static final double AA_COMPONENT = 3.0;
  private static final List<String> SURFACES =
      List.of("canvas", "bg", "surface-1", "surface-2", "surface-3");
  private static final List<String> TEXT =
      List.of("text", "text-2", "text-3", "accent", "danger", "warn", "ok");

  private static final Map<String, String> TOKENS = new HashMap<>();

  @BeforeAll
  static void read() throws Exception {
    try (InputStream in = Theme.class.getResourceAsStream("tokens.css")) {
      String css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Matcher m = Pattern.compile("-token-([a-z0-9-]+):\\s*(#[0-9a-fA-F]{6});").matcher(css);
      while (m.find()) {
        TOKENS.put(m.group(1), m.group(2));
      }
    }
  }

  private static double channel(int v) {
    double c = v / 255.0;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }

  private static double luminance(String hex) {
    int rgb = Integer.parseInt(hex.substring(1), 16);
    return 0.2126 * channel((rgb >> 16) & 0xFF)
        + 0.7152 * channel((rgb >> 8) & 0xFF)
        + 0.0722 * channel(rgb & 0xFF);
  }

  private static double contrast(String a, String b) {
    double x = luminance(TOKENS.get(a));
    double y = luminance(TOKENS.get(b));
    return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
  }

  @Test
  void everyTextTokenIsReadableOnEverySurface() {
    for (String text : TEXT) {
      for (String surface : SURFACES) {
        assertThat(contrast(text, surface))
            .as("%s sobre %s", text, surface)
            .isGreaterThanOrEqualTo(AA_TEXT);
      }
    }
  }

  @Test
  void selectedRowsAndPrimaryButtonsAreReadable() {
    assertThat(contrast("text", "select")).isGreaterThanOrEqualTo(AA_TEXT);
    assertThat(contrast("on-accent", "accent-fill")).isGreaterThanOrEqualTo(AA_TEXT);
  }

  @Test
  void controlBordersAreVisible() {
    assertThat(contrast("border-strong", "surface-1")).isGreaterThanOrEqualTo(AA_COMPONENT);
  }

  @Test
  void theCanvasIsNeutralGray() {
    String hex = TOKENS.get("canvas").substring(1);
    assertThat(hex.substring(0, 2)).isEqualTo(hex.substring(2, 4));
    assertThat(hex.substring(2, 4)).isEqualTo(hex.substring(4, 6));
  }
}
