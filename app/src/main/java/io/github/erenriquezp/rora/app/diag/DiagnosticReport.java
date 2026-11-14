package io.github.erenriquezp.rora.app.diag;

import java.lang.management.ManagementFactory;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Informe de diagnóstico en texto plano. Lleva lo justo para reproducir un fallo y nada personal:
 * sin nombre de usuario, rutas, nombre de la foto, metadatos, receta (solo ids), zona horaria ni
 * nombre del equipo. El texto completo pasa por el {@link Redactor} antes de mostrarse.
 */
public final class DiagnosticReport {

  /** Líneas de registro como máximo y bytes leídos del final del archivo. */
  public static final int LOG_LINES = 200;

  public static final int LOG_BYTES = 32 * 1024;

  /**
   * Datos del informe. {@link #system()} da los de la JVM y el sistema; la interfaz añade los
   * suyos.
   *
   * @param jvmFlags opciones relevantes de la JVM, sin {@code -D} (pueden llevar rutas)
   * @param hardwareAcceleration vacío si no se pudo saber
   * @param image tamaño y formato de la última imagen abierta, sin nombre
   * @param recipeSteps ids de los pasos activos, en orden
   */
  public record Facts(
      AppInfo app,
      boolean packaged,
      String uiLocale,
      String java,
      List<String> jvmFlags,
      String javafx,
      Optional<Boolean> hardwareAcceleration,
      String os,
      int logicalCpus,
      String cpuId,
      long physicalTotal,
      long physicalFree,
      long heapMax,
      long heapUsed,
      long cacheBytes,
      List<String> screens,
      String ai,
      Optional<String> image,
      List<String> recipeSteps,
      Optional<String> lastError,
      Optional<String> hsErr,
      String logTail,
      ZonedDateTime generated) {}

  /** Lo que la JVM y el sistema saben por sí solos. */
  public record SystemFacts(
      String java,
      List<String> jvmFlags,
      String os,
      int logicalCpus,
      String cpuId,
      long physicalTotal,
      long physicalFree,
      long heapMax,
      long heapUsed) {}

  private static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

  private DiagnosticReport() {}

  /** Puede tardar más de 100 ms en Windows (memoria física): fuera del hilo de la interfaz. */
  public static SystemFacts system() {
    Runtime rt = Runtime.getRuntime();
    long total = -1;
    long free = -1;
    if (ManagementFactory.getOperatingSystemMXBean()
        instanceof com.sun.management.OperatingSystemMXBean os) {
      total = os.getTotalMemorySize();
      free = os.getFreeMemorySize();
    }
    return new SystemFacts(
        Runtime.version()
            + " ("
            + System.getProperty("java.vendor", "?")
            + ", "
            + System.getProperty("java.vm.name", "?")
            + ")",
        jvmFlags(ManagementFactory.getRuntimeMXBean().getInputArguments()),
        System.getProperty("os.name", "?")
            + " "
            + System.getProperty("os.version", "?")
            + " "
            + System.getProperty("os.arch", "?"),
        rt.availableProcessors(),
        Optional.ofNullable(System.getenv("PROCESSOR_IDENTIFIER")).orElse(""),
        total,
        free,
        rt.maxMemory(),
        rt.totalMemory() - rt.freeMemory());
  }

  /** Solo opciones de la JVM que ayudan a diagnosticar; las {@code -D} pueden llevar rutas. */
  static List<String> jvmFlags(List<String> args) {
    return args.stream()
        .filter(
            a ->
                a.startsWith("-XX:")
                    || a.startsWith("-Xmx")
                    || a.startsWith("-Xms")
                    || a.startsWith("-Xss")
                    || a.startsWith("--enable-native-access")
                    || a.equals("-Drora.packaged=true"))
        .filter(a -> !a.startsWith("-XX:ErrorFile") && !a.startsWith("-XX:AOTCache"))
        .toList();
  }

  static String bytes(long b) {
    if (b < 0) {
      return "no disponible";
    }
    double v = b;
    String[] units = {"B", "KB", "MB", "GB", "TB"};
    int u = 0;
    while (v >= 1024 && u < units.length - 1) {
      v /= 1024;
      u++;
    }
    return u == 0 ? b + " B" : String.format(Locale.ROOT, "%.1f %s", v, units[u]);
  }

  public static String build(Facts f, Redactor redactor) {
    StringBuilder sb = new StringBuilder(4096);
    sb.append("Rora Studio ")
        .append(f.app().displayVersion())
        .append(" — informe de diagnóstico\n");
    sb.append("Generado: ").append(WHEN.format(f.generated())).append(" (hora local)\n");
    section(sb, "Aplicación");
    line(sb, "Versión", f.app().displayVersion());
    line(sb, "Empaquetada", f.packaged() ? "sí" : "no (desarrollo)");
    line(sb, "Idioma de la interfaz", f.uiLocale());
    section(sb, "Java");
    line(sb, "Versión", f.java());
    line(sb, "Opciones", f.jvmFlags().isEmpty() ? "(ninguna)" : String.join(" ", f.jvmFlags()));
    section(sb, "JavaFX");
    line(sb, "Versión", f.javafx());
    line(
        sb,
        "Aceleración por hardware",
        f.hardwareAcceleration()
            .map(b -> b ? "sí" : "no (renderizado por software)")
            .orElse("no disponible"));
    line(sb, "GPU", "no disponible");
    section(sb, "Sistema");
    line(sb, "SO", f.os());
    line(sb, "Núcleos lógicos", Integer.toString(f.logicalCpus()));
    line(sb, "Procesador", f.cpuId().isBlank() ? "no disponible" : f.cpuId());
    section(sb, "Memoria");
    line(sb, "Física", bytes(f.physicalTotal()) + " (libre " + bytes(f.physicalFree()) + ")");
    line(sb, "Heap", bytes(f.heapMax()) + " máximo, " + bytes(f.heapUsed()) + " en uso");
    line(sb, "Caché de resultados", bytes(f.cacheBytes()));
    section(sb, "Pantallas");
    if (f.screens().isEmpty()) {
      line(sb, "Pantallas", "no disponible");
    }
    for (int i = 0; i < f.screens().size(); i++) {
      line(sb, "Pantalla " + (i + 1), f.screens().get(i));
    }
    section(sb, "Región");
    line(sb, "Locale", f.uiLocale());
    section(sb, "IA");
    line(sb, "Motor", f.ai());
    section(sb, "Imagen");
    line(sb, "Última abierta", f.image().orElse("ninguna"));
    line(
        sb,
        "Pasos activos",
        f.recipeSteps().isEmpty() ? "ninguno" : String.join(", ", f.recipeSteps()));
    section(sb, "Errores");
    line(sb, "Último de la sesión", f.lastError().orElse("ninguno"));
    if (f.hsErr().isPresent()) {
      sb.append("Fallo nativo de la sesión anterior:\n");
      for (String l : f.hsErr().get().split("\n")) {
        sb.append("  ").append(l).append('\n');
      }
    }
    section(sb, "Registro (últimas líneas)");
    sb.append(f.logTail().isBlank() ? "(vacío)" : f.logTail()).append('\n');
    return redactor.redact(sb.toString());
  }

  private static void section(StringBuilder sb, String title) {
    sb.append("\n[").append(title).append("]\n");
  }

  private static void line(StringBuilder sb, String key, String value) {
    sb.append("  ").append(key).append(": ").append(value).append('\n');
  }
}
