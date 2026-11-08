package io.github.erenriquezp.rora.app.view;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.model.ControlSpec;
import io.github.erenriquezp.rora.app.ui.ColorChooser;
import io.github.erenriquezp.rora.app.viewmodel.ControlState;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.HashSet;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Crea el nodo de cada control a partir de su {@link ControlState}. */
final class Controls {

  private Controls() {}

  static Node of(ControlState state, Messages m, Subscriptions subs) {
    return switch (state.spec()) {
      case ControlSpec.Slider s -> slider(state, s, m, subs);
      case ControlSpec.Toggle t -> toggle(state, t, subs);
      case ControlSpec.Choice c -> choice(state, c, subs);
      case ControlSpec.ColorPick c -> color(state, c, m, subs);
      case ControlSpec.Checks c -> checks(state, c, subs);
    };
  }

  private static Label label(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("control-label");
    return l;
  }

  private static Node slider(ControlState state, ControlSpec.Slider s, Messages m, Subscriptions subs) {
    Label name = label(s.label());
    TextField value = new TextField();
    value.getStyleClass().add("control-value");
    value.setAccessibleText(s.label());
    Button reset =
        Icons.iconButton("fas-undo-alt", m.get("ui.action.reset.value"), "reset-button");
    reset.setOnAction(e -> state.reset());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox header = new HBox(2, name, spacer, value, reset);
    header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

    Slider slider = new Slider(s.min(), s.max(), number(state));
    slider.setAccessibleText(s.label());
    slider.setMaxWidth(Double.MAX_VALUE);
    boolean[] updating = {false};

    slider
        .valueProperty()
        .addListener(
            (obs, was, now) -> {
              if (!updating[0]) {
                state.userSet(now.doubleValue());
              }
            });
    slider
        .valueChangingProperty()
        .addListener(
            (obs, was, changing) -> {
              if (!changing) {
                state.endGesture();
              }
            });
    slider.setOnMouseReleased(e -> state.endGesture());
    slider.setOnMouseClicked(
        e -> {
          if (e.getClickCount() == 2) {
            state.reset();
          }
        });
    name.setOnMouseClicked(
        e -> {
          if (e.getClickCount() == 2) {
            state.reset();
          }
        });
    slider.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          boolean fine = e.isShiftDown();
          switch (e.getCode()) {
            case LEFT, DOWN -> {
              state.nudge(-1, fine);
              e.consume();
            }
            case RIGHT, UP -> {
              state.nudge(1, fine);
              e.consume();
            }
            case HOME -> {
              state.reset();
              e.consume();
            }
            default -> {}
          }
        });

    subs.add(
        state
            .valueProperty()
            .subscribe(
                v -> {
                  updating[0] = true;
                  slider.setValue(((Number) v).doubleValue());
                  updating[0] = false;
                  if (!value.isFocused()) {
                    value.setText(s.format(((Number) v).doubleValue(), m.locale()));
                  }
                }));

    Runnable commit =
        () -> {
          Double typed = parse(value.getText(), m);
          if (typed != null) {
            state.userSet(typed);
            state.endGesture();
          }
          value.setText(s.format(number(state), m.locale()));
        };
    value.setOnAction(e -> commit.run());
    value
        .focusedProperty()
        .addListener(
            (obs, was, focused) -> {
              if (!focused) {
                commit.run();
              }
            });
    value.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ESCAPE) {
            value.setText(s.format(number(state), m.locale()));
            slider.requestFocus();
            e.consume();
          }
        });

    VBox row = new VBox(header, slider);
    row.getStyleClass().add("control-row");
    subs.add(
        state
            .modifiedProperty()
            .subscribe(
                on -> {
                  reset.setVisible(on);
                  row.getStyleClass().remove("control-modified");
                  if (on) {
                    row.getStyleClass().add("control-modified");
                  }
                }));
    return row;
  }

  private static double number(ControlState state) {
    return ((Number) state.value()).doubleValue();
  }

  private static Double parse(String text, Messages m) {
    String t = text.trim().replace("+", "").replace('.', ',');
    ParsePosition pos = new ParsePosition(0);
    NumberFormat f = NumberFormat.getInstance(java.util.Locale.of("es"));
    Number n = f.parse(t, pos);
    return n == null || pos.getIndex() != t.length() ? null : n.doubleValue();
  }

  private static Node toggle(ControlState state, ControlSpec.Toggle t, Subscriptions subs) {
    ToggleSwitch sw = new ToggleSwitch(t.label());
    sw.setAccessibleText(t.label());
    subs.add(state.valueProperty().subscribe(v -> sw.setSelected((Boolean) v)));
    sw.selectedProperty()
        .addListener(
            (obs, was, on) -> {
              state.userSet(on);
              state.endGesture();
            });
    return new VBox(sw);
  }

  private static Node choice(ControlState state, ControlSpec.Choice c, Subscriptions subs) {
    Node control = c.segmented() ? segmented(state, c, subs) : combo(state, c, subs);
    VBox box = new VBox(2, label(c.label()), control);
    box.getStyleClass().add("control-row");
    return box;
  }

  private static Node segmented(ControlState state, ControlSpec.Choice c, Subscriptions subs) {
    ToggleGroup group = new ToggleGroup();
    HBox box = new HBox();
    for (int i = 0; i < c.values().size(); i++) {
      ToggleButton b = new ToggleButton(c.valueLabels().get(i));
      b.setUserData(c.values().get(i));
      b.setToggleGroup(group);
      b.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(b, Priority.ALWAYS);
      if (i == 0) {
        b.getStyleClass().add(Styles.LEFT_PILL);
      } else if (i == c.values().size() - 1) {
        b.getStyleClass().add(Styles.RIGHT_PILL);
      } else {
        b.getStyleClass().add(Styles.CENTER_PILL);
      }
      box.getChildren().add(b);
    }
    subs.add(
        state
            .valueProperty()
            .subscribe(
                v ->
                    group.getToggles().stream()
                        .filter(t -> v.equals(t.getUserData()))
                        .findFirst()
                        .ifPresent(group::selectToggle)));
    group
        .selectedToggleProperty()
        .addListener(
            (obs, was, now) -> {
              if (now == null) {
                group.selectToggle(was); // siempre hay una opción elegida
              } else {
                state.userSet(now.getUserData());
                state.endGesture();
              }
            });
    return box;
  }

  private static Node combo(ControlState state, ControlSpec.Choice c, Subscriptions subs) {
    ComboBox<String> box = new ComboBox<>();
    box.getItems().setAll(c.valueLabels());
    box.setMaxWidth(Double.MAX_VALUE);
    box.setAccessibleText(c.label());
    subs.add(
        state
            .valueProperty()
            .subscribe(v -> box.getSelectionModel().select(c.values().indexOf(v))));
    box.getSelectionModel()
        .selectedIndexProperty()
        .addListener(
            (obs, was, now) -> {
              if (now.intValue() >= 0) {
                state.userSet(c.values().get(now.intValue()));
                state.endGesture();
              }
            });
    return box;
  }

  private static Node color(
      ControlState state, ControlSpec.ColorPick c, Messages m, Subscriptions subs) {
    ColorChooser chooser = new ColorChooser(toColor((Integer) state.value()));
    Region view = chooser.getView();
    view.setMaxWidth(Double.MAX_VALUE);
    boolean[] updating = {false};
    subs.add(
        state
            .valueProperty()
            .subscribe(
                v -> {
                  updating[0] = true;
                  chooser.setValue(toColor((Integer) v));
                  updating[0] = false;
                }));
    chooser
        .valueProperty()
        .addListener(
            (obs, was, now) -> {
              if (!updating[0]) {
                state.userSet(toArgb(now));
                state.endGesture();
              }
            });
    Button reset =
        Icons.iconButton("fas-undo-alt", m.get("ui.action.reset.value"), "reset-button");
    reset.setOnAction(e -> state.reset());
    HBox.setHgrow(view, Priority.ALWAYS);
    VBox box = new VBox(2, label(c.label()), new HBox(4, view, reset));
    box.getStyleClass().add("control-row");
    return box;
  }

  private static Color toColor(int argb) {
    return Color.rgb((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) / 255.0);
  }

  private static int toArgb(Color c) {
    return ((int) Math.round(c.getOpacity() * 255) << 24)
        | ((int) Math.round(c.getRed() * 255) << 16)
        | ((int) Math.round(c.getGreen() * 255) << 8)
        | (int) Math.round(c.getBlue() * 255);
  }

  private static Node checks(ControlState state, ControlSpec.Checks c, Subscriptions subs) {
    VBox box = new VBox(4, label(c.label()));
    box.getStyleClass().add("control-row");
    for (int i = 0; i < c.options().size(); i++) {
      String option = c.options().get(i);
      CheckBox check = new CheckBox(c.optionLabels().get(i));
      subs.add(
          state.valueProperty().subscribe(v -> check.setSelected(((Set<?>) v).contains(option))));
      check.setOnAction(
          e -> {
            Set<String> next = new HashSet<>();
            for (Object o : (Set<?>) state.value()) {
              next.add((String) o);
            }
            if (check.isSelected()) {
              next.add(option);
            } else {
              next.remove(option);
            }
            state.userSet(Set.copyOf(next));
            state.endGesture();
          });
      box.getChildren().add(check);
    }
    return box;
  }
}
