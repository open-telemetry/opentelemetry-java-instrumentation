#!/bin/bash -e

grep -Pohr --include '*.java' --exclude-dir=test \"otel.instrumentation.[^\"]+\" \
  | grep -v otel.instrumentation.internal \
  | sort -u
