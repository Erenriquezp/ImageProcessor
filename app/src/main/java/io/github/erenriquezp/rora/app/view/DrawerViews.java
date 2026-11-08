package io.github.erenriquezp.rora.app.view;

import atlantafx.base.theme.Styles;
import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.viewmodel.CatalogViewModel;
import io.github.erenriquezp.rora.app.viewmodel.CropViewModel;
import java.util.function.Consumer;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Contenido del cajón izquierdo: catálogo de filtros y herramienta de recorte. */
public final class DrawerViews {

  private DrawerViews() {}

  public static VBox catalog(CatalogViewModel<?> vm, Messages m, Subscriptions subs) {
    Label title = new Label(m.get("ui.catalog.title"));
    title.getStyleClass().add("panel-title");
    TextField search = new TextField();
    search.setPromptText(m.get("ui.catalog.search"));
    search.setAccessibleText(m.get("ui.catalog.search"));
    search.textProperty().bindBidirectional(vm.queryProperty());

    VBox results = new VBox(1);
    Consumer<Catalog.Entry> add = vm::add;
    Runnable rebuild =
        () -> {
          results.getChildren().clear();
          if (vm.results().isEmpty()) {
            Label none = new Label(m.format("ui.catalog.none", vm.queryProperty().get()));
            none.getStyleClass().add("hint");
            none.setWrapText(true);
            results.getChildren().add(none);
            return;
          }
          for (Catalog.Category category : Catalog.Category.values()) {
            var inCategory =
                vm.results().stream().filter(e -> e.category() == category).toList();
            if (inCategory.isEmpty()) {
              continue;
            }
            Label header = new Label(m.get(category.labelKey));
            header.getStyleClass().add("catalog-category");
            results.getChildren().add(header);
            for (Catalog.Entry e : inCategory) {
              results.getChildren().add(row(e, m, add));
            }
          }
        };
    vm.results().addListener((ListChangeListener<Catalog.Entry>) c -> rebuild.run());
    rebuild.run();

    ScrollPane scroll = new ScrollPane(results);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.getStyleClass().add("context-scroll");
    VBox.setVgrow(scroll, Priority.ALWAYS);
    VBox box = new VBox(title, search, scroll);
    box.getStyleClass().add("drawer");
    return box;
  }

  private static Node row(Catalog.Entry e, Messages m, Consumer<Catalog.Entry> add) {
    Button b = new Button(m.get(e.labelKey()), Icons.icon(e.icon()));
    b.getStyleClass().add("catalog-row");
    b.setMaxWidth(Double.MAX_VALUE);
    b.setOnAction(a -> add.accept(e));
    return b;
  }

  public static VBox crop(CropViewModel<?> vm, Messages m, Subscriptions subs) {
    Label title = new Label(m.get("ui.crop.title"));
    title.getStyleClass().add("panel-title");

    Label ratioLabel = new Label(m.get("ui.crop.ratio"));
    ratioLabel.getStyleClass().add("control-label");
    ComboBox<CropViewModel.RatioOption> ratio = new ComboBox<>();
    ratio.getItems().setAll(vm.ratios());
    ratio.getSelectionModel().select(0);
    ratio.setMaxWidth(Double.MAX_VALUE);
    ratio.setConverter(
        new javafx.util.StringConverter<>() {
          @Override
          public String toString(CropViewModel.RatioOption o) {
            return o == null ? "" : o.label();
          }

          @Override
          public CropViewModel.RatioOption fromString(String s) {
            return null;
          }
        });
    ratio.setOnAction(e -> vm.chooseRatio(ratio.getValue()));

    Button left = Icons.iconButton("fas-undo", m.get("ui.crop.rotate.left"), Styles.BUTTON_ICON);
    Button right = Icons.iconButton("fas-redo", m.get("ui.crop.rotate.right"), Styles.BUTTON_ICON);
    Button flipH = Icons.iconButton("fas-arrows-alt-h", m.get("ui.crop.flip.horizontal"), Styles.BUTTON_ICON);
    Button flipV = Icons.iconButton("fas-arrows-alt-v", m.get("ui.crop.flip.vertical"), Styles.BUTTON_ICON);
    left.setOnAction(e -> vm.rotate(false));
    right.setOnAction(e -> vm.rotate(true));
    flipH.setOnAction(e -> vm.flip("horizontal"));
    flipV.setOnAction(e -> vm.flip("vertical"));
    HBox transforms = new HBox(6, left, right, flipH, flipV);

    Node straighten = Controls.of(vm.straighten(), m, subs);

    Label sizeLabel = new Label(m.get("ui.crop.resize"));
    sizeLabel.getStyleClass().add("control-label");
    TextField size = new TextField();
    size.setPrefColumnCount(6);
    size.setAccessibleText(m.get("ui.crop.resize"));
    HBox.setHgrow(size, Priority.ALWAYS);
    Button applySize = new Button(m.get("ui.crop.resize.apply"));
    Runnable applyResize =
        () -> {
          String text = size.getText().trim();
          if (text.matches("\\d{1,5}")) {
            vm.resize(Integer.parseInt(text));
          }
        };
    applySize.setOnAction(e -> applyResize.run());
    size.setOnKeyPressed(
        e -> {
          if (e.getCode() == KeyCode.ENTER) {
            applyResize.run();
            e.consume();
          }
        });
    HBox sizeRow = new HBox(6, size, applySize);

    Label hint = new Label(m.get("ui.crop.hint"));
    hint.getStyleClass().add("hint");
    hint.setWrapText(true);

    Button apply = new Button(m.get("ui.action.apply"));
    apply.getStyleClass().add(Styles.ACCENT);
    apply.setOnAction(e -> vm.commit());
    Button cancel = new Button(m.get("ui.action.cancel"));
    cancel.setOnAction(e -> vm.cancel());
    HBox actions = new HBox(8, apply, cancel);

    VBox box =
        new VBox(title, new VBox(2, ratioLabel, ratio), transforms, straighten, new VBox(2, sizeLabel, sizeRow), hint, actions);
    box.getStyleClass().add("drawer");
    return box;
  }
}
