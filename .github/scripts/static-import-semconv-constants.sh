#!/bin/bash -e

# shellcheck disable=SC2044
for file in $(find instrumentation instrumentation-api -name '*Test.java'); do
  echo "Processing $file"

  # stable semconv

  negative_lookbehind='(?<!import static io.opentelemetry.semconv.)'

  for class in "ClientAttributes" "ErrorAttributes" "ExceptionAttributes" "HttpAttributes" "JvmAttributes" "NetworkAttributes" "OtelAttributes" "ServerAttributes" "ServiceAttributes" "TelemetryAttributes" "UrlAttributes" "UserAgentAttributes"; do
    for attr in $(perl -ne "print \"\$1\n\" if /$negative_lookbehind$class\.([A-Z][A-Z][A-Z_]*)/" "$file" | sort -u); do
      perl -i -pe "s/$negative_lookbehind$class\.$attr/$attr/" "$file"
      sed -i "0,/^import/{s/^import/import static io.opentelemetry.semconv.$class.$attr;\nimport/}" "$file"
    done
  done

  # incubating semconv

  negative_lookbehind='(?<!import static io.opentelemetry.semconv.incubating.)'

  for class in "AwsIncubatingAttributes" "DbIncubatingAttributes" "HttpIncubatingAttributes" "MessagingIncubatingAttributes"; do
    for attr in $(perl -ne "print \"\$1\n\" if /$negative_lookbehind$class\.([A-Z][A-Z][A-Z_]*)/" "$file" | sort -u); do
      perl -i -pe "s/$negative_lookbehind$class\.$attr/$attr/" "$file"
      sed -i "0,/^import/{s/^import/import static io.opentelemetry.semconv.incubating.$class.$attr;\nimport/}" "$file"
    done
  done
done

./gradlew spotlessApply
