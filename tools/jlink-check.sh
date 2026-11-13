#!/usr/bin/env bash
# Comprueba que imaging-io y el lector WebP funcionan en una imagen jlink.
# Las dependencias de TwelveMonkeys son modulos automaticos, que jlink rechaza: se les
# inyecta un module-info generado con jdeps. Uso (desde la raiz): tools/jlink-check.sh
# Requisito previo: ./mvnw -q -B -DskipTests -pl imaging-io -am package
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/imaging-io/target/jlink-check"
SEP=':'
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=';' ;; esac

module_of() {
  jar --describe-module --file "$1" | grep -v '^No module' | grep -v '^$' | head -1 | awk '{print $1}' | sed 's/@.*//'
}

rm -rf "$OUT"
mkdir -p "$OUT/libs" "$OUT/gen" "$OUT/probe/src/t.probe/t" "$OUT/probe/classes" "$OUT/mi"

cd "$ROOT"
./mvnw -q -B -pl imaging-io dependency:copy-dependencies \
  -DincludeScope=runtime -DincludeGroupIds=com.twelvemonkeys.common,com.twelvemonkeys.imageio \
  -DoutputDirectory="$OUT/libs"

cd "$OUT"
JARS=$(ls libs/*.jar)
# shellcheck disable=SC2086
jdeps --multi-release 21 --ignore-missing-deps --generate-module-info gen $JARS >/dev/null

for jar in $JARS; do
  mod=$(module_of "$jar")
  mi=$(find "gen/$mod" -name module-info.java | head -1)
  mkdir -p "mi/$mod"
  javac -nowarn --release 21 -d "mi/$mod" --module-path libs --patch-module "$mod=$jar" "$mi"
  jar uf "$jar" -C "mi/$mod" module-info.class
done

cat > probe/src/t.probe/module-info.java <<'EOF'
module t.probe {
  requires io.github.erenriquezp.rora.imaging.io;
  requires io.github.erenriquezp.rora.core;
}
EOF
cat > probe/src/t.probe/t/Probe.java <<'EOF'
package t;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.io.DecodeOptions;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.imaging.io.Codecs;
import java.nio.file.Path;

public class Probe {
  public static void main(String[] a) throws Exception {
    Path webp = Path.of(a[0]);
    var format = Codecs.sniff(webp);
    var d = Codecs.decoder(format).decode(webp, DecodeOptions.DEFAULT, CancellationToken.none(), Progress.NONE);
    System.out.println("WebP abierto por SPI: " + d.image().width() + "x" + d.image().height());
    for (FormatId f : new FormatId[] {FormatId.JPEG, FormatId.PNG, FormatId.TIFF}) {
      System.out.println("Encoder " + f + ": " + Codecs.encoder(f).getClass().getSimpleName());
    }
  }
}
EOF

# Rutas relativas: el javac de Windows no entiende las rutas de Git Bash.
MP="../../../core-imaging/target/classes${SEP}../classes${SEP}libs"
# shellcheck disable=SC2046
javac -d probe/classes/t.probe --module-path "$MP" $(find probe/src/t.probe -name '*.java')

JH=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
JMODS=""
[ -d "$JH/jmods" ] && JMODS="$JH/jmods${SEP}"
TM=$(for jar in $JARS; do module_of "$jar"; done | paste -sd, -)

# Sin --bind-services: en un JDK sin jmods arrastra jdk.jlink y falla. Los modulos van listados.
jlink --module-path "${JMODS}${MP}${SEP}probe/classes" \
  --add-modules "t.probe,$TM" \
  --strip-debug --no-man-pages --no-header-files \
  --output image

echo "Tamano de la imagen: $(du -sh image | cut -f1)"
image/bin/java -Djava.awt.headless=true -m t.probe/t.Probe \
  ../../src/test/resources/io/gradient-lossless.webp

# --- Motor de IA: ONNX Runtime es un modulo automatico y su JAR lleva los nativos de todas las
# plataformas. Se le quitan, se le inyecta module-info y solo se copia win-x64 a la imagen.
# Requisito previo: ./mvnw -q -B -DskipTests -pl ai-engine -am package (incluye wallpapers)
ORT_SHA=d3f903af31fe8f01300adb6b2b67297aa5bee7895909afc32e76ef29a3921149
mkdir -p ort/libs ort/x ort/gen ort/mi ort/probe/src/t.ai/t ort/probe/classes
cd "$ROOT"
./mvnw -q -B -pl ai-engine dependency:copy-dependencies \
  -DincludeScope=runtime -DincludeGroupIds=com.microsoft.onnxruntime \
  -DoutputDirectory="$OUT/ort/libs"
cd "$OUT/ort"
ORT_JAR=$(ls libs/*.jar)
[ "$(sha256sum "$ORT_JAR" | cut -d' ' -f1)" = "$ORT_SHA" ] || { echo "SHA-256 inesperado en $ORT_JAR"; exit 1; }

(cd x && jar xf "../$ORT_JAR")
rm -rf x/ai/onnxruntime/native
# Con su manifiesto: de ahi sale el nombre com.microsoft.onnxruntime.
jar --create --file libs/ort.jar --manifest x/META-INF/MANIFEST.MF -C x .
rm "$ORT_JAR"
jdeps --ignore-missing-deps --generate-module-info gen libs/ort.jar >/dev/null
ORT_MOD=$(module_of libs/ort.jar)
javac -nowarn --release 21 -d "mi" --module-path libs --patch-module "$ORT_MOD=libs/ort.jar" \
  "$(find "gen/$ORT_MOD" -name module-info.java | head -1)"
jar uf libs/ort.jar -C mi module-info.class

cat > probe/src/t.ai/module-info.java <<'EOF'
module t.ai {
  requires io.github.erenriquezp.rora.ai;
  requires io.github.erenriquezp.rora.core;
  requires io.github.erenriquezp.rora.wallpapers;
}
EOF
cat > probe/src/t.ai/t/Probe.java <<'EOF'
package t;

import io.github.erenriquezp.rora.ai.api.AiEngines;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.wallpapers.plan.PlannedTarget;
import io.github.erenriquezp.rora.wallpapers.plan.Planner;
import io.github.erenriquezp.rora.wallpapers.plan.Target;
import io.github.erenriquezp.rora.wallpapers.preset.PresetCatalog;
import io.github.erenriquezp.rora.wallpapers.render.TargetRenderer;
import io.github.erenriquezp.rora.wallpapers.subject.SubjectDetector;
import java.util.List;

public class Probe {
  public static void main(String[] a) throws Exception {
    Img src = Img.create(800, 600);
    try (SubjectDetector d = AiEngines.faceDetector()) {
      System.out.println(d.id() + " ejecutado: " + d.detect(src, CancellationToken.none()).size() + " caras");
    }
    Target t = Target.of(PresetCatalog.builtIns().getFirst());
    PlannedTarget p = Planner.plan(800, 600, List.of(), t);
    Img wp = TargetRenderer.render(src,
        new TargetRenderer.Job(t.width(), t.height(), p.frame(), p.content(), t.fill(), false),
        new float[3], TileExecutor.sequential(), CancellationToken.none(), Progress.NONE);
    System.out.println("Wallpaper " + t.id() + ": " + wp.width() + "x" + wp.height()
        + " (" + PresetCatalog.builtIns().size() + " presets)");
    ModelSource m = AiEngines.bundledModel();
    float[] in = new float[3 * 32 * 32];
    java.util.Arrays.fill(in, 0.5f);
    try (TileRunner r = AiEngines.onnx().open(m, 2)) {
      float[] out = r.run(in, 32, 32, CancellationToken.none());
      System.out.println(m.info().id() + " ejecutado: salida de " + out.length + " valores");
    }
  }
}
EOF

ROOTREL=../../../..
AIMP="$ROOTREL/core-imaging/target/classes${SEP}$ROOTREL/wallpapers/target/classes${SEP}$ROOTREL/ai-engine/target/classes${SEP}libs"
# shellcheck disable=SC2046
javac -d probe/classes/t.ai --module-path "$AIMP" $(find probe/src/t.ai -name '*.java')
jlink --module-path "${JMODS}${AIMP}${SEP}probe/classes" \
  --add-modules "t.ai,$ORT_MOD" \
  --add-options="--enable-native-access=$ORT_MOD" \
  --strip-debug --no-man-pages --no-header-files \
  --output image

NATIVE="$ROOT/ai-engine/target/ort-native/ai/onnxruntime/native/win-x64"
mkdir -p image/ort-native
cp "$NATIVE/onnxruntime.dll" "$NATIVE/onnxruntime4j_jni.dll" image/ort-native/
echo "Imagen con el motor: $(du -sh image | cut -f1)"
if ! find image -name '*.so' -o -name '*.dylib' | grep -q .; then
  echo "Sin nativos de otras plataformas"
else
  echo "Hay nativos de otras plataformas en la imagen"; exit 1
fi
image/bin/java -m t.ai/t.Probe
echo "jlink-check OK"
