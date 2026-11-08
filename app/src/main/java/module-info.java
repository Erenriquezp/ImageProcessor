module io.github.erenriquezp.rora.app {
    requires javafx.controls;
    requires java.desktop;
    requires java.prefs;
    requires atlantafx.base;
    requires io.github.erenriquezp.rora.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome6;

    exports io.github.erenriquezp.rora.app;
    exports io.github.erenriquezp.rora.app.shell to javafx.graphics;
}