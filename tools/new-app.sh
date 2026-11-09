#!/usr/bin/env bash
# Turns a fresh copy of this starter into a new app.
#   tools/new-app.sh com.m2011b.mpgtracker "MPG Tracker"
# Renames the package, application id, app name and project name, resets the version to 0.1,
# and removes the demo note from CLAUDE.md's header. The kit (com.m2011b.kit) is left as is.
set -euo pipefail

PKG="${1:?usage: tools/new-app.sh <package.name> \"App Name\"}"
NAME="${2:?usage: tools/new-app.sh <package.name> \"App Name\"}"
OLD_PKG="com.m2011b.starter"
[[ "$PKG" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || { echo "Package must look like com.m2011b.myapp"; exit 1; }
cd "$(dirname "$0")/.."
[ -d "app/src/main/java/${OLD_PKG//.//}" ] || { echo "Already renamed (no $OLD_PKG sources found)"; exit 1; }

OLD_DIR="${OLD_PKG//.//}"; NEW_DIR="${PKG//.//}"
PROJECT=$(echo "$NAME" | tr -cd '[:alnum:]')

for src in main test; do
  mkdir -p "app/src/$src/java/$(dirname "$NEW_DIR")"
  git mv "app/src/$src/java/$OLD_DIR" "app/src/$src/java/$NEW_DIR"
  grep -rl "$OLD_PKG" "app/src/$src/java/$NEW_DIR" | xargs sed -i "s/${OLD_PKG//./\\.}/$PKG/g"
done

sed -i -e "s/namespace = \"$OLD_PKG\"/namespace = \"$PKG\"/" \
       -e "s/applicationId = \"$OLD_PKG\"/applicationId = \"$PKG\"/" \
       -e 's/versionCode = [0-9]*/versionCode = 1/' -e 's/versionName = "[^"]*"/versionName = "0.1"/' app/build.gradle.kts
sed -i "s/android:label=\"[^\"]*\"/android:label=\"$NAME\"/" app/src/main/AndroidManifest.xml
sed -i "s/rootProject.name = \"[^\"]*\"/rootProject.name = \"$PROJECT\"/" settings.gradle.kts
sed -i "s/appName = \"Starter\"/appName = \"$NAME\"/" "app/src/main/java/$NEW_DIR/MainActivity.kt"
sed -i "1s/.*/# $NAME/" CLAUDE.md

echo "Renamed to $NAME ($PKG). Next:"
echo "  1. Make a signing key for this app on your PC (see README, 'New app checklist')."
echo "  2. Add the four repo secrets, push, then set repo variable SIGNING_CERT_SHA256."
