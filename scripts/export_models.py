#!/usr/bin/env python3
"""
Export MediaPipe FaceMesh model compiled for Samsung Galaxy S25 Ultra (SM8750)
via Qualcomm AI Hub. The output is an AOT-compiled TFLite model optimized for
the Hexagon NPU, compatible with LiteRT CompiledModel API + Accelerator.NPU.
"""

import subprocess
import sys
import os

MODELS_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "models")
ASSETS_DIR = os.path.join(
    os.path.dirname(os.path.dirname(__file__)),
    "app", "src", "main", "assets"
)
TARGET_DEVICE = "Samsung Galaxy S25 Ultra"
TARGET_RUNTIME = "tflite"
OUTPUT_NAME = "face_landmark_compiled.tflite"


def install_dependencies():
    print("[1/4] Checking qai-hub-models installation...")
    try:
        import qai_hub_models
        print(f"      qai-hub-models already installed: {qai_hub_models.__version__}")
    except ImportError:
        print("      Installing qai-hub-models...")
        subprocess.check_call([
            sys.executable, "-m", "pip", "install",
            "qai-hub-models", "qai-hub", "--quiet"
        ])
        print("      Installation complete.")

    try:
        import qai_hub
        print(f"      qai-hub already installed.")
    except ImportError:
        subprocess.check_call([
            sys.executable, "-m", "pip", "install", "qai-hub", "--quiet"
        ])


def check_auth():
    print("[2/4] Checking Qualcomm AI Hub authentication...")
    result = subprocess.run(
        ["qai-hub", "configure", "--check"],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        print("\n  ERROR: Not authenticated with Qualcomm AI Hub.")
        print("  Steps:")
        print("    1. Visit https://aihub.qualcomm.com and create a free account")
        print("    2. Go to https://aihub.qualcomm.com/account to get your API token")
        print("    3. Run: qai-hub configure --api-token YOUR_TOKEN_HERE")
        print("    4. Re-run this script\n")
        sys.exit(1)
    print("      Authenticated OK.")


def export_model():
    print(f"[3/4] Exporting MediaPipe FaceMesh for '{TARGET_DEVICE}'...")
    print(f"      Runtime: {TARGET_RUNTIME}")
    print(f"      Output dir: {MODELS_DIR}")
    print("      This may take 2–5 minutes (cloud compilation)...\n")

    os.makedirs(MODELS_DIR, exist_ok=True)

    cmd = [
        sys.executable, "-m", "qai_hub_models.models.mediapipe_face.export",
        "--device", TARGET_DEVICE,
        "--target-runtime", TARGET_RUNTIME,
        "--output-dir", MODELS_DIR,
    ]

    result = subprocess.run(cmd, capture_output=False, text=True)

    if result.returncode != 0:
        print("\n  ERROR: Export failed. Common causes:")
        print("  - AI Hub account not authenticated (run check_auth step)")
        print("  - Network connectivity issue")
        print("  - Unsupported model/device combination")
        print("\n  Fallback: See models/README.md Method 2 for manual download.")
        sys.exit(1)

    print("\n      Export complete.")


def find_and_rename_output():
    """Find the exported tflite file and rename it to our canonical name."""
    tflite_files = []
    for root, dirs, files in os.walk(MODELS_DIR):
        for f in files:
            if f.endswith(".tflite") and f != OUTPUT_NAME:
                tflite_files.append(os.path.join(root, f))

    if not tflite_files:
        print(f"\n  ERROR: No .tflite files found in {MODELS_DIR}")
        print("  Check the export output above for the actual file location.")
        sys.exit(1)

    # Use the most recently modified file
    tflite_files.sort(key=os.path.getmtime, reverse=True)
    source = tflite_files[0]
    dest = os.path.join(MODELS_DIR, OUTPUT_NAME)

    if source != dest:
        os.rename(source, dest)
        print(f"      Renamed: {os.path.basename(source)} → {OUTPUT_NAME}")

    return dest


def verify_model(model_path):
    print("[4/4] Verifying model tensor shapes...")
    try:
        import tensorflow as tf
    except ImportError:
        try:
            import tflite_runtime.interpreter as tflite
        except ImportError:
            print("      Skipping verification (tensorflow/tflite_runtime not installed)")
            print("      Install with: pip install tensorflow")
            return

    try:
        import tensorflow as tf
        interpreter = tf.lite.Interpreter(model_path=model_path)
    except Exception:
        import tflite_runtime.interpreter as tflite
        interpreter = tflite.Interpreter(model_path=model_path)

    interpreter.allocate_tensors()
    inputs = interpreter.get_input_details()
    outputs = interpreter.get_output_details()

    print("\n  Input tensors:")
    for inp in inputs:
        print(f"    name={inp['name']}, shape={inp['shape']}, dtype={inp['dtype'].__name__}")

    print("\n  Output tensors:")
    for out in outputs:
        print(f"    name={out['name']}, shape={out['shape']}, dtype={out['dtype'].__name__}")

    # Check for expected shape
    expected_elements = 478 * 3  # = 1434
    output_elements = 1
    for dim in outputs[0]['shape']:
        output_elements *= dim

    if output_elements == expected_elements:
        print(f"\n  ✓ Output shape correct: {expected_elements} elements (478 landmarks × 3 coords)")
    elif output_elements == 468 * 3:
        print(f"\n  ⚠ WARNING: Got 468-landmark model (no iris landmarks 468-477).")
        print("    Iris tracking requires the 478-landmark variant.")
        print("    Check qai_hub_models version or try a different model variant.")
    else:
        print(f"\n  ⚠ WARNING: Unexpected output size: {output_elements}")
        print(f"    Expected: {expected_elements} (478×3) or {468*3} (468×3)")


def copy_to_assets(model_path):
    os.makedirs(ASSETS_DIR, exist_ok=True)
    dest = os.path.join(ASSETS_DIR, OUTPUT_NAME)
    import shutil
    shutil.copy2(model_path, dest)
    print(f"\n  Copied to Android assets: {dest}")
    print(f"  File size: {os.path.getsize(dest) / 1024 / 1024:.1f} MB")


def main():
    print("=" * 60)
    print("GazeBoard — Model Export Script")
    print(f"Target: {TARGET_DEVICE}")
    print("=" * 60 + "\n")

    install_dependencies()
    check_auth()
    export_model()
    model_path = find_and_rename_output()
    verify_model(model_path)
    copy_to_assets(model_path)

    print("\n" + "=" * 60)
    print("DONE. Model ready for use.")
    print(f"  Asset path: app/src/main/assets/{OUTPUT_NAME}")
    print(f"  Model path: models/{OUTPUT_NAME}")
    print("\nNext step: push model to device")
    print("  bash scripts/push_to_device.sh")
    print("=" * 60)


if __name__ == "__main__":
    main()
