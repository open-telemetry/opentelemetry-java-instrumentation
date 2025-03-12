#!/bin/bash -e

grep -Eohr --include '*.java' --exclude-dir="test*" \"otel.instrumentation.[^\"]+\" \
  | grep -v otel.instrumentation.internal \
  | sort -u
