#!/usr/bin/env bash
# Integra en la app el modelo entrenado en Colab y compila la APK firmada.
# Uso: ./integrar_modelo.sh ruta/a/resultados_proyecto.zip
set -euo pipefail
ZIP="${1:?Uso: $0 resultados_proyecto.zip}"
cd "$(dirname "$0")"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
unzip -q "$ZIP" -d "$TMP"
M="$TMP/modelo"
A=app/src/main/assets
for f in tyre_classifier_fp16.tflite labels.txt model_info.json; do
    [[ -f "$M/$f" ]] || { echo "Falta $f en el zip"; exit 1; }
done
rm -rf "$A/muestras"
cp "$M/tyre_classifier_fp16.tflite" "$M/labels.txt" "$M/model_info.json" "$A/"
"${PYTHON:-python3}" muestras_a_png.py "$M/muestras" "$A/muestras"   # requiere TensorFlow
echo "Modelo integrado. Compilando…"
./gradlew :app:testDebugUnitTest :app:assembleRelease --console=plain -q
mkdir -p apk ../apk
cp app/build/outputs/apk/release/app-release.apk apk/LlantaCheck-v1.1.apk
cp apk/LlantaCheck-v1.1.apk ../apk/
rm -f ../apk/LlantaCheck-v1.0-provisional.apk
./gradlew --stop -q
echo "APK lista: $(realpath apk/LlantaCheck-v1.1.apk)"
