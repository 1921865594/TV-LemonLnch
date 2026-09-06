#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P) || exit
JAVA_EXE=${JAVA_HOME:+$JAVA_HOME/bin/java}
JAVA_EXE=${JAVA_EXE:-java}
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVA_EXE" "-Xmx64m" "-Xms64m" ${JAVA_OPTS:-} ${GRADLE_OPTS:-} \
  -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
