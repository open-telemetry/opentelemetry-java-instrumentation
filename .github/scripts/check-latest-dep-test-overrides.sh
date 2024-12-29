#!/bin/bash -e

# all missing version coverage should be documented in supported-libraries.md

if grep -r --include build.gradle.kts latestDepTestLibrary instrumentation \
    | grep -v :+\" \
    | grep -v "// see .* module" \
    | grep -v "// see test suite below" \
    | grep -v "// no longer applicable" \
    | grep -v "// related dependency" \
    | grep -v "// native on-by-default instrumentation after this version" \
    | grep -v "// documented limitation" \
    | grep -v "instrumentation/jaxrs-client/jaxrs-client-2.0-testing/build.gradle.kts"; then

  echo
  echo "Found an undocumented latestDepTestLibrary (see above)."
  echo
  echo "See .gith/scripts/check-latest-dep-test-overrides.sh in this repository"
  echo "and add one of the required comments."
  exit 1
fi
