package io.github.erenriquezp.rora.core.op;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Clave estructural encadenada de un resultado intermedio: fuente + nivel + prefijo de pasos (tal
 * como se ejecutan; en preview, con los parámetros en píxeles ya escalados). Inmutable; el hash se
 * calcula una vez al encadenar.
 */
public final class RenderKey {

  private final RenderKey parent;
  private final long sourceId;
  private final RenderLevel level;
  private final OpSpec step;
  private final int depth;
  private final int hash;

  private RenderKey(RenderKey parent, long sourceId, RenderLevel level, OpSpec step) {
    this.parent = parent;
    this.sourceId = sourceId;
    this.level = level;
    this.step = step;
    this.depth = parent == null ? 0 : parent.depth + 1;
    this.hash =
        parent == null
            ? 31 * Long.hashCode(sourceId) + level.hashCode()
            : 31 * parent.hash + step.hashCode();
  }

  /** Clave de la imagen de partida (la fuente en FULL o el proxy en PREVIEW). */
  public static RenderKey root(long sourceId, RenderLevel level) {
    return new RenderKey(null, sourceId, Objects.requireNonNull(level, "level"), null);
  }

  /** Clave del resultado de aplicar {@code step} al resultado de esta clave. */
  public RenderKey then(OpSpec step) {
    return new RenderKey(this, sourceId, level, Objects.requireNonNull(step, "step"));
  }

  public long sourceId() {
    return sourceId;
  }

  public RenderLevel level() {
    return level;
  }

  /** Número de pasos del prefijo. */
  public int depth() {
    return depth;
  }

  /** Clave sin el último paso, o {@code null} en la raíz. */
  public RenderKey parent() {
    return parent;
  }

  /** Último paso, o {@code null} en la raíz. */
  public OpSpec step() {
    return step;
  }

  /** Pasos del prefijo, en orden. */
  public List<OpSpec> steps() {
    List<OpSpec> l = new ArrayList<>(depth);
    for (RenderKey k = this; k.parent != null; k = k.parent) {
      l.add(k.step);
    }
    Collections.reverse(l);
    return l;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RenderKey other) || other.hash != hash || other.depth != depth) {
      return false;
    }
    RenderKey a = this;
    RenderKey b = other;
    while (a != null) {
      if (a == b) {
        return true;
      }
      if (a.hash != b.hash) {
        return false;
      }
      if (a.parent == null) {
        return a.sourceId == b.sourceId && a.level.equals(b.level);
      }
      if (!a.step.equals(b.step)) {
        return false;
      }
      a = a.parent;
      b = b.parent;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("RenderKey[src=").append(sourceId).append(", ");
    sb.append(level).append(", ");
    sb.append(steps().stream().map(OpSpec::canonical).toList()).append(']');
    return sb.toString();
  }
}
