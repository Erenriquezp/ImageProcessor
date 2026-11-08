package io.github.erenriquezp.rora.app.view;

import java.util.ArrayList;
import java.util.List;
import javafx.util.Subscription;

/** Suscripciones de una vista; cerrarlas al retirarla evita fugas de listeners. */
public final class Subscriptions {

  private final List<Subscription> items = new ArrayList<>();

  public void add(Subscription s) {
    items.add(s);
  }

  public void close() {
    items.forEach(Subscription::unsubscribe);
    items.clear();
  }
}
