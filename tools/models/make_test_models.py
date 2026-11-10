#!/usr/bin/env python3
"""Genera los modelos ONNX sinteticos de las pruebas de ai-engine.

Solo usa el paquete onnx y no descarga nada. La salida es determinista: el mismo
script produce los mismos bytes, y los hashes quedan en models/SHA256SUMS.

Uso, desde la raiz, con el entorno de tools/models/.venv:
    tools/models/.venv/Scripts/python.exe -I tools/models/make_test_models.py
"""
import hashlib
import pathlib

import onnx
from onnx import TensorProto, helper

OPSET = 17
IR_VERSION = 8
OUT = pathlib.Path(__file__).resolve().parents[2] / "ai-engine/src/test/resources/models"


def io_info(channels=3):
    shape = [1, channels, "h", "w"]
    return (
        helper.make_tensor_value_info("input", TensorProto.FLOAT, shape),
        helper.make_tensor_value_info("output", TensorProto.FLOAT, [1, channels, None, None]),
    )


def floats(name, dims, values):
    return helper.make_tensor(name, TensorProto.FLOAT, dims, values)


def resize(inp, out, scale):
    node = helper.make_node(
        "Resize",
        [inp, "", "scales"],
        [out],
        mode="nearest",
        coordinate_transformation_mode="asymmetric",
        nearest_mode="floor",
    )
    return node, floats("scales", [4], [1.0, 1.0, float(scale), float(scale)])


def box_conv(inp, out, weights, radius):
    return helper.make_node(
        "Conv",
        [inp, weights],
        [out],
        group=3,
        kernel_shape=[2 * radius + 1, 2 * radius + 1],
        pads=[radius] * 4,
    )


def box_weights(name, radius):
    n = 2 * radius + 1
    return floats(name, [3, 1, n, n], [1.0 / (n * n)] * (3 * n * n))


def save(name, nodes, inits, channels=3):
    inp, out = io_info(channels)
    graph = helper.make_graph(nodes, name, [inp], [out], inits)
    model = helper.make_model(
        graph,
        opset_imports=[helper.make_opsetid("", OPSET)],
        ir_version=IR_VERSION,
        producer_name="rora-test-models",
        producer_version="1",
    )
    onnx.checker.check_model(model)
    data = model.SerializeToString(deterministic=True)
    assert len(data) < 50_000, name
    (OUT / f"{name}.onnx").write_bytes(data)
    return hashlib.sha256(data).hexdigest(), len(data)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    results = {}

    node, scales = resize("input", "output", 4)
    results["nearest_x4"] = save("nearest_x4", [node], [scales])

    conv = box_conv("input", "c", "w", 2)
    node, scales = resize("c", "output", 4)
    results["box5_x4"] = save("box5_x4", [conv, node], [box_weights("w", 2), scales])

    node, scales = resize("input", "output", 2)
    results["wrong_scale"] = save("wrong_scale", [node], [scales])

    node, scales = resize("input", "output", 4)
    results["wrong_channels"] = save("wrong_channels", [node], [scales], channels=1)

    # Cadena larga con pesos compartidos: tarda lo bastante para poder cancelarla.
    nodes, prev = [], "input"
    for i in range(300):
        out = "output" if i == 299 else f"n{i}"
        nodes.append(box_conv(prev, out, "w", 3))
        prev = out
    results["slow"] = save("slow", nodes, [box_weights("w", 3)])

    lines = [f"{h}  {n}.onnx" for n, (h, _) in sorted(results.items())]
    (OUT / "SHA256SUMS").write_text("\n".join(lines) + "\n", encoding="ascii", newline="\n")
    for n, (h, size) in sorted(results.items()):
        print(n, size, h)


if __name__ == "__main__":
    main()
