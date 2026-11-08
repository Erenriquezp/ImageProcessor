package io.github.erenriquezp.rora.app.viewmodel;

import java.util.List;

/**
 * Pregunta modal al usuario. La vista la muestra; cada opción sabe qué hacer.
 *
 * @param choices opciones, la última es la de cancelar
 */
public record Prompt(String title, String body, List<Choice> choices) {

  /** Una respuesta posible. */
  public record Choice(String label, boolean primary, Runnable action) {}

  public Prompt {
    choices = List.copyOf(choices);
  }
}
