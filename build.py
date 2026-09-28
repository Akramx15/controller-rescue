#!/usr/bin/env python3
"""Offline Android build. Signing material must live outside this repository."""
import hashlib
import os
from pathlib import Path
import subprocess
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / "build"
SDK = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android")))
TOOLS = SDK / "build-tools" / "34.0.0"
ANDROID = SDK / "platforms" / "android-34" / "android.jar"
KEY = Path(os.environ["RESCUE_KEYSTORE"]).resolve()
if KEY.is_relative_to(ROOT):
    raise SystemExit("Keep signing material outside the repository")
ALIAS = os.environ.get("RESCUE_KEY_ALIAS", "controller-rescue-local")
if not os.environ.get("RESCUE_KEY_PASSWORD"):
    raise SystemExit("Set RESCUE_KEY_PASSWORD in the environment")
for generated in [BUILD / "classes", BUILD / "dex"]:
    if generated.exists():
        shutil.rmtree(generated)
for folder in [BUILD, BUILD / "classes", BUILD / "dex"]:
    folder.mkdir(parents=True, exist_ok=True)

def run(args):
    subprocess.run([str(arg) for arg in args], check=True, cwd=ROOT)

run([TOOLS / "aapt2", "compile", "--dir", ROOT / "res", "-o", BUILD / "resources.zip"])
run([TOOLS / "aapt2", "link", "-o", BUILD / "unsigned.apk", "-I", ANDROID,
     "--manifest", ROOT / "AndroidManifest.xml", BUILD / "resources.zip",
     "--min-sdk-version", "26", "--target-sdk-version", "34"])
run(["javac", "--release", "8", "-classpath", ANDROID, "-d", BUILD / "classes",
     *sorted((ROOT / "src").rglob("*.java"))])
run([TOOLS / "d8", "--min-api", "26", "--lib", ANDROID, "--output", BUILD / "dex",
     *sorted((BUILD / "classes").rglob("*.class"))])
with zipfile.ZipFile(BUILD / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as apk:
    apk.write(BUILD / "dex" / "classes.dex", "classes.dex")
run([TOOLS / "zipalign", "-f", "4", BUILD / "unsigned.apk", BUILD / "aligned.apk"])
run([TOOLS / "apksigner", "sign", "--ks", KEY, "--ks-key-alias", ALIAS,
     "--ks-pass", "env:RESCUE_KEY_PASSWORD", "--key-pass", "env:RESCUE_KEY_PASSWORD",
     "--out", BUILD / "controller-rescue-0.2.0.apk", BUILD / "aligned.apk"])
run([TOOLS / "apksigner", "verify", "--verbose", BUILD / "controller-rescue-0.2.0.apk"])
print(hashlib.sha256((BUILD / "controller-rescue-0.2.0.apk").read_bytes()).hexdigest())
