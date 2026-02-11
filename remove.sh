#!/bin/bash

echo "Cleaning up project for distribution..."

# Directories to remove (Caches and IDE settings)
DIRS=(
    ".gradle"
    ".kotlin"
    ".idea"
    ".vscode"
    "build"
    "app/build"
    "captures"
    ".externalNativeBuild"
    ".cxx"
    "Release"
)

# Files to remove
FILES=(
    "*.apk"
    "*.aar"
    "*.apks"
    ".DS_Store"
)

for dir in "${DIRS[@]}"; do
    if [ -d "$dir" ]; then
        echo "Removing directory: $dir"
        rm -rf "$dir"
    fi
done

for file in "${FILES[@]}"; do
    # Using find for files to handle wildcards and subdirectories
    find . -name "$file" -not -path "./.git/*" -exec rm -rfv {} +
done

# Original patterns from old script
find . -type d -name 'bin' -exec rm -rfv {} +
find . -type d -name 'gen' -exec rm -rfv {} +
find . -type d -name '.settings' -exec rm -rfv {} +
find . -type d -name '.metadata' -exec rm -rfv {} +

echo "Cleanup complete."
