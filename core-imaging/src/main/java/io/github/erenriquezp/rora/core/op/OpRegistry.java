package io.github.erenriquezp.rora.core.op;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

/**
 * Catálogo inmutable de operaciones, indexado por id e iterado en orden de id (determinista).
 *
 * <ul>
 *   <li>{@link #load()}: descubre las operaciones con {@link ServiceLoader} ({@code provides
 *       Operation with ...} en cualquier módulo de la capa de arranque).
 *   <li>{@link #of(Collection)}: lista explícita, sin {@code ServiceLoader} (tests y herramientas).
 * </ul>
 */
public final class OpRegistry {

  /** Formato de id: segmentos en minúscula separados por puntos, con guiones dentro. */
  public static final Pattern ID_PATTERN =
      Pattern.compile("[a-z][a-z0-9]*(\\.[a-z0-9]+(-[a-z0-9]+)*)+");

  private final Map<String, Operation> byId;

  private OpRegistry(Collection<? extends Operation> ops) {
    List<Operation> sorted = new ArrayList<>(ops);
    sorted.sort(Comparator.comparing(Operation::id));
    Map<String, Operation> m = new LinkedHashMap<>();
    for (Operation op : sorted) {
      String id = op.id();
      if (id == null || !ID_PATTERN.matcher(id).matches()) {
        throw new IllegalStateException(
            "Id de operación no válido: " + id + " (" + op.getClass().getName() + ")");
      }
      Operation prev = m.put(id, op);
      if (prev != null) {
        throw new IllegalStateException(
            "Id duplicado "
                + id
                + ": "
                + prev.getClass().getName()
                + " y "
                + op.getClass().getName());
      }
    }
    this.byId = m;
  }

  /** Operaciones publicadas con {@code provides Operation with ...}. */
  public static OpRegistry load() {
    List<Operation> ops = new ArrayList<>();
    ServiceLoader.load(Operation.class, OpRegistry.class.getClassLoader()).forEach(ops::add);
    return new OpRegistry(ops);
  }

  /** Registro con exactamente estas operaciones. */
  public static OpRegistry of(Collection<? extends Operation> ops) {
    return new OpRegistry(ops);
  }

  public Optional<Operation> find(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  /**
   * @throws IllegalArgumentException si no existe
   */
  public Operation require(String id) {
    Operation op = byId.get(id);
    if (op == null) {
      throw new IllegalArgumentException("Operación desconocida: " + id);
    }
    return op;
  }

  /** Todas las operaciones, ordenadas por id. */
  public List<Operation> all() {
    return List.copyOf(byId.values());
  }

  public List<String> ids() {
    return List.copyOf(byId.keySet());
  }

  public int size() {
    return byId.size();
  }
}
