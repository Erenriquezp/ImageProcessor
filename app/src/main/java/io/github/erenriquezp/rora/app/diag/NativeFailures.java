package io.github.erenriquezp.rora.app.diag;

/**
 * Distingue un motor de IA que no carga porque a Windows le falta una biblioteca nativa (en la
 * práctica, el runtime de Visual C++ que necesita ONNX Runtime) de una instalación rota (archivos
 * que faltan o con otro hash, que {@code OrtNative} detecta antes de cargar nada).
 */
public final class NativeFailures {

  /** Página oficial de Microsoft con el redistribuible de Visual C++ (se abre en el navegador). */
  public static final String VC_REDIST_PAGE =
      "https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist";

  private NativeFailures() {}

  /** {@code true} si en la cadena de causas hay un {@link UnsatisfiedLinkError}. */
  public static boolean missingNativeLibrary(Throwable error) {
    int depth = 0;
    for (Throwable t = error; t != null && depth < 16; t = t.getCause(), depth++) {
      if (t instanceof UnsatisfiedLinkError) {
        return true;
      }
    }
    return false;
  }
}
