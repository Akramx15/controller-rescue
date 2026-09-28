#!/usr/bin/env python3
"""Build the optional LSPosed module. Signing credentials stay outside the repository."""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / 'build'
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android')))
TOOLS = SDK / 'build-tools' / '34.0.0'
ANDROID = SDK / 'platforms' / 'android-34' / 'android.jar'
API = ROOT / 'deps' / 'xposed-api-82.jar'
API_URL = 'https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar'
API_SHA = 'f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25'

def run(args):
    subprocess.run([str(arg) for arg in args], check=True, cwd=ROOT)

if BUILD.exists():
    shutil.rmtree(BUILD)
for folder in [BUILD / 'classes', BUILD / 'dex', BUILD / 'tests', API.parent]:
    folder.mkdir(parents=True, exist_ok=True)
if not API.exists():
    urllib.request.urlretrieve(API_URL, API)
if hashlib.sha256(API.read_bytes()).hexdigest() != API_SHA:
    raise SystemExit('Xposed compile-only API hash mismatch')
policy = ROOT / 'src/local/quest/controllerpowerhook/PowerPolicy.java'
sequence = ROOT / 'src/local/quest/controllerpowerhook/PowerSequence.java'
run(['javac', '--release', '8', '-d', BUILD/'tests', policy, sequence,
     ROOT/'tests/PowerPolicyTest.java', ROOT/'tests/PowerSequenceTest.java'])
run(['java', '-cp', BUILD/'tests', 'PowerPolicyTest'])
run(['java', '-cp', BUILD/'tests', 'PowerSequenceTest'])
run([TOOLS/'aapt2', 'compile', '--dir', ROOT/'res', '-o', BUILD/'resources.zip'])
run([TOOLS/'aapt2', 'link', '-o', BUILD/'unsigned.apk', '-I', ANDROID,
     '--manifest', ROOT/'AndroidManifest.xml', BUILD/'resources.zip', '-A', ROOT/'assets',
     '--min-sdk-version', '34', '--target-sdk-version', '34'])
run(['javac', '--release', '8', '-parameters', '-classpath', str(ANDROID)+os.pathsep+str(API),
     '-d', BUILD/'classes', *sorted((ROOT/'src').rglob('*.java'))])
run([TOOLS/'d8', '--min-api', '34', '--lib', ANDROID, '--classpath', API,
     '--output', BUILD/'dex', *sorted((BUILD/'classes').rglob('*.class'))])
with zipfile.ZipFile(BUILD/'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
    apk.write(BUILD/'dex/classes.dex', 'classes.dex')
run([TOOLS/'zipalign', '-f', '4', BUILD/'unsigned.apk', BUILD/'controller-power-unsigned.apk'])
key = os.environ.get('RESCUE_KEYSTORE')
if key:
    key_path = Path(key).expanduser().resolve()
    if key_path.is_relative_to(ROOT.parent):
        raise SystemExit('Keep signing keys outside the source repository')
    run([TOOLS/'apksigner', 'sign', '--ks', key_path,
         '--ks-key-alias', os.environ.get('RESCUE_KEY_ALIAS', 'controller-rescue-local'),
         '--ks-pass', 'env:RESCUE_KEY_PASSWORD', '--key-pass', 'env:RESCUE_KEY_PASSWORD',
         '--out', BUILD/'controller-power.apk', BUILD/'controller-power-unsigned.apk'])
    run([TOOLS/'apksigner', 'verify', '--verbose', '--print-certs', BUILD/'controller-power.apk'])
    print('APK SHA256:', hashlib.sha256((BUILD/'controller-power.apk').read_bytes()).hexdigest())
else:
    print('Unsigned APK ready. Set RESCUE_KEYSTORE and RESCUE_KEY_PASSWORD to sign.')
