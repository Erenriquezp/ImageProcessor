# Modelo incluido: realesr-general-x4v3

| Campo | Valor |
|---|---|
| Archivo | `realesr-general-x4v3.onnx` |
| SHA-256 | `7b10e80021ec3e0ff35017ae5022791879d93a799b2e201e1010a37510202266` |
| Tamaño | 4 866 420 bytes |
| Origen de los pesos | <https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth> |
| SHA-256 de los pesos | `8dc7edb9ac80ccdc30c3a5dca6616509367f05fbc184ad95b731f05bece96292` |
| Conversión | `tools/models/export_realesrgan.py`, opset 17, ejes dinámicos |
| Licencia | BSD-3-Clause (Copyright (c) 2021, Xintao Wang) |

Atribución: Real-ESRGAN, Xintao Wang, Liangbin Xie, Chao Dong, Ying Shan. <https://github.com/xinntao/Real-ESRGAN>

Riesgo conocido, sin que esto sea consejo legal: el código y los pesos se publican bajo BSD-3-Clause, pero `docs/Training.md` del proyecto original nombra los conjuntos DF2K y OST sin indicar la licencia de esos datos.

# Modelo incluido: face_detection_yunet_2023mar

| Campo | Valor |
|---|---|
| Archivo | `face_detection_yunet_2023mar.onnx` |
| SHA-256 | `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4` |
| Tamaño | 232 589 bytes |
| Origen | OpenCV Zoo, `models/face_detection_yunet/face_detection_yunet_2023mar.onnx` (<https://github.com/opencv/opencv_zoo>) |
| Licencia | MIT (Copyright (c) 2020 Shiqi Yu), según el `LICENSE` del directorio del modelo, leído el 2026-11-12 |
| Uso | Detector de caras de la herramienta Wallpapers (encuadre automático). Solo en local, sin red |

Atribución: YuNet, Shiqi Yu et al. <https://github.com/ShiqiYu/libfacedetection>

Riesgo conocido, sin que esto sea consejo legal: el modelo se publica bajo MIT, pero no consta la licencia de los datos de entrenamiento (WIDER FACE).
