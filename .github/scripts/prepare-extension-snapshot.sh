#!/usr/bin/env sh
sed -i -e 's/opentelemetryJavaagent[ ]*: "1.3.0"/opentelemetryJavaagent:"1.4.0-SNAPSHOT"/' build.gradle
sed -i -e 's/opentelemetryJavaagentAlpha[ ]*: "1.3.0-alpha"/opentelemetryJavaagentAlpha:"1.4.0-alpha-SNAPSHOT"/' build.gradle