module io.github.erenriquezp.rora.app {
    requires javafx.controls;
    requires java.desktop;
    requires java.prefs;
    requires jdk.management;
    requires atlantafx.base;
    requires io.github.erenriquezp.rora.core;
    requires io.github.erenriquezp.rora.imaging.io;
    requires io.github.erenriquezp.rora.ai;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome6;

    exports io.github.erenriquezp.rora.app.shell to javafx.graphics;
    exports io.github.erenriquezp.rora.app.lab;
    exports io.github.erenriquezp.rora.app.catalog;
    exports io.github.erenriquezp.rora.app.imaging to io.github.erenriquezp.rora.lab;
    exports io.github.erenriquezp.rora.app.session to io.github.erenriquezp.rora.lab;
    exports io.github.erenriquezp.rora.app.fx to io.github.erenriquezp.rora.lab;
    exports io.github.erenriquezp.rora.app.ui to io.github.erenriquezp.rora.lab;

    uses io.github.erenriquezp.rora.app.lab.LabBundle;
}
