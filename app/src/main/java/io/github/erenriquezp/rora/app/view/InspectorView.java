package io.github.erenriquezp.rora.app.view;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.viewmodel.BasicAdjustViewModel;
import io.github.erenriquezp.rora.app.viewmodel.ControlState;
import io.github.erenriquezp.rora.app.viewmodel.StackViewModel;
import io.github.erenriquezp.rora.app.viewmodel.StudioViewModel;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.css.PseudoClass;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/** Inspector derecho: histograma fijo, zona contextual y pila de ajustes. */
public final class InspectorView extends VBox {

  private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
  private static final PseudoClass OFF = PseudoClass.getPseudoClass("off");
  private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");

  private final StudioViewModel<?> vm;
  private final Messages m;
  private final Subscriptions subs;
  private final SplitPane split;

  public InspectorView(StudioViewModel<?> vm, Subscriptions subs) {
    this.vm = vm;
    this.m = vm.messages();
    this.subs = subs;
    getStyleClass().add("inspector");
    HistogramView histogram = new HistogramView(vm.histogram(), m, subs);

    VBox basic = basicPanel(vm.basic());
    VBox params = paramsPanel();
    StackPane context = new StackPane(basic, params);
    ScrollPane scroll = new ScrollPane(context);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.getStyleClass().add("context-scroll");
    subs.add(
        vm.stack()
            .selectedProperty()
            .subscribe(
                selected -> {
                  boolean step = selected.intValue() >= 0;
                  params.setVisible(step);
                  params.setManaged(step);
                  basic.setVisible(!step);
                  basic.setManaged(!step);
                }));

    Label empty = new Label(m.get("ui.empty.inspector"));
    empty.getStyleClass().add("hint");
    empty.setWrapText(true);
    VBox emptyBox = new VBox(empty);
    emptyBox.getStyleClass().add("context-body");

    split = new SplitPane(scroll, stackPanel());
    split.setOrientation(Orientation.VERTICAL);
    split.setDividerPositions(0.68);
    VBox.setVgrow(split, Priority.ALWAYS);
    VBox.setVgrow(emptyBox, Priority.ALWAYS);

    getChildren().addAll(histogram, split, emptyBox);
    subs.add(
        vm.hasImageProperty()
            .subscribe(
                has -> {
                  split.setVisible(has);
                  split.setManaged(has);
                  emptyBox.setVisible(!has);
                  emptyBox.setManaged(!has);
                }));
  }

  public SplitPane split() {
    return split;
  }

  private VBox basicPanel(BasicAdjustViewModel<?> basic) {
    VBox body = new VBox();
    body.getStyleClass().add("context-body");
    for (BasicAdjustViewModel.Group g : basic.groups()) {
      VBox rows = new VBox();
      for (ControlState c : g.controls()) {
        rows.getChildren().add(Controls.of(c, m, subs));
      }
      TitledPane pane = new TitledPane(m.get(g.titleKey()), rows);
      pane.setAnimated(false);
      pane.setExpanded(!g.titleKey().equals("ui.group.detail"));
      body.getChildren().add(pane);
    }
    return body;
  }

  private VBox paramsPanel() {
    Button back = new Button(m.get("ui.action.back"), Icons.icon("fas-arrow-left"));
    back.getStyleClass().add("back-link");
    back.setOnAction(e -> vm.stack().clearSelection());
    Label title = new Label();
    title.getStyleClass().add("panel-title");
    subs.add(vm.params().titleProperty().subscribe(title::setText));
    Label none = new Label(m.get("ui.params.none"));
    none.getStyleClass().add("hint");
    VBox controls = new VBox();
    Subscriptions[] controlSubs = {new Subscriptions()};
    Runnable rebuild =
        () -> {
          controlSubs[0].close();
          controlSubs[0] = new Subscriptions();
          controls.getChildren().clear();
          for (ControlState c : vm.params().controls()) {
            controls.getChildren().add(Controls.of(c, m, controlSubs[0]));
          }
          none.setVisible(vm.params().controls().isEmpty());
          none.setManaged(vm.params().controls().isEmpty());
        };
    vm.params().controls().addListener((ListChangeListener<ControlState>) c -> rebuild.run());
    rebuild.run();
    VBox body = new VBox(8, back, title, none, controls);
    body.getStyleClass().add("context-body");
    return body;
  }

  private VBox stackPanel() {
    StackViewModel<?> stack = vm.stack();
    Label header = new Label();
    header.getStyleClass().add("stack-header");
    subs.add(stack.headerProperty().subscribe(header::setText));
    Label empty = new Label(m.get("ui.stack.empty"));
    empty.getStyleClass().add("stack-empty");

    ListView<StackViewModel.Row> list = new ListView<>(stack.rows());
    list.getStyleClass().add("stack-list");
    list.setCellFactory(v -> new RowCell(stack));
    list.setFixedCellSize(34);
    list.setFocusTraversable(true);
    list.setAccessibleText(header.getText());
    VBox.setVgrow(list, Priority.ALWAYS);
    subs.add(
        stack
            .selectedProperty()
            .subscribe(
                s -> {
                  list.refresh();
                  if (s.intValue() >= 0) {
                    list.scrollTo(s.intValue());
                  }
                }));
    list.addEventFilter(KeyEvent.KEY_PRESSED, e -> onListKey(e, stack));

    empty.visibleProperty().bind(Bindings.isEmpty(stack.rows()));
    empty.managedProperty().bind(empty.visibleProperty());
    list.visibleProperty().bind(Bindings.isNotEmpty(stack.rows()));
    list.managedProperty().bind(list.visibleProperty());

    VBox box = new VBox(header, empty, list);
    box.getStyleClass().add("stack-box");
    box.setMinHeight(120);
    return box;
  }

  private static void onListKey(KeyEvent e, StackViewModel<?> stack) {
    int n = stack.rows().size();
    int s = stack.selectedProperty().get();
    switch (e.getCode()) {
      case UP -> {
        stack.select(Math.max(0, s - 1));
        e.consume();
      }
      case DOWN -> {
        stack.select(Math.min(n - 1, s + 1));
        e.consume();
      }
      case SPACE -> {
        if (s >= 0) {
          stack.toggle(s);
        }
        e.consume();
      }
      case DELETE -> {
        if (s >= 0) {
          stack.remove(s);
        }
        e.consume();
      }
      default -> {}
    }
  }

  /** Fila de la pila: asa, interruptor, nombre, resumen y papelera. */
  private final class RowCell extends ListCell<StackViewModel.Row> {
    private final StackViewModel<?> stack;
    private final FontIcon grip = new FontIcon("fas-grip-vertical");
    private final Button toggle = Icons.iconButton("fas-eye", m.get("ui.action.toggle"), "stack-icon-button");
    private final Label name = new Label();
    private final Label summary = new Label();
    private final Button delete = Icons.iconButton("fas-trash-alt", m.get("ui.action.delete"), "stack-icon-button");
    private final HBox row;

    RowCell(StackViewModel<?> stack) {
      this.stack = stack;
      grip.getStyleClass().add("stack-grip");
      name.getStyleClass().add("stack-name");
      name.setMinWidth(Region.USE_PREF_SIZE);
      summary.getStyleClass().add("stack-summary");
      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      row = new HBox(grip, toggle, name, spacer, summary, delete);
      row.getStyleClass().add("stack-row");
      delete.setVisible(false);
      row.hoverProperty().addListener((o, a, hover) -> delete.setVisible(hover));
      delete.focusedProperty().addListener((o, a, f) -> delete.setVisible(f || row.isHover()));
      setText(null);
      setOnMouseClicked(this::onClick);
      setOnDragDetected(this::onDragDetected);
      setOnDragOver(this::onDragOver);
      setOnDragExited(e -> row.pseudoClassStateChanged(DROP_TARGET, false));
      setOnDragDropped(this::onDropped);
    }

    @Override
    protected void updateItem(StackViewModel.Row item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        return;
      }
      name.setText(item.title());
      name.pseudoClassStateChanged(OFF, !item.enabled());
      summary.setText(item.summary());
      grip.setVisible(item.movable());
      ((FontIcon) toggle.getGraphic()).setIconLiteral(item.enabled() ? "fas-eye" : "fas-eye-slash");
      toggle.getStyleClass().remove("stack-toggle-on");
      if (item.enabled()) {
        toggle.getStyleClass().add("stack-toggle-on");
      }
      toggle.setOnAction(e -> stack.toggle(item.index()));
      delete.setOnAction(e -> stack.remove(item.index()));
      row.pseudoClassStateChanged(CURRENT, stack.selectedProperty().get() == item.index());
      setGraphic(row);
    }

    private void onClick(MouseEvent e) {
      if (getItem() != null) {
        stack.select(getItem().index());
      }
    }

    private void onDragDetected(MouseEvent e) {
      if (getItem() != null && getItem().movable()) {
        Dragboard db = startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();
        content.putString(Integer.toString(getItem().index()));
        db.setContent(content);
        e.consume();
      }
    }

    private void onDragOver(DragEvent e) {
      if (getItem() != null && getItem().movable() && e.getDragboard().hasString()) {
        e.acceptTransferModes(TransferMode.MOVE);
        row.pseudoClassStateChanged(DROP_TARGET, true);
        e.consume();
      }
    }

    private void onDropped(DragEvent e) {
      boolean done = false;
      if (getItem() != null && e.getDragboard().hasString()) {
        stack.move(Integer.parseInt(e.getDragboard().getString()), getItem().index());
        done = true;
      }
      e.setDropCompleted(done);
      e.consume();
    }
  }
}
