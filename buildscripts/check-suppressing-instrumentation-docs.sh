#!/usr/bin/env bash

# this script reports some false positives, but is helpful in comparing the keys listed
# in suppressing-instrumentation.md with the keys listed in the source files

set -e -u -o pipefail

comm -3 \
  <(
    sed -n '/----------------------/,${p;/^$/q}' \
        docs/suppressing-instrumentation.md \
      | sed '1d;$d' \
      | cut -d '|' -f 3 \
      | tr -d ' ' \
      | sort -u
  ) \
  <(
    git ls-files '*Module.java' \
      | xargs grep super \
      | grep -oP '(?<=super\(")[^"]+' \
      | sort -u
  )
