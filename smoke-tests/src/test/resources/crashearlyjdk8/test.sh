#!/bin/bash -e

echo "compiling"
javac CrashEarlyJdk8.java
echo "finish compiling"
echo "executing"
java -javaagent:opentelemetry-javaagent.jar CrashEarlyJdk8
