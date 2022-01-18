#!/bin/bash -e

git log --reverse --pretty=format:"- %s" "$1"..HEAD \
  | sed -r 's,\(#([0-9]+)\),([#\1](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/\1)),'
echo
