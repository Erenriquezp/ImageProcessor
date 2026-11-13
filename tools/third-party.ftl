<#-- Plantilla de THIRD_PARTY_LICENSES.md para license-maven-plugin. -->
# Licencias de terceros

Dependencias de ejecución de Rora Studio. Archivo generado: `./mvnw -B -DskipTests install && ./mvnw -B license:aggregate-add-third-party@third-party`.

| Dependencia | Versión | Licencia |
|---|---|---|
<#list dependencyMap as e>
| ${e.getKey().groupId}:${e.getKey().artifactId} | ${e.getKey().version} | <#list e.getValue() as l>${l}<#sep>, </#sep></#list> |
</#list>

## Motor de IA y modelo incluido

Sección fija de la plantilla: regenerar el archivo no la borra.

| Componente | Versión | Licencia | Notas |
|---|---|---|---|
| ONNX Runtime (Java y nativos win-x64) | 1.31.0 | MIT | Microsoft. JAR con SHA-256 `d3f903af31fe8f01300adb6b2b67297aa5bee7895909afc32e76ef29a3921149`; el instalador solo lleva los nativos de win-x64 |
| Modelo `realesr-general-x4v3` (Real-ESRGAN) | v0.2.5.0 | BSD-3-Clause | Copyright (c) 2021, Xintao Wang. Convertido a ONNX con `tools/models/export_realesrgan.py`; procedencia y hashes en `MODEL_NOTICE.md`. El proyecto original no indica la licencia de sus datos de entrenamiento (DF2K y OST) |
| Modelo `face_detection_yunet_2023mar` (YuNet, OpenCV Zoo) | 2023mar | MIT | Copyright (c) 2020 Shiqi Yu. Detector de caras de la herramienta Wallpapers; 232 589 bytes, procedencia y hash en `MODEL_NOTICE.md`. No consta la licencia de sus datos de entrenamiento (WIDER FACE) |
