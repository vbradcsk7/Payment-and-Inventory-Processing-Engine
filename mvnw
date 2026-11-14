#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Startup Script with macOS Java Auto-Detection
# ----------------------------------------------------------------------------

set -e

# Auto-detect JAVA_HOME on macOS if not set
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  if [ -x "/usr/libexec/java_home" ]; then
    DETECTED_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
    if [ -n "$DETECTED_HOME" ] && [ -x "$DETECTED_HOME/bin/java" ]; then
      JAVA_HOME="$DETECTED_HOME"
    fi
  fi
  if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    for candidate in /Library/Java/JavaVirtualMachines/*/Contents/Home /opt/homebrew/opt/openjdk*/libexec/openjdk.jdk/Contents/Home; do
      if [ -x "$candidate/bin/java" ]; then
        JAVA_HOME="$candidate"
        break
      fi
    done
  fi
fi

if [ -n "$JAVA_HOME" ]; then
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD="java"
fi

WRAPPER_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_PROPERTIES="$WRAPPER_DIR/.mvn/wrapper/maven-wrapper.properties"

# Read distribution URL
DISTRIBUTION_URL=$(grep '^distributionUrl=' "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)
DISTRIBUTION_NAME=$(basename "$DISTRIBUTION_URL")
MAVEN_VERSION=$(echo "$DISTRIBUTION_NAME" | sed 's/apache-maven-\(.*\)-bin.zip/\1/')

USER_HOME="${HOME:-$USERPROFILE}"
MAVEN_HOME="$USER_HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION"

if [ ! -d "$MAVEN_HOME" ]; then
  mkdir -p "$MAVEN_HOME"
  echo "Downloading Maven $MAVEN_VERSION..."
  TMP_ZIP="/tmp/apache-maven-$MAVEN_VERSION.zip"
  curl -fsSL "$DISTRIBUTION_URL" -o "$TMP_ZIP"
  unzip -q "$TMP_ZIP" -d "$MAVEN_HOME"
  rm -f "$TMP_ZIP"
fi

MVN_EXEC=$(find "$MAVEN_HOME" -name mvn -type f -perm +111 2>/dev/null | head -n 1)
if [ -z "$MVN_EXEC" ]; then
  MVN_EXEC=$(find "$MAVEN_HOME" -name mvn -type f 2>/dev/null | head -n 1)
  chmod +x "$MVN_EXEC"
fi

exec "$MVN_EXEC" "$@"
