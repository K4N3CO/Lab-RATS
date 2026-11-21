#!/bin/bash
# Lab-RATS WSL Environment Setup & Build Helper

echo "[*] Initializing Lab-RATS WSL Environment..."

# 1. Check WSL environment
if grep -q Microsoft /proc/version 2>/dev/null; then
    echo "[+] WSL environment detected."
else
    echo "[*] Standard Linux environment detected."
fi

# 2. Check Java path
if [ -z "$JAVA_HOME" ]; then
    echo "[!] JAVA_HOME is not set. Attempting to locate JDK..."
    if [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    elif [ -d "/usr/lib/jvm/default-java" ]; then
        export JAVA_HOME="/usr/lib/jvm/default-java"
    else
        echo "[!] Please install OpenJDK 17: sudo apt install openjdk-17-jdk"
    fi
fi
echo "[+] JAVA_HOME set to: $JAVA_HOME"

# 3. Check Android SDK path
if [ -z "$ANDROID_HOME" ]; then
    # Check common WSL mounting paths for Windows SDK or Linux-native SDK
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "/mnt/c/Users/$(whoami)/AppData/Local/Android/Sdk" ]; then
        echo "[*] Detected Windows Android SDK mount. Configuring WSL access..."
        export ANDROID_HOME="/mnt/c/Users/$(whoami)/AppData/Local/Android/Sdk"
    else
        echo "[!] ANDROID_HOME not found. Please install Android SDK command-line tools in WSL."
    fi
fi
echo "[+] ANDROID_HOME set to: $ANDROID_HOME"

# 4. Generate local.properties if missing
if [ ! -f "local.properties" ] && [ -n "$ANDROID_HOME" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    echo "[+] Created local.properties pointing to $ANDROID_HOME"
fi

echo "[*] WSL environment configured successfully. Running Gradle build..."
./gradlew assembleDebug
