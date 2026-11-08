package io.github.erenriquezp.rora.core.op;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lista ordenada de parámetros de una operación. Inmutable. */
public final class ParamSchema {

  /** Esquema sin parámetros. */
  public static final ParamSchema EMPTY = new ParamSchema(List.of());

  private final List<ParamDef> params;
  private final Map<String, ParamDef> byKey;

  private ParamSchema(List<ParamDef> params) {
    this.params = List.copyOf(params);
    Map<String, ParamDef> m = new LinkedHashMap<>();
    Set<String> seen = new HashSet<>();
    for (ParamDef d : this.params) {
      if (!seen.add(d.key())) {
        throw new IllegalArgumentException("Parámetro repetido: " + d.key());
      }
      m.put(d.key(), d);
    }
    this.byKey = Map.copyOf(m);
  }

  public static ParamSchema of(ParamDef... params) {
    return params.length == 0 ? EMPTY : new ParamSchema(List.of(params));
  }

  public List<ParamDef> params() {
    return params;
  }

  public boolean isEmpty() {
    return params.isEmpty();
  }

  public Optional<ParamDef> find(String key) {
    return Optional.ofNullable(byKey.get(key));
  }

  public ParamDef get(String key) {
    ParamDef d = byKey.get(key);
    if (d == null) {
      throw new IllegalArgumentException("Parámetro desconocido: " + key);
    }
    return d;
  }

  /** Valores por defecto de todos los parámetros. */
  public Params defaults() {
    return Params.builder(this).build();
  }

  /**
   * Parámetros con los que la operación es la identidad: existe solo si <b>todos</b> los parámetros
   * declaran un valor neutro (un esquema vacío no tiene neutro).
   */
  public Optional<Params> neutral() {
    if (params.isEmpty()) {
      return Optional.empty();
    }
    Params.Builder b = Params.builder(this);
    for (ParamDef d : params) {
      Optional<Object> n = d.neutralValue();
      if (n.isEmpty()) {
        return Optional.empty();
      }
      b.set(d.key(), n.get());
    }
    return Optional.of(b.build());
  }

  @Override
  public String toString() {
    return "ParamSchema" + params.stream().map(ParamDef::key).toList();
  }
}
