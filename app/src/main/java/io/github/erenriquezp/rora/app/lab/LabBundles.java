package io.github.erenriquezp.rora.app.lab;

import java.util.List;
import java.util.ServiceLoader;

/** Descubre los módulos de Laboratorio resueltos en esta ejecución. */
public final class LabBundles {

  private LabBundles() {}

  public static List<LabBundle> discover() {
    return ServiceLoader.load(LabBundle.class, LabBundle.class.getClassLoader()).stream()
        .map(ServiceLoader.Provider::get)
        .toList();
  }
}
