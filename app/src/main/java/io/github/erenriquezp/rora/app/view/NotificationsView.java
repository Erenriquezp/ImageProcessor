package io.github.erenriquezp.rora.app.view;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.viewmodel.NotificationsViewModel;
import io.github.erenriquezp.rora.app.viewmodel.NotificationsViewModel.Kind;
import io.github.erenriquezp.rora.app.viewmodel.NotificationsViewModel.Notice;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/** Pila de avisos en la esquina superior derecha; no bloquea la imagen. */
public final class NotificationsView extends VBox {

  private final NotificationsViewModel<?> vm;
  private final Messages m;

  public NotificationsView(NotificationsViewModel<?> vm, Messages m) {
    this.vm = vm;
    this.m = m;
    getStyleClass().add("notice-stack");
    setPickOnBounds(false);
    setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    vm.notices().addListener((ListChangeListener<Notice>) c -> rebuild());
    rebuild();
  }

  private void rebuild() {
    getChildren().clear();
    for (Notice n : vm.notices()) {
      getChildren().add(card(n));
    }
  }

  private Node card(Notice n) {
    FontIcon icon =
        new FontIcon(
            switch (n.kind()) {
              case ERROR -> "fas-exclamation-triangle";
              case WARNING -> "fas-info-circle";
              case INFO -> "fas-check-circle";
            });
    icon.getStyleClass()
        .add(
            switch (n.kind()) {
              case ERROR -> "notice-icon-error";
              case WARNING -> "notice-icon-warning";
              case INFO -> "notice-icon-info";
            });
    Label title = new Label(n.message());
    title.getStyleClass().add("notice-title");
    HBox.setHgrow(title, Priority.ALWAYS);
    title.setMaxWidth(Double.MAX_VALUE);
    Button close = Icons.iconButton("fas-times", m.get("ui.action.close"), "stack-icon-button");
    close.setOnAction(e -> vm.dismiss(n));
    HBox top = new HBox(8, icon, title, close);
    top.setAlignment(Pos.TOP_LEFT);
    VBox card = new VBox(top);
    card.getStyleClass().add("notice");
    if (n.kind() == Kind.ERROR) {
      card.getStyleClass().add("notice-error");
    }
    if (n.action() != null) {
      Button action = new Button(n.action().label());
      action.getStyleClass().add("notice-link");
      action.setOnAction(e -> n.action().run().run());
      card.getChildren().add(new HBox(action));
    }
    if (!n.details().isEmpty()) {
      Label details = new Label(n.details());
      details.getStyleClass().add("notice-details");
      details.setVisible(false);
      details.setManaged(false);
      Button show = new Button(m.get("ui.action.details"));
      show.getStyleClass().add("notice-link");
      show.setOnAction(
          e -> {
            details.setVisible(!details.isVisible());
            details.setManaged(details.isVisible());
          });
      Button copy = new Button(m.get("ui.action.copy"));
      copy.getStyleClass().add("notice-link");
      copy.setOnAction(
          e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(n.message() + "\n" + n.details());
            Clipboard.getSystemClipboard().setContent(content);
          });
      card.getChildren().addAll(details, new HBox(show, copy));
    }
    return card;
  }
}
