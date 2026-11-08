package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.Scheduler;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Lo que comparten los ViewModels; todo inyectable, para probarlos sin toolkit ni tiempo real.
 *
 * @param publisher hilo de la interfaz
 * @param background trabajo sin receta (histograma, superposición)
 * @param io carga y guardado de archivos
 */
public record Env<F>(
    EditSession<F> session,
    Messages messages,
    Scheduler scheduler,
    Executor publisher,
    Executor background,
    Executor io,
    List<Catalog.Entry> labEntries) {

  /** Sin Laboratorio. */
  public Env(
      EditSession<F> session,
      Messages messages,
      Scheduler scheduler,
      Executor publisher,
      Executor background,
      Executor io) {
    this(session, messages, scheduler, publisher, background, io, List.of());
  }
}
