#!/bin/bash -e

echo "compiling"
javac IbmHttpsUrlConnectionTest.java
echo "finish compiling"
echo "executing"
export OTEL_EXPORTER_OTLP_PROTOCOL=grpc
export OTEL_EXPORTER_OTLP_ENDPOINT=http://backend:8080
export OTEL_JAVAAGENT_DEBUG=true
java -javaagent:opentelemetry-javaagent.jar IbmHttpsUrlConnectionTest
