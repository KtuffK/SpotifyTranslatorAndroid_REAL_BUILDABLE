#!/usr/bin/env sh
set -e

if [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "./gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

echo "Gradle wrapper jar missing and system gradle not installed."
exit 1
