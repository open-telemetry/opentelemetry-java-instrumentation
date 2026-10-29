#!/bin/bash -e

set -o pipefail

checker_script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check-package-names.sh"
fixture_dir=$(mktemp -d)
trap 'rm -rf "$fixture_dir"' EXIT

check_case() {
  local module_name="$1"
  local package_name="$2"
  local expected_error="$3"
  local output

  rm -rf "$fixture_dir/instrumentation"
  local source_dir="$fixture_dir/instrumentation/$module_name/$source_set/src/main/java/$expected_prefix/$package_name"
  mkdir -p "$source_dir"
  touch "$source_dir/Test.java"

  if output=$(cd "$fixture_dir" && bash -e "$checker_script" 2>&1); then
    if [[ -n "$expected_error" ]]; then
      echo "ERROR: unexpectedly accepted $source_set:$module_name/$package_name"
      exit 1
    fi
  elif [[ -z "$expected_error" || "$output" != "$expected_error"* ]]; then
    echo "ERROR: unexpected failure for $source_set:$module_name/$package_name"
    echo "$output"
    exit 1
  fi
}

for source_set in library javaagent; do
  if [[ "$source_set" == "library" ]]; then
    expected_prefix=io/opentelemetry/instrumentation
  else
    expected_prefix=io/opentelemetry/javaagent/instrumentation
  fi

  while read -r module_name package_name; do
    check_case "$module_name" "$package_name" ""
    check_case "$module_name" wrong/package "ERROR:"
  done <<'EOF'
jaxrs-1.0-annotations jaxrs/v1_0/annotations
jaxrs-2.0-annotations jaxrs/v2_0/annotations
jaxrs-3.0-annotations jaxrs/v3_0/annotations
jaxrs-2.0-common jaxrs/v2_0/common
jaxrs-3.0-common jaxrs/v3_0/common
jaxrs-common jaxrs/common
jedis-common jedis/common
lettuce-common lettuce/common
netty-common netty/common
servlet-common servlet/common
spring-cloud-gateway-common spring/cloud/gateway/common
servlet-common-javax servlet/common/javax
jsf-common-javax jsf/common/javax
jsf-common-jakarta jsf/common/jakarta
EOF

  for module_name in \
    jaxrs-2-annotations jaxrs-2.0.1-annotations jaxrs-2.0-other \
    jaxrs-2-common jaxrs-2.0.1-common jaxrs-2.0-common-javax \
    other-2.0-annotations other-2.0-common other-2-common \
    other-common-variant other-common-javax-extra other-common-jakarta-extra; do
    check_case "$module_name" wrong/package "module name doesn't have a base version:"
  done

  check_case jaxrs-2.0-annotations jaxrs/v2_0 "ERROR:"
  check_case jaxrs-2.0-annotations jaxrs/v2_0/annotationsExtra "ERROR:"
  check_case jaxrs-2.0-common jaxrs/v2_0/commonExtra "ERROR:"
  check_case servlet-common-javax servlet/common/jakarta "ERROR:"
  check_case jsf-common-jakarta jsf/common/javax "ERROR:"
done

echo "Package-name rule tests passed"
