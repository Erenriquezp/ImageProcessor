#!/usr/bin/env python3
"""Exporta a ONNX los pesos oficiales de Real-ESRGAN y valida el resultado contra PyTorch.

Uso (con el venv de tools/models, ver requirements.lock):

    python -I tools/models/export_realesrgan.py --weights DIR --out DIR

Por cada modelo escribe una versión con ejes dinámicos y otra por cada tamaño de tile, y
un manifest.json con versiones, opset, SHA-256 y la diferencia máxima frente a PyTorch.

Los pesos son archivos pickle: se cargan siempre con weights_only=True y su SHA-256 se
comprueba antes de abrirlos.

Arquitecturas incluidas para no depender de basicsr, que se rompe con torchvision reciente:

- SRVGGNetCompact, de Real-ESRGAN (https://github.com/xinntao/Real-ESRGAN).
  Copyright (c) 2021, Xintao Wang. BSD 3-Clause License:
    Redistribution and use in source and binary forms, with or without modification, are
    permitted provided that the following conditions are met:
    1. Redistributions of source code must retain the above copyright notice, this list
       of conditions and the following disclaimer.
    2. Redistributions in binary form must reproduce the above copyright notice, this
       list of conditions and the following disclaimer in the documentation and/or other
       materials provided with the distribution.
    3. Neither the name of the copyright holder nor the names of its contributors may be
       used to endorse or promote products derived from this software without specific
       prior written permission.
    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
    EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
    OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
    SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
    INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
    TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
    BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
    ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
    DAMAGE.

- RRDBNet, de BasicSR (https://github.com/XPixelGroup/BasicSR), Apache License 2.0
  (http://www.apache.org/licenses/LICENSE-2.0).
  Cambios respecto al original: sin registro ni inicialización de pesos, y pixel_unshuffle
  sustituido por torch.nn.functional.pixel_unshuffle para que la exportación conserve la
  forma dinámica.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from torch import nn
from torch.nn import functional as F

OPSET = 17
TILE_SIZES = (128, 192, 256)
SEED = 20261108


class SRVGGNetCompact(nn.Module):
    def __init__(self, num_feat=64, num_conv=32, upscale=4):
        super().__init__()
        self.upscale = upscale
        self.body = nn.ModuleList()
        self.body.append(nn.Conv2d(3, num_feat, 3, 1, 1))
        self.body.append(nn.PReLU(num_parameters=num_feat))
        for _ in range(num_conv):
            self.body.append(nn.Conv2d(num_feat, num_feat, 3, 1, 1))
            self.body.append(nn.PReLU(num_parameters=num_feat))
        self.body.append(nn.Conv2d(num_feat, 3 * upscale * upscale, 3, 1, 1))
        self.upsampler = nn.PixelShuffle(upscale)

    def forward(self, x):
        out = x
        for layer in self.body:
            out = layer(out)
        out = self.upsampler(out)
        return out + F.interpolate(x, scale_factor=self.upscale, mode="nearest")


class ResidualDenseBlock(nn.Module):
    def __init__(self, num_feat=64, num_grow_ch=32):
        super().__init__()
        self.conv1 = nn.Conv2d(num_feat, num_grow_ch, 3, 1, 1)
        self.conv2 = nn.Conv2d(num_feat + num_grow_ch, num_grow_ch, 3, 1, 1)
        self.conv3 = nn.Conv2d(num_feat + 2 * num_grow_ch, num_grow_ch, 3, 1, 1)
        self.conv4 = nn.Conv2d(num_feat + 3 * num_grow_ch, num_grow_ch, 3, 1, 1)
        self.conv5 = nn.Conv2d(num_feat + 4 * num_grow_ch, num_feat, 3, 1, 1)
        self.lrelu = nn.LeakyReLU(negative_slope=0.2, inplace=True)

    def forward(self, x):
        x1 = self.lrelu(self.conv1(x))
        x2 = self.lrelu(self.conv2(torch.cat((x, x1), 1)))
        x3 = self.lrelu(self.conv3(torch.cat((x, x1, x2), 1)))
        x4 = self.lrelu(self.conv4(torch.cat((x, x1, x2, x3), 1)))
        x5 = self.conv5(torch.cat((x, x1, x2, x3, x4), 1))
        return x5 * 0.2 + x


class RRDB(nn.Module):
    def __init__(self, num_feat, num_grow_ch=32):
        super().__init__()
        self.rdb1 = ResidualDenseBlock(num_feat, num_grow_ch)
        self.rdb2 = ResidualDenseBlock(num_feat, num_grow_ch)
        self.rdb3 = ResidualDenseBlock(num_feat, num_grow_ch)

    def forward(self, x):
        out = self.rdb3(self.rdb2(self.rdb1(x)))
        return out * 0.2 + x


class RRDBNet(nn.Module):
    def __init__(self, scale=4, num_feat=64, num_block=23, num_grow_ch=32):
        super().__init__()
        self.scale = scale
        num_in_ch = 12 if scale == 2 else 3
        self.conv_first = nn.Conv2d(num_in_ch, num_feat, 3, 1, 1)
        self.body = nn.Sequential(*[RRDB(num_feat, num_grow_ch) for _ in range(num_block)])
        self.conv_body = nn.Conv2d(num_feat, num_feat, 3, 1, 1)
        self.conv_up1 = nn.Conv2d(num_feat, num_feat, 3, 1, 1)
        self.conv_up2 = nn.Conv2d(num_feat, num_feat, 3, 1, 1)
        self.conv_hr = nn.Conv2d(num_feat, num_feat, 3, 1, 1)
        self.conv_last = nn.Conv2d(num_feat, 3, 3, 1, 1)
        self.lrelu = nn.LeakyReLU(negative_slope=0.2, inplace=True)

    def forward(self, x):
        feat = F.pixel_unshuffle(x, 2) if self.scale == 2 else x
        feat = self.conv_first(feat)
        feat = feat + self.conv_body(self.body(feat))
        feat = self.lrelu(self.conv_up1(F.interpolate(feat, scale_factor=2.0, mode="nearest")))
        feat = self.lrelu(self.conv_up2(F.interpolate(feat, scale_factor=2.0, mode="nearest")))
        return self.conv_last(self.lrelu(self.conv_hr(feat)))


MODELS = {
    "x4v3": {
        "weights": "realesr-general-x4v3.pth",
        "sha256": "8dc7edb9ac80ccdc30c3a5dca6616509367f05fbc184ad95b731f05bece96292",
        "key": "params",
        "scale": 4,
        "build": SRVGGNetCompact,
    },
    "x4plus": {
        "weights": "RealESRGAN_x4plus.pth",
        "sha256": "4fa0d38905f75ac06eb49a7951b426670021be3018265fd191d2125df9d682f1",
        "key": "params_ema",
        "scale": 4,
        "build": lambda: RRDBNet(scale=4),
    },
    "x2plus": {
        "weights": "RealESRGAN_x2plus.pth",
        "sha256": "49fafd45f8fd7aa8d31ab2a22d14d91b536c34494a5cfe31eb5d89c2fa266abb",
        "key": "params_ema",
        "scale": 2,
        "build": lambda: RRDBNet(scale=2),
    },
}


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_model(spec: dict, weights_dir: Path) -> nn.Module:
    path = weights_dir / spec["weights"]
    actual = sha256_of(path)
    if actual != spec["sha256"]:
        raise SystemExit(f"SHA-256 incorrecto en {path.name}: {actual}")
    state = torch.load(path, map_location="cpu", weights_only=True)
    model = spec["build"]()
    model.load_state_dict(state[spec["key"]], strict=True)
    return model.eval()


def test_input(height: int, width: int) -> torch.Tensor:
    """Degradados más ruido con semilla: cubre el rango sin depender de ningún archivo."""
    rng = np.random.default_rng(SEED)
    ys, xs = np.mgrid[0:height, 0:width].astype(np.float32)
    base = np.stack([xs / max(width - 1, 1), ys / max(height - 1, 1), (xs + ys) / max(width + height - 2, 1)])
    noisy = np.clip(base + rng.normal(0, 0.05, base.shape).astype(np.float32), 0, 1)
    return torch.from_numpy(noisy.astype(np.float32)).unsqueeze(0)


def compare(path: Path, model: nn.Module, height: int, width: int) -> dict:
    sample = test_input(height, width)
    with torch.inference_mode():
        expected = model(sample).numpy()
    session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
    actual = session.run(None, {"input": sample.numpy()})[0]
    if actual.shape != expected.shape:
        raise SystemExit(f"Forma distinta en {path.name}: {actual.shape} frente a {expected.shape}")
    to8 = lambda a: np.rint(np.clip(a, 0, 1) * 255).astype(np.int32)
    return {
        "height": height,
        "width": width,
        "max_abs_float": float(np.abs(actual - expected).max()),
        "max_abs_8bit": int(np.abs(to8(actual) - to8(expected)).max()),
    }


def export(model: nn.Module, path: Path, tile: int, dynamic: bool) -> None:
    axes = None
    if dynamic:
        axes = {"input": {2: "height", 3: "width"}, "output": {2: "out_height", 3: "out_width"}}
    with torch.inference_mode():
        torch.onnx.export(
            model,
            (test_input(tile, tile),),
            str(path),
            dynamo=False,
            opset_version=OPSET,
            do_constant_folding=True,
            input_names=["input"],
            output_names=["output"],
            dynamic_axes=axes,
        )
    onnx.checker.check_model(onnx.load(str(path)))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--weights", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--models", nargs="*", default=list(MODELS), choices=list(MODELS))
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    manifest = {
        "versions": {
            "python": sys.version.split()[0],
            "torch": torch.__version__,
            "onnx": onnx.__version__,
            "onnxruntime": ort.__version__,
            "numpy": np.__version__,
        },
        "opset": OPSET,
        "models": {},
    }
    for name in args.models:
        spec = MODELS[name]
        model = load_model(spec, args.weights)
        entries = {}
        dyn = args.out / f"{name}_dyn.onnx"
        export(model, dyn, 64, dynamic=True)
        entries[dyn.name] = {
            "sha256": sha256_of(dyn),
            "bytes": dyn.stat().st_size,
            "checks": [compare(dyn, model, 64, 64), compare(dyn, model, 72, 120)],
        }
        for tile in TILE_SIZES:
            fixed = args.out / f"{name}_t{tile}.onnx"
            export(model, fixed, tile, dynamic=False)
            entries[fixed.name] = {
                "sha256": sha256_of(fixed),
                "bytes": fixed.stat().st_size,
                "checks": [compare(fixed, model, tile, tile)],
            }
        manifest["models"][name] = {"scale": spec["scale"], "weights_sha256": spec["sha256"], "files": entries}
        print(json.dumps({name: entries}, indent=1), flush=True)

    (args.out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
