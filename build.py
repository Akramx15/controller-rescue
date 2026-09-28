#!/usr/bin/env python3
"""Build one app with an optional embedded Xposed entry point."""
import hashlib
import os
from pathlib import Path
import subprocess
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / "build"
SDK = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android")))
TOOLS = SDK / "build-tools" / "34.0.0"
ANDROID = SDK / "platforms" / "android-34" / "android.jar"
API = ROOT / "deps" / "xposed-api-82.jar"
API_URL = "https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar"
API_SHA = "f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25"
APK = BUILD / "controller-rescue-0.3.0.apk"
KEY = Path(os.environ["RESCUE_KEYSTORE"]).resolve()
if KEY.is_relative_to(ROOT):
    raise SystemExit("Keep signing material outside the repository")
ALIAS = os.environ.get("RESCUE_KEY_ALIAS", "controller-rescue-local")
if not os.environ.get("RESCUE_KEY_PASSWORD"):
    raise SystemExit("Set RESCUE_KEY_PASSWORD in the environment")
for generated in [BUILD / "classes", BUILD / "dex", BUILD / "tests"]:
    if generated.exists():
        shutil.rmtree(generated)
for folder in [BUILD, BUILD / "classes", BUILD / "dex", BUILD / "tests", API.parent]:
    folder.mkdir(parents=True, exist_ok=True)

def run(args):
    subprocess.run([str(arg) for arg in args], check=True, cwd=ROOT)

if not API.exists():
    urllib.request.urlretrieve(API_URL, API)
if hashlib.sha256(API.read_bytes()).hexdigest() != API_SHA:
    raise SystemExit("Xposed compile-only API hash mismatch")
run(["javac", "--release", "8", "-d", BUILD / "tests",
     ROOT / "src/local/quest/controllerrescue/RunPolicy.java",
     ROOT / "src/local/quest/controllerrescue/AccessibilityPolicy.java",
     ROOT / "src/local/quest/controllerpowerhook/PowerPolicy.java",
     ROOT / "src/local/quest/controllerpowerhook/PowerSequence.java",
     *sorted((ROOT / "tests").glob("*.java"))])
for test in ["local.quest.controllerrescue.RunPolicyTest",
             "local.quest.controllerrescue.AccessibilityPolicyTest", "PowerPolicyTest", "PowerSequenceTest"]:
    run(["java", "-cp", BUILD / "tests", test])
run([TOOLS / "aapt2", "compile", "--dir", ROOT / "res", "-o", BUILD / "resources.zip"])
run([TOOLS / "aapt2", "link", "-o", BUILD / "unsigned.apk", "-I", ANDROID,
     "--manifest", ROOT / "AndroidManifest.xml", BUILD / "resources.zip", "-A", ROOT / "assets",
     "--min-sdk-version", "26", "--target-sdk-version", "34"])
run(["javac", "--release", "8", "-parameters", "-classpath", str(ANDROID) + os.pathsep + str(API), "-d", BUILD / "classes",
     *sorted((ROOT / "src").rglob("*.java"))])
run([TOOLS / "d8", "--min-api", "26", "--lib", ANDROID, "--classpath", API, "--output", BUILD / "dex",
     *sorted((BUILD / "classes").rglob("*.class"))])
with zipfile.ZipFile(BUILD / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as apk:
    apk.write(BUILD / "dex" / "classes.dex", "classes.dex")
run([TOOLS / "zipalign", "-f", "4", BUILD / "unsigned.apk", BUILD / "aligned.apk"])
run([TOOLS / "apksigner", "sign", "--ks", KEY, "--ks-key-alias", ALIAS,
     "--ks-pass", "env:RESCUE_KEY_PASSWORD", "--key-pass", "env:RESCUE_KEY_PASSWORD",
     "--out", APK, BUILD / "aligned.apk"])
run([TOOLS / "apksigner", "verify", "--verbose", APK])
print(hashlib.sha256(APK.read_bytes()).hexdigest())
