"""Convierte las muestras de verificación a PNG con los píxeles decodificados por TensorFlow.

Así la app (que decodifica PNG sin pérdida) recibe exactamente los mismos píxeles que el
modelo vio en Colab, y la verificación compara solo preprocesamiento + inferencia.
Uso: python muestras_a_png.py <carpeta modelo/muestras> <assets/muestras>
"""
import json, sys
from pathlib import Path
import numpy as np
import tensorflow as tf
from PIL import Image

src, dst = Path(sys.argv[1]), Path(sys.argv[2])
dst.mkdir(parents=True, exist_ok=True)
d = json.loads((src / "muestras.json").read_text())
for s in d["muestras"]:
    img = tf.io.decode_image(tf.io.read_file(str(src / s["archivo"])), channels=3, expand_animations=False).numpy()
    nombre = Path(s["archivo"]).stem.replace(" ", "_").replace("(", "").replace(")", "") + ".png"
    Image.fromarray(img.astype(np.uint8)).save(dst / nombre)
    s["archivo"] = nombre
(dst / "muestras.json").write_text(json.dumps(d, indent=1))
print(len(d["muestras"]), "muestras convertidas")
