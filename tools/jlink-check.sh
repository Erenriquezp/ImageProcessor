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
echo "jlink-check OK"
