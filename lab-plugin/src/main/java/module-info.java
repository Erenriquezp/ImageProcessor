/**
 * Laboratorio: operaciones didácticas, generador y blending, y el código heredado que los tests
 * diferenciales siguen usando. Es opcional y no forma parte del instalador.
 */
module io.github.erenriquezp.rora.lab {
    requires javafx.controls;
    requires java.desktop;
    requires io.github.erenriquezp.rora.core;
    requires io.github.erenriquezp.rora.app;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome6;

    provides io.github.erenriquezp.rora.app.lab.LabBundle with io.github.erenriquezp.rora.lab.LegacyLabBundle;
}
