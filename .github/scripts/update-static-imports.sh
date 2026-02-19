#!/bin/bash -e

# Fixes checkstyle static import violations for:
#   Objects.requireNonNull   -> import static java.util.Objects.requireNonNull
#   ElementMatchers.<method> -> import static net.bytebuddy.matcher.ElementMatchers.<method>
#   AgentElementMatchers.<method> -> import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.<method>
#   TimeUnit.<CONSTANT>      -> import static java.util.concurrent.TimeUnit.<CONSTANT>
#   StandardCharsets.<CONST> -> import static java.nio.charset.StandardCharsets.<CONST>
#
# After rewriting usages, runs ./gradlew spotlessApply to sort/clean imports.

fix_static_imports() {
  local file="$1"
  local class="$2"
  local package="$3"
  local member_regex="$4"

  local negative_lookbehind="(?<!import static ${package}.)"

  for member in $(perl -ne "print \"\$1\n\" if /${negative_lookbehind}\b${class}\.($member_regex)/" "$file" | sort -u); do
    echo "  $file: $class.$member -> $member"
    perl -i -pe "s/${negative_lookbehind}\b${class}\.${member}\b/${member}/g" "$file"
    # Insert the new static import before the first existing import statement.
    # spotlessApply will sort and deduplicate imports afterward.
    sed -i "0,/^import/{s/^import/import static ${package}.${member};\nimport/}" "$file"
  done
}

while IFS= read -r -d '' file; do
  fix_static_imports "$file" \
    "Objects" \
    "java.util.Objects" \
    "requireNonNull"

  fix_static_imports "$file" \
    "ElementMatchers" \
    "net.bytebuddy.matcher.ElementMatchers" \
    "[a-z][a-zA-Z0-9]*"

  fix_static_imports "$file" \
    "AgentElementMatchers" \
    "io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers" \
    "[a-z][a-zA-Z0-9]*"

  fix_static_imports "$file" \
    "TimeUnit" \
    "java.util.concurrent.TimeUnit" \
    "[A-Z][A-Z_0-9]*"

  fix_static_imports "$file" \
    "StandardCharsets" \
    "java.nio.charset.StandardCharsets" \
    "[A-Z][A-Z_0-9]*"
done < <(find . -name '*.java' -not -path '*/build/*' -not -path '*/.gradle/*' -print0)

./gradlew spotlessApply
