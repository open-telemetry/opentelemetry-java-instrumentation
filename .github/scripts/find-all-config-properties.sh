#!/bin/bash -e

grep -ohr --include '*.java' \"otel.instrumentation.*\" instrumentation | sort -u
