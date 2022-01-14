#!/bin/bash -e

sed -r 's,\[#([0-9]+)]\(https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/[0-9]+\),#\1,' CHANGELOG.md \
  | perl -0pe 's/\n +/ /g'
