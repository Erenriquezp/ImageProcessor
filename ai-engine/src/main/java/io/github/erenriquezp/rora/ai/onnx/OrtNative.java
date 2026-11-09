package io.github.erenriquezp.rora.ai.onnx;

import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prepara la carga nativa de ONNX Runtime y <b>falla cerrado</b>: si el directorio de bibliotecas
 * no es válido, ni siquiera se toca una clase de ORT, que en otro caso extraería sus DLL al
 * directorio temporal del sistema.
 *
 * <p>El hash detecta corrupción; no protege de quien ya pueda escribir en el directorio.
 */
final class OrtNative {

  static final String PROPERTY = "onnxruntime.native.path";

  private static final String TABLE = "/io/github/erenriquezp/rora/ai/onnx/ort-native.sha256";

  private static boolean ready;

  private OrtNative() {}

  /** Comprueba las bibliotecas y fija la ruta. Es idempotente. */
  static synchronized void prepare() throws AiFailure {
    if (ready) {
      return;
    }
    String platform = platform(System.getProperty("os.name"), System.getProperty("os.arch"));
    Path dir = resolve(System.getProperty(PROPERTY), System.getProperty("java.home"));
    verify(dir, platform, loadTable());
    System.setProperty(PROPERTY, dir.toAbsolutePath().toString());
    ready = true;
  }

  /** Directorio de nativos: la propiedad si está fijada; si no, el de la imagen de la app. */
  static Path resolve(String property, String javaHome) {
    return property != null && !property.isBlank()
        ? Path.of(property)
        : Path.of(javaHome, "ort-native");
  }

  static String platform(String osName, String osArch) throws AiFailure {
    String os = osName.toLowerCase(Locale.ROOT);
    boolean x64 = osArch.equals("amd64") || osArch.equals("x86_64");
    boolean arm = osArch.equals("aarch64") || osArch.equals("arm64");
    if (os.contains("win") && !os.contains("darwin") && x64) {
      return "win-x64";
    }
    if (os.contains("linux") && x64) {
      return "linux-x64";
    }
    if (os.contains("linux") && arm) {
      return "linux-aarch64";
    }
    if ((os.contains("mac") || os.contains("darwin")) && arm) {
      return "osx-aarch64";
    }
    throw new AiFailure(
        Reason.ENGINE_UNAVAILABLE, "Plataforma sin nativos: " + os + "/" + osArch, null);
  }

  static List<String> libraries(String platform) {
    if (platform.startsWith("win")) {
      return List.of("onnxruntime.dll", "onnxruntime4j_jni.dll");
    }
    String ext = platform.startsWith("osx") ? ".dylib" : ".so";
    return List.of("libonnxruntime" + ext, "libonnxruntime4j_jni" + ext);
  }

  /**
   * @param table hash esperado por {@code plataforma/archivo}
   * @throws AiFailure con {@code ENGINE_UNAVAILABLE} si falta algo o un hash no coincide
   */
  static void verify(Path dir, String platform, Map<String, String> table) throws AiFailure {
    if (!Files.isDirectory(dir)) {
      throw new AiFailure(Reason.ENGINE_UNAVAILABLE, "No existe el directorio " + dir, null);
    }
    for (String lib : libraries(platform)) {
      Path file = dir.resolve(lib);
      if (!Files.isRegularFile(file)) {
        throw new AiFailure(Reason.ENGINE_UNAVAILABLE, "Falta la biblioteca " + file, null);
      }
      String expected = table.get(platform + "/" + lib);
      if (expected == null) {
        throw new AiFailure(Reason.ENGINE_UNAVAILABLE, "Sin hash para " + lib, null);
      }
      String actual = sha256(file);
      if (!actual.equalsIgnoreCase(expected)) {
        throw new AiFailure(Reason.ENGINE_UNAVAILABLE, "Hash inesperado en " + lib, null);
      }
    }
  }

  private static Map<String, String> loadTable() throws AiFailure {
    try (InputStream in = OrtNative.class.getResourceAsStream(TABLE)) {
      if (in == null) {
        throw new AiFailure(Reason.ENGINE_UNAVAILABLE, "Falta la tabla de hashes", null);
      }
      Map<String, String> out = new HashMap<>();
      try (BufferedReader r =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
        for (String line = r.readLine(); line != null; line = r.readLine()) {
          String[] parts = line.trim().split("\\s+");
          if (parts.length == 2) {
            out.put(parts[1], parts[0]);
          }
        }
      }
      return out;
    } catch (IOException e) {
      throw new AiFailure(Reason.ENGINE_UNAVAILABLE, "No se pudo leer la tabla de hashes", e);
    }
  }

  private static String sha256(Path file) throws AiFailure {
    try (InputStream in = Files.newInputStream(file)) {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream d = new DigestInputStream(in, md)) {
        d.transferTo(java.io.OutputStream.nullOutputStream());
      }
      return HexFormat.of().formatHex(md.digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new AiFailure(Reason.ENGINE_UNAVAILABLE, "No se pudo leer " + file, e);
    }
  }
}
