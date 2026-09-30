#!/bin/bash -e

has_preview_constructor() {
  EXPECTED_OWNER="$simple_module_name" EXPECTED_VERSION="$module_name" perl -0ne '
    my $owner = quotemeta($ENV{EXPECTED_OWNER});
    my $version = quotemeta($ENV{EXPECTED_VERSION});
    my $preview = qr/AgentCommonConfig\.get\(\)\.isV3Preview\(\)/;
    my $first_arg = qr/(?:"$owner"|$preview\s*\?\s*"$owner"\s*:\s*"[^"]+")/;
    # The base version selector is unchanged by v3-preview unless the owner selector changes.
    my $preview_second_arg = qr/(?:$preview\s*\?\s*new\s+String\[\]\s*\{\s*)?"$version"/;
    exit !/super\(\s*$first_arg\s*,\s*$preview_second_arg/;
  ' "$file"
}

for file in $(find "${1:-instrumentation}" -name "*Module.java"); do

  if ! grep -q "extends InstrumentationModule" "$file"; then
    continue
  fi

  if [[ "$file" != *"/javaagent/src/"* ]]; then
    continue
  fi

  module_name=$(echo "$file" | sed 's#.*/\([^/]*\)/javaagent/src/.*#\1#')
  simple_module_name=$(echo "$module_name" | sed 's/-[0-9.]*$//')

  if [[ "$simple_module_name" == *jaxrs* ]]; then
    # TODO these need some work still
    continue
  fi
  if [[ "$simple_module_name" == *jaxws* ]]; then
    # TODO these need some work still
    continue
  fi
  if [[ "$simple_module_name" == jdbc ]]; then
    # TODO split jdbc-datasource out into separate instrumentation?
    # the directory name carries no version, so the expected selectors cannot be derived here
    continue
  fi

  if [ "$module_name" == "$simple_module_name" ]; then
    expected="super\(\n? *\"$simple_module_name\""
  else
    expected="super\(\n? *\"$simple_module_name\",\n? *\"$module_name\""
  fi

  echo "$module_name"

  matches=$(perl -0 -ne "print if /$expected/" "$file" | wc -l)
  if [ "$matches" == 0 ]; then
    if has_preview_constructor; then
      continue
    fi
    if grep -q "expandDeprecatedNames" "$file" \
      && { grep -q "\"$simple_module_name|deprecated:" "$file" \
        || perl -0ne "exit !/super\(\s*\"$simple_module_name\"/" "$file"; } \
      && { [ "$module_name" == "$simple_module_name" ] || grep -q "\"$module_name|deprecated:" "$file"; }
    then
      continue
    fi
    echo "Expected to find $expected in $file"
    exit 1
  fi

done
