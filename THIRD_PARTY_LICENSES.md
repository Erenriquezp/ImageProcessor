# Licencias de terceros

Dependencias de ejecución de Rora Studio. Archivo generado: `./mvnw -B -DskipTests install && ./mvnw -B license:aggregate-add-third-party@third-party`. `tools/package-windows.ps1` lo regenera en cada empaquetado y falla si el confirmado no está al día.

| Dependencia | Versión | Licencia |
|---|---|---|
| com.microsoft.onnxruntime:onnxruntime | 1.31.0 | MIT License |
| com.twelvemonkeys.common:common-image | 3.15.3 | BSD-3-Clause |
| com.twelvemonkeys.common:common-io | 3.15.3 | BSD-3-Clause |
| com.twelvemonkeys.common:common-lang | 3.15.3 | BSD-3-Clause |
| com.twelvemonkeys.imageio:imageio-core | 3.15.3 | BSD-3-Clause |
| com.twelvemonkeys.imageio:imageio-metadata | 3.15.3 | BSD-3-Clause |
| com.twelvemonkeys.imageio:imageio-webp | 3.15.3 | BSD-3-Clause |
| io.github.mkpaz:atlantafx-base | 2.1.0 | MIT License |
| org.kordamp.ikonli:ikonli-core | 12.4.0 | Apache-2.0 |
| org.kordamp.ikonli:ikonli-fontawesome6-pack | 12.4.0 | Apache-2.0 |
| org.kordamp.ikonli:ikonli-javafx | 12.4.0 | Apache-2.0 |
| org.openjfx:javafx-base | 25.0.5 | GPLv2+CE |
| org.openjfx:javafx-controls | 25.0.5 | GPLv2+CE |
| org.openjfx:javafx-graphics | 25.0.5 | GPLv2+CE |

## Motor de IA y modelo incluido

Sección fija de la plantilla: regenerar el archivo no la borra.

| Componente | Versión | Licencia | Notas |
|---|---|---|---|
| ONNX Runtime (Java y nativos win-x64) | 1.31.0 | MIT | Microsoft. JAR con SHA-256 `d3f903af31fe8f01300adb6b2b67297aa5bee7895909afc32e76ef29a3921149`; el instalador solo lleva los nativos de win-x64 |
| Modelo `realesr-general-x4v3` (Real-ESRGAN) | v0.2.5.0 | BSD-3-Clause | Copyright (c) 2021, Xintao Wang. Convertido a ONNX con `tools/models/export_realesrgan.py`; procedencia y hashes en `MODEL_NOTICE.md`. El proyecto original no indica la licencia de sus datos de entrenamiento (DF2K y OST) |
| Modelo `face_detection_yunet_2023mar` (YuNet, OpenCV Zoo) | 2023mar | MIT | Copyright (c) 2020 Shiqi Yu. Detector de caras de la herramienta Wallpapers; 232 589 bytes, procedencia y hash en `MODEL_NOTICE.md`. No consta la licencia de sus datos de entrenamiento (WIDER FACE) |

## Incluidos en el instalador de Windows

Sección fija de la plantilla. El instalador y el ZIP llevan además estos componentes; sus textos completos están en la carpeta `legal\` de la instalación y en `runtime\legal\`.

| Componente | Versión | Licencia | Notas |
|---|---|---|---|
| Eclipse Temurin (runtime de Java reducido con jlink) | 25.0.5+8 | GPLv2 con Classpath Exception | Avisos de cada módulo en `runtime\legal\`. Código fuente: `legal\SOURCE.txt` |
| OpenJFX (jmods de Gluon para Windows x64) | 25.0.5 | GPLv2 con Classpath Exception | Avisos en `runtime\legal\javafx.*` (incluye `jpeg_fx.md`, `mesa3d.md`, `pipewire.md`). Código fuente: `legal\SOURCE.txt` |
| Font Awesome Free (fuentes del paquete de iconos de Ikonli) | 6.5.2 | Iconos CC BY 4.0, fuentes SIL OFL 1.1, código MIT | © Fonticons, Inc. Texto en `legal\fonts\FontAwesome-6.5.2-LICENSE.txt` |
| IBM Plex Sans | — | SIL OFL 1.1 | © IBM Corp. Texto en `legal\fonts\IBM-Plex-OFL.txt` |
| Avisos de terceros de ONNX Runtime | 1.31.0 | Varias (incluye la Intel Simplified Software License de MKL) | `legal\onnxruntime\ThirdPartyNotices.txt`, tal como los publica Microsoft en el JAR |
| Runtime de Visual C++ y Universal CRT (DLL de Microsoft) | 14.40 (Temurin), 14.42 (OpenJFX) | Licencia de Microsoft | No las añade Rora Studio: vienen dentro de Temurin y de OpenJFX. Su redistribución está pendiente de decisión legal del mantenedor |
