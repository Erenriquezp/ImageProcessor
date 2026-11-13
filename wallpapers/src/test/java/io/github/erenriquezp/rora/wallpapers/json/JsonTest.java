package io.github.erenriquezp.rora.wallpapers.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {

  @Test
  void readsEveryValueKind() throws JsonException {
    Object v =
        Json.parse(
            "{\"s\":\"a\\\"b\\\\c\\n\\u00e9\",\"n\":-12.5e1,\"i\":0,\"t\":true,\"f\":false,"
                + "\"z\":null,\"l\":[1,[],{}]}");

    assertThat(v).isInstanceOf(Map.class);
    Map<?, ?> m = (Map<?, ?>) v;
    assertThat(List.<Object>copyOf(m.keySet())).containsExactly("s", "n", "i", "t", "f", "z", "l");
    assertThat(m.get("s")).isEqualTo("a\"b\\c\né");
    assertThat(m.get("n")).isEqualTo(new BigDecimal("-12.5e1"));
    assertThat(m.get("i")).isEqualTo(BigDecimal.ZERO);
    assertThat(m.get("t")).isEqualTo(Boolean.TRUE);
    assertThat(m.get("f")).isEqualTo(Boolean.FALSE);
    assertThat(m.get("z")).isSameAs(Json.NULL);
    assertThat((List<?>) m.get("l")).hasSize(3);
  }

  @Test
  void acceptsAByteOrderMarkAndSurroundingSpace() throws JsonException {
    assertThat(Json.parse("\uFEFF \n [ 1 , 2 ]\r\n"))
        .isEqualTo(List.of(BigDecimal.ONE, new BigDecimal(2)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "{",
        "{\"a\":1,}",
        "[1,]",
        "[01]",
        "[1.]",
        "[.5]",
        "[+1]",
        "[1e]",
        "{a:1}",
        "{'a':1}",
        "[\"\\x\"]",
        "[\"\\u12g4\"]",
        "[\"abc",
        "[\"a\tb\"]",
        "[tru]",
        "[NaN]",
        "[1] x",
        "{\"a\" 1}",
        "[1 2]"
      })
  void rejectsInvalidDocuments(String text) {
    assertThatThrownBy(() -> Json.parse(text)).isInstanceOf(JsonException.class);
  }

  @Test
  void rejectsDuplicateKeysWithTheirPosition() {
    assertThatThrownBy(() -> Json.parse("{\n  \"a\": 1,\n  \"a\": 2\n}"))
        .isInstanceOf(JsonException.class)
        .hasMessageContaining("duplicada")
        .satisfies(
            e -> {
              assertThat(((JsonException) e).line()).isEqualTo(3);
              assertThat(((JsonException) e).column()).isEqualTo(3);
            });
  }

  @Test
  void limitsTheNestingDepth() throws JsonException {
    String ok = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH);
    assertThat(Json.parse(ok)).isInstanceOf(List.class);

    String deep = "[".repeat(Json.MAX_DEPTH + 1) + "]".repeat(Json.MAX_DEPTH + 1);
    assertThatThrownBy(() -> Json.parse(deep)).hasMessageContaining("anidamiento");
  }

  @Test
  void rejectsOverlongNumbers() {
    assertThatThrownBy(() -> Json.parse("[" + "9".repeat(65) + "]")).hasMessageContaining("largo");
  }

  @Test
  void parsedContainersAreImmutable() throws JsonException {
    Map<?, ?> m = (Map<?, ?>) Json.parse("{\"a\":[1]}");
    assertThatThrownBy(() -> ((Map<Object, Object>) m).put("b", 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void writesAndReadsBack() throws JsonException {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", "Móvil \"grande\"\t\u0001");
    m.put("w", 1080);
    m.put("ratio", 0.05);
    m.put("big", 3_000_000_000L);
    m.put("ok", true);
    m.put("none", Json.NULL);
    m.put("list", List.of(1, Map.of(), List.of()));

    String text = Json.write(m);

    assertThat(text).endsWith("\n").contains("\\u0001").contains("\"ratio\": 0.05");
    Map<?, ?> back = (Map<?, ?>) Json.parse(text);
    assertThat(back.get("name")).isEqualTo(m.get("name"));
    assertThat(back.get("w")).isEqualTo(new BigDecimal(1080));
    assertThat(back.get("big")).isEqualTo(new BigDecimal(3_000_000_000L));
    assertThat(back.get("none")).isSameAs(Json.NULL);
    assertThat(Json.write(back)).isEqualTo(text);
  }

  @Test
  void refusesNonFiniteNumbersAndUnknownTypes() {
    assertThatThrownBy(() -> Json.write(List.of(Double.NaN)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Json.write(List.of(new Object())))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
