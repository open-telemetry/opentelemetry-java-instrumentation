#!/bin/bash -e

grep -r --include build.gradle.kts latestDepTestLibrary instrumentation \
  | grep -v :+\" \
  | grep -v "// see .* module" \
  | grep -v "// documented limitation" \
  | grep -v "// related dependency" \
  | grep -v "// native on-by-default instrumentation after this version"
